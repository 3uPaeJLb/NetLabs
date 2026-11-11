import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceParser {

    private PlaceParser() {}

    private static final Pattern LAT_LNG = Pattern.compile(
            "lat:\\s*(-?\\d+(?:\\.\\d+)?),\\s*lng:\\s*(-?\\d+(?:\\.\\d+)?)");

    private static final Pattern INTERESTING_PLACE = Pattern.compile(
            "^id:(\\d+)\\s+(.+?)\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)$");

    /** Ошибка разбора ответа Wikipedia — не «данных нет», а «формат не тот». */
    public static class ParseException extends RuntimeException {
        public ParseException(String message) { super(message); }
        public ParseException(String message, Throwable cause) { super(message, cause); }
    }

    public record Coordinates(double lat, double lng) {}

    public record InterestingPlace(int pageId, String title, double lat, double lon) {}

    public record WikiResult(String description, String imageUrl) {}

    @Deprecated
    public static String formatPlace(String country, String city,
                                     double lat, double lng, String osmValue) {
        return String.format(Locale.ROOT,
                "country: %s, city: %s, lat: %.6f, lng: %.6f, type: %s",
                country, city, lat, lng, osmValue);
    }

    @Deprecated
    public static String formatInterestingPlace(int pageId, String title,
                                                double lat, double lon) {
        return String.format(Locale.ROOT, "id:%d %s %.6f %.6f",
                pageId, title, lat, lon);
    }

    @Deprecated
    public static String[] parseLatLng(String place) {
        Coordinates c = parseCoordinates(place);
        if (c == null) return null;
        return new String[]{ String.valueOf(c.lat()), String.valueOf(c.lng()) };
    }

    @Deprecated
    public static String[] parseInterestingPlace(String place) {
        InterestingPlace p = parseInterestingPlaceTyped(place);
        if (p == null) return null;
        return new String[]{
                String.valueOf(p.pageId()), p.title(),
                String.valueOf(p.lat()), String.valueOf(p.lon())
        };
    }

    public static Coordinates parseCoordinates(String place) {
        if (place == null) return null;
        Matcher m = LAT_LNG.matcher(place);
        if (!m.find()) return null;
        try {
            return new Coordinates(
                    Double.parseDouble(m.group(1)),
                    Double.parseDouble(m.group(2)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static InterestingPlace parseInterestingPlaceTyped(String place) {
        if (place == null) return null;
        Matcher m = INTERESTING_PLACE.matcher(place);
        if (!m.find()) return null;
        try {
            return new InterestingPlace(
                    Integer.parseInt(m.group(1)),
                    m.group(2),
                    Double.parseDouble(m.group(3)),
                    Double.parseDouble(m.group(4)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static List<String> parseGeosearchResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new ParseException("Empty geosearch response");
        }
        try {
            JSONObject json = new JSONObject(response);

            if (json.has("error")) {
                JSONObject err = json.getJSONObject("error");
                throw new ParseException("Wikipedia API error: "
                        + err.optString("code") + " — " + err.optString("info"));
            }

            JSONObject query = json.optJSONObject("query");
            if (query == null) return List.of();

            JSONArray geosearch = query.optJSONArray("geosearch");
            if (geosearch == null) return List.of();

            List<String> result = new ArrayList<>();
            for (int i = 0; i < geosearch.length() && i < 10; i++) {
                JSONObject p = geosearch.getJSONObject(i);
                result.add(formatInterestingPlace(
                        p.getInt("pageid"),
                        p.getString("title"),
                        p.getDouble("lat"),
                        p.getDouble("lon")));
            }
            return result;
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("Failed to parse geosearch response: "
                    + e.getMessage(), e);
        }
    }

    public static WikiResult parseDescriptionAndImage(String response) {
        if (response == null || response.isBlank()) {
            throw new ParseException("Empty description response");
        }
        try {
            JSONObject json = new JSONObject(response);

            if (json.has("error")) {
                JSONObject err = json.getJSONObject("error");
                throw new ParseException("Wikipedia API error: "
                        + err.optString("code") + " — " + err.optString("info"));
            }

            JSONObject query = json.optJSONObject("query");
            if (query == null) {
                throw new ParseException("No 'query' in description response");
            }
            JSONObject pages = query.optJSONObject("pages");
            if (pages == null || pages.isEmpty()) {
                throw new ParseException("No 'pages' in description response");
            }

            String pageId = pages.keys().next();
            JSONObject page = pages.getJSONObject(pageId);

            if (page.has("missing") || page.has("invalid")) {
                throw new ParseException("Page " + pageId + " is missing/invalid");
            }

            String description = page.optString("extract", null);
            if (description != null) {
                description = description.replace("\n", " ").trim();
                if (description.length() > 200) {
                    description = description.substring(0, 200) + "...";
                }
            }

            String imageUrl = null;
            JSONObject thumb = page.optJSONObject("thumbnail");
            if (thumb != null) {
                imageUrl = thumb.optString("source", null);
                if (imageUrl != null && imageUrl.isBlank()) imageUrl = null;
            }

            return new WikiResult(description, imageUrl);
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("Failed to parse description response: "
                    + e.getMessage(), e);
        }
    }

    public static Map<String, WikiResult> parseDescriptionsAndImages(String response) {
        if (response == null || response.isBlank()) {
            throw new ParseException("Empty batch response");
        }
        try {
            JSONObject json = new JSONObject(response);

            if (json.has("error")) {
                JSONObject err = json.getJSONObject("error");
                throw new ParseException("Wikipedia API error: "
                        + err.optString("code") + " — " + err.optString("info"));
            }

            JSONObject query = json.optJSONObject("query");
            if (query == null) return Map.of();
            JSONObject pages = query.optJSONObject("pages");
            if (pages == null) return Map.of();

            Map<String, WikiResult> result = new LinkedHashMap<>();
            for (String pageId : pages.keySet()) {
                JSONObject page = pages.getJSONObject(pageId);
                if (page.has("missing") || page.has("invalid")) continue;

                String description = page.optString("extract", null);
                if (description != null) {
                    description = description.replace("\n", " ").trim();
                    if (description.length() > 200) {
                        description = description.substring(0, 200) + "...";
                    }
                }
                String imageUrl = null;
                JSONObject thumb = page.optJSONObject("thumbnail");
                if (thumb != null) {
                    imageUrl = thumb.optString("source", null);
                    if (imageUrl != null && imageUrl.isBlank()) imageUrl = null;
                }
                result.put(pageId, new WikiResult(description, imageUrl));
            }
            return result;
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("Failed to parse batch response: "
                    + e.getMessage(), e);
        }
    }

    public static List<String> parseGraphhopperResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new ParseException("Empty graphhopper response");
        }
        try {
            JSONObject json = new JSONObject(response);
            JSONArray hits = json.optJSONArray("hits");
            if (hits == null) return List.of();

            List<String> placeData = new ArrayList<>();
            for (int i = 0; i < hits.length(); i++) {
                JSONObject hit = hits.getJSONObject(i);
                JSONObject point = hit.getJSONObject("point");
                placeData.add(formatPlace(
                        hit.optString("country", "Unknown"),
                        hit.optString("city", ""),
                        point.getDouble("lat"),
                        point.getDouble("lng"),
                        hit.optString("osm_value", "unknown")));
            }
            return placeData;
        } catch (Exception e) {
            throw new ParseException("Failed to parse graphhopper response: "
                    + e.getMessage(), e);
        }
    }
}