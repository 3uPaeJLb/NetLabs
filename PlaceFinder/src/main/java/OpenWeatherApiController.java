import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class OpenWeatherApiController implements ApiController {

    private static final String TEMP = "temp";
    private static final String FEELS_LIKE = "feels_like";
    private static final String TEMP_MIN = "temp_min";
    private static final String TEMP_MAX = "temp_max";
    private static final String PRESSURE = "pressure";
    private static final String HUMIDITY = "humidity";
    private static final String WIND = "wind";
    private static final String SPEED = "speed";

    private static final double KELVIN_TO_CELSIUS = 273.15;
    private static final double HPA_TO_MMHG = 0.750062;

    private final HttpClient client = HttpClient.newHttpClient();
    private final Properties prop = new Properties();

    public OpenWeatherApiController() throws IOException {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (stream == null) {
                throw new IOException("application.properties not found on classpath");
            }
            prop.load(stream);
        }
    }

    @Override
    public CompletableFuture<List<String>> find(String lat, String lon) {
        String apiKey = prop.getProperty("openweather.api.key");
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("openweather.api.key is missing"));
        }

        String uri = String.format(
                "https://api.openweathermap.org/data/2.5/weather?lat=%s&lon=%s&appid=%s&units=standard",
                lat, lon, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(uri))
                .build();

        return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException(
                                "OpenWeather HTTP " + response.statusCode() + ": " + response.body());
                    }
                    return List.of(parseResponseToString(response.body()));
                })
                .exceptionally(ex -> List.of("Weather error: " + ex.getMessage()));
    }
    private String parseResponseToString(String response) {
        JSONObject json = new JSONObject(response);
        JSONObject main = json.getJSONObject("main");

        double tempC        = kelvinToCelsius(main.getDouble(TEMP));
        double feelsLikeC   = kelvinToCelsius(main.getDouble(FEELS_LIKE));
        double tempMinC     = kelvinToCelsius(main.getDouble(TEMP_MIN));
        double tempMaxC     = kelvinToCelsius(main.getDouble(TEMP_MAX));
        double pressureMmHg = main.getDouble(PRESSURE) * HPA_TO_MMHG;
        int    humidity     = main.getInt(HUMIDITY);

        double windMs = 0.0;
        JSONObject wind = json.optJSONObject(WIND);
        if (wind != null) {
            windMs = wind.optDouble(SPEED, 0.0);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "Temperature: %.1f °C (feels like %.1f °C)%n" +
                        "Min: %.1f °C, Max: %.1f °C%n" +
                        "Pressure: %.1f mmHg%n" +
                        "Humidity: %d%%%n" +
                        "Wind: %.1f m/s",
                tempC, feelsLikeC, tempMinC, tempMaxC,
                pressureMmHg, humidity, windMs));

        return sb.toString();
    }

    private static double kelvinToCelsius(double kelvin) {
        return kelvin - KELVIN_TO_CELSIUS;
    }
}