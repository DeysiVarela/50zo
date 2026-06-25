package com.cincuentazo.controller;

import com.cincuentazo.event.GameEventAdapter;
import com.cincuentazo.exception.GameInitializationException;
import com.cincuentazo.exception.InvalidMoveException;
import com.cincuentazo.model.Operation;
import com.cincuentazo.service.GameService;
import com.cincuentazo.service.GameSnapshot;
import com.cincuentazo.service.PlayerSnapshot;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * JavaFX controller for the main game scene.
 */
public class MainController implements Initializable {

    @FXML
    private Pane rootPane;
    @FXML
    private ComboBox<Integer> machineCountCombo;
    @FXML
    private Button startButton;
    @FXML
    private Label tableSumLabel;
    @FXML
    private Label topCardLabel;
    @FXML
    private Label deckCountLabel;
    @FXML
    private Label currentTurnLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private VBox machinePlayersBox;
    @FXML
    private HBox humanCardsBox;
    @FXML
    private RadioButton addRadio;
    @FXML
    private RadioButton subtractRadio;
    @FXML
    private Button playButton;
    @FXML
    private Button drawButton;
    @FXML
    private ListView<String> tablePileList;
    @FXML
    private ListView<String> historyList;

    private final ToggleGroup operationGroup = new ToggleGroup();
    private final GameService gameService = new GameService();

    private int selectedHumanCardIndex = -1;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Carga opciones para la cantidad de jugadores maquina.
        machineCountCombo.getItems().addAll(1, 2, 3);
        // Configura valores por defecto de inicio.
        machineCountCombo.setValue(1);

        // Agrupa los radio buttons para permitir una sola operacion.
        addRadio.setToggleGroup(operationGroup);
        subtractRadio.setToggleGroup(operationGroup);
        // La operacion por defecto para jugar es suma.
        addRadio.setSelected(true);

        // Enlaza botones de UI con acciones del controlador.
        startButton.setOnAction(new StartGameHandler());
        playButton.setOnAction(this::handlePlayAction);
        drawButton.setOnAction(this::handleDrawAction);

        // Habilita atajos de teclado en el contenedor raiz.
        rootPane.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyboardShortcuts);
        rootPane.setFocusTraversable(true);

        // Suscribe la UI a actualizaciones del estado del juego.
        gameService.addListener(new UiGameEvents());
        // Mensaje inicial antes de la primera partida.
        updateStatus("Selecciona jugadores maquina e inicia el juego.");
        // Tomar carta se habilita solo despues de que el humano juega.
        drawButton.setDisable(true);
    }

    @FXML
    private void handlePlayAction(ActionEvent event) {
        // Rechaza jugar si no hay una partida activa.
        if (!gameService.isRunning()) {
            updateStatus("Primero inicia una partida.");
            return;
        }

        // Exige seleccionar primero una carta de la mano humana.
        if (selectedHumanCardIndex < 0) {
            updateStatus("Selecciona una carta antes de jugar.");
            return;
        }

        // Determina si se suma o resta el valor de la carta elegida.
        Operation operation = subtractRadio.isSelected() ? Operation.SUBTRACT : Operation.ADD;

        try {
            // Envia la jugada al servicio; el servicio valida reglas.
            gameService.submitHumanMove(selectedHumanCardIndex, operation);
            // Limpia la seleccion despues de enviar una jugada valida.
            selectedHumanCardIndex = -1;
        } catch (InvalidMoveException ex) {
            // Muestra al jugador cualquier violacion de reglas.
            updateStatus(ex.getMessage());
        }
    }

    @FXML
    private void handleDrawAction(ActionEvent event) {
        if (!gameService.isRunning()) {
            updateStatus("Primero inicia una partida.");
            return;
        }

        try {
            // Solicita tomar carta explicita tras jugar una carta valida.
            gameService.submitHumanDraw();
        } catch (InvalidMoveException ex) {
            updateStatus(ex.getMessage());
        }
    }

    private void handleKeyboardShortcuts(KeyEvent keyEvent) {
        // Los atajos solo funcionan mientras la partida esta en curso.
        if (!gameService.isRunning()) {
            return;
        }

        // Las teclas numericas mapean posiciones de carta 1..4.
        if (keyEvent.getCode() == KeyCode.DIGIT1) {
            selectedHumanCardIndex = 0;
            updateStatus("Carta 1 seleccionada");
        } else if (keyEvent.getCode() == KeyCode.DIGIT2) {
            selectedHumanCardIndex = 1;
            updateStatus("Carta 2 seleccionada");
        } else if (keyEvent.getCode() == KeyCode.DIGIT3) {
            selectedHumanCardIndex = 2;
            updateStatus("Carta 3 seleccionada");
        } else if (keyEvent.getCode() == KeyCode.DIGIT4) {
            selectedHumanCardIndex = 3;
            updateStatus("Carta 4 seleccionada");
        } else if (keyEvent.getCode() == KeyCode.PLUS || keyEvent.getCode() == KeyCode.ADD) {
            addRadio.setSelected(true);
            updateStatus("Operacion configurada en +");
        } else if (keyEvent.getCode() == KeyCode.MINUS || keyEvent.getCode() == KeyCode.SUBTRACT) {
            subtractRadio.setSelected(true);
            updateStatus("Operacion configurada en -");
        } else if (keyEvent.getCode() == KeyCode.ENTER || keyEvent.getCode() == KeyCode.SPACE) {
            // Enter/Espacio ejecuta jugar o tomar segun la fase del turno.
            if (!drawButton.isDisable()) {
                handleDrawAction(new ActionEvent());
            } else {
                handlePlayAction(new ActionEvent());
            }
        }
    }

    private void renderSnapshot(GameSnapshot snapshot) {
        // Actualiza widgets de mesa con el snapshot mas reciente.
        tableSumLabel.setText(String.valueOf(snapshot.tableSum()));
        topCardLabel.setText(snapshot.topCard());
        deckCountLabel.setText(String.valueOf(snapshot.deckSize()));
        currentTurnLabel.setText(snapshot.currentTurn());
        tablePileList.getItems().setAll(snapshot.tablePileCards());
        tablePileList.scrollTo(Math.max(0, tablePileList.getItems().size() - 1));

        boolean humanTurn = "Humano".equals(snapshot.currentTurn());
        // Mientras espera toma, deshabilita jugar y habilita tomar.
        playButton.setDisable(!humanTurn || snapshot.waitingHumanDraw());
        drawButton.setDisable(!humanTurn || !snapshot.waitingHumanDraw());

        // Reemplaza historial y desplaza a la accion mas reciente.
        historyList.getItems().setAll(snapshot.history());
        historyList.scrollTo(Math.max(0, historyList.getItems().size() - 1));

        // Reconstruye zonas de jugadores (maquinas y mano humana).
        renderPlayers(snapshot.players());
    }

    private void renderPlayers(List<PlayerSnapshot> players) {
        // Limpia controles previos antes de redibujar el estado actual.
        machinePlayersBox.getChildren().clear();
        humanCardsBox.getChildren().clear();

        for (PlayerSnapshot player : players) {
            if (player.human()) {
                // Las cartas humanas se muestran visibles y clickeables.
                for (int i = 0; i < player.visibleCards().size(); i++) {
                    String cardText = player.visibleCards().get(i);
                    Button cardButton = new Button((i + 1) + ": " + cardText);
                    cardButton.getStyleClass().add("card-button");
                    int index = i;
                    cardButton.setOnMouseClicked(mouseEvent -> {
                        // El clic selecciona el indice de carta para la jugada.
                        selectedHumanCardIndex = index;
                        updateStatus("Carta seleccionada " + (index + 1) + " -> " + cardText);
                        // Refresca el estado visual de seleccion.
                        highlightSelectedCard();
                    });
                    humanCardsBox.getChildren().add(cardButton);
                }
                // Aplica estilo de seleccion tras crear botones.
                highlightSelectedCard();
            } else {
                // Panel de maquina con nombre y cartas ocultas.
                HBox row = new HBox(8);
                row.getStyleClass().add("machine-row");

                Label name = new Label(player.name() + (player.active() ? "" : " (FUERA)"));
                name.getStyleClass().add("machine-name");

                String hidden = "[X] ".repeat(Math.max(0, player.hiddenCards())).trim();
                Label cards = new Label(hidden.isBlank() ? "-" : hidden);
                cards.getStyleClass().add("machine-cards");

                row.getChildren().addAll(name, cards);
                machinePlayersBox.getChildren().add(row);
            }
        }
    }

    private void highlightSelectedCard() {
        // Marca solo una carta como seleccionada a la vez.
        for (int i = 0; i < humanCardsBox.getChildren().size(); i++) {
            Button button = (Button) humanCardsBox.getChildren().get(i);
            if (i == selectedHumanCardIndex) {
                button.getStyleClass().add("selected-card");
            } else {
                button.getStyleClass().remove("selected-card");
            }
        }
    }

    private void updateStatus(String message) {
        // Punto centralizado para actualizar estado en pantalla.
        statusLabel.setText(message);
    }

    private class StartGameHandler implements EventHandler<ActionEvent> {
        @Override
        public void handle(ActionEvent event) {
            // Lee la configuracion elegida (cantidad de maquinas).
            Integer machineCount = machineCountCombo.getValue();
            if (machineCount == null) {
                updateStatus("Selecciona primero la cantidad de maquinas.");
                return;
            }

            // Reinicia seleccion de carta antes de nueva partida.
            selectedHumanCardIndex = -1;
            try {
                // Crea e inicia una nueva sesion de juego.
                gameService.startNewGame(machineCount);
                drawButton.setDisable(true);
                // Devuelve foco al root para habilitar atajos de inmediato.
                rootPane.requestFocus();
            } catch (GameInitializationException ex) {
                updateStatus("No se pudo iniciar el juego: " + ex.getMessage());
            }
        }
    }

    private class UiGameEvents extends GameEventAdapter {
        @Override
        public void onGameStateChanged(GameSnapshot snapshot) {
            // Garantiza que actualizaciones ocurran en hilo de JavaFX.
            Platform.runLater(() -> renderSnapshot(snapshot));
        }

        @Override
        public void onTurnMessage(String message) {
            // Muestra mensajes en tiempo real del servicio de juego.
            Platform.runLater(() -> updateStatus(message));
        }

        @Override
        public void onGameOver(String winnerName) {
            // Muestra anuncio de ganador al finalizar el juego.
            Platform.runLater(() -> updateStatus("Juego terminado. Ganador: " + winnerName));
        }

        @Override
        public void onError(String message, Throwable throwable) {
            // Reporta errores de ejecucion del game loop en la UI.
            Platform.runLater(() -> updateStatus(message + " -> " + throwable.getMessage()));
        }
    }
}
