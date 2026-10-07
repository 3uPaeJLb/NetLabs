import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

public class Server {

    private static final int  BUFFER_SIZE              = 4096;
    private static final long SPEED_REPORT_INTERVAL_MS = 3000;
    private static final double MILLIS_PER_SECOND      = 1000.0;
    private static final double BYTES_PER_KB           = 1024.0;

    private final ServerSocket serverSocket;

    public Server(String port) throws IOException {
        serverSocket = new ServerSocket(Integer.parseInt(port));
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            outputStream.write(b);
        }
        return outputStream.toString("UTF-8");
    }

    private FileOutputStream createFile(String fileName) throws IOException {
        File dir = new File("uploads");
        if (!dir.exists()) dir.mkdirs();
        return new FileOutputStream(new File(dir, fileName));
    }

    private void listen(Socket socket) {
        try {
            InputStream in = socket.getInputStream();

            String fileNameStr = readLine(in);
            String fileSizeStr = readLine(in);
            String dataStart   = readLine(in);

            System.out.println("fileNameStr: " + fileNameStr);
            System.out.println("fileSizeStr: " + fileSizeStr);

            if (!"DATA:".equals(dataStart.trim())) {
                System.err.println("Unexpected header: " + dataStart);
                socket.close();
                return;
            }

            String fileName = fileNameStr.replace("NAME:", "");
            long fileSize   = Long.parseLong(fileSizeStr.replace("SIZE:", ""));

            System.out.println("Receiving file: " + fileName + " (" + fileSize + " bytes)");

            FileOutputStream out = createFile(fileName);

            final AtomicLong received = new AtomicLong(0);

            final long startTime = System.currentTimeMillis();
            final long[] lastCheckTime  = { startTime };
            final long[] lastCheckBytes = { 0 };

            Thread monitor = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(SPEED_REPORT_INTERVAL_MS);

                        long now = System.currentTimeMillis();
                        long currentBytes = received.get();

                        long intervalMs = now - lastCheckTime[0];
                        long intervalBytes = currentBytes - lastCheckBytes[0];

                        double instantSpeed = intervalMs > 0
                                ? intervalBytes / (intervalMs / MILLIS_PER_SECOND)
                                : 0.0;

                        long   elapsedMs = now - startTime;

                        double averageSpeed = elapsedMs > 0
                                ? currentBytes / (elapsedMs / MILLIS_PER_SECOND)
                                : 0.0;

                        System.out.printf(
                                "[Speed] Instant: %.2f KB/s | Average: %.2f KB/s | Received: %d / %d bytes%n",
                                instantSpeed / BYTES_PER_KB,
                                averageSpeed / BYTES_PER_KB,
                                currentBytes,
                                fileSize
                        );

                        lastCheckTime[0]  = now;
                        lastCheckBytes[0] = currentBytes;

                        if (currentBytes >= fileSize) break;
                    }
                } catch (InterruptedException e) {
                }
            });
            monitor.setDaemon(true);
            monitor.start();

            byte[] buffer = new byte[BUFFER_SIZE];
            while (received.get() < fileSize) {
                int read = in.read(buffer);
                if (read == -1) break;
                out.write(buffer, 0, read);
                received.addAndGet(read);
            }

            monitor.interrupt();

            out.close();
            socket.close();

            long totalMs    = System.currentTimeMillis() - startTime;
            long totalBytes = received.get();
            double finalAvg = totalMs > 0
                    ? totalBytes / (totalMs / MILLIS_PER_SECOND)
                    : 0.0;

            System.out.printf(
                    "File saved! Received bytes: %d, Total time: %.2f s, Average: %.2f KB/s%n",
                    totalBytes, totalMs / MILLIS_PER_SECOND, finalAvg / BYTES_PER_KB
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run(String serverIP, String port) throws IOException {
        System.out.println("Server is running on host with IP " + serverIP
                + "\nListening on port " + port);

        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("Client connected: " + socket.getInetAddress());

            new Thread(() -> listen(socket)).start();
        }
    }

    public static void main(String[] args) throws IOException {
        String port = args[0];
        Server server = new Server(port);
        String serverIP = InetAddress.getLocalHost().getHostAddress();

        server.run(serverIP, port);
    }
}
