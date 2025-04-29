package main;

import config.AppConfig;
import messaging.RabbitMQManager;
import utils.LoggerUtil;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class ClientReaderV2 {
    public static void main(String[] args) {
        try {
            RabbitMQManager manager = new RabbitMQManager();
            manager.publish(AppConfig.MSG_READ_ALL);
            manager.close();
        } catch (IOException | TimeoutException e) {
            LoggerUtil.error("ClientReaderV2 failed", e);
        }
    }
}