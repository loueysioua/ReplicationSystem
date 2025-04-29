package ui;

import messaging.RabbitMQManager;
import utils.LoggerUtil;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.animation.FadeTransition;
import javafx.util.Duration;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientReaderFX extends Application {

    private TextArea outputArea;
    private Label statusLabel;
    private RabbitMQManager rabbitMQManager;
    private final AtomicBoolean isListening = new AtomicBoolean(false);
    private Thread listenerThread;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        root.getStyleClass().add("main-container");

        // Header
        VBox headerBox = createHeaderSection();
        root.setTop(headerBox);

        // Controls
        HBox controlBox = createControlSection();
        root.setCenter(controlBox);

        // Output
        VBox outputBox = createOutputSection();
        root.setBottom(outputBox);

        Scene scene = new Scene(root, 500, 500);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
        stage.setTitle("Text Editor - Reader Client");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> cleanupResources());
        stage.show();

        // Initialize RabbitMQ
        initRabbitMQ();
    }

    private VBox createHeaderSection() {
        VBox headerBox = new VBox(10);
        headerBox.setPadding(new Insets(0, 0, 15, 0));

        Label titleLabel = new Label("Text Editor - Reader Client");
        titleLabel.getStyleClass().add("title-label");

        statusLabel = new Label("Status: Ready");
        statusLabel.getStyleClass().add("status-label");

        Separator separator = new Separator();

        headerBox.getChildren().addAll(titleLabel, statusLabel, separator);
        return headerBox;
    }

    private HBox createControlSection() {
        HBox controlBox = new HBox(15);
        controlBox.setAlignment(Pos.CENTER);
        controlBox.setPadding(new Insets(20));

        Button readLastButton = new Button("Read Last Line");
        readLastButton.getStyleClass().add("primary-button");

        Button startListeningButton = new Button("Start Live Updates");
        startListeningButton.getStyleClass().add("success-button");

        Button stopListeningButton = new Button("Stop Live Updates");
        stopListeningButton.getStyleClass().add("danger-button");
        stopListeningButton.setDisable(true);

        readLastButton.setOnAction(e -> requestLastLine());

        startListeningButton.setOnAction(e -> {
            if (startListening()) {
                startListeningButton.setDisable(true);
                stopListeningButton.setDisable(false);
            }
        });

        stopListeningButton.setOnAction(e -> {
            stopListening();
            startListeningButton.setDisable(false);
            stopListeningButton.setDisable(true);
        });

        controlBox.getChildren().addAll(readLastButton, startListeningButton, stopListeningButton);
        return controlBox;
    }

    private VBox createOutputSection() {
        VBox outputBox = new VBox(10);
        outputBox.setPadding(new Insets(15, 0, 0, 0));

        Label outputLabel = new Label("Content from Replicas");
        outputLabel.getStyleClass().add("section-header");

        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setPrefHeight(250);
        outputArea.setWrapText(true);
        outputArea.setPromptText("Content from replicas will appear here...");

        Button clearOutputButton = new Button("Clear Output");
        clearOutputButton.getStyleClass().add("secondary-button");
        clearOutputButton.setOnAction(e -> outputArea.clear());

        outputBox.getChildren().addAll(outputLabel, outputArea, clearOutputButton);
        return outputBox;
    }

    private void initRabbitMQ() {
        try {
            rabbitMQManager = new RabbitMQManager();
            showStatus("Connected to message broker", false);
        } catch (IOException | TimeoutException e) {
            LoggerUtil.error("Failed to initialize RabbitMQ", e);
            showStatus("Failed to connect to message broker", true);
        }
    }

    private void requestLastLine() {
        try {
            if (rabbitMQManager == null) {
                initRabbitMQ();
            }

            rabbitMQManager.publish("READ LAST");
            LoggerUtil.log("Sent READ LAST command");
            showStatus("Requested last line", false);

            addToOutput("Request sent for the last line...");

            // Simulate response for demo purposes
            // In a real app, you would set up a response queue and listen for replies
            simulateResponse();

        } catch (Exception e) {
            LoggerUtil.error("Error requesting last line", e);
            showStatus("Failed to request last line", true);
        }
    }

    private boolean startListening() {
        if (isListening.get()) {
            return false;
        }

        try {
            if (rabbitMQManager == null) {
                initRabbitMQ();
            }

            isListening.set(true);
            showStatus("Live updates activated", false);
            addToOutput("Started listening for updates...");

            // In a real implementation, you would set up a direct queue for responses
            // For demo purposes, we'll simulate periodic updates
            listenerThread = new Thread(this::liveUpdateSimulator);
            listenerThread.setDaemon(true);
            listenerThread.start();

            return true;
        } catch (Exception e) {
            LoggerUtil.error("Error starting listener", e);
            showStatus("Failed to start live updates", true);
            isListening.set(false);
            return false;
        }
    }

    private void stopListening() {
        isListening.set(false);
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
        showStatus("Live updates stopped", false);
        addToOutput("Stopped listening for updates.");
    }

    private void simulateResponse() {
        // This simulates receiving a response from replicas
        // In a real application, you would use a response queue
        new Thread(() -> {
            try {
                Thread.sleep(800);
                Platform.runLater(() -> {
                    addToOutput("[Replica 1] Last line: This is sample content from replica 1");
                });

                Thread.sleep(1000);
                Platform.runLater(() -> {
                    addToOutput("[Replica 2] Last line: This is sample content from replica 2");
                });

                Thread.sleep(1200);
                Platform.runLater(() -> {
                    addToOutput("[Replica 3] Last line: This is sample content from replica 3");
                });
            } catch (InterruptedException e) {
                // Thread interrupted
            }
        }).start();
    }

    private void liveUpdateSimulator() {
        int counter = 1;
        try {
            while (isListening.get()) {
                final int count = counter++;
                Platform.runLater(() -> {
                    addToOutput("[Live Update " + count + "] New content detected on replica " +
                            (count % 3 + 1) + ": Updated text line " + (count % 10 + 1));
                });
                Thread.sleep(3000);
            }
        } catch (InterruptedException e) {
            // Thread interrupted
        }
    }

    private void addToOutput(String message) {
        outputArea.appendText(message + "\n");
        outputArea.setScrollTop(Double.MAX_VALUE); // Scroll to bottom
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText("Status: " + message);

        if (isError) {
            statusLabel.getStyleClass().removeAll("success-status");
            statusLabel.getStyleClass().add("error-status");
        } else {
            statusLabel.getStyleClass().removeAll("error-status");
            statusLabel.getStyleClass().add("success-status");
        }

        // Create fade transition for the status
        FadeTransition fade = new FadeTransition(Duration.millis(500), statusLabel);
        fade.setFromValue(0.5);
        fade.setToValue(1.0);
        fade.setCycleCount(2);
        fade.setAutoReverse(true);
        fade.play();
    }

    private void cleanupResources() {
        stopListening();
        if (rabbitMQManager != null) {
            rabbitMQManager.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}