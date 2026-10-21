import java.io.IOException;


public interface TcpServer {

    String HEADER_NAME = "NAME:";
    String HEADER_SIZE = "SIZE:";
    String HEADER_DATA = "DATA:";
    String RESPONSE_OK    = "OK";
    String RESPONSE_ERROR = "ERROR:";
    char LINE_SEPARATOR = '\n';
    int BUFFER_SIZE = 4096;
    long SPEED_REPORT_INTERVAL_MS = 3000;

    void run(String serverIP, String port) throws IOException;

    void close() throws IOException;
}