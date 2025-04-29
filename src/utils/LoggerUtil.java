package utils;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.function.Consumer;

public class LoggerUtil {
    private static Consumer<String> logListener;

    public static void log(String message) {
        String formattedMessage = "[LOG " + LocalDateTime.now() + "] " + message;
        System.out.println(formattedMessage);
        if (logListener != null) {
            logListener.accept(formattedMessage);
        }
    }

    public static void error(String message, Exception e) {
        String formattedMessage = "[ERROR " + LocalDateTime.now() + "] " + message;
        System.err.println(formattedMessage);
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        System.err.println(sw);

        if (logListener != null) {
            logListener.accept(formattedMessage);
            logListener.accept(sw.toString());
        }
    }

    public static void setLogListener(Consumer<String> listener) {
        logListener = listener;
    }
}