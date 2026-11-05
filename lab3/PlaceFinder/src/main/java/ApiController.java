import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ApiController {

    String NO_SECOND_ARG = "";

    CompletableFuture<List<String>> find(String first, String second) throws IOException;

}