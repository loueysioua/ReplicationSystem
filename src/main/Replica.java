package main;

import config.AppConfig;
import utils.JPAUtil;
import database.TextRepository;
import messaging.RabbitMQManager;
import utils.LoggerUtil;
import com.rabbitmq.client.DeliverCallback;

import javax.persistence.EntityManager;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Replica {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Replica ID is required");
            System.exit(1);
        }
        int replicaId = Integer.parseInt(args[0]);

        try {
            EntityManager em = JPAUtil.getEntityManager(replicaId);
            TextRepository repo = new TextRepository(em);
            RabbitMQManager rabbitMQManager = new RabbitMQManager();

            String queueName = AppConfig.QUEUE_PREFIX + replicaId;
            rabbitMQManager.declareQueue(queueName);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), "UTF-8");
                LoggerUtil.log("Replica " + replicaId + " received: " + message);

                if (message.startsWith("WRITE ")) {
                    String[] parts = message.substring(6).split(" ", 2);
                    int lineNumber = Integer.parseInt(parts[0]);
                    String content = parts[1];
                    repo.saveLine(lineNumber, content);
                } else if (message.equals("READ LAST")) {
                    String lastLine = repo.getLastLine();
                    if (lastLine != null) {
                        LoggerUtil.log("Replica " + replicaId + " last line: " + lastLine);
                    }
                } else if (message.equals("READ ALL")) {
                    repo.getAllLines().forEach(text -> {
                        LoggerUtil.log("Replica " + replicaId + " Line: " + text.getLineNumber() + " => " + text.getContent());
                    });
                }
            };

            rabbitMQManager.consume(queueName, deliverCallback);
            em.close();
            JPAUtil.closeEntityManagerFactory(replicaId);
        } catch (IOException | TimeoutException e) {
            LoggerUtil.error("Replica failed", e);
        }
    }
}