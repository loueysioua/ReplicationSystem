package config;
import java.lang.* ;

public class AppConfig {
    public static final String RABBITMQ_HOST = "localhost";
    public static final int RABBITMQ_PORT = 5672;
    public static final String RABBITMQ_USER = "guest";
    public static final String RABBITMQ_PASSWORD = "guest";

    public static final String EXCHANGE_NAME = "replica_exchange";
    public static final String QUEUE_PREFIX = "replica_queue_";

    public static final String DATABASE_PATH_PREFIX = "replica";

    public static final int RESPONSE_TIMEOUT_MS = 5000; // 5 seconds
}
