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
        // Loads the FXML view and links it to MainController.
        FXMLLoader loader = new FXMLLoader(CincuentazoApplication.class.getResource("/com/cincuentazo/view/main-view.fxml"));
        // Creates the main window scene with an initial size.
        Scene scene = new Scene(loader.load(), 1024, 680);
        // Applies global stylesheet for visual theme.
        scene.getStylesheets().add(CincuentazoApplication.class.getResource("/com/cincuentazo/style/app.css").toExternalForm());
        // Window title shown in the operating system frame.
        stage.setTitle("Cincuentazo");
        // Prevents shrinking below usable dimensions.
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        // Attaches scene graph to the JavaFX stage.
        stage.setScene(scene);
        // Renders and displays the window.
        stage.show();
    }

    public static void main(String[] args) {
        // Starts JavaFX runtime and calls start().
        launch(args);
    }
}
