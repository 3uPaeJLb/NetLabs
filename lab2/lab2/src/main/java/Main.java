import java.io.IOException;
import java.net.InetAddress;

public class Main {

    public static void main(String[] args) {
        if (args.length == 3) {
            TcpClient client = new Client();
            String serverIP   = args[0];
            String serverPort = args[1];
            String filePath   = args[2];

            Thread clientThread = new Thread(() -> {
                try {
                    client.connect(serverIP, serverPort);
                    client.run(filePath);
                } catch (IOException e) {
                    System.err.println("[Client] Ошибка: " + e.getMessage());
                } finally {
                    try { client.close(); } catch (IOException ignored) {}
                }
            }, "ClientThread");

            clientThread.start();
            joinQuietly(clientThread);

        } else if (args.length == 1) {
            String port = args[0];

            Thread serverThread = new Thread(() -> {
                TcpServer server = null;
                try {
                    server = new Server(port);
                    String serverIP = InetAddress.getLocalHost().getHostAddress();
                    server.run(serverIP, port);
                } catch (IOException e) {
                    System.err.println("[Server] Ошибка: " + e.getMessage());
                } finally {
                    if (server != null) {
                        try { server.close(); } catch (IOException ignored) {}
                    }
                }
            }, "ServerThread");

            serverThread.start();
            joinQuietly(serverThread);

        } else {
            System.err.println("Usage:");
            System.err.println("  Client: java Main <serverIP> <serverPort> <filePath>");
            System.err.println("  Server: java Main <port>");
            System.exit(1);
        }
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}