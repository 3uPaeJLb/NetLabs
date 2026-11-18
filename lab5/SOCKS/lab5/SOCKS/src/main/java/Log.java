import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Log {

    public enum Level { ERROR, WARN, INFO, DEBUG }

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final int QUEUE_CAPACITY = 65536;
    private static final BlockingQueue<String> QUEUE = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private static final AtomicLong DROPPED = new AtomicLong();

    private static volatile Level level = Level.INFO;
    private static volatile Writer out;
    private static Thread writerThread;

    private Log() {}

    public static synchronized void init(Level lvl, Path file) throws IOException {
        level = lvl;
        if (out != null) return;
        Writer w;
        if (file == null) {
            w = new BufferedWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8));
        } else {
            if (file.toAbsolutePath().getParent() != null) {
                Files.createDirectories(file.toAbsolutePath().getParent());
            }
            w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        out = w;
        writerThread = new Thread(Log::writerLoop, "log-writer");
        writerThread.setDaemon(true);
        writerThread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(Log::flushAndClose, "log-shutdown"));
    }

    public static boolean isDebug() { return level.ordinal() >= Level.DEBUG.ordinal(); }
    public static boolean isInfo()  { return level.ordinal() >= Level.INFO.ordinal(); }

    public static void error(String fmt, Object... a) { log(Level.ERROR, fmt, a); }
    public static void warn (String fmt, Object... a) { log(Level.WARN,  fmt, a); }
    public static void info (String fmt, Object... a) { log(Level.INFO,  fmt, a); }
    public static void debug(String fmt, Object... a) { log(Level.DEBUG, fmt, a); }

    public static void log(Level lvl, String fmt, Object... args) {
        if (lvl.ordinal() > level.ordinal()) return;
        String msg = (args == null || args.length == 0) ? fmt : safeFormat(fmt, args);
        String line = LocalDateTime.now().format(TS)
                + " [" + pad(lvl.name()) + "] "
                + "[" + Thread.currentThread().getName() + "] "
                + msg;
        if (!QUEUE.offer(line)) DROPPED.incrementAndGet();
    }

    private static String safeFormat(String fmt, Object[] args) {
        try { return String.format(fmt, args); }
        catch (RuntimeException e) { return fmt + " " + java.util.Arrays.toString(args); }
    }

    private static String pad(String s) { return (s + "     ").substring(0, 5); }

    private static void writerLoop() {
        try {
            while (true) {
                String line = QUEUE.poll(1, TimeUnit.SECONDS);
                if (line == null) {
                    long d = DROPPED.getAndSet(0);
                    if (d > 0) {
                        out.write(LocalDateTime.now().format(TS)
                                + " [WARN ] [log-writer] dropped " + d + " records\n");
                    }
                    out.flush();
                    continue;
                }
                out.write(line);
                out.write('\n');
                if (QUEUE.isEmpty()) out.flush();
            }
        } catch (InterruptedException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void flushAndClose() {
        try {
            long d = DROPPED.getAndSet(0);
            if (d > 0 && out != null) {
                out.write(LocalDateTime.now().format(TS)
                        + " [WARN ] [shutdown] dropped " + d + " records\n");
            }
            String line;
            while ((line = QUEUE.poll()) != null) { out.write(line); out.write('\n'); }
            if (out != null) { out.flush(); out.close(); }
        } catch (IOException ignored) { }
    }

    public static String tag(int id, String peer) {
        return "#" + Integer.toHexString(id) + (peer == null ? "" : " " + peer);
    }
}