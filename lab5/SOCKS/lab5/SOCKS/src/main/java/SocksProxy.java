import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class SocksProxy {

    private final Selector selector;
    private final ServerSocketChannel server;
    private final DnsResolver dns;
    private long nextDnsDeadline = Long.MAX_VALUE;

    public SocksProxy(int port, String dnsSpec) throws IOException {
        this.selector = Selector.open();
        this.server = ServerSocketChannel.open();
        this.server.bind(new InetSocketAddress(port));
        this.server.configureBlocking(false);
        this.server.register(selector, SelectionKey.OP_ACCEPT);
        this.dns = new DnsResolver(selector, dnsSpec);
        Log.info("listening on %s, dns=%s", server.getLocalAddress(), dns.resolverAddr());
    }

    Selector selector() { return selector; }
    DnsResolver dns() { return dns; }

    public void run() {
        Log.info("event loop started");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                long timeout = (nextDnsDeadline == Long.MAX_VALUE)
                        ? 0 : Math.max(0, nextDnsDeadline - System.currentTimeMillis());
                int n = selector.select(timeout);
                if (n > 0) {
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> it = keys.iterator();
                    while (it.hasNext()) {
                        SelectionKey k = it.next();
                        it.remove();
                        try {
                            handleKey(k);
                        } catch (CancelledKeyException e) {
                            Log.debug("cancelled key: %s", k);
                        } catch (IOException e) {
                            Object att = k.attachment();
                            if (att instanceof ClientSession cs) {
                                Log.warn("I/O error in session %s: %s", cs.tag(), e.toString());
                                cs.closeAll("io error: " + e.getMessage());
                            } else {
                                Log.error("I/O error: %s", e.toString());
                            }
                        } catch (RuntimeException e) {
                            Object att = k.attachment();
                            if (att instanceof ClientSession cs) cs.closeAll("runtime: " + e);
                            Log.error("unexpected error: %s", e);
                            e.printStackTrace();
                        }
                    }
                }
                try {
                    nextDnsDeadline = dns.onTick();
                } catch (RuntimeException e) {
                    Log.error("dns tick error: %s", e);
                }
            }
        } catch (IOException e) {
            Log.error("fatal: %s", e.toString());
        } finally {
            Log.info("event loop stopped");
        }
    }

    private void handleKey(SelectionKey k) throws IOException {
        if (!k.isValid()) return;
        Object att = k.attachment();

        if (k.isAcceptable()) { acceptNew(); return; }
        if (att == dns) { if (k.isReadable()) dns.onReadable(); return; }
        if (att instanceof ClientSession cs) handleSession(cs, k);
    }

    private void handleSession(ClientSession cs, SelectionKey k) throws IOException {
        if (cs.closed) return;
        if (Log.isDebug()) Log.debug("%s ready %s", cs.tag(), describeOps(k.readyOps()));

        if (k.channel() == cs.client) {
            if (k.isValid() && k.isReadable()) cs.readFromClient();
            if (cs.closed) return;
            if (cs.state == ClientSession.State.GREETING
                    || cs.state == ClientSession.State.REQUEST) {
                cs.tryAdvance(this);
            }
            if (cs.closed) return;
            if (k.isValid() && k.isWritable()) cs.writeToClient();
        } else if (k.channel() == cs.remote) {
            if (k.isValid() && k.isConnectable()) {
                boolean ok;
                try { ok = cs.remote.finishConnect(); }
                catch (IOException e) { ok = false; Log.warn("%s finishConnect: %s", cs.tag(), e.toString()); }
                if (ok) cs.onConnected(); else cs.onConnectFailed();
                if (cs.closed) return;
            }
            if (k.isValid() && k.isReadable()) cs.readFromRemote();
            if (cs.closed) return;
            if (k.isValid() && k.isWritable()) cs.writeToRemote();
        }

        cs.pump();
        if (!cs.closed) updateInterestOps(cs);
    }

    private void acceptNew() throws IOException {
        SocketChannel ch;
        while ((ch = server.accept()) != null) {
            ch.configureBlocking(false);
            ClientSession cs = new ClientSession(ch, this);
            SelectionKey k = ch.register(selector, SelectionKey.OP_READ, cs);
            cs.attachClient(k);
            Log.info("accepted %s", cs.tag());
        }
    }

    void updateInterestOps(ClientSession cs) {
        if (cs.closed) return;
        try {
            if (cs.clientKey != null && cs.clientKey.isValid()) {
                int ops = 0;
                boolean canRead = !cs.clientEof
                        && cs.state != ClientSession.State.CLOSING
                        && !cs.isRemoteOutFull()
                        && cs.hasClientInRoom();
                if (canRead) ops |= SelectionKey.OP_READ;
                if (cs.wantsClientWrite()) ops |= SelectionKey.OP_WRITE;
                if (cs.clientKey.interestOps() != ops) {
                    if (Log.isDebug()) Log.debug("%s client ops -> %s", cs.tag(), describeOps(ops));
                    cs.clientKey.interestOps(ops);
                }
            }
            if (cs.remoteKey != null && cs.remoteKey.isValid() && cs.remote != null) {
                int ops;
                if (cs.state == ClientSession.State.CONNECTING) {
                    ops = SelectionKey.OP_CONNECT;
                } else if (cs.state == ClientSession.State.RELAY) {
                    ops = 0;
                    boolean canRead = !cs.remoteEof
                            && !cs.isClientOutFull()
                            && cs.hasRemoteInRoom();
                    if (canRead) ops |= SelectionKey.OP_READ;
                    if (cs.wantsRemoteWrite()) ops |= SelectionKey.OP_WRITE;
                } else {
                    ops = 0;
                }
                if (cs.remoteKey.interestOps() != ops) {
                    if (Log.isDebug()) Log.debug("%s remote ops -> %s", cs.tag(), describeOps(ops));
                    cs.remoteKey.interestOps(ops);
                }
            }
        } catch (CancelledKeyException ignored) {
            cs.closeAll("cancelled key");
        }
    }

    private static String describeOps(int ops) {
        if (ops == 0) return "0";
        StringBuilder sb = new StringBuilder();
        if ((ops & SelectionKey.OP_READ) != 0) sb.append("R");
        if ((ops & SelectionKey.OP_WRITE) != 0) sb.append("W");
        if ((ops & SelectionKey.OP_CONNECT) != 0) sb.append("C");
        if ((ops & SelectionKey.OP_ACCEPT) != 0) sb.append("A");
        return sb.toString();
    }
}