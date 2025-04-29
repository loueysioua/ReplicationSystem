package main;

import config.AppConfig;
import messaging.RabbitMQManager;
import utils.LoggerUtil;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ClientReader {
    public static void main(String[] args) {
        try {
            RabbitMQManager manager = new RabbitMQManager();
            CompletableFuture<String> future = manager.publishWithResponse(AppConfig.MSG_READ_LAST);
            
            System.out.println("Waiting for replica responses...");
            
            // Process responses for the configured timeout period
            long startTime = System.currentTimeMillis();
            long elapsedTime = 0;
            JSONObject mostRecent = null;
            long mostRecentTimestamp = 0;
            int successCount = 0;
            int errorCount = 0;
            
            while (elapsedTime < AppConfig.REPLICA_RESPONSE_TIMEOUT) {
                try {
                    String response = future.get(AppConfig.RESPONSE_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
                    if (response != null) {
                        JSONObject json = new JSONObject(response);
                        
                        // Handle error responses
                        if (json.has("error")) {
                            errorCount++;
                            System.out.printf("❌ Error from Replica %d: %s%n", 
                                json.getInt("replicaId"), 
                                json.getString("error"));
                            continue;
                        }
                        
                        // Skip empty responses but count them
                        if (json.optBoolean("empty", false)) {
                            successCount++;
                            System.out.printf("ℹ️ Replica %d: No data available%n", 
                                json.getInt("replicaId"));
                            continue;
                        }
                        
                        successCount++;
                        // Make sure all required fields are present
                        if (json.has("timestamp") && json.has("lineNumber") && json.has("content")) {
                            long timestamp = json.getLong("timestamp");
                            if (timestamp > mostRecentTimestamp) {
                                mostRecentTimestamp = timestamp;
                                mostRecent = json;
                            }
                        }
                    }
                } catch (TimeoutException e) {
                    // This is expected, continue checking
                }
                
                elapsedTime = System.currentTimeMillis() - startTime;
                
                // Early completion if we have enough responses
                if (successCount + errorCount >= 2) { // Assuming at least 2 replicas
                    break;
                }
            }
            
            if (mostRecent != null) {
                System.out.println("\nMost recent line (from replica " + mostRecent.getInt("replicaId") + "):");
                System.out.println("Line " + mostRecent.getInt("lineNumber") + ": " + mostRecent.getString("content"));
                System.out.println("\nReceived " + successCount + " successful response(s)");
                if (errorCount > 0) {
                    System.out.println("Received " + errorCount + " error response(s)");
                }
            } else {
                if (errorCount > 0) {
                    System.out.println("No valid responses received. " + errorCount + " replica(s) reported errors.");
                } else {
                    System.out.println("No responses received from any replicas after " + 
                        (AppConfig.REPLICA_RESPONSE_TIMEOUT / 1000.0) + " seconds");
                }
            }
            
            manager.close();
        } catch (Exception e) {
            LoggerUtil.error("ClientReader failed", e);
        }
    }
}
