package ui;

import config.AppConfig;
import database.TextEntity;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import messaging.RabbitMQManager;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.LoggerUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ClientReaderV2FX extends Application {

    private TableView<LineItem> contentTable;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Button refreshButton;
    private RabbitMQManager rmq;
    private Map<Integer, List<LineItem>> replicaData = new HashMap<>();

    public static class LineItem {
        private final Integer lineNumber;
        private final String content;
        private final String timestamp;
        private final Integer replicaId;

        public LineItem(Integer lineNumber, String content, Long timestamp, Integer replicaId) {
            this.lineNumber = lineNumber;
            this.content = content;

            // Format timestamp
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());
            this.timestamp = formatter.format(Instant.ofEpochMilli(timestamp));

            this.replicaId = replicaId;
        }

        public Integer getLineNumber() { return lineNumber; }
        public String getContent() { return content; }
        public String getTimestamp() { return timestamp; }
        public Integer getReplicaId() { return replicaId; }
    }

    @Override
    public void start(Stage stage) {
        try {
            rmq = new RabbitMQManager();
        } catch (Exception e) {
            LoggerUtil.error("Failed to initialize RabbitMQ", e);
            showError("Failed to connect to message broker: " + e.getMessage());
            return;
        }

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        // Top section
        VBox topSection = createTopSection();
        root.setTop(topSection);

        // Center - Table view
        contentTable = createContentTable();
        root.setCenter(contentTable);

        // Bottom - Status bar
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 800, 600);
        stage.setTitle("TextSync Reader V2");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (rmq != null) rmq.close();
        });
        stage.show();

        // Perform initial data fetch
        fetchDataFromReplicas();
    }

    private VBox createTopSection() {
        VBox topSection = new VBox(10);
        topSection.setPadding(new Insets(0, 0, 10, 0));

        Label titleLabel = new Label("TextSync Reader V2");
        titleLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold;");

        HBox controlsBox = new HBox(10);

        refreshButton = new Button("Refresh Data");
        refreshButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        refreshButton.setPrefWidth(150);
        refreshButton.setOnAction(e -> fetchDataFromReplicas());

        ComboBox<String> viewSelector = new ComboBox<>();
        viewSelector.getItems().addAll("All Lines", "Latest Only", "Conflicts Only");
        viewSelector.setValue("All Lines");
        viewSelector.setPrefWidth(150);
        viewSelector.setOnAction(e -> updateTableView(viewSelector.getValue()));

        TextField filterField = new TextField();
        filterField.setPromptText("Filter by content...");
        filterField.setPrefWidth(200);
        filterField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterTableContent(newVal);
        });

        HBox.setHgrow(filterField, Priority.ALWAYS);
        controlsBox.getChildren().addAll(refreshButton, viewSelector, new Label("Filter:"), filterField);

        topSection.getChildren().addAll(titleLabel, controlsBox);
        return topSection;
    }

    private TableView<LineItem> createContentTable() {
        TableView<LineItem> table = new TableView<>();

        TableColumn<LineItem, Integer> lineNumberCol = new TableColumn<>("Line #");
        lineNumberCol.setCellValueFactory(new PropertyValueFactory<>("lineNumber"));
        lineNumberCol.setPrefWidth(70);

        TableColumn<LineItem, String> contentCol = new TableColumn<>("Content");
        contentCol.setCellValueFactory(new PropertyValueFactory<>("content"));
        contentCol.setPrefWidth(350);

        TableColumn<LineItem, String> timestampCol = new TableColumn<>("Timestamp");
        timestampCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timestampCol.setPrefWidth(180);

        TableColumn<LineItem, Integer> replicaCol = new TableColumn<>("Replica");
        replicaCol.setCellValueFactory(new PropertyValueFactory<>("replicaId"));
        replicaCol.setPrefWidth(70);

        table.getColumns().addAll(lineNumberCol, contentCol, timestampCol, replicaCol);

        table.setPlaceholder(new Label("No data available"));

        // Add row highlighting for conflicts
        table.setRowFactory(tv -> new TableRow<LineItem>() {
            @Override
            protected void updateItem(LineItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                    return;
                }

                // Check if this row represents a conflicting line
                boolean isConflict = false;
                for (List<LineItem> replicaItems : replicaData.values()) {
                    for (LineItem otherItem : replicaItems) {
                        if (otherItem.getLineNumber().equals(item.getLineNumber()) &&
                                !otherItem.getReplicaId().equals(item.getReplicaId()) &&
                                !otherItem.getContent().equals(item.getContent())) {
                            isConflict = true;
                            break;
                        }
                    }
                    if (isConflict) break;
                }

                if (isConflict) {
                    setStyle("-fx-background-color: #ffecb3;"); // Light amber for conflicts
                } else {
                    setStyle("");
                }
            }
        });

        return table;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(10, 0, 0, 0));
        statusBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        statusLabel = new Label("Ready");
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);

        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        statusBar.getChildren().addAll(statusLabel, progressBar);

        return statusBar;
    }

    private void fetchDataFromReplicas() {
        // Reset data and UI state
        replicaData.clear();
        contentTable.getItems().clear();
        refreshButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        statusLabel.setText("Querying replicas...");

        try {
            // Send READ_ALL command via a custom implementation
            CompletableFuture<String> future = createCustomReadAllRequest();

            // Process responses
            long startTime = System.currentTimeMillis();
            long elapsedTime = 0;
            int replicaCount = 0;

            while (elapsedTime < AppConfig.REPLICA_RESPONSE_TIMEOUT) {
                try {
                    String response = future.get(AppConfig.RESPONSE_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
                    if (response != null) {
                        JSONObject jsonResponse = new JSONObject(response);

                        // Handle error response
                        if (jsonResponse.has("error")) {
                            Platform.runLater(() -> {
                                statusLabel.setText("Error from Replica " + jsonResponse.getInt("replicaId") +
                                        ": " + jsonResponse.getString("error"));
                            });
                            continue;
                        }

                        // Process successful response with data
                        if (jsonResponse.has("replicaId") && jsonResponse.has("lines")) {
                            int replicaId = jsonResponse.getInt("replicaId");
                            JSONArray linesArray = jsonResponse.getJSONArray("lines");
                            List<LineItem> replicaLines = new ArrayList<>();

                            for (int i = 0; i < linesArray.length(); i++) {
                                JSONObject line = linesArray.getJSONObject(i);
                                LineItem item = new LineItem(
                                        line.getInt("lineNumber"),
                                        line.getString("content"),
                                        line.getLong("timestamp"),
                                        replicaId
                                );
                                replicaLines.add(item);
                            }

                            replicaData.put(replicaId, replicaLines);
                            replicaCount++;

                            // Update UI with progress
                            final int currentCount = replicaCount;
                            Platform.runLater(() -> {
                                statusLabel.setText("Received data from " + currentCount + " replica(s)");
                                updateTableView("All Lines");
                            });
                        }
                    }
                } catch (TimeoutException e) {
                    // This is expected, continue checking
                }

                elapsedTime = System.currentTimeMillis() - startTime;
            }

            // Finalize UI update
            final int finalReplicaCount = replicaCount;
            Platform.runLater(() -> {
                refreshButton.setDisable(false);
                progressBar.setVisible(false);

                if (finalReplicaCount > 0) {
                    statusLabel.setText("Received data from " + finalReplicaCount + " replica(s)");
                } else {
                    statusLabel.setText("No responses received from any replicas");
                }
            });

        } catch (Exception e) {
            LoggerUtil.error("Error fetching data from replicas", e);
            Platform.runLater(() -> {
                refreshButton.setDisable(false);
                progressBar.setVisible(false);
                statusLabel.setText("Error: " + e.getMessage());
                showError("Failed to fetch data: " + e.getMessage());
            });
        }
    }

    private CompletableFuture<String> createCustomReadAllRequest() throws Exception {
        // Create a custom READ_ALL request that expects a JSON response
        // This extends the standard READ_ALL command to return structured data
        String customCommand = AppConfig.MSG_READ_ALL + "_JSON";
        return rmq.publishWithResponse(customCommand);
    }

    private void updateTableView(String viewMode) {
        ObservableList<LineItem> allItems = FXCollections.observableArrayList();
        Map<Integer, LineItem> latestItems = new HashMap<>();
        Set<LineItem> conflictItems = new HashSet<>();

        // Collect all items and identify latest versions
        for (Map.Entry<Integer, List<LineItem>> entry : replicaData.entrySet()) {
            int replicaId = entry.getKey();
            List<LineItem> lines = entry.getValue();

            for (LineItem item : lines) {
                allItems.add(item);

                // Track latest version by comparing timestamps
                LineItem existing = latestItems.get(item.getLineNumber());
                if (existing == null) {
                    latestItems.put(item.getLineNumber(), item);
                } else {
                    // Compare timestamps (formatted strings won't work directly)
                    if (item.timestamp.compareTo(existing.timestamp) > 0) {
                        latestItems.put(item.getLineNumber(), item);
                    }

                    // Check for content conflict
                    if (!item.getContent().equals(existing.getContent())) {
                        conflictItems.add(item);
                        conflictItems.add(existing);
                    }
                }
            }
        }

        switch (viewMode) {
            case "Latest Only":
                contentTable.setItems(FXCollections.observableArrayList(latestItems.values()));
                break;
            case "Conflicts Only":
                contentTable.setItems(FXCollections.observableArrayList(conflictItems));
                break;
            case "All Lines":
            default:
                contentTable.setItems(allItems);
                break;
        }

        // Sort by line number
        contentTable.getSortOrder().clear();
        contentTable.getColumns().get(0).setSortType(TableColumn.SortType.ASCENDING);
        contentTable.getSortOrder().add(contentTable.getColumns().get(0));
        contentTable.sort();
    }

    private void filterTableContent(String filter) {
        if (filter == null || filter.isEmpty()) {
            // Reset to current view mode
            updateTableView("All Lines");
            return;
        }

        String lowercaseFilter = filter.toLowerCase();
        ObservableList<LineItem> filteredItems = FXCollections.observableArrayList();

        for (LineItem item : contentTable.getItems()) {
            if (item.getContent().toLowerCase().contains(lowercaseFilter)) {
                filteredItems.add(item);
            }
        }

        contentTable.setItems(filteredItems);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}