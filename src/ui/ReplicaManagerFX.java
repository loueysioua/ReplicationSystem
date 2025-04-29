package ui;

import main.ClientReaderV2;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import utils.LoggerUtil;

import java.util.ArrayList;
import java.util.List;

public class ReplicaManagerFX extends Application {

    private List<Process> replicaProcesses = new ArrayList<>();
    private Label statusLabel;

    @Override
    public void start(Stage stage) {
        VBox root = new VBox(20);
        root.setPadding(new Insets(25));
        root.getStyleClass().add("main-container");

        // Title section
        Label titleLabel = new Label("Distributed Text Editor - Control Panel");
        titleLabel.getStyleClass().add("title-label");

        // Separator
        Separator separator = new Separator();
        separator.getStyleClass().add("separator");

        // Status indicator
        statusLabel = new Label("System Status: Ready");
        statusLabel.getStyleClass().add("status-label");

        // Replica controls section
        TitledPane replicaControlsPane = createReplicaControlsPane();

        // Client controls section
        TitledPane clientControlsPane = createClientControlsPane();

        // System controls
        TitledPane systemControlsPane = createSystemControlsPane();

        root.getChildren().addAll(titleLabel, separator, statusLabel,
                replicaControlsPane, clientControlsPane, systemControlsPane);

        Scene scene = new Scene(root, 500, 650);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
        stage.setTitle("Replica Manager");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> cleanupResources());
        stage.show();
    }

    private TitledPane createReplicaControlsPane() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(10));

        HBox replicaControls = new HBox(10);
        replicaControls.setAlignment(Pos.CENTER_LEFT);

        Label spinnerLabel = new Label("Number of replicas:");
        Spinner<Integer> replicaSpinner = new Spinner<>(1, 10, 3);
        replicaSpinner.setEditable(true);
        replicaSpinner.setPrefWidth(100);

        replicaControls.getChildren().addAll(spinnerLabel, replicaSpinner);

        Button launchButton = new Button("Launch Replicas");
        launchButton.getStyleClass().add("primary-button");

        launchButton.setOnAction(e -> {
            int count = replicaSpinner.getValue();
            launchReplicas(count, launchButton);
        });

        Button stopReplicasButton = new Button("Stop All Replicas");
        stopReplicasButton.getStyleClass().add("danger-button");
        stopReplicasButton.setOnAction(e -> stopAllReplicas());

        HBox buttonContainer = new HBox(15);
        buttonContainer.setAlignment(Pos.CENTER);
        buttonContainer.getChildren().addAll(launchButton, stopReplicasButton);

        content.getChildren().addAll(replicaControls, buttonContainer);

        TitledPane pane = new TitledPane("Replica Management", content);
        pane.setCollapsible(true);
        pane.setExpanded(true);

        return pane;
    }

    private TitledPane createClientControlsPane() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(10));

        GridPane clientGrid = new GridPane();
        clientGrid.setHgap(10);
        clientGrid.setVgap(15);
        clientGrid.setPadding(new Insets(5));

        // Writer client section
        Label writerLabel = new Label("Text Writer Client:");
        Button launchWriterButton = new Button("Launch Writer");
        launchWriterButton.getStyleClass().add("success-button");
        launchWriterButton.setMaxWidth(Double.MAX_VALUE);

        launchWriterButton.setOnAction(e -> launchClientWriter());

        // Reader client section
        Label readerLabel = new Label("Text Reader Client:");
        Button launchSimpleReaderButton = new Button("Launch Simple Reader");
        launchSimpleReaderButton.getStyleClass().add("info-button");
        launchSimpleReaderButton.setMaxWidth(Double.MAX_VALUE);

        Button launchAdvancedReaderButton = new Button("Launch Advanced Reader");
        launchAdvancedReaderButton.getStyleClass().add("info-button");
        launchAdvancedReaderButton.setMaxWidth(Double.MAX_VALUE);

        launchSimpleReaderButton.setOnAction(e -> launchClientReader(false));
        launchAdvancedReaderButton.setOnAction(e -> launchClientReader(true));

        // Add components to grid
        clientGrid.add(writerLabel, 0, 0);
        clientGrid.add(launchWriterButton, 1, 0);
        clientGrid.add(readerLabel, 0, 1);
        clientGrid.add(launchSimpleReaderButton, 1, 1);
        clientGrid.add(launchAdvancedReaderButton, 1, 2);

        // Set column constraints
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(35);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(65);
        clientGrid.getColumnConstraints().addAll(col1, col2);

        content.getChildren().add(clientGrid);

        TitledPane pane = new TitledPane("Client Applications", content);
        pane.setCollapsible(true);
        pane.setExpanded(true);

        return pane;
    }

    private TitledPane createSystemControlsPane() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(10));

        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(150);
        logArea.setPromptText("System logs will appear here...");

        // Create a custom LoggerUtil listener that updates the UI
        LoggerUtil.setLogListener(message -> {
            Platform.runLater(() -> {
                logArea.appendText(message + "\n");
                logArea.setScrollTop(Double.MAX_VALUE); // Auto-scroll to bottom
            });
        });

        Button clearLogsButton = new Button("Clear Logs");
        clearLogsButton.getStyleClass().add("secondary-button");
        clearLogsButton.setOnAction(e -> logArea.clear());

        content.getChildren().addAll(logArea, clearLogsButton);

        TitledPane pane = new TitledPane("System Logs", content);
        pane.setCollapsible(true);
        pane.setExpanded(true);

        return pane;
    }

    private void launchReplicas(int count, Button launchButton) {
        updateStatus("Launching " + count + " replicas...");
        launchButton.setDisable(true);
        launchButton.setText("Launching...");

        final int[] currentReplica = {1};

        Timeline launchTimeline = new Timeline();
        for (int i = 0; i < count; i++) {
            launchTimeline.getKeyFrames().add(
                new KeyFrame(Duration.seconds(i * 0.8), e -> {
                    try {
                        LoggerUtil.log("Starting Replica " + currentReplica[0]);

                        ProcessBuilder builder = new ProcessBuilder(
                                System.getProperty("java.home") + System.getProperty("file.separator") + "bin" +
                                        System.getProperty("file.separator") + "java",
                                "-cp", System.getProperty("java.class.path"),
                                "--module-path", "C:\\Users\\ASUS\\Downloads\\javafx-sdk-21.0.7\\lib",
                                "--add-modules", "javafx.controls,javafx.fxml",
                                launcher.ReplicaLauncher.class.getName(),
                                String.valueOf(currentReplica[0]));

                        Process process = builder.start();
                        replicaProcesses.add(process);
                        currentReplica[0]++;
                    } catch (Exception ex) {
                        LoggerUtil.error("Error launching replica " + currentReplica[0], ex);
                    }
                })
            );
        }

        launchTimeline.getKeyFrames().add(
            new KeyFrame(Duration.seconds(count * 0.8), e -> {
                launchButton.setDisable(false);
                launchButton.setText("Launch Replicas");
                updateStatus("All replicas launched successfully");
            })
        );

        launchTimeline.setOnFinished(e -> {
            if (currentReplica[0] < count + 1) {
                LoggerUtil.error("Some replicas failed to launch", new Exception("Not all replicas were started"));
                launchButton.setDisable(false);
                launchButton.setText("Launch Replicas");
                updateStatus("Error launching replicas");
            }
        });

        launchTimeline.play();
    }

    private void stopAllReplicas() {
        updateStatus("Stopping all replicas...");
        
        for (Process process : replicaProcesses) {
            if (process.isAlive()) {
                process.destroy();
            }
        }

        replicaProcesses.clear();
        updateStatus("All replicas stopped");
    }

    private void launchClientWriter() {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    System.getProperty("java.home") + System.getProperty("file.separator") + "bin" +
                            System.getProperty("file.separator") + "java",
                    "-cp", System.getProperty("java.class.path"),
                    "--module-path", "C:\\Users\\ASUS\\Downloads\\javafx-sdk-21.0.7\\lib",
                    "--add-modules", "javafx.controls,javafx.fxml",
                    ClientWriterFX.class.getName());

            builder.start();
            LoggerUtil.log("Started Client Writer in a new process");
            updateStatus("Client Writer launched");
        } catch (Exception e) {
            LoggerUtil.error("Failed to start Client Writer", e);
            updateStatus("Error launching Client Writer");
        }
    }

    private void launchClientReader(boolean advanced) {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    System.getProperty("java.home") + System.getProperty("file.separator") + "bin" +
                            System.getProperty("file.separator") + "java",
                    "-cp", System.getProperty("java.class.path"),
                    "--module-path", "C:\\Users\\ASUS\\Downloads\\javafx-sdk-21.0.7\\lib",
                    "--add-modules", "javafx.controls,javafx.fxml",
                    advanced ? ClientReaderV2.class.getName() : ClientReaderFX.class.getName());

            builder.start();
            LoggerUtil.log("Started " + (advanced ? "Advanced" : "Simple") + " Client Reader in a new process");
            updateStatus((advanced ? "Advanced" : "Simple") + " Client Reader launched");
        } catch (Exception e) {
            LoggerUtil.error("Failed to start Client Reader", e);
            updateStatus("Error launching Client Reader");
        }
    }



    private void updateStatus(String status) {
        statusLabel.setText("System Status: " + status);
    }

    private void cleanupResources() {
        // Stop all replicas when application closes
        stopAllReplicas();
    }

    public static void main(String[] args) {
        launch(args);
    }
}