package ui;

import messaging.RabbitMQManager;
import utils.LoggerUtil;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ClientReaderFX extends Application {

    @Override
    public void start(Stage stage) {
        VBox root = new VBox(20);
        TextArea output = new TextArea();
        output.setEditable(false);

        Button readButton = new Button("Request Last Line");

        readButton.setOnAction(e -> {
            try {
                RabbitMQManager rabbit = new RabbitMQManager();
                rabbit.publish("READ LAST");
                LoggerUtil.log("Sent READ LAST command.");
                rabbit.close();
            } catch (Exception ex) {
                LoggerUtil.error("Error sending READ LAST", ex);
            }
        });

        root.getChildren().addAll(readButton, output);

        Scene scene = new Scene(root, 400, 300);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
        stage.setTitle("Client Reader");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
