package main;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.DeliverCallback;
import config.AppConfig;
import database.TextEntity;
import database.TextRepository;
import messaging.RabbitMQManager;
import utils.LoggerUtil;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
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

            // Create a durable queue for this replica
            String queueName = AppConfig.QUEUE_PREFIX + replicaId;
            rabbitMQManager.declareQueue(queueName);  // This will now create a durable queue and bind it

            // Use a latch to keep the main thread alive
            CountDownLatch latch = new CountDownLatch(1);

            // Set up asynchronous message handling with manual acknowledgment
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), "UTF-8");
                LoggerUtil.log("Replica " + replicaId + " received: " + message);

                try {
                    processMessage(repo, replicaId, message, rabbitMQManager, delivery);
                } catch (Exception e) {
                    LoggerUtil.error("Error processing message in replica " + replicaId, e);
                }
            };

            rabbitMQManager.consume(queueName, deliverCallback);
            LoggerUtil.log("Replica " + replicaId + " is waiting for messages...");

            // Keep the application running
            latch.await();
        } catch (IOException | TimeoutException | InterruptedException e) {
            LoggerUtil.error("Replica failed", e);
        }
    }

    private static void processMessage(TextRepository repo, int replicaId, String message, 
                                    RabbitMQManager rabbitMQManager, com.rabbitmq.client.Delivery delivery) throws Exception {
        AMQP.BasicProperties properties = delivery.getProperties();
        String replyTo = properties.getReplyTo();
        String correlationId = properties.getCorrelationId();

        try {
            if (message.startsWith(AppConfig.MSG_WRITE_PREFIX)) {
                String[] parts = message.substring(AppConfig.MSG_WRITE_PREFIX.length()).split(" ", 2);
                int lineNumber = Integer.parseInt(parts[0]);
                String content = parts[1];
                repo.insertLine(lineNumber, content);
                LoggerUtil.log("✅ Replica " + replicaId + " successfully wrote line " + lineNumber);
            } else if (message.equals(AppConfig.MSG_READ_LAST)) {
                TextEntity lastLine = repo.getLastLine();
                if (lastLine != null && replyTo != null) {
                    JSONObject response = new JSONObject();
                    response.put("replicaId", replicaId);
                    response.put("lineNumber", lastLine.getLineNumber());
                    response.put("content", lastLine.getContent());
                    response.put("timestamp", lastLine.getTimestamp());
                    
                    LoggerUtil.log("Replica " + replicaId + " sending response: " + response.toString());
                    rabbitMQManager.publishResponse(response.toString(), replyTo, correlationId);
                } else {
                    LoggerUtil.log("Replica " + replicaId + " has no data to send");
                    if (replyTo != null) {
                        JSONObject response = new JSONObject();
                        response.put("replicaId", replicaId);
                        response.put("empty", true);
                        rabbitMQManager.publishResponse(response.toString(), replyTo, correlationId);
                    }
                }
            } else if (message.equals(AppConfig.MSG_READ_ALL)) {
                repo.getAllLines().forEach(text -> {
                    LoggerUtil.log("Replica " + replicaId + " Line: " + text.getLineNumber() + " => " + text.getContent());
                });
            } else {
                try {
                    JSONObject json = new JSONObject(message);
                    if (json.has("line_number") && json.has("content")) {
                        int lineNumber = json.getInt("line_number");
                        String content = json.getString("content");
                        repo.insertLine(lineNumber, content);
                        LoggerUtil.log("Replica " + replicaId + " wrote line " + lineNumber + " from JSON");
                    }
                } catch (Exception ex) {
                    LoggerUtil.log("Invalid message format received by replica " + replicaId + ": " + message);
                }
            }
        } catch (Exception e) {
            LoggerUtil.error("Error processing message in replica " + replicaId + ": " + message, e);
            if (replyTo != null) {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("replicaId", replicaId);
                errorResponse.put("error", e.getMessage());
                rabbitMQManager.publishResponse(errorResponse.toString(), replyTo, correlationId);
            }
            throw e;
        }
    }
}