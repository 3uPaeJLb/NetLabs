import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.io.IOException;

public class MainFrame2 extends JFrame {

    private static final String NO_SECOND_ARG = "";

    private final GraphhopperApiController graphhopperApiController;
    private final OpenWeatherApiController openWeatherApiController;
    private final WikipediaApiController wikipediaApiController;

    private final DefaultListModel<String> placeModel = new DefaultListModel<>();
    private final DefaultListModel<String> interestingPlaceModel = new DefaultListModel<>();

    private final JTextArea weatherTextArea = new JTextArea();
    private final JTextPane descOfInterestPlacesTextPane = new JTextPane();
    private final JLabel imageLabel = new JLabel();

    public MainFrame2() throws IOException {
        super("Place Finder");

        graphhopperApiController = new GraphhopperApiController();
        openWeatherApiController = new OpenWeatherApiController();
        wikipediaApiController = new WikipediaApiController();

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

        JList<String> interestingPlacesTable = new JList<>(interestingPlaceModel);
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
                graphhopperApiController.find(searchText, NO_SECOND_ARG)
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
            String lat = latLng[0];
            String lng = latLng[1];

            openWeatherApiController.find(lat, lng)
                    .thenAccept(weather -> SwingUtilities.invokeLater(() ->
                            weatherTextArea.setText(String.valueOf(weather))))
                    .exceptionally(ex -> {
                        SwingUtilities.invokeLater(() ->
                                weatherTextArea.setText("Weather error: " + rootMessage(ex)));
                        return null;
                    });

            wikipediaApiController.find(lat, lng)
                    .thenAccept(interestingPlaces -> SwingUtilities.invokeLater(() -> {
                        interestingPlaceModel.clear();
                        interestingPlaceModel.addAll(interestingPlaces);
                        descOfInterestPlacesTextPane.setText("");
                        imageLabel.setIcon(null);
                        imageLabel.setText("");
                    }))
                    .exceptionally(ex -> {
                        SwingUtilities.invokeLater(() ->
                                showDescription("Ошибка", "Places error: " + rootMessage(ex)));
                        return null;
                    });
        });

        interestingPlacesTable.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;

            String interestingPlace = interestingPlacesTable.getSelectedValue();
            if (interestingPlace == null) return;

            String[] parsed = PlaceParser.parseInterestingPlace(interestingPlace);
            if (parsed == null) {
                showDescription("Ошибка", "Не удалось разобрать: " + interestingPlace);
                return;
            }
            String pageId = parsed[0];
            String title  = parsed[1];

            wikipediaApiController.findDescriptionWithImage(pageId)
                    .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                        showDescription(title, result.description());
                        loadImage(result.imageUrl());
                    }))
                    .exceptionally(ex -> {
                        SwingUtilities.invokeLater(() ->
                                showDescription(title, "Description error: " + rootMessage(ex)));
                        return null;
                    });
        });

        setVisible(true);
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
            doc.insertString(doc.getLength(), title + "\n", titleStyle);
            doc.insertString(doc.getLength(), description + "\n", descStyle);
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private void loadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            imageLabel.setIcon(null);
            imageLabel.setText("Нет изображения");
            return;
        }

        imageLabel.setIcon(null);
        imageLabel.setText("Загрузка...");

        wikipediaApiController.loadImage(imageUrl)
                .thenAccept(icon -> SwingUtilities.invokeLater(() -> {
                    if (icon != null) {
                        imageLabel.setIcon(icon);
                        imageLabel.setText("");
                    } else {
                        imageLabel.setIcon(null);
                        imageLabel.setText("Ошибка загрузки");
                    }
                }));
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
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