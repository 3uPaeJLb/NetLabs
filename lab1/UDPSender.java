import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;

public class UDPSender implements UDPMulticastNode {

    private static final int PORT = 12345;
    private static final int SEND_INTERVAL_MS = 2000;

    private final String multicastGroupAddress;
    private final String APP_UUID;
    private final NetworkInterface ni;

    private volatile boolean running = true;
    private int packetCount = 0;

    public UDPSender(String multicastGroupAddress, String APP_UUID, NetworkInterface ni) {
        this.multicastGroupAddress = multicastGroupAddress;
        this.APP_UUID = APP_UUID;
        this.ni = ni;
    }

    @Override
    public void run() throws IOException, InterruptedException {
        MulticastSocket sendSocket = new MulticastSocket();
        sendSocket.setNetworkInterface(ni);
        sendSocket.setTimeToLive(1);
        sendSocket.setLoopbackMode(false);

        InetAddress group = InetAddress.getByName(multicastGroupAddress);

        System.out.println("[SENDER] Отправляю в " + multicastGroupAddress + ":" + PORT
                + " через " + ni.getDisplayName()
                + " (локальный IP " + NetworkUtils.resolveLocalIp(ni) + ")");

        try {
            while (running) {
                packetCount++;

                String message = String.format(
                        "COPYFINDER|%d|%s|%s",
                        packetCount, LocalTime.now(), APP_UUID
                );

                byte[] data = message.getBytes(StandardCharsets.UTF_8);
                DatagramPacket sendPacket = new DatagramPacket(data, data.length, group, PORT);
                sendSocket.send(sendPacket);

                Thread.sleep(SEND_INTERVAL_MS);
            }
        } finally {
            sendSocket.close();
        }
    }

    public void stop() {
        running = false;
    }
}