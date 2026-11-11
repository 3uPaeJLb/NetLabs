import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.io.IOException;
import java.util.List;

public class MainFrame2 extends JFrame {

    private final PlaceDetailsService placeDetailsService;

    private final DefaultListModel<String> placeModel = new DefaultListModel<>();
    private final DefaultListModel<PlaceDetailsResult.PlaceDetail> interestingPlaceModel =
            new DefaultListModel<>();

    private final JTextArea weatherTextArea = new JTextArea();
    private final JTextPane descOfInterestPlacesTextPane = new JTextPane();
    private final JLabel imageLabel = new JLabel();

    public MainFrame2() throws IOException {
        super("Place Finder");

        GraphhopperApiController graphhopper = new GraphhopperApiController();
        OpenWeatherApiController openWeather = new OpenWeatherApiController();
        WikipediaApiController wikipedia = new WikipediaApiController();
        placeDetailsService = new PlaceDetailsService(openWeather, wikipedia);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setBounds(400, 50, 1000, 900);
        setMinimumSize(new Dimension(800, 700));
        setLayout(null);

        JTextField textField = new JTextField();
        textField.setBounds(10, 10, 400, 30);
        add(textField);

        JButton searchButton = new JButton("Find");
        searchButton.setBounds(410, 10, 100, 30);
        add(searchButton);

        JList<String> placeTable = new JList<>(placeModel);
        JScrollPane placeScroll = new JScrollPane(placeTable);
        placeScroll.setBounds(10, 50, 400, 250);
        add(placeScroll);

        weatherTextArea.setEditable(false);
        weatherTextArea.setLineWrap(true);
        weatherTextArea.setWrapStyleWord(true);
        JScrollPane weatherScroll = new JScrollPane(weatherTextArea);
        weatherScroll.setBounds(430, 50, 550, 250);
        add(weatherScroll);

        JList<PlaceDetailsResult.PlaceDetail> interestingPlacesTable =
                new JList<>(interestingPlaceModel);
        interestingPlacesTable.setCellRenderer(new PlaceDetailRenderer());
        JScrollPane interestingScroll = new JScrollPane(interestingPlacesTable);
        interestingScroll.setBounds(10, 320, 400, 520);
        add(interestingScroll);

        descOfInterestPlacesTextPane.setEditable(false);
        JScrollPane descScroll = new JScrollPane(descOfInterestPlacesTextPane);
        descScroll.setBounds(430, 320, 550, 125);
        add(descScroll);

        imageLabel.setBounds(430, 455, 550, 385);
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        add(imageLabel);

        searchButton.addActionListener(e -> {
            String searchText = textField.getText().trim();
            if (searchText.isEmpty()) return;

            try {
                graphhopper.find(searchText, "")
                        .thenAccept(places -> SwingUtilities.invokeLater(() -> {
                            placeModel.clear();
                            placeModel.addAll(places);
                        }))
                        .exceptionally(ex -> {
                            SwingUtilities.invokeLater(() ->
                                    weatherTextArea.setText("Search error: " + rootMessage(ex)));
                            return null;
                        });
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        placeTable.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;

            String place = placeTable.getSelectedValue();
            if (place == null) return;

            String[] latLng = PlaceParser.parseLatLng(place);
            if (latLng == null) {
                weatherTextArea.setText("Не удалось извлечь координаты из: " + place);
                return;
            }

            placeDetailsService.load(place, latLng[0], latLng[1])
                    .thenAccept(result -> SwingUtilities.invokeLater(() -> applyResult(result)))
                    .exceptionally(ex -> {
                        SwingUtilities.invokeLater(() ->
                                weatherTextArea.setText("Error: " + rootMessage(ex)));
                        return null;
                    });
        });

        interestingPlacesTable.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;

            PlaceDetailsResult.PlaceDetail detail =
                    interestingPlacesTable.getSelectedValue();
            if (detail == null) return;

            showDetail(detail);
        });

        setVisible(true);
    }

    private void applyResult(PlaceDetailsResult r) {
        weatherTextArea.setText(r.weather());

        interestingPlaceModel.clear();
        if (r.details() != null) {
            interestingPlaceModel.addAll(r.details());
        }

        PlaceDetailsResult.PlaceDetail first =
                interestingPlaceModel.isEmpty() ? null : interestingPlaceModel.get(0);

        if (first == null) {
            descOfInterestPlacesTextPane.setText("");
            imageLabel.setIcon(null);
            imageLabel.setText("Нет мест");
            return;
        }

        showDetail(first);
    }

    /** Единая точка показа PlaceDetail — с учётом ошибки. */
    private void showDetail(PlaceDetailsResult.PlaceDetail detail) {
        if (detail.hasError()) {
            showDescription(detail.title(),
                    "Ошибка загрузки: " + detail.error());
            imageLabel.setIcon(null);
            imageLabel.setText("Ошибка загрузки");
            return;
        }

        showDescription(detail.title(), detail.description());
        if (detail.image() != null) {
            imageLabel.setIcon(detail.image());
            imageLabel.setText("");
        } else {
            imageLabel.setIcon(null);
            imageLabel.setText("Нет изображения");
        }
    }

    private void showDescription(String title, String description) {
        descOfInterestPlacesTextPane.setText("");
        StyledDocument doc = descOfInterestPlacesTextPane.getStyledDocument();

        SimpleAttributeSet titleStyle = new SimpleAttributeSet();
        StyleConstants.setBold(titleStyle, true);
        StyleConstants.setFontSize(titleStyle, 15);

        SimpleAttributeSet descStyle = new SimpleAttributeSet();
        StyleConstants.setBold(descStyle, false);
        StyleConstants.setFontSize(descStyle, 13);

        try {
            doc.insertString(doc.getLength(),
                    (title != null ? title : "") + "\n", titleStyle);
            doc.insertString(doc.getLength(),
                    (description != null ? description : "Описание отсутствует") + "\n",
                    descStyle);
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    private static final class PlaceDetailRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof PlaceDetailsResult.PlaceDetail d) {
                String text = d.title() != null ? d.title() : "(без названия)";
                if (d.hasError()) {
                    text += "  ⚠";
                    if (!isSelected) setForeground(new Color(180, 0, 0));
                }
                setText(text);
            }
            return this;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new MainFrame2();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null,
                        "Startup error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}