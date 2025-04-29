package messaging;

import com.rabbitmq.client.*;
import config.AppConfig;
import utils.LoggerUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

public class RabbitMQManager {
    private final ConnectionFactory factory;
    private Connection connection;
    private Channel channel;

    public RabbitMQManager() throws IOException, TimeoutException {
        factory = new ConnectionFactory();
        factory.setHost(AppConfig.RABBITMQ_HOST);
        factory.setPort(AppConfig.RABBITMQ_PORT);
        factory.setUsername(AppConfig.RABBITMQ_USER);
        factory.setPassword(AppConfig.RABBITMQ_PASSWORD);
        connect();
    }

    private void connect() throws IOException, TimeoutException {
        connection = factory.newConnection();
        channel = connection.createChannel();
        channel.exchangeDeclare(AppConfig.EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true);
    }

    public void publish(String message) throws IOException {
        channel.basicPublish(AppConfig.EXCHANGE_NAME, "", null, message.getBytes(StandardCharsets.UTF_8));
        LoggerUtil.log("Published: " + message);
    }

    public void declareQueue(String queueName) throws IOException {
        channel.queueDeclare(queueName, true, false, false, null);
        channel.queueBind(queueName, AppConfig.EXCHANGE_NAME, "");
    }

    public void consume(String queueName, DeliverCallback deliverCallback) throws IOException {
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> { });
    }

    public void close() {
        try {
            if (channel != null) channel.close();
            if (connection != null) connection.close();
        } catch (Exception e) {
            LoggerUtil.error("RabbitMQ closing failed", e);
        }
    }
}
