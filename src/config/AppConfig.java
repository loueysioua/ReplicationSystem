package config;

public class AppConfig {
    public static final String RABBITMQ_HOST = "localhost";
    public static final int RABBITMQ_PORT = 5672;
    public static final String RABBITMQ_USER = "guest";
    public static final String RABBITMQ_PASSWORD = "guest";

    public static final String EXCHANGE_NAME = "replica_exchange";
    public static final String QUEUE_PREFIX = "replica_queue_";

    // Message types (using spaces for consistency)
    public static final String MSG_READ_LAST = "READ LAST";
    public static final String MSG_READ_ALL = "READ ALL";
    public static final String MSG_WRITE_PREFIX = "WRITE ";

    // New status message types
    public static final String MSG_STATUS_CHECK = "STATUS";
    public static final String MSG_HEARTBEAT = "HEARTBEAT";

    // Timeouts (in milliseconds)
    public static final long REPLICA_RESPONSE_TIMEOUT = 2000;  // Wait 2 seconds for replica responses
    public static final long RESPONSE_CHECK_INTERVAL = 100;    // Check for new responses every 100ms
    public static final long HEARTBEAT_INTERVAL = 5000;        // Send heartbeat every 5 seconds

    // Connection parameters
    public static final int CONNECTION_RETRY_COUNT = 3;        // Number of connection retry attempts
    public static final long CONNECTION_RETRY_DELAY = 1000;    // Delay between retry attempts in ms

    // Message processing
    public static final int MAX_PREFETCH_COUNT = 10;           // Maximum number of unacknowledged messages
}