package ui;

import messaging.RabbitMQManager;
import utils.LoggerUtil;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ClientWriterFX extends Application {

    @Override
    public void start(Stage stage) {
        VBox root = new VBox(20);

        TextField lineNumberField = new TextField();
        lineNumberField.setPromptText("Line number");

        TextField contentField = new TextField();
        contentField.setPromptText("Text content");

        Button sendButton = new Button("Send Message");

        sendButton.setOnAction(e -> {
            try {
                RabbitMQManager rabbit = new RabbitMQManager();
                int lineNumber = Integer.parseInt(lineNumberField.getText());
                String content = contentField.getText();
                rabbit.publish("WRITE " + lineNumber + " " + content);
                LoggerUtil.log("Sent message: WRITE " + lineNumber + " " + content);
                rabbit.close();

                lineNumberField.clear();
                contentField.clear();
            } catch (Exception ex) {
                LoggerUtil.error("Error sending message", ex);
            }
        });

        root.getChildren().addAll(lineNumberField, contentField, sendButton);

        Scene scene = new Scene(root, 400, 250);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
        stage.setTitle("Client Writer");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
