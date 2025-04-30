package messaging;

import com.rabbitmq.client.*;
import config.AppConfig;
import utils.LoggerUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

public class RabbitMQManager {
    private final ConnectionFactory factory;
    private Connection connection;
    private Channel channel;
    private String replyQueueName;
    /***
     * 1. ConcurrentHashMap
     *This is a thread-safe version of HashMap in Java, used in RabbitMQManager to store pending responses from replicas
     *In the code, it maps correlation IDs (unique identifiers for messages) to their corresponding CompletableFuture objects
     *It's used here because multiple threads might access the map simultaneously when:
     1-The main sends requests and stores futures
     2-The RabbitMQ callback thread processes responses
     3-The timeout thread checks for expired requests
     * 2. CompletableFuture
     *This is a Java class that represents a future result of an asynchronous computation
     *In our codebase, it's used to handle the asynchronous nature of RabbitMQ request-reply pattern where:
     1-A client sends a READ_LAST message
     2-Multiple replicas might respond at different times
     3-We need to wait for and collect all responses within a timeout period
     ***/
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingResponses;
    private boolean isConnected = false;

    public RabbitMQManager() throws IOException, TimeoutException {
        factory = new ConnectionFactory();
        factory.setHost(AppConfig.RABBITMQ_HOST);
        factory.setPort(AppConfig.RABBITMQ_PORT);
        factory.setUsername(AppConfig.RABBITMQ_USER);
        factory.setPassword(AppConfig.RABBITMQ_PASSWORD);

        // Set automatic connection recovery
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(2000);

        pendingResponses = new ConcurrentHashMap<>();

        // Try to connect with retry logic
        connect();
        setupReplyQueue();
    }

    private void connect() throws IOException, TimeoutException {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < 3 && !isConnected) {
            try {
                LoggerUtil.log("Attempting to connect to RabbitMQ (attempt " + (retryCount + 1) + ")");
                connection = factory.newConnection();
                channel = connection.createChannel();

                // Declare exchange - CRITICAL: use consistent parameters across all applications
                // Use "fanout" exchange type to broadcast to all queues
                channel.exchangeDeclare(AppConfig.EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true);

                isConnected = true;
                LoggerUtil.log("Successfully connected to RabbitMQ");

                // Add connection shutdown listener
                connection.addShutdownListener(cause -> {
                    isConnected = false;
                    LoggerUtil.log("RabbitMQ connection closed: " + cause.getMessage());
                });

            } catch (Exception e) {
                lastException = e;
                retryCount++;
                LoggerUtil.log("Failed to connect to RabbitMQ: " + e.getMessage() + ". Retrying in 1 second...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (!isConnected) {
            LoggerUtil.error("Failed to connect to RabbitMQ after " + retryCount + " attempts", lastException);
            throw new IOException("Failed to connect to RabbitMQ", lastException);
        }
    }

    private void setupReplyQueue() throws IOException {
        // Use an exclusive, auto-delete queue for replies
        replyQueueName = channel.queueDeclare("", false, true, true, null).getQueue();

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String correlationId = delivery.getProperties().getCorrelationId();
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);

            CompletableFuture<String> future = pendingResponses.get(correlationId);
            if (future != null && !future.isDone()) {
                future.complete(message);
            }
        };

        // Set up consumer for reply queue
        channel.basicConsume(replyQueueName, true, deliverCallback, consumerTag -> {});
        LoggerUtil.log("Reply queue set up: " + replyQueueName);
    }

    // Reconnect if needed
    private void reconnectIfNeeded() throws IOException, TimeoutException {
        if (!isConnected || connection == null || !connection.isOpen() || channel == null || !channel.isOpen()) {
            LoggerUtil.log("Connection lost, attempting to reconnect...");
            connect();
            setupReplyQueue();
        }
    }

    public void publish(String message) throws IOException {
        try {
            reconnectIfNeeded();

            // Make messages persistent
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .deliveryMode(2) // Make message persistent
                    .build();

            // Publish to the exchange with no routing key (fanout will broadcast to all bound queues)
            channel.basicPublish(AppConfig.EXCHANGE_NAME, "", properties, message.getBytes(StandardCharsets.UTF_8));
            LoggerUtil.log("Published: " + message);
        } catch (IOException | TimeoutException e) {
            LoggerUtil.error("Failed to publish message", e);
            throw new IOException("Failed to publish message", e);
        }
    }

    public CompletableFuture<String> publishWithResponse(String message) throws IOException {
        try {
            reconnectIfNeeded();

            String correlationId = UUID.randomUUID().toString();

            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .correlationId(correlationId)
                    .replyTo(replyQueueName)
                    .deliveryMode(2) // Make message persistent
                    .build();

            CompletableFuture<String> future = new CompletableFuture<>();
            pendingResponses.put(correlationId, future);

            channel.basicPublish(AppConfig.EXCHANGE_NAME, "", props, message.getBytes(StandardCharsets.UTF_8));
            LoggerUtil.log("Published with response request: " + message + " (correlationId: " + correlationId + ")");

            // Set up timeout to remove pending response after timeout period
            java.util.Timer timer = new java.util.Timer(true);
            timer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    CompletableFuture<String> f = pendingResponses.remove(correlationId);
                    if (f != null && !f.isDone()) {
                        f.complete(null);
                    }
                }
            }, AppConfig.REPLICA_RESPONSE_TIMEOUT);

            return future;
        } catch (IOException | TimeoutException e) {
            LoggerUtil.error("Failed to publish message with response", e);
            throw new IOException("Failed to publish message with response", e);
        }
    }

    public void publishResponse(String message, String replyTo, String correlationId) throws IOException {
        try {
            reconnectIfNeeded();

            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .correlationId(correlationId)
                    .build();

            channel.basicPublish("", replyTo, props, message.getBytes(StandardCharsets.UTF_8));
            LoggerUtil.log("Published response to " + replyTo + ": " + message);
        } catch (IOException | TimeoutException e) {
            LoggerUtil.error("Failed to publish response", e);
            throw new IOException("Failed to publish response", e);
        }
    }

    public void declareQueue(String queueName) throws IOException {
        try {
            reconnectIfNeeded();

            // Use durability settings for the queue
            Map<String, Object> args = new HashMap<>();
            args.put("x-queue-type", "classic"); // Ensure we use classic queue type

            // Declare the queue as durable (persists after restart) and not exclusive
            channel.queueDeclare(queueName, true, false, false, args);
            // Bind the queue to the exchange - critical step!
            channel.queueBind(queueName, AppConfig.EXCHANGE_NAME, "");

            LoggerUtil.log("Queue declared and bound: " + queueName + " to exchange: " + AppConfig.EXCHANGE_NAME);
        } catch (IOException | TimeoutException e) {
            LoggerUtil.error("Failed to declare queue: " + queueName, e);
            throw new IOException("Failed to declare queue: " + queueName, e);
        }
    }

    public void consume(String queueName, DeliverCallback deliverCallback) throws IOException {
        try {
            reconnectIfNeeded();

            // Set QoS - limit the number of unacknowledged messages
            channel.basicQos(1);

            // Set up consumer with auto-acknowledge mode (true)
            // This is important as the default is no acknowledgment
            String consumerTag = channel.basicConsume(queueName, true, deliverCallback, consTag -> {});

            LoggerUtil.log("Consumer registered for queue: " + queueName + " with tag: " + consumerTag);
        } catch (IOException | TimeoutException e) {
            LoggerUtil.error("Failed to set up consumer for queue: " + queueName, e);
            throw new IOException("Failed to set up consumer for queue: " + queueName, e);
        }
    }

    public void close() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
            isConnected = false;
            LoggerUtil.log("RabbitMQ connection closed");
        } catch (Exception e) {
            LoggerUtil.error("RabbitMQ closing failed", e);
        }
    }
}