import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Main {


    private static String multicastGroupAddress;
    private static final String APP_UUID = UUID.randomUUID().toString();

    public Main() throws IOException {
    }

    public static void main(String[] args) throws InterruptedException, IOException {

        NetworkInterface ni = NetworkUtils.pickMulticastInterface();

        if (args.length < 1) {
            System.err.println("Usage: java Main <multicast-group-address>");
            System.err.println("Example: java Main 239.255.255.250");
            System.exit(1);
        }
        multicastGroupAddress = args[0];
        //printAllInterfaces();
        Map<String, CopyInfo> copies = new ConcurrentHashMap<>();

        UDPReceiver receiver = new UDPReceiver(multicastGroupAddress, APP_UUID, copies, ni);
        UDPSender   sender   = new UDPSender(multicastGroupAddress, APP_UUID, ni);
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

