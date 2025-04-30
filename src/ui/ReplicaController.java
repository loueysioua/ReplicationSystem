package ui;

import com.rabbitmq.client.AMQP;
import config.AppConfig;
import database.TextEntity;
import database.TextRepository;
import messaging.RabbitMQManager;
import org.json.JSONArray;
import utils.LoggerUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplicaController {

    private final int replicaId;
    private final VBox view;
    private final TextArea logArea = new TextArea();
    private final ListView<TextEntity> dbContentsView = new ListView<>();
    private final ObservableList<TextEntity> dbContents = FXCollections.observableArrayList();
    private final TextRepository repository;
    private final SimpleBooleanProperty isRunning = new SimpleBooleanProperty(true);
    private final SimpleBooleanProperty isFaulty = new SimpleBooleanProperty(false);
    private final Label statusLabel;
    private final AtomicInteger receivedMessages = new AtomicInteger(0);
    private final AtomicInteger processedMessages = new AtomicInteger(0);
    private RabbitMQManager rmq;

    public ReplicaController(int replicaId) {
        this.replicaId = replicaId;
        this.repository = new TextRepository(replicaId);
        this.statusLabel = new Label("Status: Online");
        this.statusLabel.setTextFill(Color.GREEN);
        this.statusLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        this.view = buildUI();
        startListeningToQueue();
        refreshDbContents(); // Initial load of DB contents
    }

    private VBox buildUI() {
        // Title and status section
        Label title = new Label("Replica #" + replicaId);
        title.setFont(Font.font("System", FontWeight.BOLD, 16));

        HBox statusBar = new HBox(10);
        statusBar.setAlignment(Pos.CENTER_LEFT);

        Label messageStats = new Label("Received: 0 | Processed: 0");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBar.getChildren().addAll(statusLabel, spacer, messageStats);

        // Tabs for logs and database contents
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Log tab
        logArea.setEditable(false);
        logArea.setWrapText(true);
        Tab logTab = new Tab("Logs", new BorderPane(logArea));

        // Database content tab
        dbContentsView.setItems(dbContents);
        dbContentsView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(TextEntity item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                }
            }
        });

        VBox dbContentsBox = new VBox(10);
        Button refreshDbBtn = new Button("Refresh Database");
        refreshDbBtn.setOnAction(e -> refreshDbContents());

        dbContentsBox.getChildren().addAll(new BorderPane(dbContentsView), refreshDbBtn);
        Tab dbTab = new Tab("Database Contents", dbContentsBox);

        tabPane.getTabs().addAll(logTab, dbTab);

        // Control buttons
        HBox controlButtons = new HBox(10);
        controlButtons.setAlignment(Pos.CENTER);

        Button toggleRunningBtn = new Button("Stop");
        toggleRunningBtn.setStyle("-fx-background-color: #ff6b6b; -fx-text-fill: white;");
        toggleRunningBtn.setOnAction(e -> toggleRunning(toggleRunningBtn));
        toggleRunningBtn.disableProperty().bind(isFaulty);

        Button simulateFailureBtn = new Button("Simulate Breakdown");
        simulateFailureBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        simulateFailureBtn.setOnAction(e -> simulateFailure(simulateFailureBtn));
        simulateFailureBtn.disableProperty().bind(isFaulty);

        Button simulateRecoveryBtn = new Button("Recover");
        simulateRecoveryBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white;");
        simulateRecoveryBtn.setOnAction(e -> simulateRecovery(simulateRecoveryBtn));
        simulateRecoveryBtn.disableProperty().bind(isRunning.or(isFaulty.not()));

        controlButtons.getChildren().addAll(toggleRunningBtn, simulateFailureBtn, simulateRecoveryBtn);

        // Put everything together
        VBox box = new VBox(10, title, statusBar, tabPane, controlButtons);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: white;");

        // Update message stats periodically
        javafx.animation.Timeline statsUpdater = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> {
                    messageStats.setText(String.format("Received: %d | Processed: %d",
                            receivedMessages.get(), processedMessages.get()));
                })
        );
        statsUpdater.setCycleCount(javafx.animation.Animation.INDEFINITE);
        statsUpdater.play();

        return box;
    }

    private void processMessage(String message, AMQP.BasicProperties properties) throws Exception {
        String replyTo = properties != null ? properties.getReplyTo() : null;
        String correlationId = properties != null ? properties.getCorrelationId() : null;
        
        if (message.startsWith("WRITE ")) {
            String[] parts = message.substring(6).split(" ", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid WRITE message format. Expected: WRITE <lineNumber> <content>");
            }
            int lineNumber = Integer.parseInt(parts[0]);
            String content = parts[1];
            repository.insertLine(lineNumber, content);
            Platform.runLater(() -> log("✅ Processed write: Line " + lineNumber));
        } else if (message.equals("READ LAST")) {
            TextEntity lastLine = repository.getLastLine();
            if (lastLine != null && replyTo != null) {
                JSONObject response = new JSONObject();
                response.put("replicaId", replicaId);
                response.put("lineNumber", lastLine.getLineNumber());
                response.put("content", lastLine.getContent());
                response.put("timestamp", lastLine.getTimestamp());
                
                rmq.publishResponse(response.toString(), replyTo, correlationId);
                Platform.runLater(() -> log("📖 Sent last line response: " + lastLine));
            } else if (replyTo != null) {
                JSONObject response = new JSONObject();
                response.put("replicaId", replicaId);
                response.put("empty", true);
                rmq.publishResponse(response.toString(), replyTo, correlationId);
                Platform.runLater(() -> log("📖 Sent empty response - no data"));
            } else {
                Platform.runLater(() -> log("📖 Read last line request without reply queue"));
            }
        } else if (message.equals("READ ALL")) {
            Platform.runLater(() -> log("📖 Reading all lines..."));
            repository.getAllLines().forEach(text -> {
                Platform.runLater(() -> log("  → Line " + text.getLineNumber() + ": " + text.getContent()));
            });
        } else if (message.equals("READ ALL_JSON")) {
            // Handle the READ ALL_JSON command
            Platform.runLater(() -> log("📖 Reading all lines for JSON response..."));
            if (replyTo != null) {
                List<TextEntity> allLines = repository.getAllLines();
                JSONObject response = new JSONObject();
                response.put("replicaId", replicaId);

                JSONArray linesArray = new JSONArray();
                allLines.forEach(line -> {
                    JSONObject lineObj = new JSONObject();
                    lineObj.put("lineNumber", line.getLineNumber());
                    lineObj.put("content", line.getContent());
                    lineObj.put("timestamp", line.getTimestamp());
                    linesArray.put(lineObj);
                });

                response.put("lines", linesArray);
                rmq.publishResponse(response.toString(), replyTo, correlationId);
                Platform.runLater(() -> log("📖 Sent JSON response with " + allLines.size() + " lines"));
            } else {
                Platform.runLater(() -> log("📖 READ ALL_JSON request received without reply queue"));
            }
        } else {
            try {
                JSONObject json = new JSONObject(message);
                if (json.has("line_number") && json.has("content")) {
                    int lineNumber = json.getInt("line_number");
                    String content = json.getString("content");
                    repository.insertLine(lineNumber, content);
                    Platform.runLater(() -> log("✅ Processed JSON write: Line " + lineNumber));
                }
            } catch (Exception ex) {
                Platform.runLater(() -> log("❌ Invalid message format: " + message));
                throw ex;
            }
        }
    }

    private void startListeningToQueue() {
        try {
            rmq = new RabbitMQManager();
            String queueName = AppConfig.QUEUE_PREFIX + replicaId;
            
            // Declare queue and ensure binding
            rmq.declareQueue(queueName);

            rmq.consume(queueName, (consumerTag, delivery) -> {
                if (!isRunning.get() || isFaulty.get()) return;

                String message = new String(delivery.getBody(), "UTF-8");
                receivedMessages.incrementAndGet();
                Platform.runLater(() -> log("📥 Received: " + message));

                try {
                    // Process the message with its properties
                    processMessage(message, delivery.getProperties());
                    processedMessages.incrementAndGet();
                    Platform.runLater(this::refreshDbContents);
                } catch (Exception ex) {
                    Platform.runLater(() -> log("❌ Error processing message: " + ex.getMessage()));
                    LoggerUtil.error("Failed to process message in replica " + replicaId, ex);
                }
            });

            updateStatus("Online", Color.GREEN);
            log("🚀 Replica " + replicaId + " is listening for messages...");

        } catch (Exception e) {
            updateStatus("Connection Error", Color.RED);
            log("❌ Failed to connect to RabbitMQ: " + e.getMessage());
            LoggerUtil.error("Failed to connect RabbitMQ for replica " + replicaId, e);
        }
    }

    private void toggleRunning(Button button) {
        boolean newStatus = !isRunning.get();
        isRunning.set(newStatus);

        if (newStatus) {
            updateStatus("Online", Color.GREEN);
            button.setText("Stop");
            button.setStyle("-fx-background-color: #ff6b6b; -fx-text-fill: white;");
            log("🟢 Replica resumed operation");
        } else {
            updateStatus("Stopped", Color.ORANGE);
            button.setText("Resume");
            button.setStyle("-fx-background-color: #4dabf7; -fx-text-fill: white;");
            log("🟠 Replica operation paused");
        }
    }

    private void simulateFailure(Button button) {
        isFaulty.set(true);
        isRunning.set(false);
        updateStatus("BREAKDOWN", Color.RED);
        log("🔴 Replica has experienced a simulated breakdown!");

        // Simulate glitchy behavior using Timeline
        javafx.animation.Timeline glitchTimeline = new javafx.animation.Timeline();
        for (int i = 0; i < 5; i++) {
            glitchTimeline.getKeyFrames().addAll(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(i * 0.4), e -> 
                    log("⚠️ ERROR: Connection lost to message broker")),
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(i * 0.4 + 0.2), e -> 
                    log("⚠️ ERROR: Database connection unstable"))
            );
        }
        glitchTimeline.getKeyFrames().add(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2.0), e -> 
                log("💀 System in failure state - recovery required"))
        );
        glitchTimeline.play();
    }

    private void simulateRecovery(Button button) {
        isFaulty.set(false);
        log("🔄 Starting recovery procedure...");

        javafx.animation.Timeline recoveryTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, e -> 
                log("🔄 Reconnecting to message broker...")),
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> 
                log("🔄 Reestablishing database connection...")),
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2), e -> 
                log("🔄 Validating system integrity...")),
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(3), e -> {
                log("✅ Recovery complete! System is back online.");
                isRunning.set(true);
                updateStatus("Recovered", Color.GREEN);
            })
        );
        recoveryTimeline.play();
    }

    private void refreshDbContents() {
        Platform.runLater(() -> {
            dbContents.clear();
            dbContents.addAll(repository.getAllLines());
        });
    }

    private void updateStatus(String status, Color color) {
        Platform.runLater(() -> {
            statusLabel.setText("Status: " + status);
            statusLabel.setTextFill(color);
        });
    }

    private void log(String msg) {
        Platform.runLater(() -> logArea.appendText("[" + java.time.LocalTime.now().toString().substring(0, 8) + "] " + msg + "\n"));
    }

    public VBox getView() {
        return view;
    }
}