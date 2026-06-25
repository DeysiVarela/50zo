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
    private ListView<String> historyList;

    private final ToggleGroup operationGroup = new ToggleGroup();
    private final GameService gameService = new GameService();

    private int selectedHumanCardIndex = -1;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        machineCountCombo.getItems().addAll(1, 2, 3);
        machineCountCombo.setValue(1);

        addRadio.setToggleGroup(operationGroup);
        subtractRadio.setToggleGroup(operationGroup);
        addRadio.setSelected(true);

        startButton.setOnAction(new StartGameHandler());
        playButton.setOnAction(this::handlePlayAction);

        rootPane.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyboardShortcuts);
        rootPane.setFocusTraversable(true);

        gameService.addListener(new UiGameEvents());
        updateStatus("Selecciona jugadores maquina e inicia el juego.");
    }

    @FXML
    private void handlePlayAction(ActionEvent event) {
        if (!gameService.isRunning()) {
            updateStatus("Primero inicia una partida.");
            return;
        }

        if (selectedHumanCardIndex < 0) {
            updateStatus("Selecciona una carta antes de jugar.");
            return;
        }

        Operation operation = subtractRadio.isSelected() ? Operation.SUBTRACT : Operation.ADD;

        try {
            gameService.submitHumanMove(selectedHumanCardIndex, operation);
            selectedHumanCardIndex = -1;
        } catch (InvalidMoveException ex) {
            updateStatus(ex.getMessage());
        }
    }

    private void handleKeyboardShortcuts(KeyEvent keyEvent) {
        if (!gameService.isRunning()) {
            return;
        }

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
            handlePlayAction(new ActionEvent());
        }
    }

    private void renderSnapshot(GameSnapshot snapshot) {
        tableSumLabel.setText(String.valueOf(snapshot.tableSum()));
        topCardLabel.setText(snapshot.topCard());
        deckCountLabel.setText(String.valueOf(snapshot.deckSize()));
        currentTurnLabel.setText(snapshot.currentTurn());

        historyList.getItems().setAll(snapshot.history());
        historyList.scrollTo(Math.max(0, historyList.getItems().size() - 1));

        renderPlayers(snapshot.players());
    }

    private void renderPlayers(List<PlayerSnapshot> players) {
        machinePlayersBox.getChildren().clear();
        humanCardsBox.getChildren().clear();

        for (PlayerSnapshot player : players) {
            if (player.human()) {
                for (int i = 0; i < player.visibleCards().size(); i++) {
                    String cardText = player.visibleCards().get(i);
                    Button cardButton = new Button((i + 1) + ": " + cardText);
                    cardButton.getStyleClass().add("card-button");
                    int index = i;
                    cardButton.setOnMouseClicked(mouseEvent -> {
                        selectedHumanCardIndex = index;
                        updateStatus("Carta seleccionada " + (index + 1) + " -> " + cardText);
                        highlightSelectedCard();
                    });
                    humanCardsBox.getChildren().add(cardButton);
                }
                highlightSelectedCard();
            } else {
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
        statusLabel.setText(message);
    }

    private class StartGameHandler implements EventHandler<ActionEvent> {
        @Override
        public void handle(ActionEvent event) {
            Integer machineCount = machineCountCombo.getValue();
            if (machineCount == null) {
                updateStatus("Selecciona primero la cantidad de maquinas.");
                return;
            }

            selectedHumanCardIndex = -1;
            try {
                gameService.startNewGame(machineCount);
                rootPane.requestFocus();
            } catch (GameInitializationException ex) {
                updateStatus("No se pudo iniciar el juego: " + ex.getMessage());
            }
        }
    }

    private class UiGameEvents extends GameEventAdapter {
        @Override
        public void onGameStateChanged(GameSnapshot snapshot) {
            Platform.runLater(() -> renderSnapshot(snapshot));
        }

        @Override
        public void onTurnMessage(String message) {
            Platform.runLater(() -> updateStatus(message));
        }

        @Override
        public void onGameOver(String winnerName) {
            Platform.runLater(() -> updateStatus("Juego terminado. Ganador: " + winnerName));
        }

        @Override
        public void onError(String message, Throwable throwable) {
            Platform.runLater(() -> updateStatus(message + " -> " + throwable.getMessage()));
        }
    }
}
