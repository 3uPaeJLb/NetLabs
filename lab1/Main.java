import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Main {

    private static final String MULTICAST_GROUP_ADDRESS = "239.255.255.250";
    private static final String APP_UUID = UUID.randomUUID().toString();

    public static void main(String[] args) throws InterruptedException, SocketException {


        //printAllInterfaces();
        Map<String, CopyInfo> copies = new ConcurrentHashMap<>();

        UDPReceiver receiver = new UDPReceiver(MULTICAST_GROUP_ADDRESS, APP_UUID, copies);
        UDPSender   sender   = new UDPSender(MULTICAST_GROUP_ADDRESS, APP_UUID);
        CopyMonitor monitor  = new CopyMonitor(copies);

        Thread senderThread  = new Thread(() -> runSafely(sender), "udp-sender");
        Thread receiveThread = new Thread(() -> runSafely(receiver), "udp-receiver");
        Thread monitorThread = new Thread(monitor, "copy-monitor");

        senderThread.start();
        receiveThread.start();
        monitorThread.start();

        senderThread.join();
        receiveThread.join();
        monitorThread.join();
    }

    private static void runSafely(UDPMulticastNode node) {
        try {
            node.run();
        } catch (Exception e) {
            System.err.println("[" + node.getClass().getSimpleName() + "] " + e.getMessage());
        }
    }
}

