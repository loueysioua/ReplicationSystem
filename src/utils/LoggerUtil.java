package utils;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

public class LoggerUtil {
    public static void log(String message) {
        System.out.println("[LOG " + LocalDateTime.now() + "] " + message);
    }

    public static void error(String message, Exception e) {
        System.err.println("[ERROR " + LocalDateTime.now() + "] " + message);
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        System.err.println(sw);
    }
}
