package com.cincuentazo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main JavaFX entry point for Cincuentazo game.
 */
public class CincuentazoApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(CincuentazoApplication.class.getResource("/com/cincuentazo/view/main-view.fxml"));
        Scene scene = new Scene(loader.load(), 1024, 680);
        scene.getStylesheets().add(CincuentazoApplication.class.getResource("/com/cincuentazo/style/app.css").toExternalForm());
        stage.setTitle("Cincuentazo");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
