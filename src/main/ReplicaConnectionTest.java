package main;

import config.AppConfig;
import messaging.RabbitMQManager;
import utils.LoggerUtil;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class tests the connection between replicas and verifies message distribution.
 * It can be used to diagnose communication issues in the replication system.
 */
public class ReplicaConnectionTest {
    public static void main(String[] args) {
        LoggerUtil.log("Starting Replica Connection Test");

        try {
            // Initialize RabbitMQ connection
            RabbitMQManager manager = new RabbitMQManager();
            LoggerUtil.log("Connected to RabbitMQ server");

            // Send a status check message
            LoggerUtil.log("Sending status check to all replicas...");
            CompletableFuture<String> future = manager.publishWithResponse(AppConfig.MSG_STATUS_CHECK);

            // Wait for responses
            LoggerUtil.log("Waiting for responses...");
            boolean receivedResponse = false;
            long startTime = System.currentTimeMillis();

            while (System.currentTimeMillis() - startTime < 5000) { // Wait up to 5 seconds
                try {
                    String response = future.get(100, TimeUnit.MILLISECONDS);
                    if (response != null) {
                        receivedResponse = true;
                        JSONObject responseJson = new JSONObject(response);
                        LoggerUtil.log("✅ Received response from Replica " + responseJson.getInt("replicaId"));
                        LoggerUtil.log("Status: " + responseJson.getString("status"));
                        LoggerUtil.log("Queue: " + responseJson.getString("queueName"));
                        LoggerUtil.log("Lines: " + responseJson.getInt("lineCount"));
                    }
                } catch (TimeoutException e) {
                    // This is expected when waiting for responses
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }

            if (!receivedResponse) {
                LoggerUtil.log("❌ No responses received within 5 seconds.");
                LoggerUtil.log("Possible issues:");
                LoggerUtil.log("1. No replicas are running");
                LoggerUtil.log("2. RabbitMQ exchange or queue binding is misconfigured");
                LoggerUtil.log("3. Network connectivity issues");

                // Let's try a direct test message
                LoggerUtil.log("\nTrying direct test message...");
                manager.publish("TEST MESSAGE " + System.currentTimeMillis());
                LoggerUtil.log("Test message sent to exchange, check replica logs for receipt.");
            }

            LoggerUtil.log("\nTesting specific write operation...");
            // Try writing to a specific line
            int testLine = 999;
            String testContent = "Test line from connection test " + System.currentTimeMillis();
            manager.publish(AppConfig.MSG_WRITE_PREFIX + testLine + " " + testContent);
            LoggerUtil.log("Write message sent, check replica logs for confirmation.");

            // Clean up
            manager.close();
            LoggerUtil.log("\nConnection test complete.");

        } catch (IOException | TimeoutException | InterruptedException e) {
            LoggerUtil.error("Connection test failed", e);
        }
    }
}