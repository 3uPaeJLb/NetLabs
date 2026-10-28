import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MainFrame2 extends JFrame {

    private GraphhopperApiController graphhopperApiController = new GraphhopperApiController();
    private OpenWeatherApiController openWeatherApiController = new OpenWeatherApiController();
    private WikipediaApiController wikipediaApiController = new WikipediaApiController();
    private DefaultListModel<String> placeModel = new DefaultListModel<>();
    private DefaultListModel<String> interestingPlaceModel = new DefaultListModel<>();

    public MainFrame2() {
        super("Place Finder");

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setBounds(400, 100, 900, 700);
        setMinimumSize(new Dimension(640, 480));

        setLayout(null);

        JTextField textField = new JTextField();
        textField.setBounds(10, 10, 400, 30);
        textField.setVisible(true);
        this.add(textField);

        JButton searchButton = new JButton("Find");
        searchButton.setBounds(410, 10, 100, 30);
        searchButton.setVisible(true);
        this.add(searchButton);

        JList<String> placeTable = new JList<>(placeModel);
        placeTable.setBounds(10, 50, 400, 100);
        placeTable.setVisible(true);
        this.add(placeTable);

        JTextArea weatherTextArea = new JTextArea();
        weatherTextArea.setBounds(430, 50, 400, 100);
        weatherTextArea.setEditable(false);
        weatherTextArea.setLineWrap(true);
        weatherTextArea.setWrapStyleWord(true);
        this.add(weatherTextArea);

        JList<String> interestingPlacesTable = new JList<>(interestingPlaceModel);
        interestingPlacesTable.setBounds(10, 200, 400, 100);
        interestingPlacesTable.setVisible(true);
        this.add(interestingPlacesTable);

        JTextArea descOfInterestPlacesTextArea = new JTextArea();
        descOfInterestPlacesTextArea.setBounds(430, 200, 400, 400);
        descOfInterestPlacesTextArea.setEditable(false);
        descOfInterestPlacesTextArea.setLineWrap(true);
        descOfInterestPlacesTextArea.setWrapStyleWord(true);
        this.add(descOfInterestPlacesTextArea);

        searchButton.addActionListener(e -> {
            try {
                String searchText = textField.getText().trim();
                graphhopperApiController.findPlaces(searchText).thenAccept(places -> {
                    placeModel.clear();
                    placeModel.addAll(places);
                });
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        placeTable.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String place = placeTable.getSelectedValue();
                String lat = " ";
                String lng = " ";

                if (place != null) {
                    String[] parts = place.split(" ");
                    if (parts.length == 4) {
                        lat = parts[2];
                        lng = parts[3];
                    }
                    else if (parts.length == 5) {
                        lat = parts[3];
                        lng = parts[4];
                    }
                }

                lat = lat.trim().replace(",", ".");
                lng = lng.trim().replace(",", ".");

                try {
                    openWeatherApiController.findWeather(lat, lng).thenAccept(weatherTextArea::setText);

                    wikipediaApiController.findInterestingPlaces(lat, lng).thenAccept(interestingPlaces ->{
                        interestingPlaceModel.clear();
                        interestingPlaceModel.addAll(interestingPlaces);
                            });

                    //CompletableFuture<List<String>> descOfInterestPlacesFuture = wikipediaApiController
                    //      .findDescOfInterestPlaces(placesFuture);

                    interestingPlacesTable.addListSelectionListener(e1-> {
                        if (!e1.getValueIsAdjusting()) {
                            String interestingPlace = interestingPlacesTable.getSelectedValue();
                             String pageId = "";
                            if (interestingPlace != null)
                                pageId = wikipediaApiController.extractPageId(interestingPlace);

                            wikipediaApiController.findDescOfInterestPlaceById(pageId).thenAccept(descOfInterestPlace -> {
                                descOfInterestPlacesTextArea.setText("");

                                descOfInterestPlacesTextArea.append(descOfInterestPlace + "\n");

                            });

                        }

                    });

                    /*CompletableFuture.allOf(weatherFuture, placesFuture, descOfInterestPlacesFuture)
                            .thenRun(() -> {
                                try {
                                    String weather = weatherFuture.get();
                                    List<String> interestingPlaces = placesFuture.get();
                                    List<String> descOfInterestPlaces = descOfInterestPlacesFuture.get();

                                    weatherTextArea.setText(weather);

                                    interestingPlacesTextArea.setText("");
                                    for (String interestingPlace : interestingPlaces) {
                                        interestingPlacesTextArea.append(interestingPlace + "\n");
                                    }

                                    descOfInterestPlacesTextArea.setText("");
                                    for (String descOfInterestPlace : descOfInterestPlaces) {
                                        descOfInterestPlacesTextArea.append(descOfInterestPlace + "\n");
                                    }

                                } catch (Exception ex) {
                                    System.err.println("Error getting results: " + ex.getMessage());
                                }
                            });*/
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        setVisible(true);
    }
}
