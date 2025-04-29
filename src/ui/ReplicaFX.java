package ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ReplicaFX extends Application {
    private static int replicaId;
    private ReplicaController controller;

    public static void launchReplica(int id) {
        replicaId = id;
        launch();
    }

    @Override
    public void start(Stage stage) {
        controller = new ReplicaController(replicaId);
        
        Scene scene = new Scene(controller.getView(), 600, 800);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
        
        stage.setTitle("Replica #" + replicaId);
        stage.setScene(scene);
        
        // Position the window based on replica ID to avoid overlap
        stage.setX(50 + (replicaId - 1) * 100);
        stage.setY(50 + (replicaId - 1) * 50);
        
        stage.show();
    }
}