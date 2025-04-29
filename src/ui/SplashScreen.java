package ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class SplashScreen extends Application {

    @Override
    public void start(Stage stage) {
        Label label = new Label("Loading Replica Manager...");
        label.setStyle("-fx-font-size: 24px; -fx-text-fill: #4CAF50;");

        StackPane root = new StackPane(label);
        root.setStyle("-fx-background-color: #f0f2f5;");
        Scene scene = new Scene(root, 400, 200);

        stage.initStyle(StageStyle.UNDECORATED);
        stage.setScene(scene);
        stage.show();

        new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Instead of launching from JavaFX thread, just show the main application
            javafx.application.Platform.runLater(() -> {
                stage.close();
                Stage mainStage = new Stage();
                try {
                    new ReplicaManagerFX().start(mainStage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }).start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}