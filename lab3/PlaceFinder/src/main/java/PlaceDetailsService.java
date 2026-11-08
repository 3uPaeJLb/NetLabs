import javax.swing.ImageIcon;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PlaceDetailsService {

    private final OpenWeatherApiController weather;
    private final WikipediaApiController wiki;

    public PlaceDetailsService(OpenWeatherApiController weather,
                               WikipediaApiController wiki) {
        this.weather = weather;
        this.wiki = wiki;
    }

    public CompletableFuture<PlaceDetailsResult> load(String place, String lat, String lng) {

        CompletableFuture<String> weatherFuture =
                weather.find(lat, lng)
                        .thenApply(list -> String.join("\n", list));

        CompletableFuture<List<String>> placesFuture =
                wiki.find(lat, lng);

        return weatherFuture
                .thenCombine(placesFuture, Pair::new)
                .thenCompose(pair -> {

                    String weatherText = pair.weather;
                    List<String> places = pair.places;

                    if (places.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new PlaceDetailsResult(place, weatherText, places,
                                        null, null, null));
                    }

                    String[] parsed = PlaceParser.parseInterestingPlace(places.get(0));
                    if (parsed == null) {
                        return CompletableFuture.completedFuture(
                                new PlaceDetailsResult(place, weatherText, places,
                                        null, null, null));
                    }
                    String pageId = parsed[0];
                    String title  = parsed[1];

                    return wiki.findDescriptionWithImage(pageId)
                            .thenCompose(wikiResult ->
                                    wiki.loadImage(wikiResult.imageUrl())
                                            .thenApply(img -> new PlaceDetailsResult(
                                                    place, weatherText, places,
                                                    title, wikiResult.description(), img)));
                });
    }

    private record Pair(String weather, List<String> places) {}
}