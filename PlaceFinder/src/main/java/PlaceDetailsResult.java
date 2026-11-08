import javax.swing.ImageIcon;
import java.util.List;

public record PlaceDetailsResult(
        String place,
        String weatherText,
        List<String> interestingPlaces,
        String selectedTitle,
        String selectedDescription,
        ImageIcon image
) {}