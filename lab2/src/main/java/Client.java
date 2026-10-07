import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client {
    //private static final String PORT = "8000";

    private String fileName;
    private Socket socket;

    private void sendFile(String filePath) {
        try (FileInputStream fileInputStream = new FileInputStream(filePath)){
            int minDataSize = 512;
            byte[] Data = new byte[minDataSize];
            int readCountBytes;

            while (true){
                readCountBytes = fileInputStream.read(Data);

                if (readCountBytes < 0)
                    break;

                socket.getOutputStream().write(Data, 0, readCountBytes);
            }
        }catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Long getFileSize(String filePath) {
        File file = new File(filePath);
        return file.length();
    }

    private String extractFileName(String filePath) {
        File file = new File(filePath);
        fileName = file.getName();
        return fileName;
    }

    public void run(String filePath) throws IOException, InterruptedException {
        try {
            fileName = extractFileName(filePath);
            long fileSize = getFileSize(filePath);

            System.out.println("Sending file: " + fileName + " (" + fileSize + " bytes)");

            socket.getOutputStream().write(("NAME:" + fileName + "\n").getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().write(("SIZE:" + fileSize + "\n").getBytes(StandardCharsets.UTF_8));

            socket.getOutputStream().write("DATA:\n".getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();

            System.out.println("Headers sent, starting file transfer...");

            sendFile(filePath);

            Thread.sleep(1000);

            System.out.println("File sent successfully!");

        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    public Boolean setConnectionWithServer(String ip, String port) throws IOException {
        socket = new Socket(InetAddress.getByName(ip), Integer.parseInt(port));
        System.out.println("Connected to server!");
        return true;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        //String serverIP ="127.0.0.1";
        String serverIP = args[0];
        String serverPort = args[1];

        String filePath = args[2];
        System.out.println("File successful found. Path: " + filePath);
        Client client = new Client();

        client.setConnectionWithServer(serverIP, serverPort);

        client.run(filePath);
    }
}