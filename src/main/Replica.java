package main;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.DeliverCallback;
import config.AppConfig;
import database.TextEntity;
import database.TextRepository;
import messaging.RabbitMQManager;
import utils.LoggerUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
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
            LoggerUtil.log("Starting Replica " + replicaId);
            TextRepository repo = new TextRepository(replicaId);

            // Create the RabbitMQ manager
            LoggerUtil.log("Connecting to RabbitMQ...");
            RabbitMQManager rabbitMQManager = new RabbitMQManager();

            // Create a durable queue for this replica
            String queueName = AppConfig.QUEUE_PREFIX + replicaId;
            LoggerUtil.log("Declaring queue: " + queueName);
            rabbitMQManager.declareQueue(queueName);  // This will create a durable queue and bind it

            // Use a latch to keep the main thread alive
            CountDownLatch latch = new CountDownLatch(1);

            // Define the message handler
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), "UTF-8");

                // Log the message receipt with details
                LoggerUtil.log(String.format("Replica %d received message: %s (delivery tag: %d)",
                        replicaId, message, delivery.getEnvelope().getDeliveryTag()));

                try {
                    // Process the message and respond if needed
                    processMessage(repo, replicaId, message, rabbitMQManager, delivery);
                } catch (Exception e) {
                    LoggerUtil.error("Error processing message in replica " + replicaId, e);
                }
            };

            // Register consumer for the queue
            LoggerUtil.log("Setting up consumer for queue: " + queueName);
            rabbitMQManager.consume(queueName, deliverCallback);

            // Send a status message to indicate this replica is ready
            String status = String.format("Replica %d is ready on queue %s", replicaId, queueName);
            LoggerUtil.log(status);

            // Wait indefinitely
            latch.await();

        } catch (IOException | TimeoutException | InterruptedException e) {
            LoggerUtil.error("Replica " + replicaId + " failed to start", e);
        }
    }

    private static void processMessage(TextRepository repo, int replicaId, String message,
                                       RabbitMQManager rabbitMQManager, com.rabbitmq.client.Delivery delivery) throws Exception {
        AMQP.BasicProperties properties = delivery.getProperties();
        String replyTo = properties.getReplyTo();
        String correlationId = properties.getCorrelationId();

        try {
            // Parse as JSON first to see if it's a valid JSON message
            boolean isJsonMessage = false;
            JSONObject jsonMessage = null;
            try {
                jsonMessage = new JSONObject(message);
                isJsonMessage = true;
            } catch (Exception e) {
                // Not a JSON message, will process as text command
            }

            // Process text commands
            if (message.startsWith(AppConfig.MSG_WRITE_PREFIX)) {
                String[] parts = message.substring(AppConfig.MSG_WRITE_PREFIX.length()).split(" ", 2);
                if (parts.length < 2) {
                    throw new IllegalArgumentException("Invalid WRITE message format");
                }

                int lineNumber = Integer.parseInt(parts[0]);
                String content = parts[1];
                repo.insertLine(lineNumber, content);
                LoggerUtil.log("✅ Replica " + replicaId + " successfully wrote line " + lineNumber);

                // Send acknowledgement if replyTo exists
                if (replyTo != null) {
                    JSONObject response = new JSONObject();
                    response.put("replicaId", replicaId);
                    response.put("status", "success");
                    response.put("lineNumber", lineNumber);
                    rabbitMQManager.publishResponse(response.toString(), replyTo, correlationId);
                }

            } else if (message.equals(AppConfig.MSG_READ_LAST)) {
                TextEntity lastLine = repo.getLastLine();
                JSONObject response = new JSONObject();
                response.put("replicaId", replicaId);

                if (lastLine != null) {
                    response.put("lineNumber", lastLine.getLineNumber());
                    response.put("content", lastLine.getContent());
                    response.put("timestamp", lastLine.getTimestamp());
                    LoggerUtil.log("Replica " + replicaId + " sending last line: " + lastLine.getLineNumber());
                } else {
                    response.put("empty", true);
                    LoggerUtil.log("Replica " + replicaId + " has no data to send");
                }

                if (replyTo != null) {
                    rabbitMQManager.publishResponse(response.toString(), replyTo, correlationId);
                }

            } else if (message.equals(AppConfig.MSG_READ_ALL)) {
                List<TextEntity> lines = repo.getAllLines();
                LoggerUtil.log("Replica " + replicaId + " reading all lines (" + lines.size() + " found)");

                lines.forEach(text -> {
                    LoggerUtil.log("Replica " + replicaId + " Line: " + text.getLineNumber() + " => " + text.getContent());
                });

                // Send response if replyTo exists
                if (replyTo != null) {
                    sendAllLinesResponse(repo, replicaId, rabbitMQManager, replyTo, correlationId);
                }

            } else if (message.equals(AppConfig.MSG_STATUS_CHECK)) {
                // Send status info about this replica
                if (replyTo != null) {
                    JSONObject response = new JSONObject();
                    response.put("replicaId", replicaId);
                    response.put("status", "online");
                    response.put("queueName", AppConfig.QUEUE_PREFIX + replicaId);
                    response.put("timestamp", System.currentTimeMillis());

                    // Get count of lines in repository
                    List<TextEntity> lines = repo.getAllLines();
                    response.put("lineCount", lines.size());

                    rabbitMQManager.publishResponse(response.toString(), replyTo, correlationId);
                    LoggerUtil.log("Replica " + replicaId + " sent status response");
                }

            } else {
                LoggerUtil.log("Replica " + replicaId + " received unknown message: " + message);

                // Send error response if replyTo exists
                if (replyTo != null) {
                    JSONObject errorResponse = new JSONObject();
                    errorResponse.put("replicaId", replicaId);
                    errorResponse.put("error", "Unknown command");
                    errorResponse.put("receivedMessage", message);
                    rabbitMQManager.publishResponse(errorResponse.toString(), replyTo, correlationId);
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

    private static void sendAllLinesResponse(TextRepository repo, int replicaId,
                                             RabbitMQManager rabbitMQManager, String replyTo, String correlationId) throws IOException {
        List<TextEntity> allLines = repo.getAllLines();
        JSONObject response = new JSONObject();
        response.put("replicaId", replicaId);

        JSONArray linesArray = new JSONArray();
        allLines.forEach(line -> {
            JSONObject lineObj = new JSONObject();
            lineObj.put("lineNumber", line.getLineNumber());
            lineObj.put("content", line.getContent());
            lineObj.put("timestamp", line.getTimestamp());
            linesArray.put(lineObj);
        });

        response.put("lines", linesArray);
        LoggerUtil.log("Replica " + replicaId + " sending JSON response with " + allLines.size() + " lines");
        rabbitMQManager.publishResponse(response.toString(), replyTo, correlationId);
    }
}