package ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import messaging.RabbitMQManager;
import org.json.JSONObject;

public class ClientWriterFX extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        TextField lineNumber = new TextField();
        lineNumber.setPromptText("Line number");
        TextField content = new TextField();
        content.setPromptText("Content");

        Button send = new Button("Send");

        VBox root = new VBox(10, lineNumber, content, send);
        Scene scene = new Scene(root, 400, 200);
        stage.setScene(scene);
        stage.setTitle("Client Writer");
        stage.show();

        RabbitMQManager rmq = new RabbitMQManager();

        send.setOnAction(e -> {
            try {
                int ln = Integer.parseInt(lineNumber.getText());
                String txt = content.getText();
                JSONObject obj = new JSONObject();
                obj.put("line_number", ln);
                obj.put("content", txt);
                rmq.publish(obj.toString());
                lineNumber.clear();
                content.clear();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
