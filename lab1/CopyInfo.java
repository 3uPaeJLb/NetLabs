import java.time.Instant;

public class CopyInfo {

    private final String ip;
    private final String uuid;
    private volatile Instant lastSeen;
    private volatile int packetsSeen;

    public CopyInfo(String ip, String uuid) {
        this.ip = ip;
        this.uuid = uuid;
        this.lastSeen = Instant.now();
        this.packetsSeen = 1;
    }

    public String getIp() {
        return ip;
    }

    public String getUuid() {
        return uuid;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public int getPacketsSeen() {
        return packetsSeen;
    }

    public void touch() {
        this.lastSeen = Instant.now();
        this.packetsSeen++;
    }

    public long secondsSinceLastSeen() {
        return Instant.now().getEpochSecond() - lastSeen.getEpochSecond();
    }

    @Override
    public String toString() {
        return String.format("CopyInfo{ip=%s, uuid=%s, lastSeen=%s, packets=%d}",
                ip, uuid, lastSeen, packetsSeen);
    }
}