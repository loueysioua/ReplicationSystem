package utils;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class RabbitMQHelper {
    private static final String HOST = "localhost";
    private static ConnectionFactory factory;
    private static Connection connection;
    private static Channel channel;

    static {
        factory = new ConnectionFactory();
        factory.setHost(HOST);
        try {
            connection = factory.newConnection();
            channel = connection.createChannel();
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    public static void sendToQueue(String queueName, String message) {
        try {
            channel.queueDeclare(queueName, false, false, false, null);
            channel.basicPublish("", queueName, null, message.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void listenToQueue(String queueName, Consumer<String> callback) {
        try {
            channel.queueDeclare(queueName, false, false, false, null);
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                callback.accept(message);
            };
            channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void close() {
        try {
            if (channel != null) channel.close();
            if (connection != null) connection.close();
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }
}
