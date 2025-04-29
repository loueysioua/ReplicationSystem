package launcher;

import messaging.RabbitMQManager;
import org.json.JSONObject;

public class ClientWriterConsole {
    public static void main(String[] args) throws Exception {
        RabbitMQManager rmq = new RabbitMQManager();

        JSONObject msg1 = new JSONObject();
        msg1.put("line_number", 1);
        msg1.put("content", "Hello from console");

        JSONObject msg2 = new JSONObject();
        msg2.put("line_number", 2);
        msg2.put("content", "Second line");

        rmq.publish(msg1.toString());
        rmq.publish(msg2.toString());

        System.out.println("✅ Messages sent.");
        rmq.close();
    }
}
