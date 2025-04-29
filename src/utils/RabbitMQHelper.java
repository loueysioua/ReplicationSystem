package utils;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class RabbitMQHelper {
    private static final String HOST = "localhost";
    private static ConnectionFactory factory;

    static {
        factory = new ConnectionFactory();
        factory.setHost(HOST);
    }

    public static void sendToQueue(String queueName, String message) {
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            channel.queueDeclare(queueName, false, false, false, null);
            channel.basicPublish("", queueName, null, message.getBytes(StandardCharsets.UTF_8));

        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    public static void listenToQueue(String queueName, Consumer<String> callback) {
        new Thread(() -> {
            try {
                Connection connection = factory.newConnection();
                Channel channel = connection.createChannel();

                channel.queueDeclare(queueName, false, false, false, null);
                DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                    String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    callback.accept(message);
                };
                channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
            } catch (IOException | TimeoutException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
