
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientSession {

    public enum State { GREETING, REQUEST, RESOLVING, CONNECTING, RELAY, CLOSING }

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final int READ_BUDGET = 64 * 1024;
    static final int MAX_BUFFER = 256 * 1024;
    static final int READ_ROOM_MIN = 16 * 1024;
    private static final int READ_CHUNK = 16 * 1024;

    private final int id = SEQ.incrementAndGet();
    private final SocksProxy proxy;

    final SocketChannel client;
    SocketChannel remote;
    State state = State.GREETING;
    boolean closed;

    SelectionKey clientKey;
    SelectionKey remoteKey;

    ByteBuffer clientIn, clientOut, remoteIn, remoteOut;
    boolean clientEof, remoteEof;

    private byte[] dstAddr;
    private String dstName;
    private int dstPort;

    private long bytesClientToRemote;
    private long bytesRemoteToClient;

    private int readBudgetLeft;

    ClientSession(SocketChannel client, SocksProxy proxy) {
        this.client = client;
        this.proxy = proxy;
    }

    void attachClient(SelectionKey k) { this.clientKey = k; }

    String tag() {
        String peer;
        try { peer = String.valueOf(client.getRemoteAddress()); }
        catch (IOException e) { peer = "?"; }
        return Log.tag(id, peer);
    }

    void readFromClient() throws IOException {
        readBudgetLeft = READ_BUDGET;
        while (readBudgetLeft > 0) {
            ByteBuffer grown = Buffers.ensureWritable(clientIn, READ_CHUNK, MAX_BUFFER);
            if (grown == null) return;
            clientIn = grown;
            int n = client.read(clientIn);
            if (n < 0) { clientEof = true; return; }
            if (n == 0) return;
            readBudgetLeft -= n;
        }
    }

    void readFromRemote() throws IOException {
        readBudgetLeft = READ_BUDGET;
        while (readBudgetLeft > 0) {
            ByteBuffer grown = Buffers.ensureWritable(remoteIn, READ_CHUNK, MAX_BUFFER);
            if (grown == null) return;
            remoteIn = grown;
            int n = remote.read(remoteIn);
            if (n < 0) { remoteEof = true; return; }
            if (n == 0) return;
            readBudgetLeft -= n;
        }
    }

    void tryAdvance(SocksProxy ignored) {
        if (closed) return;
        try {
            if (state == State.GREETING) tryGreeting();
            if (state == State.REQUEST) tryRequest();
        } catch (IOException e) {
            closeAll("io error in advance: " + e.getMessage());
        }
    }

    private void tryGreeting() {
        ByteBuffer in = Buffers.toReadMode(clientIn);
        if (in == null || in.remaining() < 2) { clientIn = Buffers.toWriteMode(in); return; }
        int base = in.position();
        if (in.get(base) != 0x05) {
            Log.warn("%s bad greeting VER=0x%02x", tag(), in.get(base) & 0xFF);
            clientIn = Buffers.toWriteMode(in);
            closeAll("bad greeting version");
            return;
        }
        int n = in.get(base + 1) & 0xFF;
        if (in.remaining() < 2 + n) { clientIn = Buffers.toWriteMode(in); return; }

        boolean noAuth = false;
        StringBuilder methods = new StringBuilder();
        for (int i = 0; i < n; i++) {
            byte m = in.get(base + 2 + i);
            methods.append(String.format("%02x", m)).append(i + 1 < n ? "," : "");
            if (m == 0x00) noAuth = true;
        }
        in.position(base + 2 + n);
        clientIn = Buffers.toWriteMode(in);
        Log.debug("%s greeting methods=[%s] noAuth=%s", tag(), methods, noAuth);

        if (!noAuth) {
            Log.info("%s no acceptable auth method", tag());
            enqueueClientOut(new byte[]{0x05, (byte) 0xFF});
            enterClosing();
            return;
        }
        enqueueClientOut(new byte[]{0x05, 0x00});
        state = State.REQUEST;
    }

    private void tryRequest() throws IOException {
        ByteBuffer in = Buffers.toReadMode(clientIn);
        if (in == null || in.remaining() < 4) { clientIn = Buffers.toWriteMode(in); return; }
        int base = in.position();
        if (in.get(base) != 0x05) { clientIn = Buffers.toWriteMode(in); closeAll("bad request VER"); return; }
        byte cmd = in.get(base + 1);
        byte rsv = in.get(base + 2);
        byte atyp = in.get(base + 3);
        if (rsv != 0x00) { clientIn = Buffers.toWriteMode(in); closeAll("bad RSV"); return; }
        if (cmd != 0x01) {
            clientIn = Buffers.toWriteMode(in);
            Log.info("%s unsupported CMD=0x%02x", tag(), cmd & 0xFF);
            sendReply(0x07);
            return;
        }

        int need;
        if (atyp == 0x01) {
            need = 4 + 4 + 2;
        } else if (atyp == 0x03) {
            if (in.remaining() < 5) { clientIn = Buffers.toWriteMode(in); return; }
            int len = in.get(base + 4) & 0xFF;
            need = 4 + 1 + len + 2;
        } else {
            clientIn = Buffers.toWriteMode(in);
            Log.info("%s unsupported ATYP=0x%02x", tag(), atyp & 0xFF);
            sendReply(0x08);
            return;
        }
        if (in.remaining() < need) { clientIn = Buffers.toWriteMode(in); return; }

        if (atyp == 0x01) {
            dstAddr = new byte[4];
            for (int i = 0; i < 4; i++) dstAddr[i] = in.get(base + 4 + i);
            dstPort = ((in.get(base + 8) & 0xFF) << 8) | (in.get(base + 9) & 0xFF);
            Log.info("%s CONNECT ipv4 %s:%d", tag(), ipString(dstAddr), dstPort);
        } else {
            int len = in.get(base + 4) & 0xFF;
            byte[] nameBytes = new byte[len];
            for (int i = 0; i < len; i++) nameBytes[i] = in.get(base + 5 + i);
            dstName = new String(nameBytes, java.nio.charset.StandardCharsets.US_ASCII);
            dstPort = ((in.get(base + 5 + len) & 0xFF) << 8) | (in.get(base + 6 + len) & 0xFF);
            Log.info("%s CONNECT domain %s:%d", tag(), dstName, dstPort);
        }
        in.position(base + need);
        clientIn = Buffers.toWriteMode(in);

        if (dstName != null) {
            state = State.RESOLVING;
            final ClientSession self = this;
            proxy.dns().resolve(dstName, new DnsResolver.Callback() {
                @Override public void onResolved(byte[] ipv4) {
                    if (self.closed) return;
                    self.dstAddr = ipv4;
                    self.dstName = null;
                    self.startConnect();
                }
                @Override public void onFailure() {
                    if (self.closed) return;
                    Log.info("%s dns failure -> REP=0x04", self.tag());
                    self.sendReply(0x04);
                }
            });
        } else {
            startConnect();
        }
    }

    void startConnect() {
        if (closed) return;
        try {
            remote = SocketChannel.open();
            remote.configureBlocking(false);
            InetSocketAddress target = new InetSocketAddress(
                    InetAddress.getByAddress(dstAddr), dstPort);
            Log.debug("%s connecting to %s", tag(), target);
            boolean connected = remote.connect(target);
            remoteKey = remote.register(proxy.selector(),
                    connected ? SelectionKey.OP_READ : SelectionKey.OP_CONNECT, this);
            if (connected) onConnected();
            else state = State.CONNECTING;
            proxy.updateInterestOps(this);
        } catch (IOException e) {
            Log.warn("%s connect to %s:%d failed: %s", tag(),
                    ipString(dstAddr), dstPort, e.toString());
            sendReply(0x05);
        }
    }

    void onConnected() {
        if (closed) return;
        state = State.RELAY;
        Log.info("%s connected to %s:%d", tag(), ipString(dstAddr), dstPort);
        enqueueClientOut(new byte[]{0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0});
    }

    void onConnectFailed() {
        Log.warn("%s finishConnect=false", tag());
        sendReply(0x05);
    }

    void writeToClient() throws IOException {
        if (clientOut == null) return;
        ByteBuffer out = Buffers.toReadMode(clientOut);
        client.write(out);
        clientOut = Buffers.toWriteMode(out);
        if (Buffers.size(clientOut) == 0) clientOut = null;
    }

    void writeToRemote() throws IOException {
        if (remoteOut == null || remote == null) return;
        ByteBuffer out = Buffers.toReadMode(remoteOut);
        remote.write(out);
        remoteOut = Buffers.toWriteMode(out);
        if (Buffers.size(remoteOut) == 0) remoteOut = null;
    }

    void pump() {
        if (closed) return;
        if (state == State.RELAY || state == State.CONNECTING) {
            if (Buffers.size(clientIn) > 0) {
                int n = Buffers.size(clientIn);
                bytesClientToRemote += n;
                ByteBuffer in = Buffers.toReadMode(clientIn);
                clientIn = null;
                enqueueRemoteOut(in);
                if (Log.isDebug()) Log.debug("%s C->R %d B (total %d)", tag(), n, bytesClientToRemote);
            }
            if (Buffers.size(remoteIn) > 0) {
                int n = Buffers.size(remoteIn);
                bytesRemoteToClient += n;
                ByteBuffer in = Buffers.toReadMode(remoteIn);
                remoteIn = null;
                enqueueClientOut(in);
                if (Log.isDebug()) Log.debug("%s R->C %d B (total %d)", tag(), n, bytesRemoteToClient);
            }
        }
        if (state == State.RELAY) {
            if (clientEof && remoteOut == null && remote != null) {
                Log.debug("%s client EOF -> shutdown remote output", tag());
                try { remote.shutdownOutput(); } catch (IOException ignored) {}
            }
            if (remoteEof && clientOut == null) {
                Log.debug("%s remote EOF -> shutdown client output", tag());
                try { client.shutdownOutput(); } catch (IOException ignored) {}
            }
            if (clientEof && remoteEof && clientOut == null && remoteOut == null) {
                Log.info("%s done C->R=%d R->C=%d",
                        tag(), bytesClientToRemote, bytesRemoteToClient);
                closeAll("both sides EOF, queues empty");
                return;
            }
        }
        if (state == State.CLOSING && clientOut == null) {
            closeAll("closing after reply flush");
        }
    }

    void enqueueClientOut(byte[] data) { enqueueClientOut(ByteBuffer.wrap(data)); }

    void enqueueClientOut(ByteBuffer data) {
        if (closed) return;
        clientOut = append(clientOut, data);
    }

    void enqueueRemoteOut(ByteBuffer data) {
        if (closed) return;
        remoteOut = append(remoteOut, data);
    }

    private static ByteBuffer append(ByteBuffer dst, ByteBuffer src) {
        if (src == null || !src.hasRemaining()) return dst;
        ByteBuffer d = Buffers.ensureWritable(dst, src.remaining(), MAX_BUFFER);
        if (d == null) {
            Log.warn("out buffer full, dropping %d bytes (unexpected)", src.remaining());
            return dst;
        }
        d.put(src);
        return d;
    }

    boolean wantsClientWrite() { return Buffers.size(clientOut) > 0; }
    boolean wantsRemoteWrite() { return Buffers.size(remoteOut) > 0; }
    boolean isClientOutFull() { return Buffers.size(clientOut) >= MAX_BUFFER; }
    boolean isRemoteOutFull() { return Buffers.size(remoteOut) >= MAX_BUFFER; }

    boolean hasClientInRoom() {
        return Buffers.writable(clientIn) >= READ_ROOM_MIN
                || Buffers.size(clientIn) < MAX_BUFFER - READ_ROOM_MIN;
    }

    boolean hasRemoteInRoom() {
        return Buffers.writable(remoteIn) >= READ_ROOM_MIN
                || Buffers.size(remoteIn) < MAX_BUFFER - READ_ROOM_MIN;
    }

    void sendReply(int rep) {
        if (closed) return;
        Log.debug("%s reply REP=0x%02x", tag(), rep);
        enqueueClientOut(new byte[]{0x05, (byte) rep, 0x00, 0x01, 0, 0, 0, 0, 0, 0});
        enterClosing();
    }

    private void enterClosing() {
        state = State.CLOSING;
        if (remote != null) {
            try { remote.close(); } catch (IOException ignored) {}
            if (remoteKey != null) {
                try { remoteKey.cancel(); } catch (CancelledKeyException ignored) {}
            }
            remote = null;
            remoteKey = null;
        }
        remoteIn = null;
        remoteOut = null;
        proxy.updateInterestOps(this);
    }

    void closeAll(String reason) {
        if (closed) return;
        closed = true;
        Log.info("%s closing: %s", tag(), reason);
        try { client.close(); } catch (IOException ignored) {}
        if (remote != null) try { remote.close(); } catch (IOException ignored) {}
        if (clientKey != null) { try { clientKey.cancel(); } catch (CancelledKeyException ignored) {} }
        if (remoteKey != null) { try { remoteKey.cancel(); } catch (CancelledKeyException ignored) {} }
        clientIn = clientOut = remoteIn = remoteOut = null;
    }

    private static String ipString(byte[] ip) {
        return (ip[0] & 0xFF) + "." + (ip[1] & 0xFF) + "." + (ip[2] & 0xFF) + "." + (ip[3] & 0xFF);
    }
}