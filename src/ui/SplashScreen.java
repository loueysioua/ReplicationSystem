package ui;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class SplashScreen extends Application {

    private ProgressBar progressBar;
    private Label statusLabel;
    private Timeline progressTimeline;

    @Override
    public void start(Stage stage) {
        // Create logo text (as a placeholder for a real logo)
        Text logoText = new Text("TextSync");
        logoText.setFont(Font.font("Verdana", FontWeight.BOLD, 36));
        logoText.setFill(Color.web("#2575fc"));

        // Add a shadow effect to the logo
        DropShadow dropShadow = new DropShadow();
        dropShadow.setRadius(5.0);
        dropShadow.setOffsetX(3.0);
        dropShadow.setOffsetY(3.0);
        dropShadow.setColor(Color.color(0.4, 0.4, 0.4, 0.5));
        logoText.setEffect(dropShadow);

        // Subtitle
        Label subtitle = new Label("Distributed Text Storage System");
        subtitle.setFont(Font.font("Verdana", 14));
        subtitle.setTextFill(Color.web("#5a5a5a"));

        // Progress indicators
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);

        statusLabel = new Label("Initializing System...");
        statusLabel.setTextFill(Color.web("#757575"));

        // Version info
        Label versionLabel = new Label("Version 1.0");
        versionLabel.setTextFill(Color.web("#9e9e9e"));
        versionLabel.setFont(Font.font("Verdana", 10));

        // Layout
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));
        root.getChildren().addAll(logoText, subtitle, progressBar, statusLabel, versionLabel);

        // Add a nice background
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #f0f8ff, #e6f2ff);");

        Scene scene = new Scene(root, 450, 300);

        // Make stage undecorated and set position
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setScene(scene);
        stage.setOpacity(0.0);  // Start with 0 opacity for fade-in
        stage.show();

        // Center the stage on the screen
        stage.setX((javafx.stage.Screen.getPrimary().getVisualBounds().getWidth() - stage.getWidth()) / 2);
        stage.setY((javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() - stage.getHeight()) / 2);

        // Create fade-in effect
        FadeTransition fadeIn = new FadeTransition(Duration.seconds(1), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        // Animate stage opacity
        Timeline stageOpacity = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(stage.opacityProperty(), 0)),
                new KeyFrame(Duration.seconds(0.5), new KeyValue(stage.opacityProperty(), 1))
        );
        stageOpacity.play();

        // Update progress bar
        simulateLoading(stage);
    }

    private void simulateLoading(Stage splashStage) {
        final int[] steps = {0, 15, 35, 65, 85, 100};
        final String[] messages = {
                "Initializing System...",
                "Loading Configuration...",
                "Connecting to Message Broker...",
                "Preparing Storage System...",
                "Starting Services...",
                "Ready!"
        };

        progressTimeline = new Timeline();

        for (int i = 0; i < steps.length; i++) {
            final int stepIndex = i;
            KeyFrame keyFrame = new KeyFrame(
                    Duration.seconds(i * 0.5),  // Time between steps
                    e -> {
                        progressBar.setProgress(steps[stepIndex] / 100.0);
                        statusLabel.setText(messages[stepIndex]);
                    }
            );
            progressTimeline.getKeyFrames().add(keyFrame);
        }

        // Add a final keyframe to launch the main application
        KeyFrame finalFrame = new KeyFrame(
                Duration.seconds(steps.length * 0.5 + 0.5),  // Additional 0.5 seconds at the end
                e -> launchMainApplication(splashStage)
        );
        progressTimeline.getKeyFrames().add(finalFrame);

        progressTimeline.play();
    }

    private void launchMainApplication(Stage splashStage) {
        // Create fade out effect
        FadeTransition fadeOut = new FadeTransition(Duration.seconds(0.5), splashStage.getScene().getRoot());
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            // Launch the main application
            Platform.runLater(() -> {
                try {
                    Stage mainStage = new Stage();
                    new ReplicaManagerFX().start(mainStage);
                    splashStage.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        });
        fadeOut.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}