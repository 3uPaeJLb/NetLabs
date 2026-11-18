import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class DnsResolver {

    public interface Callback {
        void onResolved(byte[] ipv4);
        void onFailure();
    }

    private static final int MAX_ATTEMPTS = 3;
    private static final long TIMEOUT_MS = 2000L;
    private static final int MAX_PENDING = 60000;

    private final DatagramChannel channel;
    private final InetSocketAddress resolverAddr;
    private final Map<Integer, Pending> pending = new HashMap<>();

    private static final class Pending {
        final String name;
        final Callback cb;
        int attempts;
        long deadline;
        byte[] wire;
        Pending(String name, Callback cb) { this.name = name; this.cb = cb; }
    }

    public DnsResolver(Selector selector, String resolverSpec) throws IOException {
        this.resolverAddr = parseResolver(resolverSpec);
        this.channel = DatagramChannel.open();
        this.channel.configureBlocking(false);
        this.channel.bind(null);
        this.channel.register(selector, SelectionKey.OP_READ, this);
    }

    InetSocketAddress resolverAddr() { return resolverAddr; }

    private static InetSocketAddress parseResolver(String spec) {
        if (spec != null) {
            int idx = spec.lastIndexOf(':');
            if (idx <= 0) throw new IllegalArgumentException("Bad resolver spec: " + spec);
            String host = spec.substring(0, idx);
            int port;
            try { port = Integer.parseInt(spec.substring(idx + 1)); }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad resolver port: " + spec);
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Bad resolver port: " + spec);
            }
            return new InetSocketAddress(parseIpv4(host), port);
        }
        try {
            java.io.File f = new java.io.File("/etc/resolv.conf");
            if (f.isFile()) {
                try (var r = new java.io.BufferedReader(new java.io.FileReader(f))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("nameserver")) {
                            String[] parts = line.split("\\s+");
                            if (parts.length >= 2) {
                                try {
                                    return new InetSocketAddress(parseIpv4(parts[1]), 53);
                                } catch (IllegalArgumentException e) {
                                    Log.warn("dns: skip non-IPv4 nameserver %s", parts[1]);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) { }
        return new InetSocketAddress(parseIpv4("127.0.0.1"), 53);
    }

    private static InetAddress parseIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) throw new IllegalArgumentException("Not an IPv4 literal: " + host);
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            int v;
            try { v = Integer.parseInt(parts[i]); }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not an IPv4 literal: " + host);
            }
            if (v < 0 || v > 255) throw new IllegalArgumentException("Not an IPv4 literal: " + host);
            b[i] = (byte) v;
        }
        try { return InetAddress.getByAddress(b); }
        catch (UnknownHostException e) {
            throw new IllegalArgumentException("Not an IPv4 literal: " + host);
        }
    }

    public boolean resolve(String name, Callback cb) {
        if (pending.size() >= MAX_PENDING) {
            Log.warn("dns: too many pending (%d), reject %s", pending.size(), name);
            cb.onFailure();
            return false;
        }
        try {
            int id = allocId();
            byte[] wire = buildQuery(name, id);
            Pending p = new Pending(name, cb);
            p.wire = wire;
            pending.put(id, p);
            Log.debug("dns: query id=0x%04x name=%s", id, name);
            if (!sendAttempt(id, p)) {
                pending.remove(id);
                p.cb.onFailure();
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.warn("dns: build query for %s failed: %s", name, e.toString());
            cb.onFailure();
            return false;
        }
    }

    private int allocId() {
        int id = ThreadLocalRandom.current().nextInt(0, 0x10000);
        while (pending.containsKey(id)) id = (id + 1) & 0xFFFF;
        return id;
    }

    private byte[] buildQuery(String name, int id) throws Exception {
        Name n = Name.fromString(name.endsWith(".") ? name : name + ".");
        Record q = Record.newRecord(n, Type.A, DClass.IN);
        Message m = Message.newQuery(q);
        m.getHeader().setID(id);
        return m.toWire();
    }

    private boolean sendAttempt(int id, Pending p) {
        try {
            channel.send(ByteBuffer.wrap(p.wire), resolverAddr);
            p.attempts++;
            p.deadline = System.currentTimeMillis() + TIMEOUT_MS;
            Log.debug("dns: send id=0x%04x attempt=%d name=%s", id, p.attempts, p.name);
            return true;
        } catch (IOException e) {
            Log.warn("dns: send id=0x%04x failed: %s", id, e.toString());
            return false;
        }
    }

    public void onReadable() {
        ByteBuffer bb = ByteBuffer.allocate(1500);
        try {
            while (true) {
                bb.clear();
                SocketAddress src = channel.receive(bb);
                if (src == null) break;
                if (!resolverAddr.equals(src)) {
                    Log.debug("dns: ignore packet from unexpected source %s", src);
                    continue;
                }
                bb.flip();
                handleResponse(bb);
            }
        } catch (IOException e) {
            Log.warn("dns: receive failed: %s", e.toString());
            failAll();
        }
    }

    private void handleResponse(ByteBuffer bb) {
        try {
            byte[] data = new byte[bb.remaining()];
            bb.get(data);
            Message m = new Message(data);

            if (!m.getHeader().getFlag(Flags.QR)) {
                Log.debug("dns: ignore non-response packet");
                return;
            }

            int id = m.getHeader().getID();
            Pending p = pending.get(id);
            if (p == null) {
                Log.debug("dns: unexpected/expired response id=0x%04x", id);
                return;
            }

            Record q = m.getQuestion();
            if (q == null
                    || q.getType() != Type.A
                    || q.getDClass() != DClass.IN
                    || !questionMatches(q.getName(), p.name)) {
                Log.debug("dns: id=0x%04x question mismatch (got %s, expected %s)",
                        id, q == null ? "null" : q.getName(), p.name);
                return;
            }

            pending.remove(id);
            byte[] ip = extractA(m);
            if (ip == null) {
                Log.info("dns: id=0x%04x name=%s no A record (rcode=%s tc=%s)",
                        id, p.name, m.getRcode(), m.getHeader().getFlag(Flags.TC));
                p.cb.onFailure();
            } else {
                Log.info("dns: id=0x%04x name=%s -> %s", id, p.name,
                        InetAddress.getByAddress(ip).getHostAddress());
                p.cb.onResolved(ip);
            }
        } catch (Exception e) {
            Log.debug("dns: malformed response: %s", e.toString());
        }
    }

    private static boolean questionMatches(Name got, String expected) {
        try {
            Name exp = Name.fromString(expected.endsWith(".") ? expected : expected + ".");
            return exp.equals(got);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] extractA(Message m) {
        if (m.getRcode() != Rcode.NOERROR && !m.getHeader().getFlag(Flags.TC)) return null;
        for (Record r : m.getSection(Section.ANSWER)) {
            if (r instanceof ARecord a) return a.getAddress().getAddress();
        }
        return null;
    }

    private void failAll() {
        Log.warn("dns: transport failure, failing %d pending queries", pending.size());
        var it = pending.values().iterator();
        while (it.hasNext()) { it.next().cb.onFailure(); it.remove(); }
    }

    public long onTick() {
        if (pending.isEmpty()) return Long.MAX_VALUE;
        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;
        Iterator<Map.Entry<Integer, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Pending p = e.getValue();
            if (p.deadline <= now) {
                if (p.attempts >= MAX_ATTEMPTS) {
                    it.remove();
                    Log.warn("dns: id=0x%04x name=%s timeout after %d attempts",
                            e.getKey(), p.name, p.attempts);
                    p.cb.onFailure();
                } else {
                    Log.debug("dns: id=0x%04x name=%s timeout, retry", e.getKey(), p.name);
                    if (!sendAttempt(e.getKey(), p)) {
                        it.remove();
                        p.cb.onFailure();
                    } else {
                        next = Math.min(next, p.deadline);
                    }
                }
            } else {
                next = Math.min(next, p.deadline);
            }
        }
        return next;
    }
}