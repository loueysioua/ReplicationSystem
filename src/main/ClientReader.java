package main;

import messaging.RabbitMQManager;
import utils.LoggerUtil;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class ClientReader {
    public static void main(String[] args) {
        try {
            RabbitMQManager manager = new RabbitMQManager();
            manager.publish("READ LAST");
            manager.close();
        } catch (IOException | TimeoutException e) {
            LoggerUtil.error("ClientReader failed", e);
        }
    }
}
