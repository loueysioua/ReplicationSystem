package main;

import messaging.RabbitMQManager;
import utils.LoggerUtil;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

public class ClientWriter {
    public static void main(String[] args) {
        try {
            RabbitMQManager manager = new RabbitMQManager();
            Scanner scanner = new Scanner(System.in);

            while (true) {
                System.out.print("Enter line (or 'exit'): ");
                String input = scanner.nextLine();
                if ("exit".equalsIgnoreCase(input)) break;

                String[] parts = input.split(" ", 2);
                if (parts.length == 2) {
                    String message = "WRITE " + parts[0] + " " + parts[1];
                    manager.publish(message);
                } else {
                    System.out.println("Invalid input format! Use: <lineNumber> <content>");
                }
            }

            manager.close();
        } catch (IOException | TimeoutException e) {
            LoggerUtil.error("ClientWriter failed", e);
        }
    }
}