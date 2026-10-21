import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client implements TcpClient {

    private static final int SEND_BUFFER_SIZE = 512;

    private String fileName;
    private Socket socket;

    private void sendFile(String filePath) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(filePath)) {
            byte[] data = new byte[SEND_BUFFER_SIZE];
            int readCountBytes;
            OutputStream out = socket.getOutputStream();

            while ((readCountBytes = fileInputStream.read(data)) != -1) {
                out.write(data, 0, readCountBytes);
            }
            out.flush();
        }
    }

    private long getFileSize(String filePath) {
        return new File(filePath).length();
    }

    private String extractFileName(String filePath) {
        return new File(filePath).getName();
    }

    private String readResponse() throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        return reader.readLine();
    }

    @Override
    public void run(String filePath) throws IOException {
        try {
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                throw new IOException("Файл не найден или не является обычным файлом: " + filePath);
            }

            fileName = extractFileName(filePath);
            long fileSize = getFileSize(filePath);

            int nameBytes = fileName.getBytes(StandardCharsets.UTF_8).length;
            if (nameBytes > MAX_FILE_NAME_BYTES) {
                throw new IOException("Длина имени файла превышает " + MAX_FILE_NAME_BYTES
                        + " байт: " + nameBytes);
            }

            if (fileSize > MAX_FILE_SIZE) {
                throw new IOException("Размер файла превышает 1 ТБ: " + fileSize + " байт");
            }

            System.out.println("Sending file: " + fileName + " (" + fileSize + " bytes)");

            OutputStream out = socket.getOutputStream();

            out.write((HEADER_NAME + fileName + LINE_SEPARATOR)
                    .getBytes(StandardCharsets.UTF_8));
            out.write((HEADER_SIZE + fileSize + LINE_SEPARATOR)
                    .getBytes(StandardCharsets.UTF_8));
            out.write((HEADER_DATA + LINE_SEPARATOR)
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();

            System.out.println("Headers sent, starting file transfer...");

            sendFile(filePath);

            System.out.println("File sent, waiting for server response...");

            String response = readResponse();
            if (response == null) {
                System.out.println("Server closed connection without response.");
            } else if (RESPONSE_OK.equals(response)) {
                System.out.println("Server confirmed: file received successfully.");
            } else if (response.startsWith(RESPONSE_ERROR)) {
                System.out.println("Server reported error: " + response);
            } else {
                System.out.println("Unexpected server response: " + response);
            }

        } finally {
            close();
        }
    }

    @Override
    public void connect(String ip, String port) throws IOException {
        socket = new Socket(InetAddress.getByName(ip), Integer.parseInt(port));
        System.out.println("Connected to server!");
    }

    @Override
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}