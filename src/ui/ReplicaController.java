package ui;

import config.AppConfig;
import database.TextEntity;
import database.TextRepository;
import messaging.RabbitMQManager;
import utils.JPAUtil;
import utils.LoggerUtil;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReplicaController extends Application {

    private static int replicaId;
    private boolean running = true;
    private TextArea logArea;
    private Label statusLabel;
    private Circle statusIndicator;
    private Label messageCountLabel;
    private Label lastUpdateLabel;
    private int messageCount = 0;
    private RabbitMQManager rabbit;
    private TextRepository repo;
    private EntityManager em;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        root.getStyleClass().add("main-container");

        // Header
        HBox headerBox = createHeaderSection();
        root.setTop(headerBox);

        // Center with status and controls
        VBox centerBox = createCenterSection();
        root.setCenter(centerBox);

        // Log area
        VBox logBox = createLogSection();
        root.setBottom(logBox);

        Scene scene = new Scene(root, 500, 500);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
        stage.setTitle("Replica " + replicaId + " - Text Storage Node");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> cleanupResources());
        stage.show();

        startListening();
        setupStatusUpdater();
    }

    private HBox createHeaderSection() {
        HBox headerBox = new HBox(15);
        headerBox.setPadding(new Insets(0, 0, 15, 0));
        headerBox.setAlignment(Pos.CENTER_LEFT);

        statusIndicator = new Circle(8);
        statusIndicator.setFill(Color.GRAY);

        Label titleLabel = new Label("Replica " + replicaId);
        titleLabel.getStyleClass().add("title-label");

        statusLabel = new Label("Initializing...");
        statusLabel.getStyleClass().add("status-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button stopButton = new Button("Stop Replica");
        stopButton.getStyleClass().add("danger-button");
        stopButton.setOnAction(e -> {
            running = false;
            statusIndicator.setFill(Color.RED);
            statusLabel.setText("Stopped");
            LoggerUtil.log("Replica " + replicaId + " stopped manually.");
            addToLog("Replica stopped manually by user");
        });

        headerBox.getChildren().addAll(statusIndicator, titleLabel, statusLabel, spacer, stopButton);
        return headerBox;
    }

    private VBox createCenterSection() {
        VBox centerBox = new VBox(15);
        centerBox.setPadding(new Insets(10));
        centerBox.getStyleClass().add("info-section");

        // Statistics section
        TitledPane statsPane = new TitledPane();
        statsPane.setText("Replica Statistics");

        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(10);
        statsGrid.setVgap(10);
        statsGrid.setPadding(new Insets(10));

        messageCountLabel = new Label("0");
        lastUpdateLabel = new Label("Never");

        statsGrid.add(new Label("Database ID:"), 0, 0);
        statsGrid.add(new Label("replica" + replicaId + ".db"), 1, 0);
        statsGrid.add(new Label("Messages Processed:"), 0, 1);
        statsGrid.add(messageCountLabel, 1, 1);
        statsGrid.add(new Label("Last Update:"), 0, 2);
        statsGrid.add(lastUpdateLabel, 1, 2);

        statsPane.setContent(statsGrid);
        statsPane.setExpanded(true);

        // Storage content preview
        TitledPane contentPane = new TitledPane();
        contentPane.setText("Recent Content Preview");

        VBox contentBox = new VBox(5);
        contentBox.setPadding(new Insets(10));

        Button refreshButton = new Button("Refresh Content");
        refreshButton.getStyleClass().add("info-button");

        ListView<String> contentList = new ListView<>();
        contentList.setPrefHeight(120);

        refreshButton.setOnAction(e -> refreshContentPreview(contentList));

        contentBox.getChildren().addAll(refreshButton, contentList);
        contentPane.setContent(contentBox);

        centerBox.getChildren().addAll(statsPane, contentPane);
        return centerBox;
    }

    private VBox createLogSection() {
        VBox logBox = new VBox(10);
        logBox.setPadding(new Insets(15, 0, 0, 0));

        Label logLabel = new Label("Activity Log");
        logLabel.getStyleClass().add("section-header");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(150);
        logArea.setWrapText(true);
        logArea.setPromptText("Activity logs will appear here...");

        Button clearLogButton = new Button("Clear Log");
        clearLogButton.getStyleClass().add("secondary-button");
        clearLogButton.setOnAction(e -> logArea.clear());

        logBox.getChildren().addAll(logLabel, logArea, clearLogButton);
        return logBox;
    }

    private void refreshContentPreview(ListView<String> contentList) {
        if (repo != null) {
            Platform.runLater(() -> {
                contentList.getItems().clear();
                List<TextEntity> lines = repo.getAllLines();

                if (lines.isEmpty()) {
                    contentList.getItems().add("No content stored yet");
                } else {
                    for (TextEntity line : lines) {
                        contentList.getItems().add("Line " + line.getLineNumber() + ": " + line.getContent());
                    }
                }
            });
        }
    }

    private void startListening() {
        new Thread(() -> {
            try {
                addToLog("Initializing replica " + replicaId + "...");

                rabbit = new RabbitMQManager();
                em = JPAUtil.getEntityManager(replicaId);
                repo = new TextRepository(em);

                String queueName = AppConfig.QUEUE_PREFIX + replicaId;
                rabbit.declareQueue(queueName);
                addToLog("Connected to message queue: " + queueName);

                updateStatus("Ready", Color.GREEN);

                rabbit.consume(queueName, (consumerTag, delivery) -> {
                    String message = new String(delivery.getBody(), "UTF-8");
                    messageCount++;

                    updateLastMessage();
                    updateStatus("Processing", Color.ORANGE);
                    addToLog("Received: " + message);

                    if (running) {
                        processMessage(message);
                    }

                    updateStatus("Ready", Color.GREEN);
                });

            } catch (Exception e) {
                LoggerUtil.error("Error in Replica " + replicaId, e);
                updateStatus("Error", Color.RED);
                addToLog("ERROR: " + e.getMessage());
            }
        }).start();
    }

    private void processMessage(String message) {
        try {
            if (message.startsWith("WRITE ")) {
                String[] parts = message.substring(6).split(" ", 2);
                int lineNumber = Integer.parseInt(parts[0]);
                String content = parts[1];

                repo.saveLine(lineNumber, content);
                addToLog("Saved line " + lineNumber + ": " + content);
            }
            else if (message.equals("READ LAST")) {
                String lastLine = repo.getLastLine();
                if (lastLine != null) {
                    addToLog("Retrieved last line: " + lastLine);
                } else {
                    addToLog("No content available yet");
                }
            }
            else if (message.equals("READ ALL")) {
                List<TextEntity> allLines = repo.getAllLines();
                addToLog("Retrieved all lines, count: " + allLines.size());
                for (TextEntity line : allLines) {
                    addToLog("Line " + line.getLineNumber() + ": " + line.getContent());
                }
            }
            else {
                addToLog("Unknown command: " + message);
            }
        } catch (Exception e) {
            LoggerUtil.error("Error processing message", e);
            addToLog("ERROR processing message: " + e.getMessage());
        }
    }

    private void setupStatusUpdater() {
        // Update message count display periodically
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            Platform.runLater(() -> {
                messageCountLabel.setText(String.valueOf(messageCount));
            });
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private void updateStatus(String status, Color color) {
        Platform.runLater(() -> {
            statusLabel.setText(status);
            statusIndicator.setFill(color);
        });
    }

    private void updateLastMessage() {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            lastUpdateLabel.setText(timestamp);
        });
    }

    private void addToLog(String message) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            logArea.appendText("[" + timestamp + "] " + message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);  // Auto-scroll to bottom
        });
    }

    private void cleanupResources() {
        running = false;
        if (rabbit != null) {
            rabbit.close();
        }
        // Close EntityManager if needed
        if (em != null && em.isOpen()) {
            em.close();
        }
    }

    public static void launchReplica(int id) {
        ReplicaController.replicaId = id;
        Application.launch(ReplicaController.class);
    }
}