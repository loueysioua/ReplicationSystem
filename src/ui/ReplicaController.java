package ui;

import config.AppConfig;
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
    private final TextArea logArea;
    private final TextRepository repo;
    private volatile boolean running = true;

    public ReplicaController(int replicaId) {
        this.replicaId = replicaId;
        this.repo = new TextRepository(replicaId);
        this.view = buildUI();
        initRabbitMQ();
    }

    private VBox buildUI() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-border-color: gray; -fx-background-color: white;");

        Label title = new Label("Replica #" + replicaId);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(200);

        Button stopBtn = new Button("Stop");
        stopBtn.setOnAction(e -> {
            running = false;
            log("Replica stopped.");
        });

        Button viewDbBtn = new Button("View DB");
        viewDbBtn.setOnAction(e -> {
            for (JSONObject obj : repo.getAllLinesAsJson()) {
                log("📄 " + obj.toString());
            }
        });

        box.getChildren().addAll(title, logArea, stopBtn, viewDbBtn);
        return box;
    }

    private void initRabbitMQ() {
        new Thread(() -> {
            try {
                RabbitMQManager rmq = new RabbitMQManager();
                String queueName = AppConfig.QUEUE_PREFIX + replicaId;
                rmq.declareQueue(queueName);
                rmq.consume(queueName, (tag, delivery) -> {
                    if (!running) return;
                    String msg = new String(delivery.getBody(), "UTF-8");
                    log("📥 Received: " + msg);
                    JSONObject json = new JSONObject(msg);
                    if (json.has("line_number") && json.has("content")) {
                        int line = json.getInt("line_number");
                        String text = json.getString("content");
                        repo.insertLine(line, text);
                    }
                });
            } catch (Exception e) {
                LoggerUtil.error("RabbitMQ error", e);
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
