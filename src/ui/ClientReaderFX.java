package ui;

import config.AppConfig;
import database.TextRepository;
import database.TextEntity;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import messaging.RabbitMQManager;
import utils.LoggerUtil;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ClientReaderFX extends Application {
    private TextArea output;
    private ListView<String> replicaResponsesList;
    private RabbitMQManager rmq;
    
    @Override
    public void start(Stage stage) {
        try {
            rmq = new RabbitMQManager();
        } catch (Exception e) {
            LoggerUtil.error("Failed to initialize RabbitMQ", e);
            showError("Failed to connect to message broker: " + e.getMessage());
            return;
        }

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        Label titleLabel = new Label("TextSync Reader");
        titleLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold;");

        Button readBtn = new Button("Read Last Line");
        readBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white;");
        readBtn.setPrefWidth(200);

        output = new TextArea();
        output.setEditable(false);
        output.setPrefRowCount(3);
        output.setStyle("-fx-font-family: 'Courier New';");

        Label responsesLabel = new Label("Replica Responses:");
        replicaResponsesList = new ListView<>();
        replicaResponsesList.setPrefHeight(200);
        VBox.setVgrow(replicaResponsesList, Priority.ALWAYS);

        readBtn.setOnAction(e -> readLastLine());

        root.getChildren().addAll(
            titleLabel,
            readBtn,
            new Label("Most Recent Line:"),
            output,
            responsesLabel,
            replicaResponsesList
        );

        Scene scene = new Scene(root, 500, 500);
        stage.setScene(scene);
        stage.setTitle("TextSync Reader");
        stage.setOnCloseRequest(event -> {
            if (rmq != null) rmq.close();
        });
        stage.show();
    }

    private void readLastLine() {
        output.clear();
        replicaResponsesList.getItems().clear();
        output.setText("Querying replicas...");
        
        try {
            CompletableFuture<String> future = rmq.publishWithResponse(AppConfig.MSG_READ_LAST);
            
            // Process responses
            List<JSONObject> responses = new ArrayList<>();
            List<JSONObject> errorResponses = new ArrayList<>();
            
            // Find the most recent response
            JSONObject mostRecent = null;
            long mostRecentTimestamp = 0;
            
            long startTime = System.currentTimeMillis();
            long elapsedTime = 0;
            
            while (elapsedTime < AppConfig.REPLICA_RESPONSE_TIMEOUT) {
                try {
                    String response = future.get(AppConfig.RESPONSE_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
                    if (response != null) {
                        JSONObject json = new JSONObject(response);
                        
                        // Check if this is an error response
                        if (json.has("error")) {
                            errorResponses.add(json);
                            Platform.runLater(() -> {
                                replicaResponsesList.getItems().add(String.format(
                                    "❌ Error from Replica %d: %s",
                                    json.getInt("replicaId"),
                                    json.getString("error")
                                ));
                            });
                            continue;
                        }
                        
                        // Skip empty responses but add them to the list
                        if (json.optBoolean("empty", false)) {
                            responses.add(json);
                            Platform.runLater(() -> {
                                replicaResponsesList.getItems().add(String.format(
                                    "Replica %d: No data available",
                                    json.getInt("replicaId")
                                ));
                            });
                            continue;
                        }
                        
                        responses.add(json);
                        long timestamp = json.getLong("timestamp");
                        if (timestamp > mostRecentTimestamp) {
                            mostRecentTimestamp = timestamp;
                            mostRecent = json;
                        }
                        
                        // Add to responses list
                        Platform.runLater(() -> {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                                .withZone(ZoneId.systemDefault());
                            String timeStr = formatter.format(Instant.ofEpochMilli(timestamp));
                            
                            replicaResponsesList.getItems().add(String.format(
                                "Replica %d: Line %d [%s] - %s",
                                json.getInt("replicaId"),
                                json.getInt("lineNumber"),
                                timeStr,
                                json.getString("content")
                            ));
                        });
                    }
                } catch (TimeoutException e) {
                    // This is expected, continue checking
                }
                
                elapsedTime = System.currentTimeMillis() - startTime;
                
                // Early completion if we have received responses from all replicas
                // or if we have received error responses from all non-responding replicas
                if (responses.size() + errorResponses.size() >= 2) { // Assuming at least 2 replicas
                    break;
                }
            }
            
            final int totalResponses = responses.size();
            final int totalErrors = errorResponses.size();
            
            if (mostRecent != null) {
                final JSONObject finalMostRecent = mostRecent;
                Platform.runLater(() -> {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                        .withZone(ZoneId.systemDefault());
                    String timeStr = formatter.format(Instant.ofEpochMilli(finalMostRecent.getLong("timestamp")));
                    
                    String statusMsg = String.format(
                        "Most recent line (from replica %d):\nLine %d: %s\nWritten at: %s\n\n" +
                        "Received %d successful response(s)\n" +
                        "Received %d error response(s)",
                        finalMostRecent.getInt("replicaId"),
                        finalMostRecent.getInt("lineNumber"),
                        finalMostRecent.getString("content"),
                        timeStr,
                        totalResponses,
                        totalErrors
                    );
                    
                    output.setText(statusMsg);
                    output.setStyle("-fx-text-fill: " + (totalErrors > 0 ? "orange" : "black") + ";");
                });
            } else {
                Platform.runLater(() -> {
                    String errorMsg = totalErrors > 0 ?
                        String.format("No valid responses received. %d replica(s) reported errors.", totalErrors) :
                        "No responses received from any replicas after " + 
                        (AppConfig.REPLICA_RESPONSE_TIMEOUT / 1000.0) + " seconds";
                    
                    output.setText(errorMsg);
                    output.setStyle("-fx-text-fill: red;");
                });
            }
            
        } catch (Exception e) {
            LoggerUtil.error("Error reading last line", e);
            Platform.runLater(() -> {
                output.setText("Error: " + e.getMessage());
                output.setStyle("-fx-text-fill: red;");
            });
        }
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
