import javax.swing.ImageIcon;
import java.util.List;

public record PlaceDetailsResult(
        String place,
        String weather,
        List<String> places,
        List<PlaceDetail> details
) {
    public record PlaceDetail(
            String pageId,
            String title,
            String description,
            ImageIcon image,
            String error        // null, если всё ок; иначе — текст ошибки
    ) {
        public boolean hasError() {
            return error != null && !error.isBlank();
        }
    }
}