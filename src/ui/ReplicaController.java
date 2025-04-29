package ui;

import config.AppConfig;
import database.TextRepository;
import messaging.RabbitMQManager;
import utils.JPAUtil;
import utils.LoggerUtil;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.persistence.EntityManager;

public class ReplicaController extends Application {

    private static int replicaId;
    private boolean running = true;
    private TextArea logArea;

    @Override
    public void start(Stage stage) {
        VBox root = new VBox(10);
        root.setStyle("-fx-padding: 20;");

        Button stopButton = new Button("Stop Replica " + replicaId);
        stopButton.setStyle("-fx-background-color: #ff4d4d; -fx-text-fill: white;");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(300);

        stopButton.setOnAction(e -> {
            running = false;
            LoggerUtil.log("Replica " + replicaId + " stopped manually.");
        });

        root.getChildren().addAll(stopButton, logArea);

        Scene scene = new Scene(root, 400, 400);
        scene.getStylesheets().add(getClass().getResource("/ui/style.css").toExternalForm());
        stage.setTitle("Replica " + replicaId);
        stage.setScene(scene);
        stage.show();

        startListening();
    }

    private void startListening() {
        new Thread(() -> {
            try {
                RabbitMQManager rabbit = new RabbitMQManager();
                EntityManager em = JPAUtil.getEntityManager(replicaId);
                TextRepository repo = new TextRepository(em);

                String queueName = AppConfig.QUEUE_PREFIX + replicaId;
                rabbit.declareQueue(queueName);

                rabbit.consume(queueName, (consumerTag, delivery) -> {
                    String message = new String(delivery.getBody(), "UTF-8");

                    Platform.runLater(() -> logArea.appendText("Received: " + message + "\n"));

                    if (running) {
                        if (message.startsWith("WRITE ")) {
                            String[] parts = message.substring(6).split(" ", 2);
                            int lineNumber = Integer.parseInt(parts[0]);
                            String content = parts[1];
                            repo.saveLine(lineNumber, content);
                            LoggerUtil.log("Replica " + replicaId + " saved line: " + content);
                        }
                        // TODO : gérer "READ LAST" / "READ ALL" ici plus tard
                    }
                });

            } catch (Exception e) {
                LoggerUtil.error("Error in Replica " + replicaId, e);
            }
        }).start();
    }

    public static void launchReplica(int id) {
        ReplicaController.replicaId = id;
        Application.launch(ReplicaController.class);
    }
}
