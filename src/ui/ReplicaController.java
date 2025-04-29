package ui;

import config.AppConfig;
import database.TextEntity;
import database.TextRepository;
import messaging.RabbitMQManager;
import utils.LoggerUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.json.JSONObject;

public class ReplicaController {

    private final int replicaId;
    private final VBox view;
    private final TextArea logArea= new TextArea();;
    private final TextRepository repository;
    private volatile boolean running = true;

    public ReplicaController(int replicaId) {
        this.replicaId = replicaId;
        this.repository = new TextRepository(replicaId);
        this.view = buildUI();
        startListeningToQueue();
    }

    private VBox buildUI() {
        Label title = new Label("Replica #" + replicaId);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        logArea.setEditable(false);
        logArea.setPrefHeight(250);

        Button stopBtn = new Button("Stop");
        stopBtn.setOnAction(e -> {
            running = false;
            log("🔴 Replica stopped");
        });

        Button showDbBtn = new Button("View DB");
        showDbBtn.setOnAction(e -> {
            for (TextEntity entity : repository.getAllLines()) {
                log("📄 " + entity.toString());
            }
        });

        VBox box = new VBox(10, title, logArea, stopBtn, showDbBtn);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: white;");
        return box;
    }

    private void startListeningToQueue() {
        new Thread(() -> {
            try {
                RabbitMQManager rmq = new RabbitMQManager();
                String queueName = AppConfig.QUEUE_PREFIX + replicaId;
                rmq.declareQueue(queueName);
                rmq.consume(queueName, (consumerTag, delivery) -> {
                    if (!running) return;

                    String message = new String(delivery.getBody(), "UTF-8");
                    log("📥 Received: " + message);

                    try {
                        JSONObject json = new JSONObject(message);
                        if (json.has("line_number") && json.has("content")) {
                            TextEntity entity = new TextEntity(
                                    json.getInt("line_number"),
                                    json.getString("content")
                            );
                            repository.insertLine(entity.getLineNumber(), entity.getContent());
                        }
                    } catch (Exception ex) {
                        LoggerUtil.error("❌ Invalid message format", ex);
                    }
                });

            } catch (Exception e) {
                LoggerUtil.error("❌ Failed to connect RabbitMQ for replica " + replicaId, e);
            }
        }).start();
    }

    private void log(String msg) {
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }

    public VBox getView() {
        return view;
    }
}
