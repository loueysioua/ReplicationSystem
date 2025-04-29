package messaging;

import com.rabbitmq.client.*;
import config.AppConfig;
import utils.LoggerUtil;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    public RabbitMQManager() throws IOException, TimeoutException {
        factory = new ConnectionFactory();
        factory.setHost(AppConfig.RABBITMQ_HOST);
        factory.setPort(AppConfig.RABBITMQ_PORT);
        factory.setUsername(AppConfig.RABBITMQ_USER);
        factory.setPassword(AppConfig.RABBITMQ_PASSWORD);
        pendingResponses = new ConcurrentHashMap<>();
        connect();
        setupReplyQueue();
    }

    private void connect() throws IOException, TimeoutException {
        connection = factory.newConnection();
        channel = connection.createChannel();
        // Declare exchange as durable to persist across broker restarts
        channel.exchangeDeclare(AppConfig.EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true);
    }

    private void setupReplyQueue() throws IOException {
        replyQueueName = channel.queueDeclare().getQueue();
        
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String correlationId = delivery.getProperties().getCorrelationId();
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            
            CompletableFuture<String> future = pendingResponses.get(correlationId);
            if (future != null && !future.isDone()) {
                future.complete(message);
            }
        };
        
        channel.basicConsume(replyQueueName, true, deliverCallback, consumerTag -> {});
    }

    public void publish(String message) throws IOException {
        if (channel == null || !channel.isOpen()) {
            try {
                connect();
            } catch (TimeoutException e) {
                throw new IOException("Failed to reconnect", e);
            }
        }
        
        // Make messages persistent and mandatory
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
            .deliveryMode(2) // Make message persistent
            .build();

        channel.basicPublish(AppConfig.EXCHANGE_NAME, "", true, properties, message.getBytes(StandardCharsets.UTF_8));
        LoggerUtil.log("Published: " + message);
    }

    public CompletableFuture<String> publishWithResponse(String message) throws IOException {
        String correlationId = UUID.randomUUID().toString();
        
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
            .correlationId(correlationId)
            .replyTo(replyQueueName)
            .build();

        CompletableFuture<String> future = new CompletableFuture<>();
        pendingResponses.put(correlationId, future);
        
        channel.basicPublish(AppConfig.EXCHANGE_NAME, "", props, message.getBytes(StandardCharsets.UTF_8));
        LoggerUtil.log("Published with response: " + message + " (correlationId: " + correlationId + ")");
        
        // Set up timeout to remove pending response after timeout period
        java.util.Timer timer = new java.util.Timer(true);
        timer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                if (future.isDone()) {
                    pendingResponses.remove(correlationId);
                } else {
                    CompletableFuture<String> f = pendingResponses.remove(correlationId);
                    if (f != null) {
                        f.complete(null);
                    }
                }
            }
        }, AppConfig.REPLICA_RESPONSE_TIMEOUT);
        
        return future;
    }

    public void publishResponse(String message, String replyTo, String correlationId) throws IOException {
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
            .correlationId(correlationId)
            .build();

        channel.basicPublish("", replyTo, props, message.getBytes(StandardCharsets.UTF_8));
    }

    public void declareQueue(String queueName) throws IOException {
        if (channel == null || !channel.isOpen()) {
            try {
                connect();
            } catch (TimeoutException e) {
                throw new IOException("Failed to reconnect", e);
            }
        }
        channel.queueDeclare(queueName, true, false, false, null);
        channel.queueBind(queueName, AppConfig.EXCHANGE_NAME, "");
    }

    public void consume(String queueName, DeliverCallback deliverCallback) throws IOException {
        if (channel == null || !channel.isOpen()) {
            try {
                connect();
            } catch (TimeoutException e) {
                throw new IOException("Failed to reconnect", e);
            }
        }
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
    }

    public void close() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception e) {
            LoggerUtil.error("RabbitMQ closing failed", e);
        }
    }
}
