import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class GraphhopperApiController {

    private final String COUNTRY = "country";
    private final String CITY = "city";
    private final String LAT = "lat";
    private final String LNG = "lng";
    private final String POINT = "point";
    private Properties prop = new Properties();

    public CompletableFuture<List<String>> findPlaces(String placeName) throws IOException {

        HttpClient client = HttpClient.newHttpClient();

        InputStream stream = getClass().getClassLoader().getResourceAsStream("application.properties");
        prop.load(stream);
        String apiKey = prop.getProperty("graphhopper.api.key");

        String uri = String.format("https://graphhopper.com/api/1/geocode?q=%s&locale=en&key=%s", placeName, apiKey);
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

        String cityName;
        String countryName;
        String lat;
        String lng;

        for (int i = 0; i < hits.length(); i++) {
            JSONObject hit = hits.getJSONObject(i);

            countryName = hit.optString(COUNTRY, "Unknown");
            cityName = hit.optString(CITY, "");

            JSONObject point = hit.getJSONObject(POINT);
            lat = point.optString(LAT, "");
            lng = point.optString(LNG, "");

        placeData.add(countryName + " " + cityName + " " + lat + " " + lng);
        }
        return placeData;
    }
}
