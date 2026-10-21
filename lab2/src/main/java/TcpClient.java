import java.io.IOException;

public interface TcpClient {


    String HEADER_NAME = "NAME:";
    String HEADER_SIZE = "SIZE:";
    String HEADER_DATA = "DATA:";
    String RESPONSE_OK    = "OK";
    String RESPONSE_ERROR = "ERROR:";
    char LINE_SEPARATOR = '\n';
    long MAX_FILE_SIZE       = 1L * 1024 * 1024 * 1024 * 1024;
    int  MAX_FILE_NAME_BYTES = 4096;

    void connect(String ip, String port) throws IOException;

    void run(String filePath) throws IOException;

    void close() throws IOException;
}