package application;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        try {
        	GameUI gameUI = new GameUI();
        	Scene scene = new Scene(gameUI.getRoot(primaryStage));
            scene.getStylesheets().add(getClass().getResource("/application/style.css").toExternalForm());

            Rectangle2D screenBounds = Screen.getPrimary().getBounds();
            primaryStage.setX(screenBounds.getMinX());
            primaryStage.setY(screenBounds.getMinY());
            primaryStage.setWidth(screenBounds.getWidth());
            primaryStage.setHeight(screenBounds.getHeight());

            primaryStage.setTitle("Anger Management 😠");
            primaryStage.setScene(scene);
            primaryStage.setFullScreen(true);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}