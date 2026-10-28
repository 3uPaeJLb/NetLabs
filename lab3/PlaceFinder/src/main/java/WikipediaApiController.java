import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class WikipediaApiController {
    private final String QUERY = "query";
    private final String GEOSEARCH = "geosearch";
    private final String TITLE = "title";
    private final String LAT = "lat";
    private final String LON = "lon";
    private final String PAGEID = "pageid";
    private final String PAGES = "pages";
    private final String EXTRACT = "extract";

    private final Properties prop = new Properties();

    public CompletableFuture<List<String>> findInterestingPlaces(String lat, String lng) throws IOException {
        HttpClient client = HttpClient.newHttpClient();

        String uri = String.format(
                "https://en.wikipedia.org/w/api.php?action=query&list=geosearch&gsradius=10000&gscoord=%s%%7C%s&gslimit=5&format=json",
                lat, lng
        );

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(uri))
                .header("User-Agent", "PlaceFinderApp/1.0")
                .build();

        CompletableFuture<List<String>> interestingPlaces = client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parsePlacesResponseToList(response.body()));

        return interestingPlaces;
    }

    private List<String> parsePlacesResponseToList(String response) {
        List<String> interestingPlaces = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(response);
            JSONObject query = json.getJSONObject(QUERY);
            JSONArray geosearch = query.getJSONArray(GEOSEARCH);

            for (int i = 0; i < geosearch.length() && i < 10; i++) {
                JSONObject place = geosearch.getJSONObject(i);
                String title = place.getString(TITLE);
                double lat = place.getDouble(LAT);
                double lng = place.getDouble(LON);
                int pageId = place.getInt(PAGEID);

                interestingPlaces.add(String.format("id:%d %s %.6f %.6f", pageId, title, lat, lng));
            }

        } catch (Exception e) {
            interestingPlaces.add("Error loading places");
        }

        return interestingPlaces;
    }


    public CompletableFuture<List<String>> findDescOfInterestPlaces(CompletableFuture<List<String>> interestingPlaces) {
        List<CompletableFuture<String>> descriptionOfPlaces = new ArrayList<>();

        return interestingPlaces.thenCompose(places -> {
            for (String place : places) {
                String pageId = extractPageId(place);

                String uri = String.format(
                        "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=true&explaintext=true&pageids=%s&format=json",
                        pageId
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create(uri))
                        .header("User-Agent", "PlaceFinder/1.0")
                        .build();

                CompletableFuture<String> descriptionOfPlace = HttpClient.newHttpClient()
                        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenApply(response -> {
                            String description = parseDescriptionResponse(response.body());
                            return place + " - " + description;
                        });

                descriptionOfPlaces.add(descriptionOfPlace);
            }

            return CompletableFuture.allOf(descriptionOfPlaces.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        List<String> results = new ArrayList<>();
                        for (CompletableFuture<String> future : descriptionOfPlaces) {
                            results.add(future.join());
                        }
                        return results;
                    });
        });
    }

    public CompletableFuture<String> findDescOfInterestPlaceById(String id) {

        HttpClient client = HttpClient.newHttpClient();

        String uri = String.format(
                "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=true&explaintext=true&pageids=%s&format=json",
                    id
                );

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(uri))
                .header("User-Agent", "PlaceFinder/1.0")
                .build();

        return client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseDescriptionResponse(response.body()));

    }

    private String parseDescriptionResponse(String response) {
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

            return description;

        } catch (Exception e) {
            System.err.println("Error parsing description: " + e.getMessage());
            return "Description not available";
        }
    }

    public String extractPageId(String place) {
        try {
            if (place.startsWith("id:")) {
                int firstSpaceIndex = place.indexOf(' ');

                return place.substring(3, firstSpaceIndex);
            }
        } catch (Exception e) {
            System.err.println("Error extracting pageId from: " + place);
        }
        return "0";
    }
}
