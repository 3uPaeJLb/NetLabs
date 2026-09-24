import java.io.IOException;
import java.net.*;
import java.util.Map;

public class UDPReceiver implements UDPMulticastNode {

    private static final int PORT = 12345;
    private static final int TIMEOUT_MS = 1000;
    private static final int BUFFER_SIZE = 1024;

    private final String multicastGroup;
    private final CopyChecker copyChecker;

    private volatile boolean running = true;
    private int timeoutCount = 0;

    public UDPReceiver(String multicastGroup, String APP_UUID,
                       Map<String, CopyInfo> copies) {
        this.multicastGroup = multicastGroup;
        this.copyChecker = new CopyChecker(APP_UUID, copies);
    }

    @Override
    public void run() throws IOException {
        NetworkInterface ni = NetworkUtils.pickMulticastInterface();

        MulticastSocket socket = new MulticastSocket(null);
        socket.setReuseAddress(true);
        socket.setSoTimeout(TIMEOUT_MS);
        socket.bind(new InetSocketAddress(PORT));
        socket.setNetworkInterface(ni);
        socket.setLoopbackMode(false);

        InetAddress group = InetAddress.getByName(multicastGroup);
        SocketAddress groupAddr = new InetSocketAddress(group, PORT);
        socket.joinGroup(groupAddr, ni);

        System.out.println("[RECEIVER] Слушаю " + multicastGroup + ":" + PORT
                + " через " + ni.getDisplayName());

        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    copyChecker.checkCopy(packet);
                } catch (SocketTimeoutException e) {
                    timeoutCount++;
                    if (timeoutCount % 10 == 0) {
                        System.out.println("Ждем сообщения... (таймаут " + timeoutCount + ")");
                    }
                }
            }
        } finally {
            try {
                socket.leaveGroup(groupAddr, ni);
            } catch (IOException ignored) {
            }
            socket.close();
        }
    }

    public void stop() {
        running = false;
    }
}