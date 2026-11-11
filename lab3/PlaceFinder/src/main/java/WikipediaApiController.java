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
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class WikipediaApiController implements ApiController {

    private static final String USER_AGENT =
            "PlaceFinder/1.0 (https://github.com/your-repo/placefinder; martinvartazaran0416@gmail.com)";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

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
                    .thenApply(wr -> List.of(
                            wr.description() != null ? wr.description() : "",
                            wr.imageUrl() != null ? wr.imageUrl() : ""));
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
                .header("User-Agent", USER_AGENT)
                .build();

        return sendForString(request)
                .thenApply(PlaceParser::parseGeosearchResponse);
    }

    public CompletableFuture<PlaceParser.WikiResult> findDescriptionWithImage(String id) {
        String uri = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts%7Cpageimages"
                + "&exintro=true&explaintext=true&pageids=" + id
                + "&format=json&pithumbsize=400";

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(uri))
                .header("User-Agent", USER_AGENT)
                .build();

        return sendForString(request)
                .thenApply(PlaceParser::parseDescriptionAndImage);
    }

    public CompletableFuture<Map<String, PlaceParser.WikiResult>>
    findDescriptionsWithImages(List<String> ids) {

        if (ids == null || ids.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        String joined = String.join("|", ids);
        String uri = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts%7Cpageimages"
                + "&exintro=true&explaintext=true&pageids=" + joined
                + "&format=json&pithumbsize=400";

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(uri))
                .header("User-Agent", USER_AGENT)
                .build();

        return sendForString(request)
                .thenApply(PlaceParser::parseDescriptionsAndImages);
    }

    public CompletableFuture<ImageIcon> loadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(imageUrl))
                .header("User-Agent", USER_AGENT)
                .build();

        return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException(
                                "Image HTTP " + response.statusCode() + " for " + imageUrl);
                    }
                    try {
                        Image img = ImageIO.read(new ByteArrayInputStream(response.body()));
                        if (img == null) {
                            throw new RuntimeException(
                                    "Image decode returned null for " + imageUrl);
                        }
                        int w = img.getWidth(null);
                        int h = img.getHeight(null);
                        double scale = Math.min(500.0 / w, 180.0 / h);
                        Image scaled = img.getScaledInstance(
                                (int) (w * scale), (int) (h * scale), Image.SCALE_SMOOTH);
                        return new ImageIcon(scaled);
                    } catch (IOException e) {
                        throw new RuntimeException(
                                "Image parse error for " + imageUrl + ": " + e.getMessage(), e);
                    }
                });
    }

    private CompletableFuture<String> sendForString(HttpRequest request) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    int code = response.statusCode();
                    if (code == 200) {
                        return CompletableFuture.completedFuture(response.body());
                    }
                    if (code == 429) {
                        long waitSec = response.headers()
                                .firstValue("Retry-After")
                                .map(WikipediaApiController::parseRetryAfter)
                                .orElse(5L);
                        System.err.println(
                                "Wikipedia 429 — retry after " + waitSec + "s");
                        return CompletableFuture
                                .supplyAsync(() -> {
                                    try { Thread.sleep(waitSec * 1000L); }
                                    catch (InterruptedException ignored) { }
                                    return null;
                                })
                                .thenCompose(v -> client.sendAsync(
                                        request, HttpResponse.BodyHandlers.ofString()))
                                .thenApply(retry -> {
                                    if (retry.statusCode() != 200) {
                                        throw new RuntimeException(
                                                "Wikipedia HTTP " + retry.statusCode()
                                                        + " after retry: " + truncate(retry.body()));
                                    }
                                    return retry.body();
                                });
                    }
                    throw new RuntimeException(
                            "Wikipedia HTTP " + code + ": " + truncate(response.body()));
                });
    }

    private static long parseRetryAfter(String value) {
        try {
            return Math.max(1L, Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return 5L;
        }
    }

    private static String truncate(String s) {
        if (s == null) return "<null>";
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }
}