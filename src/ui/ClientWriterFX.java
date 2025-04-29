package ui;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import messaging.RabbitMQManager;
import utils.LoggerUtil;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientWriterFX extends Application {

    private final ObservableList<String> messageHistory = FXCollections.observableArrayList();
    private RabbitMQManager rmq;
    private TextField lineNumberField;
    private TextArea contentArea;
    private ListView<String> historyListView;
    private Label statusLabel;
    private ProgressIndicator sendingIndicator;
    private Button sendButton;
    private AtomicInteger sentCounter = new AtomicInteger(0);

    @Override
    public void start(Stage stage) {
        try {
            // Initialize RabbitMQ
            rmq = new RabbitMQManager();

            // Build UI
            BorderPane root = new BorderPane();
            root.setPadding(new Insets(15));

            // Header
            HBox header = createHeader();

            // Input form
            VBox inputForm = createInputForm();

            // Message history
            VBox historySection = createHistorySection();

            // Status bar
            HBox statusBar = createStatusBar();

            // Layout
            root.setTop(header);
            root.setCenter(new VBox(20, inputForm, historySection));
            root.setBottom(statusBar);

            // Set the scene
            Scene scene = new Scene(root, 650, 700);
            scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("TextSync Writer Client");
            stage.setOnCloseRequest(e -> closeResources());
            stage.show();

            updateStatus("Ready to send messages", Color.GREEN);

        } catch (Exception e) {
            LoggerUtil.error("Failed to initialize ClientWriter", e);
            showError("Failed to initialize: " + e.getMessage());
        }
    }

    private HBox createHeader() {
        Label titleLabel = new Label("TextSync Writer");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 24));

        Label subtitleLabel = new Label("Send text lines to all replicas");
        subtitleLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));

        VBox titleVBox = new VBox(5, titleLabel, subtitleLabel);

        HBox header = new HBox(titleVBox);
        header.setPadding(new Insets(0, 0, 15, 0));
        header.setAlignment(Pos.CENTER_LEFT);

        return header;
    }

    private VBox createInputForm() {
        // Line number input
        Label lineNumberLabel = new Label("Line Number:");
        lineNumberField = new TextField();
        lineNumberField.setPromptText("Enter numeric line number");

        // Content input
        Label contentLabel = new Label("Content:");
        contentArea = new TextArea();
        contentArea.setPromptText("Enter the text content for this line");
        contentArea.setPrefRowCount(4);
        contentArea.setWrapText(true);

        // Send button
        sendButton = new Button("Send to All Replicas");
        sendButton.getStyleClass().add("primary-button");
        sendButton.setPrefWidth(200);

        sendingIndicator = new ProgressIndicator(-1);
        sendingIndicator.setVisible(false);
        sendingIndicator.setMaxSize(20, 20);

        HBox buttonBox = new HBox(10, sendButton, sendingIndicator);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        // Quick template buttons
        HBox templateButtons = createTemplateButtons();

        VBox form = new VBox(10,
                lineNumberLabel, lineNumberField,
                contentLabel, contentArea,
                buttonBox,
                new Separator(),
                templateButtons
        );

        // Action
        sendButton.setOnAction(e -> sendMessage());

        return form;
    }

    private HBox createTemplateButtons() {
        Button template1 = new Button("Hello World");
        template1.setOnAction(e -> {
            lineNumberField.setText("1");
            contentArea.setText("Hello, world! This is a test message.");
        });

        Button template2 = new Button("Lorem Ipsum");
        template2.setOnAction(e -> {
            lineNumberField.setText("2");
            contentArea.setText("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.");
        });

        Button template3 = new Button("Important Note");
        template3.setOnAction(e -> {
            lineNumberField.setText("3");
            contentArea.setText("[IMPORTANT] This message should be synced across all replicas immediately!");
        });

        Label label = new Label("Quick Templates:");

        HBox templateBox = new HBox(10, label, template1, template2, template3);
        templateBox.setPadding(new Insets(10, 0, 0, 0));

        return templateBox;
    }

    private VBox createHistorySection() {
        Label historyLabel = new Label("Message History");
        historyLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        historyListView = new ListView<>(messageHistory);
        historyListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    if (item.contains("SUCCESS")) {
                        setTextFill(Color.GREEN);
                    } else if (item.contains("ERROR")) {
                        setTextFill(Color.RED);
                    } else {
                        setTextFill(Color.BLACK);
                    }
                }
            }
        });

        Button clearButton = new Button("Clear History");
        clearButton.setOnAction(e -> messageHistory.clear());

        VBox historyBox = new VBox(10, historyLabel, historyListView, clearButton);
        VBox.setVgrow(historyListView, Priority.ALWAYS);

        return historyBox;
    }

    private HBox createStatusBar() {
        statusLabel = new Label("Ready");
        statusLabel.setTextFill(Color.GREEN);

        Label counterLabel = new Label("Messages sent: 0");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox statusBar = new HBox(10, statusLabel, spacer, counterLabel);
        statusBar.setPadding(new Insets(10, 0, 0, 0));

        // Update counter every second
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> {
                    counterLabel.setText("Messages sent: " + sentCounter.get());
                })
        );
        timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        timeline.play();

        return statusBar;
    }

    private void sendMessage() {
        // Validate input
        String lineNumberText = lineNumberField.getText().trim();
        String content = contentArea.getText().trim();

        if (lineNumberText.isEmpty() || content.isEmpty()) {
            showError("Line number and content are required");
            return;
        }

        int lineNumber;
        try {
            lineNumber = Integer.parseInt(lineNumberText);
            if (lineNumber <= 0) {
                showError("Line number must be positive");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Line number must be an integer");
            return;
        }

        // Disable UI during sending
        sendButton.setDisable(true);
        sendingIndicator.setVisible(true);
        updateStatus("Sending message...", Color.BLUE);

        // Send the message in a background thread
        new Thread(() -> {
            try {
                String message = "WRITE " + lineNumber + " " + content;
                rmq.publish(message);

                // Simulate network delay
                Thread.sleep(500);

                sentCounter.incrementAndGet();

                // Update UI on success
                javafx.application.Platform.runLater(() -> {
                    String timestamp = java.time.LocalTime.now().toString().substring(0, 8);
                    messageHistory.add(0, "[" + timestamp + "] SUCCESS: Sent line " + lineNumber);
                    updateStatus("Message sent successfully", Color.GREEN);
                    lineNumberField.clear();
                    contentArea.clear();
                });

            } catch (Exception e) {
                LoggerUtil.error("Failed to send message", e);
                javafx.application.Platform.runLater(() -> {
                    String timestamp = java.time.LocalTime.now().toString().substring(0, 8);
                    messageHistory.add(0, "[" + timestamp + "] ERROR: Failed to send - " + e.getMessage());
                    updateStatus("Failed to send message", Color.RED);
                });
            } finally {
                javafx.application.Platform.runLater(() -> {
                    sendButton.setDisable(false);
                    sendingIndicator.setVisible(false);
                });
            }
        }).start();
    }

    private void updateStatus(String message, Color color) {
        javafx.application.Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setTextFill(color);
        });
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void closeResources() {
        try {
            if (rmq != null) {
                rmq.close();
            }
        } catch (Exception e) {
            LoggerUtil.error("Error closing RabbitMQ connection", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}