package ui;

import database.TextRepository;
import database.TextEntity;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ClientReaderFX extends Application {

    @Override
    public void start(Stage stage) {
        Spinner<Integer> replicaSpinner = new Spinner<>(1, 10, 1);
        Button readBtn = new Button("Read Last");
        TextArea output = new TextArea();
        output.setEditable(false);

        readBtn.setOnAction(e -> {
            TextRepository repo = new TextRepository(replicaSpinner.getValue());
            TextEntity last = repo.getLastLine();
            output.setText(last != null ? last.toString() : "(vide)");
        });

        VBox root = new VBox(10, replicaSpinner, readBtn, output);
        Scene scene = new Scene(root, 400, 300);
        stage.setScene(scene);
        stage.setTitle("Client Reader");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
