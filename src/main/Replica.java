package main;

import config.AppConfig;
import database.TextEntity;

import database.TextRepository;
import messaging.RabbitMQManager;
import utils.LoggerUtil;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Replica {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Replica ID is required");
            System.exit(1);
        }
        int replicaId = Integer.parseInt(args[0]);

        try {
            TextRepository repo = new TextRepository(replicaId);
            RabbitMQManager rabbitMQManager = new RabbitMQManager();

            String queueName = AppConfig.QUEUE_PREFIX + replicaId;
            rabbitMQManager.declareQueue(queueName);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), "UTF-8");
                LoggerUtil.log("Replica " + replicaId + " received: " + message);

                if (message.startsWith("WRITE ")) {
                    String[] parts = message.substring(6).split(" ", 2);
                    int lineNumber = Integer.parseInt(parts[0]);
                    String content = parts[1];
                    repo.insertLine(lineNumber, content);
                } else if (message.equals("READ LAST")) {
                    TextEntity lastLine = repo.getLastLine();
                    if (lastLine != null) {
                        LoggerUtil.log("Replica " + replicaId + " last line: " + lastLine);
                    }
                } else if (message.equals("READ ALL")) {
                    repo.getAllLines().forEach(text -> {
                        LoggerUtil.log("Replica " + replicaId + " Line: " + text.getLineNumber() + " => " + text.getContent());
                    });
                }
            };

            rabbitMQManager.consume(queueName, deliverCallback);
LoggerUtil.log("Replica " + replicaId + " is waiting for messages...");
        } catch (IOException | TimeoutException e) {
            LoggerUtil.error("Replica failed", e);
        }
    }
}