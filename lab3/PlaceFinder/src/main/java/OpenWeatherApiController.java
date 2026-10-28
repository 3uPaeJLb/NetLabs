import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class OpenWeatherApiController {

    private final String TEMP = "temp";
    private final String FEELS_LIKE = "feels_like";
    private final String TEMP_MIN = "temp_min";
    private final String TEMP_MAX = "temp_max";
    private final String PRESSURE = "pressure";
    private final String HUMIDITY = "humidity";

    private final Properties prop = new Properties();

    CompletableFuture<String> findWeather(String lat, String lon) throws IOException {

        HttpClient client = HttpClient.newHttpClient();

        InputStream stream = getClass().getClassLoader().getResourceAsStream("application.properties");
        prop.load(stream);
        String apiKey = prop.getProperty("openweather.api.key");

        String uri = String.format("https://api.openweathermap.org/data/2.5/weather?lat=%s&lon=%s&appid=%s",
                lat, lon, apiKey);

        HttpRequest request = HttpRequest.newBuilder().GET().uri(URI.create(uri)).build();

        return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseResponseToString(response.body()));
    }

    private String parseResponseToString(String response) {
        JSONObject json = new JSONObject(response);
        JSONObject main = json.getJSONObject("main");

        return String.format(
                "Temperature: %.1f K (feels like %.1f K)%n" +
                        "Min: %.1f K, Max: %.1f K%n" +
                        "Pressure: %d hPa%n" +
                        "Humidity: %d%%",
                main.getDouble(TEMP), main.getDouble(FEELS_LIKE),
                main.getDouble(TEMP_MIN), main.getDouble(TEMP_MAX),
                main.getInt(PRESSURE), main.getInt(HUMIDITY)
        );

    }
}
