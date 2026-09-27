import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public class CopyChecker {

    private final String uuId;
    private final Map<String, CopyInfo> copies;

    public CopyChecker(String APP_UUID, Map<String, CopyInfo> copies) {
        this.uuId = APP_UUID;
        this.copies = copies;
    }

    private static UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void checkCopy(DatagramPacket packet) {
        String data = new String(packet.getData(), 0, packet.getLength(),
                StandardCharsets.UTF_8);

        if (!data.startsWith("COPYFINDER|")) {
            return;
        }

        String[] parts = data.split("\\|");
        if (parts.length < 4) {
            return;
        }

        String senderUuidStr = parts[3];
        UUID senderUuid = parseUuid(senderUuidStr);
        if (senderUuid == null) {
            return;
        }
        if (senderUuid.toString().equals(uuId)) {
            return;
        }

        InetAddress senderAddr = packet.getAddress();
        if (senderAddr == null) {
            return;
        }
        String copyIP = senderAddr.getHostAddress();

        String key = copyIP + "#" + senderUuid;

        CopyInfo existing = copies.get(key);
        if (existing != null) {
            existing.touch();
            return;
        }

        final boolean[] created = {false};
        copies.computeIfAbsent(key, k -> {
            created[0] = true;
            return new CopyInfo(copyIP, senderUuid.toString());
        });

        if (created[0]) {
            System.out.println("[CHECKER] НОВАЯ КОПИЯ: ip=" + copyIP
                    + " uuid=" + senderUuid);
        }
    }
}