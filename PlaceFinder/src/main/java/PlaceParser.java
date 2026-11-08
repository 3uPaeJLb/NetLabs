import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceParser {

    private PlaceParser() {}

    private static final Pattern LAT_LNG = Pattern.compile(
            "lat:\\s*(-?\\d+(?:\\.\\d+)?),\\s*lng:\\s*(-?\\d+(?:\\.\\d+)?)");

    private static final Pattern INTERESTING_PLACE = Pattern.compile(
            "^id:(\\d+)\\s+(.+?)\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)$");

    public static String formatPlace(String country, String city,
                                     double lat, double lng, String osmValue) {
        return String.format(Locale.ROOT,
                "country: %s, city: %s, lat: %.6f, lng: %.6f, type: %s",
                country, city, lat, lng, osmValue);
    }

    public static String[] parseLatLng(String place) {
        if (place == null) return null;
        Matcher m = LAT_LNG.matcher(place);
        if (!m.find()) return null;
        return new String[]{ m.group(1), m.group(2) };
    }

    public static String formatInterestingPlace(int pageId, String title,
                                                double lat, double lon) {
        return String.format(Locale.ROOT, "id:%d %s %.6f %.6f",
                pageId, title, lat, lon);
    }

    public static String[] parseInterestingPlace(String place) {
        if (place == null) return null;
        Matcher m = INTERESTING_PLACE.matcher(place);
        if (!m.find()) return null;
        return new String[]{ m.group(1), m.group(2), m.group(3), m.group(4) };
    }

    public static String extractPageId(String place) {
        if (place != null && place.startsWith("id:")) {
            int firstSpaceIndex = place.indexOf(' ');
            if (firstSpaceIndex > 3) {
                return place.substring(3, firstSpaceIndex);
            }
        }
        return "0";
    }

    public static List<String> parseGeosearchResponse(String response) {
        List<String> interestingPlaces = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(response);
            JSONObject query = json.optJSONObject("query");
            if (query == null) return interestingPlaces;

            JSONArray geosearch = query.optJSONArray("geosearch");
            if (geosearch == null) return interestingPlaces;

            for (int i = 0; i < geosearch.length() && i < 10; i++) {
                JSONObject place = geosearch.getJSONObject(i);
                interestingPlaces.add(formatInterestingPlace(
                        place.getInt("pageid"),
                        place.getString("title"),
                        place.getDouble("lat"),
                        place.getDouble("lon")));
            }
        } catch (Exception e) {
            System.err.println("Error parsing Wikipedia places: " + e.getMessage());
            interestingPlaces.add("[Error loading places]");
        }
        return interestingPlaces;
    }

    public record WikiResult(String description, String imageUrl) {}

    public static WikiResult parseDescriptionAndImage(String response) {
        try {
            JSONObject json = new JSONObject(response);
            JSONObject query = json.getJSONObject("query");
            JSONObject pages = query.getJSONObject("pages");

            String pageId = pages.keys().next();
            JSONObject page = pages.getJSONObject(pageId);

            String description = page.optString("extract", "No description available");
            description = description.replace("\n", " ").trim();
            if (description.length() > 200) {
                description = description.substring(0, 200) + "...";
            }

            String imageUrl = null;
            JSONObject thumbnail = page.optJSONObject("thumbnail");
            if (thumbnail != null) {
                imageUrl = thumbnail.optString("source", null);
            }

            return new WikiResult(description, imageUrl);
        } catch (Exception e) {
            System.err.println("Error parsing description: " + e.getMessage());
            return new WikiResult("Description not available", null);
        }
    }

    public static List<String> parseGraphhopperResponse(String response) {
        List<String> placeData = new ArrayList<>();
        JSONObject json = new JSONObject(response);
        JSONArray hits = json.getJSONArray("hits");

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
    }
}