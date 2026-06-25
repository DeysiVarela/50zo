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
        // Loads options for number of machine players.
        machineCountCombo.getItems().addAll(1, 2, 3);
        // Sets default game setup.
        machineCountCombo.setValue(1);

        // Groups radio buttons so only one operation can be selected.
        addRadio.setToggleGroup(operationGroup);
        subtractRadio.setToggleGroup(operationGroup);
        // Default operation for playing is addition.
        addRadio.setSelected(true);

        // Binds UI buttons to controller actions.
        startButton.setOnAction(new StartGameHandler());
        playButton.setOnAction(this::handlePlayAction);
        drawButton.setOnAction(this::handleDrawAction);

        // Enables keyboard shortcuts on the root container.
        rootPane.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyboardShortcuts);
        rootPane.setFocusTraversable(true);

        // Subscribes UI to game state updates.
        gameService.addListener(new UiGameEvents());
        // Initial message shown before first game.
        updateStatus("Selecciona jugadores maquina e inicia el juego.");
        // Draw action is enabled only after human plays a card.
        drawButton.setDisable(true);
    }

    @FXML
    private void handlePlayAction(ActionEvent event) {
        // Rejects play if no active game exists.
        if (!gameService.isRunning()) {
            updateStatus("Primero inicia una partida.");
            return;
        }

        // Requires selecting one card from human hand first.
        if (selectedHumanCardIndex < 0) {
            updateStatus("Selecciona una carta antes de jugar.");
            return;
        }

        // Determines whether to add or subtract selected card value.
        Operation operation = subtractRadio.isSelected() ? Operation.SUBTRACT : Operation.ADD;

        try {
            // Sends selected move to service; service validates game rules.
            gameService.submitHumanMove(selectedHumanCardIndex, operation);
            // Clears selection after submitting valid move.
            selectedHumanCardIndex = -1;
        } catch (InvalidMoveException ex) {
            // Any business-rule violation is presented to the player.
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
            // Requests explicit human draw after a valid human play.
            gameService.submitHumanDraw();
        } catch (InvalidMoveException ex) {
            updateStatus(ex.getMessage());
        }
    }

    private void handleKeyboardShortcuts(KeyEvent keyEvent) {
        // Keyboard shortcuts are enabled only while a game is running.
        if (!gameService.isRunning()) {
            return;
        }

        // Numeric keys map directly to card positions 1..4.
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
            // Enter/Space executes play or draw based on current turn phase.
            if (!drawButton.isDisable()) {
                handleDrawAction(new ActionEvent());
            } else {
                handlePlayAction(new ActionEvent());
            }
        }
    }

    private void renderSnapshot(GameSnapshot snapshot) {
        // Updates table widgets from latest immutable game snapshot.
        tableSumLabel.setText(String.valueOf(snapshot.tableSum()));
        topCardLabel.setText(snapshot.topCard());
        deckCountLabel.setText(String.valueOf(snapshot.deckSize()));
        currentTurnLabel.setText(snapshot.currentTurn());
        tablePileList.getItems().setAll(snapshot.tablePileCards());
        tablePileList.scrollTo(Math.max(0, tablePileList.getItems().size() - 1));

        boolean humanTurn = "Humano".equals(snapshot.currentTurn());
        // While waiting draw, disable play and enable draw to enforce flow.
        playButton.setDisable(!humanTurn || snapshot.waitingHumanDraw());
        drawButton.setDisable(!humanTurn || !snapshot.waitingHumanDraw());

        // Replaces history list and scrolls to most recent action.
        historyList.getItems().setAll(snapshot.history());
        historyList.scrollTo(Math.max(0, historyList.getItems().size() - 1));

        // Rebuilds player areas (machine rows and human hand cards).
        renderPlayers(snapshot.players());
    }

    private void renderPlayers(List<PlayerSnapshot> players) {
        // Clears old controls before redrawing current state.
        machinePlayersBox.getChildren().clear();
        humanCardsBox.getChildren().clear();

        for (PlayerSnapshot player : players) {
            if (player.human()) {
                // Human cards are visible and clickable.
                for (int i = 0; i < player.visibleCards().size(); i++) {
                    String cardText = player.visibleCards().get(i);
                    Button cardButton = new Button((i + 1) + ": " + cardText);
                    cardButton.getStyleClass().add("card-button");
                    int index = i;
                    cardButton.setOnMouseClicked(mouseEvent -> {
                        // Click picks current card index for next move.
                        selectedHumanCardIndex = index;
                        updateStatus("Carta seleccionada " + (index + 1) + " -> " + cardText);
                        // Visual selection state is refreshed.
                        highlightSelectedCard();
                    });
                    humanCardsBox.getChildren().add(cardButton);
                }
                // Applies selected-card style after creating buttons.
                highlightSelectedCard();
            } else {
                // Machine panel shows name and hidden-card placeholders.
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
        // Marks only one card button as selected at any time.
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
        // Centralized status updates for consistent UX.
        statusLabel.setText(message);
    }

    private class StartGameHandler implements EventHandler<ActionEvent> {
        @Override
        public void handle(ActionEvent event) {
            // Reads selected setup (number of machine opponents).
            Integer machineCount = machineCountCombo.getValue();
            if (machineCount == null) {
                updateStatus("Selecciona primero la cantidad de maquinas.");
                return;
            }

            // Resets card selection before starting a fresh match.
            selectedHumanCardIndex = -1;
            try {
                // Creates and starts a new game session.
                gameService.startNewGame(machineCount);
                drawButton.setDisable(true);
                // Returns keyboard focus so shortcuts work immediately.
                rootPane.requestFocus();
            } catch (GameInitializationException ex) {
                updateStatus("No se pudo iniciar el juego: " + ex.getMessage());
            }
        }
    }

    private class UiGameEvents extends GameEventAdapter {
        @Override
        public void onGameStateChanged(GameSnapshot snapshot) {
            // Ensures UI updates happen on JavaFX Application Thread.
            Platform.runLater(() -> renderSnapshot(snapshot));
        }

        @Override
        public void onTurnMessage(String message) {
            // Displays real-time turn/system messages from game service.
            Platform.runLater(() -> updateStatus(message));
        }

        @Override
        public void onGameOver(String winnerName) {
            // Shows end-of-game winner announcement.
            Platform.runLater(() -> updateStatus("Juego terminado. Ganador: " + winnerName));
        }

        @Override
        public void onError(String message, Throwable throwable) {
            // Reports runtime errors from game loop to UI status area.
            Platform.runLater(() -> updateStatus(message + " -> " + throwable.getMessage()));
        }
    }
}
