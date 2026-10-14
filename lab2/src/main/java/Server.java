import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

public class Server implements TcpServer {

    private static final double MILLIS_PER_SECOND = 1000.0;
    private static final double BYTES_PER_KB      = 1024.0;
    private static final double BYTES_PER_MB      = 1024.0 * 1024.0;

    private static final AtomicLong CONNECTION_COUNTER = new AtomicLong(0);

    private final ServerSocket serverSocket;

    public Server(String port) throws IOException {
        serverSocket = new ServerSocket(Integer.parseInt(port));
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == LINE_SEPARATOR) break;
            outputStream.write(b);
        }
        return outputStream.toString("UTF-8");
    }

    private File resolveUniqueFile(File dir, String fileName) {
        File candidate = new File(dir, fileName);
        if (!candidate.exists()) return candidate;

        String baseName = fileName;
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            baseName = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }

        int counter = 1;
        while (candidate.exists()) {
            candidate = new File(dir, baseName + " (" + counter + ")" + extension);
            counter++;
        }
        return candidate;
    }

    private FileOutputStream createFile(String fileName, String clientTag) throws IOException {
        File dir = new File("uploads");
        if (!dir.exists()) dir.mkdirs();

        File target = resolveUniqueFile(dir, fileName);
        System.out.println(clientTag + " Saving to: " + target.getAbsolutePath());
        return new FileOutputStream(target);
    }

    private void listen(Socket socket, long connectionId) {
        String clientTag = "[#" + connectionId + " "
                + socket.getInetAddress().getHostAddress()
                + ":" + socket.getPort() + "]";

        FileOutputStream out = null;
        try {
            InputStream in = socket.getInputStream();

            String fileNameStr = readLine(in);
            String fileSizeStr = readLine(in);
            String dataStart   = readLine(in);

            System.out.println(clientTag + " fileNameStr: " + fileNameStr);
            System.out.println(clientTag + " fileSizeStr: " + fileSizeStr);

            if (!HEADER_DATA.equals(dataStart.trim())) {
                System.err.println(clientTag + " Unexpected header: " + dataStart);
                sendResponse(socket, RESPONSE_ERROR + "unexpected header");
                return;
            }

            String fileName = fileNameStr.replace(HEADER_NAME, "");
            fileName = new File(fileName).getName();

            long fileSize = Long.parseLong(fileSizeStr.replace(HEADER_SIZE, ""));

            System.out.println(clientTag + " Receiving file: " + fileName
                    + " (" + fileSize + " bytes)");

            out = createFile(fileName, clientTag);

            final AtomicLong received = new AtomicLong(0);
            final long startTime = System.currentTimeMillis();
            final long[] lastCheckTime  = { startTime };
            final long[] lastCheckBytes = { 0 };
            final String tag = clientTag;

            Thread monitor = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(SPEED_REPORT_INTERVAL_MS);

                        long now = System.currentTimeMillis();
                        long currentBytes = received.get();

                        long intervalMs    = now - lastCheckTime[0];
                        long intervalBytes = currentBytes - lastCheckBytes[0];

                        double instantSpeed = intervalMs > 0
                                ? intervalBytes / (intervalMs / MILLIS_PER_SECOND)
                                : 0.0;

                        long elapsedMs = now - startTime;
                        double averageSpeed = elapsedMs > 0
                                ? currentBytes / (elapsedMs / MILLIS_PER_SECOND)
                                : 0.0;

                        System.out.printf(
                                "%s [Speed] Instant: %.2f MB/s (%.2f KB/s) | " +
                                        "Average: %.2f MB/s (%.2f KB/s) | Received: %d / %d bytes%n",
                                tag,
                                instantSpeed / BYTES_PER_MB,
                                instantSpeed / BYTES_PER_KB,
                                averageSpeed / BYTES_PER_MB,
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
            out = null;

            long totalMs    = System.currentTimeMillis() - startTime;
            long totalBytes = received.get();
            double finalAvg = totalMs > 0
                    ? totalBytes / (totalMs / MILLIS_PER_SECOND)
                    : 0.0;

            System.out.printf(
                    "%s File saved! Received bytes: %d, Total time: %.2f s, " +
                            "Average: %.2f MB/s (%.2f KB/s)%n",
                    clientTag,
                    totalBytes,
                    totalMs / MILLIS_PER_SECOND,
                    finalAvg / BYTES_PER_MB,
                    finalAvg / BYTES_PER_KB
            );

            if (totalBytes == fileSize) {
                System.out.println(clientTag + " Size check OK: "
                        + totalBytes + " == " + fileSize);
                sendResponse(socket, RESPONSE_OK);
            } else {
                System.out.println(clientTag + " Size check FAILED: got " + totalBytes
                        + ", expected " + fileSize);
                sendResponse(socket, RESPONSE_ERROR + "size mismatch (got "
                        + totalBytes + ", expected " + fileSize + ")");
            }

        } catch (Exception e) {
            System.err.println(clientTag + " Error while receiving file: " + e.getMessage());
            e.printStackTrace();
            sendResponse(socket, RESPONSE_ERROR + e.getMessage());
        } finally {
            if (out != null) {
                try { out.close(); } catch (IOException ignored) {}
            }
            try {
                if (!socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
            System.out.println(clientTag + " Connection closed");
        }
    }

    private void sendResponse(Socket socket, String message) {
        try {
            OutputStream out = socket.getOutputStream();
            out.write((message + LINE_SEPARATOR).getBytes("UTF-8"));
            out.flush();
        } catch (IOException e) {
            System.err.println("Failed to send response: " + e.getMessage());
        }
    }

    @Override
    public void run(String serverIP, String port) throws IOException {
        System.out.println("Server is running on host with IP " + serverIP
                + "\nListening on port " + port);

        while (true) {
            Socket socket = serverSocket.accept();
            long connectionId = CONNECTION_COUNTER.incrementAndGet();

            String clientTag = "[#" + connectionId + " "
                    + socket.getInetAddress().getHostAddress()
                    + ":" + socket.getPort() + "]";
            System.out.println(clientTag + " Client connected");

            new Thread(() -> listen(socket, connectionId)).start();
        }
    }

    @Override
    public void close() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }
}