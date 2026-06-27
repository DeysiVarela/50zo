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
        // Carga la vista FXML y la enlaza con MainController.
        FXMLLoader loader = new FXMLLoader(CincuentazoApplication.class.getResource("/com/cincuentazo/view/main-view.fxml"));
        // Crea la escena principal con tamano inicial.
        Scene scene = new Scene(loader.load(), 1280, 780);
        // Aplica la hoja de estilos global para la interfaz.
        scene.getStylesheets().add(CincuentazoApplication.class.getResource("/com/cincuentazo/style/app.css").toExternalForm());
        // Titulo de ventana mostrado por el sistema operativo.
        stage.setTitle("Cincuentazo");
        // Evita reducir la ventana por debajo de un tamano util.
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        // Asocia la escena al Stage de JavaFX.
        stage.setScene(scene);
        // Renderiza y muestra la ventana.
        stage.show();
    }

    public static void main(String[] args) {
        // Inicia el runtime de JavaFX y ejecuta start().
        launch(args);
    }
}
