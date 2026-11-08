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
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class WikipediaApiController implements ApiController {

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

    public CompletableFuture<List<String>> findInterestingPlaces(String lat, String lng) {
        String uri = String.format(Locale.ROOT,
                "https://en.wikipedia.org/w/api.php?action=query&list=geosearch"
                        + "&gsradius=10000&gscoord=%s%%7C%s&gslimit=10&format=json",
                lat, lng);

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
                    return PlaceParser.parseGeosearchResponse(response.body());
                });
    }

    public CompletableFuture<PlaceParser.WikiResult> findDescriptionWithImage(String id) {
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
                    return PlaceParser.parseDescriptionAndImage(response.body());
                });
    }

    public CompletableFuture<ImageIcon> loadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(imageUrl))
                .header("User-Agent", "PlaceFinderApp/1.0 (martinvartazaran0416@gmail.com)")
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
}