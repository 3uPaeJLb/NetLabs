import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class GraphhopperApiController implements ApiController{

    private final String COUNTRY = "country";
    private final String CITY = "city";
    private final String LAT = "lat";
    private final String LNG = "lng";
    private final String POINT = "point";
    private static final String OSM_VALUE = "osm_value";
    private Properties prop = new Properties();

    @Override
    public CompletableFuture<List<String>> find(String placeName, String NO_SECOND_ARG) throws IOException {

        HttpClient client = HttpClient.newHttpClient();

        InputStream stream = getClass().getClassLoader().getResourceAsStream("application.properties");
        prop.load(stream);
        String apiKey = prop.getProperty("graphhopper.api.key");
        String encoded = URLEncoder.encode(placeName, StandardCharsets.UTF_8);
        String uri = String.format("https://graphhopper.com/api/1/geocode?q=%s&locale=en&limit=10&key=%s", encoded, apiKey);
        HttpRequest request = HttpRequest.newBuilder().GET().uri(URI.create(uri)).build();

        CompletableFuture<List<String>> places = client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseResponseToList(response.body()));

        return places;
    }

    private List<String> parseResponseToList(String response) {
        List<String> placeData = new ArrayList<>();

        JSONObject json = new JSONObject(response);
        JSONArray hits = json.getJSONArray("hits");

        for (int i = 0; i < hits.length(); i++) {
            JSONObject hit = hits.getJSONObject(i);
            JSONObject point = hit.getJSONObject(POINT);

            placeData.add(String.format(java.util.Locale.ROOT,
                    "country: %s, city: %s, lat: %.6f, lng: %.6f, type: %s",
                    hit.optString(COUNTRY, "Unknown"),
                    hit.optString(CITY, ""),
                    point.getDouble(LAT),
                    point.getDouble(LNG),
                    hit.optString(OSM_VALUE, "unknown")));
        }
        return placeData;
    }
}
