import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

public class GraphhopperApiController implements ApiController {

    private final Properties prop = new Properties();

    @Override
    public CompletableFuture<List<String>> find(String placeName, String NO_SECOND_ARG) throws IOException {
        HttpClient client = HttpClient.newHttpClient();

        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (stream == null) throw new IOException("application.properties not found");
            prop.load(stream);
        }
        String apiKey = prop.getProperty("graphhopper.api.key");
        String encoded = URLEncoder.encode(placeName, StandardCharsets.UTF_8);
        String uri = String.format(
                "https://graphhopper.com/api/1/geocode?q=%s&locale=en&limit=10&key=%s",
                encoded, apiKey);
        HttpRequest request = HttpRequest.newBuilder().GET().uri(URI.create(uri)).build();

        return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> PlaceParser.parseGraphhopperResponse(response.body()));
    }
}