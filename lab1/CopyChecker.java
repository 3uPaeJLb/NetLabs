import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Map;

public class CopyChecker {

    private final String uuId;
    private final Map<String, CopyInfo> copies;

    public CopyChecker(String APP_UUID, Map<String, CopyInfo> copies) {
        this.uuId = APP_UUID;
        this.copies = copies;
    }

    public void checkCopy(DatagramPacket packet) {
        String data = new String(packet.getData(), 0, packet.getLength());

        if (!data.startsWith("COPYFINDER|")) {
            return;
        }

        String[] parts = data.split("\\|");
        if (parts.length < 5) {
            return;
        }

        String copyIP     = parts[1];
        String senderUuid = parts[4];

        if (senderUuid.equals(uuId)) {
            return;
        }

        String key = copyIP + "#" + senderUuid;

        CopyInfo existing = copies.get(key);
        if (existing != null) {
            existing.touch();
            return;
        }

        final boolean[] created = {false};
        copies.computeIfAbsent(key, k -> {
            created[0] = true;
            return new CopyInfo(copyIP, senderUuid);
        });

        if (created[0]) {
            System.out.println("[CHECKER] НОВАЯ КОПИЯ: ip=" + copyIP + " uuid=" + senderUuid);
        }
    }
}