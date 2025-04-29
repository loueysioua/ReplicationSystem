package main;

import messaging.RabbitMQManager;
import utils.LoggerUtil;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class ClientReaderV2 {
    public static void main(String[] args) {
        try {
            RabbitMQManager manager = new RabbitMQManager();
            manager.publish("READ ALL");
            manager.close();
        } catch (IOException | TimeoutException e) {
            LoggerUtil.error("ClientReaderV2 failed", e);
        }
    }
}