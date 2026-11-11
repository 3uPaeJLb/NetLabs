    import java.util.List;
    import java.util.Objects;
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
                            .thenApply( list -> String.join("\n", list));

            CompletableFuture<List<String>> placesFuture = wiki.find(lat, lng);

            return weatherFuture
                    .thenCombine(placesFuture, Pair::new)
                    .thenCompose(pair -> loadAllDetails(place, pair.weather, pair.places));
        }

        private CompletableFuture<PlaceDetailsResult> loadAllDetails(
                String placeName,
                String weatherText,
                List<String> places) {

            List<String[]> parsedPlaces = places.stream()
                    .map(PlaceParser::parseInterestingPlace)
                    .filter(Objects::nonNull)
                    .toList();

            if (parsedPlaces.isEmpty()) {
                return CompletableFuture.completedFuture(
                        new PlaceDetailsResult(placeName, weatherText, places, List.of()));
            }

            List<CompletableFuture<PlaceDetailsResult.PlaceDetail>> futures =
                    parsedPlaces.stream()
                            .map(parsed -> {
                                String pageId = parsed[0];
                                String title  = parsed[1];

                                return wiki.findDescriptionWithImage(pageId)
                                        .thenCompose(wr ->
                                                wiki.   loadImage(wr.imageUrl())
                                                        .handle((img, imgEx) -> {
                                                            if (imgEx != null) {
                                                                System.err.println(
                                                                        "Image failed for pageId="
                                                                                + pageId + ": "
                                                                                + rootMessage(imgEx));
                                                                return null;
                                                            }
                                                            return img;
                                                        })
                                                        .thenApply(img ->
                                                                new PlaceDetailsResult.PlaceDetail(
                                                                        pageId,
                                                                        title,
                                                                        wr.description(),
                                                                        img,
                                                                        null)))
                                            .handle((detail, ex) -> {
                                            if (ex != null) {
                                                return new PlaceDetailsResult.PlaceDetail(
                                                        pageId, title, null, null,
                                                        rootMessage(ex));
                                            }
                                            return detail;
                                        });
                            })
                            .toList();

            return CompletableFuture
                    .allOf(futures.toArray(CompletableFuture[]::new))
                    .thenApply(v -> futures.stream()
                            .map(CompletableFuture::join)
                            .toList())
                    .thenApply(details ->
                            new PlaceDetailsResult(placeName, weatherText, places, details));
        }

        private static String rootMessage(Throwable ex) {
            Throwable t = ex;
            while (t.getCause() != null) t = t.getCause();
            return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
        }

        private record Pair(String weather, List<String> places) {}
    }