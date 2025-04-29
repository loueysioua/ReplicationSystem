package ui;

import messaging.RabbitMQManager;
import utils.LoggerUtil;

import javafx.application.Application;
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

public class ClientWriterFX extends Application {

    private TextArea messageHistoryArea;
    private Label statusLabel;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        root.getStyleClass().add("main-container");

        // Header section
        VBox headerBox = createHeaderSection();
        root.setTop(headerBox);

        // Input section
        VBox inputSection = createInputSection();
        root.setCenter(inputSection);

        // History section
        VBox historySection = createHistorySection();
        root.setBottom(historySection);

        Scene scene = new Scene(root, 600, 600);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
        stage.setTitle("Text Editor - Writer Client");
        stage.setScene(scene);
        stage.show();
    }

    private VBox createHeaderSection() {
        VBox headerBox = new VBox(10);
        headerBox.setPadding(new Insets(0, 0, 15, 0));

        Label titleLabel = new Label("Text Editor - Writer Client");
        titleLabel.getStyleClass().add("title-label");

        statusLabel = new Label("Status: Ready");
        statusLabel.getStyleClass().add("status-label");

        Separator separator = new Separator();

        headerBox.getChildren().addAll(titleLabel, statusLabel, separator);
        return headerBox;
    }

    private VBox createInputSection() {
        VBox inputBox = new VBox(15);
        inputBox.setPadding(new Insets(10));
        inputBox.getStyleClass().add("input-section");

        Label instructionLabel = new Label("Enter line number and content to write to distributed text storage:");
        instructionLabel.getStyleClass().add("instruction-label");

        // Line number input with validation
        HBox lineNumberBox = new HBox(10);
        lineNumberBox.setAlignment(Pos.CENTER_LEFT);

        Label lineNumberLabel = new Label("Line Number:");
        Spinner<Integer> lineNumberSpinner = new Spinner<>(1, 999, 1);
        lineNumberSpinner.setEditable(true);
        lineNumberSpinner.setPrefWidth(120);

        lineNumberBox.getChildren().addAll(lineNumberLabel, lineNumberSpinner);

        // Content input
        Label contentLabel = new Label("Text Content:");
        TextArea contentArea = new TextArea();
        contentArea.setPrefRowCount(5);
        contentArea.setWrapText(true);
        contentArea.setPromptText("Enter the text you want to write to this line number...");

        // Button section
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        Button sendButton = new Button("Send Message");
        sendButton.getStyleClass().add("primary-button");

        Button clearButton = new Button("Clear Input");
        clearButton.getStyleClass().add("secondary-button");

        buttonBox.getChildren().addAll(sendButton, clearButton);

        // Add action handlers
        sendButton.setOnAction(e -> {
            try {
                int lineNumber = lineNumberSpinner.getValue();
                String content = contentArea.getText().trim();

                if (content.isEmpty()) {
                    showStatus("Please enter content to send", true);
                    return;
                }

                sendMessage(lineNumber, content);
                addToHistory("Line " + lineNumber + ": " + content);

                // Don't clear line number, just content
                contentArea.clear();

                showStatus("Message sent successfully", false);
            } catch (Exception ex) {
                LoggerUtil.error("Error sending message", ex);
                showStatus("Failed to send message", true);
            }
        });

        clearButton.setOnAction(e -> {
            contentArea.clear();
            showStatus("Input cleared", false);
        });

        inputBox.getChildren().addAll(instructionLabel, lineNumberBox, contentLabel, contentArea, buttonBox);
        return inputBox;
    }

    private VBox createHistorySection() {
        VBox historyBox = new VBox(10);
        historyBox.setPadding(new Insets(15, 0, 0, 0));

        Label historyLabel = new Label("Message History");
        historyLabel.getStyleClass().add("section-header");

        messageHistoryArea = new TextArea();
        messageHistoryArea.setEditable(false);
        messageHistoryArea.setPrefHeight(150);
        messageHistoryArea.setWrapText(true);
        messageHistoryArea.setPromptText("Your sent messages will appear here...");

        Button clearHistoryButton = new Button("Clear History");
        clearHistoryButton.getStyleClass().add("secondary-button");
        clearHistoryButton.setOnAction(e -> messageHistoryArea.clear());

        historyBox.getChildren().addAll(historyLabel, messageHistoryArea, clearHistoryButton);
        return historyBox;
    }

    private void sendMessage(int lineNumber, String content) {
        try {
            RabbitMQManager rabbit = new RabbitMQManager();
            rabbit.publish("WRITE " + lineNumber + " " + content);
            LoggerUtil.log("Sent message: WRITE " + lineNumber + " " + content);
            rabbit.close();
        } catch (IOException | TimeoutException ex) {
            LoggerUtil.error("Error sending message", ex);
            throw new RuntimeException("Failed to send message", ex);
        }
    }

    private void addToHistory(String message) {
        messageHistoryArea.appendText(message + "\n");
        messageHistoryArea.setScrollTop(Double.MAX_VALUE); // Scroll to bottom
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

    public static void main(String[] args) {
        launch(args);
    }
}