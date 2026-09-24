import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class CopyMonitor implements Runnable {

    private static final long CHECK_INTERVAL_MS   = 2000;
    private static final long STALE_THRESHOLD_SEC = 6;

    private final Map<String, CopyInfo> copies;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public CopyMonitor(Map<String, CopyInfo> copies) {
        this.copies = copies;
    }

    @Override
    public void run() {
        System.out.println("[MONITOR] Запущен. Порог неактивности: "
                + STALE_THRESHOLD_SEC + " c, интервал: " + CHECK_INTERVAL_MS + " мс");

        while (running.get()) {
            try {
                Thread.sleep(CHECK_INTERVAL_MS);

                copies.entrySet().removeIf(e -> {
                    CopyInfo info = e.getValue();
                    boolean stale = info.secondsSinceLastSeen() > STALE_THRESHOLD_SEC;
                    if (stale) {
                        System.out.println("[MONITOR] КОПИЯ ПОТЕРЯНА: ip=" + info.getIp()
                                + " uuid=" + info.getUuid()
                                + " (молчит " + info.secondsSinceLastSeen() + " c)");
                    }
                    return stale;
                });

                System.out.println("[MONITOR] Активных копий: " + copies.size());
                copies.values().forEach(info ->
                        System.out.println("   -> " + info.getIp()
                                + " | uuid=" + info.getUuid()
                                + " | пакетов=" + info.getPacketsSeen()
                                + " | последний раз=" + info.secondsSinceLastSeen() + " c назад")
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("[MONITOR] Остановлен.");
    }

    public void stop() {
        running.set(false);
    }
}