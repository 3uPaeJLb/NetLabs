import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class WikipediaApiController implements ApiController {

    private static final String QUERY = "query";
    private static final String GEOSEARCH = "geosearch";
    private static final String TITLE = "title";
    private static final String LAT = "lat";
    private static final String LON = "lon";
    private static final String PAGEID = "pageid";
    private static final String PAGES = "pages";
    private static final String EXTRACT = "extract";
    private static final String THUMBNAIL = "thumbnail";
    private static final String SOURCE = "source";

    private final HttpClient client = HttpClient.newHttpClient();
    private final Properties prop = new Properties();

    public WikipediaApiController() throws IOException {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (stream == null) {
                throw new IOException("application.properties not found on classpath");
            }
            prop.load(stream);
        }
    }

    public record WikiResult(String description, String imageUrl) {}

    @Override
    public CompletableFuture<List<String>> find(String first, String second) {
        if (second == null || second.isEmpty()) {
            return findDescriptionWithImage(first)
                    .thenApply(res -> List.of(
                            res.description() + "\u0000" +
                                    (res.imageUrl() != null ? res.imageUrl() : "")));
        }
        return findInterestingPlaces(first, second);
    }

    public CompletableFuture<WikiResult> findDescriptionWithImage(String id) {
        String uri = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts%7Cpageimages"
                + "&exintro=true&explaintext=true&pageids=" + id
                + "&format=json&pithumbsize=400";

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(uri))
                .header("User-Agent", "PlaceFinder/1.0")
                .build();

        return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException(
                                "Wikipedia HTTP " + response.statusCode() + ": " + response.body());
                    }
                    return parseDescriptionAndImage(response.body());
                });
    }

    public CompletableFuture<ImageIcon> loadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(imageUrl))
                .header("User-Agent", "PlaceFinderApp/1.0 (contact@example.com)")
                .build();

        return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        System.err.println("Image HTTP error: " + response.statusCode());
                        return null;
                    }
                    try {
                        Image img = ImageIO.read(new ByteArrayInputStream(response.body()));
                        if (img == null) return null;

                        int w = img.getWidth(null);
                        int h = img.getHeight(null);
                        double scale = Math.min(500.0 / w, 180.0 / h);
                        Image scaled = img.getScaledInstance(
                                (int) (w * scale), (int) (h * scale), Image.SCALE_SMOOTH);
                        return new ImageIcon(scaled);
                    } catch (Exception e) {
                        System.err.println("Image parse error: " + e.getMessage());
                        return null;
                    }
                })
                .exceptionally(ex -> {
                    System.err.println("Image load exception: " + ex.getMessage());
                    return null;
                });
    }

    private CompletableFuture<List<String>> findInterestingPlaces(String lat, String lng) {
        String uri = String.format(Locale.ROOT,
                "https://en.wikipedia.org/w/api.php?action=query&list=geosearch"
                        + "&gsradius=10000&gscoord=%s%%7C%s&gslimit=10&format=json",
                lat, lng);

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(uri))
                .header("User-Agent", "PlaceFinderApp/1.0")
                .build();

        return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException(
                                "Wikipedia HTTP " + response.statusCode() + ": " + response.body());
                    }
                    return parsePlacesResponseToList(response.body());
                });
    }

    private List<String> parsePlacesResponseToList(String response) {
        List<String> interestingPlaces = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(response);
            JSONObject query = json.optJSONObject(QUERY);
            if (query == null) return interestingPlaces;

            JSONArray geosearch = query.optJSONArray(GEOSEARCH);
            if (geosearch == null) return interestingPlaces;

            for (int i = 0; i < geosearch.length() && i < 10; i++) {
                JSONObject place = geosearch.getJSONObject(i);
                interestingPlaces.add(String.format(Locale.ROOT,
                        "id:%d %s %.6f %.6f",
                        place.getInt(PAGEID),
                        place.getString(TITLE),
                        place.getDouble(LAT),
                        place.getDouble(LON)));
            }
        } catch (Exception e) {
            System.err.println("Error parsing Wikipedia places: " + e.getMessage());
            interestingPlaces.add("[Error loading places]");
        }
        return interestingPlaces;
    }

    private WikiResult parseDescriptionAndImage(String response) {
        try {
            JSONObject json = new JSONObject(response);
            JSONObject query = json.getJSONObject(QUERY);
            JSONObject pages = query.getJSONObject(PAGES);

            String pageId = pages.keys().next();
            JSONObject page = pages.getJSONObject(pageId);

            String description = page.optString(EXTRACT, "No description available");
            description = description.replace("\n", " ").trim();
            if (description.length() > 200) {
                description = description.substring(0, 200) + "...";
            }

            String imageUrl = null;
            JSONObject thumbnail = page.optJSONObject(THUMBNAIL);
            if (thumbnail != null) {
                imageUrl = thumbnail.optString(SOURCE, null);
            }

            return new WikiResult(description, imageUrl);
        } catch (Exception e) {
            System.err.println("Error parsing description: " + e.getMessage());
            return new WikiResult("Description not available", null);
        }
    }
}