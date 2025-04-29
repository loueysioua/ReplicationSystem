package ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import utils.LoggerUtil;

public class ReplicaManagerFX extends Application {

    @Override
    public void start(Stage stage) {
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));

        Label titleLabel = new Label("Distributed Text Editor - Control Panel");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        HBox replicaControls = new HBox(10);
        Label spinnerLabel = new Label("Number of replicas:");
        Spinner<Integer> replicaSpinner = new Spinner<>(1, 10, 3);
        replicaSpinner.setEditable(true);
        replicaControls.getChildren().addAll(spinnerLabel, replicaSpinner);

        Button launchButton = new Button("Launch Replicas");
        launchButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");

        Button launchClientWriterButton = new Button("Launch Writer Client");
        launchClientWriterButton.setOnAction(e -> {
            launchClientWriter();
        });

        Button launchClientReaderButton = new Button("Launch Reader Client");
        launchClientReaderButton.setOnAction(e -> {
            launchClientReader();
        });

        launchButton.setOnAction(e -> {
            int count = replicaSpinner.getValue();
            launchButton.setDisable(true);
            launchButton.setText("Launching...");

            // Start each replica in a separate thread to avoid blocking the UI
            new Thread(() -> {
                for (int i = 1; i <= count; i++) {
                    ReplicaController.launchReplica(i);

                    // Add a small delay between launching replicas to prevent resource contention
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {
                        LoggerUtil.error("Thread interrupted while launching replicas", ex);
                    }
                }

                Platform.runLater(() -> {
                    launchButton.setDisable(false);
                    launchButton.setText("Launch Replicas");
                });
            }).start();
        });

        root.getChildren().addAll(titleLabel, replicaControls, launchButton,
                launchClientWriterButton, launchClientReaderButton);

        Scene scene = new Scene(root, 350, 250);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
        stage.setTitle("Replica Manager");
        stage.setScene(scene);
        stage.show();
    }

    private void launchClientWriter() {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    System.getProperty("java.home") + System.getProperty("file.separator") + "bin" +
                            System.getProperty("file.separator") + "java",
                    "-cp", System.getProperty("java.class.path"),
                    ClientWriterFX.class.getName());

            builder.start();
            LoggerUtil.log("Started Client Writer in a new process");
        } catch (Exception e) {
            LoggerUtil.error("Failed to start Client Writer", e);
        }
    }

    private void launchClientReader() {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    System.getProperty("java.home") + System.getProperty("file.separator") + "bin" +
                            System.getProperty("file.separator") + "java",
                    "-cp", System.getProperty("java.class.path"),
                    ClientReaderFX.class.getName());

            builder.start();
            LoggerUtil.log("Started Client Reader in a new process");
        } catch (Exception e) {
            LoggerUtil.error("Failed to start Client Reader", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}