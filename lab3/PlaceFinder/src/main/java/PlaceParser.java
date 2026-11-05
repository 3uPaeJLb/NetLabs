import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceParser {

    private PlaceParser() {}

    private static final Pattern LAT_LNG = Pattern.compile(
            "lat:\\s*(-?\\d+(?:\\.\\d+)?),\\s*lng:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern INTERESTING_PLACE = Pattern.compile(
            "^id:(\\d+)\\s+(.+?)\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)$");

    public static String[] parseLatLng(String place) {
        if (place == null) return null;
        Matcher m = LAT_LNG.matcher(place);
        if (!m.find()) return null;
        return new String[]{ m.group(1), m.group(2) };
    }


    public static String extractPageId(String place) {
        try {
            if (place != null && place.startsWith("id:")) {
                int firstSpaceIndex = place.indexOf(' ');
                if (firstSpaceIndex > 3) {
                    return place.substring(3, firstSpaceIndex);
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting pageId from: " + place);
        }
        return "0";
    }

    public static String[] parseInterestingPlace(String place) {
        if (place == null) return null;
        Matcher m = INTERESTING_PLACE.matcher(place);
        if (!m.find()) return null;
        return new String[]{ m.group(1), m.group(2), m.group(3), m.group(4) };
    }
}