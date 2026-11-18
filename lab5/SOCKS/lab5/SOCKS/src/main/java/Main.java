
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        Integer port = null;
        String dnsSpec = null;
        Log.Level level = Log.Level.INFO;
        Path logFile = null;

        for (String a : args) {
            if (a.startsWith("--log=")) {
                level = Log.Level.valueOf(a.substring("--log=".length()).toUpperCase());
            } else if (a.startsWith("--logfile=")) {
                logFile = Path.of(a.substring("--logfile=".length()));
            } else if (port == null) {
                port = Integer.parseInt(a);
            } else if (dnsSpec == null) {
                dnsSpec = a;
            } else {
                usage();
            }
        }
        if (port == null) usage();

        Log.init(level, logFile);
        Log.info("starting SOCKS5 proxy port=%d dns=%s level=%s file=%s",
                port,
                dnsSpec == null ? "<system>" : dnsSpec,
                level,
                logFile == null ? "<stderr>" : logFile.toAbsolutePath());

        new SocksProxy(port, dnsSpec).run();
    }

    private static void usage() {
        System.err.println("Usage: java socks5.Main <port> [dnsResolverIPv4:port] "
                + "[--log=ERROR|WARN|INFO|DEBUG] [--logfile=PATH]");
        System.exit(1);
    }
}