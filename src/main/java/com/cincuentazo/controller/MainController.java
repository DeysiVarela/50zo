package com.cincuentazo.controller;

import com.cincuentazo.event.GameEventAdapter;
import com.cincuentazo.exception.GameInitializationException;
import com.cincuentazo.exception.InvalidMoveException;
import com.cincuentazo.model.GameRules;
import com.cincuentazo.model.Operation;
import com.cincuentazo.model.Rank;
import com.cincuentazo.service.GameService;
import com.cincuentazo.service.GameSnapshot;
import com.cincuentazo.service.PlayerSnapshot;
import com.cincuentazo.view.CardImageProvider;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * JavaFX controller for the main game scene.
 */
public class MainController implements Initializable {

    private static final double CARD_WIDTH = 90;
    private static final double CARD_HEIGHT = 130;
    private static final double MACHINE_CARD_WIDTH = 55;
    private static final double MACHINE_CARD_HEIGHT = 80;

    @FXML
    private Pane rootPane;
    @FXML
    private ComboBox<Integer> machineCountCombo;
    @FXML
    private Button startButton;
    @FXML
    private Label tableSumLabel;
    @FXML
    private VBox sumBox;
    @FXML
    private Label deckCountCenterLabel;
    @FXML
    private Label currentTurnLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label humanTurnHint;
    @FXML
    private AnchorPane tablePane;
    @FXML
    private VBox machineTopBox;
    @FXML
    private VBox machineLeftBox;
    @FXML
    private VBox machineRightBox;
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
    private ImageView topCardView;
    @FXML
    private Label topCardValueLabel;
    @FXML
    private StackPane deckBackPane;
    @FXML
    private ListView<String> historyList;
    @FXML
    private VBox alivePlayersBox;

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

        CardImageProvider.styleCardBack(deckBackPane, CARD_WIDTH, CARD_HEIGHT);

        startButton.setOnAction(new StartGameHandler());
        playButton.setOnAction(this::handlePlayAction);
        drawButton.setOnAction(this::handleDrawAction);

        operationGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> highlightSelectedCard());

        rootPane.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyboardShortcuts);
        rootPane.setFocusTraversable(true);

        gameService.addListener(new UiGameEvents());
        updateStatus("Selecciona jugadores maquina e inicia el juego.");
        drawButton.setDisable(true);
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

    @FXML
    private void handleDrawAction(ActionEvent event) {
        if (!gameService.isRunning()) {
            updateStatus("Primero inicia una partida.");
            return;
        }

        try {
            gameService.submitHumanDraw();
        } catch (InvalidMoveException ex) {
            updateStatus(ex.getMessage());
        }
    }

    private void handleKeyboardShortcuts(KeyEvent keyEvent) {
        if (!gameService.isRunning()) {
            return;
        }

        if (keyEvent.getCode() == KeyCode.DIGIT1) {
            selectCard(0);
        } else if (keyEvent.getCode() == KeyCode.DIGIT2) {
            selectCard(1);
        } else if (keyEvent.getCode() == KeyCode.DIGIT3) {
            selectCard(2);
        } else if (keyEvent.getCode() == KeyCode.DIGIT4) {
            selectCard(3);
        } else if (keyEvent.getCode() == KeyCode.PLUS || keyEvent.getCode() == KeyCode.ADD) {
            addRadio.setSelected(true);
            updateStatus("Operacion configurada en +");
        } else if (keyEvent.getCode() == KeyCode.MINUS || keyEvent.getCode() == KeyCode.SUBTRACT) {
            subtractRadio.setSelected(true);
            updateStatus("Operacion configurada en -");
        } else if (keyEvent.getCode() == KeyCode.ENTER || keyEvent.getCode() == KeyCode.SPACE) {
            if (!drawButton.isDisable()) {
                handleDrawAction(new ActionEvent());
            } else {
                handlePlayAction(new ActionEvent());
            }
        }
    }

    private void selectCard(int index) {
        if (index >= humanCardsBox.getChildren().size()) {
            return;
        }
        selectedHumanCardIndex = index;
        updateStatus("Carta " + (index + 1) + " seleccionada");
        highlightSelectedCard();
    }

    private void renderSnapshot(GameSnapshot snapshot) {
        tableSumLabel.setText(String.valueOf(snapshot.tableSum()));
        deckCountCenterLabel.setText(String.valueOf(snapshot.deckSize()));
        currentTurnLabel.setText(formatTurnName(snapshot.currentTurn()));
        updateSumBoxStyle(snapshot.tableSum());

        renderTopCard(snapshot.topCard());
        renderHistory(snapshot.history());

        boolean humanTurn = "Humano".equals(snapshot.currentTurn());
        boolean currentPlayerIsHuman = snapshot.currentTurn() != null && snapshot.currentTurn().equals("Humano");
        playButton.setDisable(!currentPlayerIsHuman || snapshot.waitingHumanDraw());
        drawButton.setDisable(!currentPlayerIsHuman || !snapshot.waitingHumanDraw());
        humanTurnHint.setText(currentPlayerIsHuman ? "TU TURNO — elige una carta" : "TU MANO");

        renderPlayers(snapshot.players());
        renderAlivePlayers(snapshot.players());
    }

    private void renderTopCard(String topCard) {
        if (topCard == null || "-".equals(topCard) || topCard.isBlank()) {
            topCardView.setImage(null);
            topCardView.setViewport(null);
            topCardValueLabel.setText("");
            topCardValueLabel.setVisible(false);
            return;
        }

        ImageView source = CardImageProvider.cardView(toShortFromDisplay(topCard), CARD_WIDTH, CARD_HEIGHT);
        topCardView.setImage(source.getImage());
        topCardView.setViewport(source.getViewport());

        int value = extractGameValue(topCard);
        topCardValueLabel.setText(value >= 0 ? "+" + value : String.valueOf(value));
        topCardValueLabel.setVisible(true);
    }

    private void renderHistory(List<String> history) {
        List<String> formatted = new ArrayList<>();
        int start = Math.max(0, history.size() - 8);
        for (int i = start; i < history.size(); i++) {
            formatted.add(formatHistoryEntry(history.get(i)));
        }
        historyList.getItems().setAll(formatted);
        historyList.scrollTo(Math.max(0, historyList.getItems().size() - 1));
    }

    private String formatHistoryEntry(String entry) {
        if (entry.contains(" jugo ")) {
            int idx = entry.indexOf(" jugo ");
            String player = entry.substring(0, idx);
            String rest = entry.substring(idx + " jugo ".length());
            int arrow = rest.indexOf(" con ");
            if (arrow > 0) {
                String cardPart = rest.substring(0, arrow).trim();
                String symbol = rest.contains("+") ? "+" : "-";
                return player + " jugo " + cardPart + " " + symbol;
            }
        }
        return entry;
    }

    private void renderPlayers(List<PlayerSnapshot> players) {
        machineTopBox.getChildren().clear();
        machineLeftBox.getChildren().clear();
        machineRightBox.getChildren().clear();
        humanCardsBox.getChildren().clear();

        List<PlayerSnapshot> machines = players.stream().filter(p -> !p.human()).toList();

        for (int i = 0; i < machines.size(); i++) {
            VBox slot = buildMachinePanel(machines.get(i));
            switch (machines.size()) {
                case 1 -> machineTopBox.getChildren().add(slot);
                case 2 -> {
                    if (i == 0) {
                        machineLeftBox.getChildren().add(slot);
                    } else {
                        machineRightBox.getChildren().add(slot);
                    }
                }
                default -> {
                    if (i == 0) {
                        machineTopBox.getChildren().add(slot);
                    } else if (i == 1) {
                        machineLeftBox.getChildren().add(slot);
                    } else {
                        machineRightBox.getChildren().add(slot);
                    }
                }
            }
        }

        for (PlayerSnapshot player : players) {
            if (player.human()) {
                for (int i = 0; i < player.visibleCards().size(); i++) {
                    String cardText = player.visibleCards().get(i);
                    humanCardsBox.getChildren().add(buildHumanCardSlot(cardText, i));
                }
                highlightSelectedCard();
            }
        }
    }

    private VBox buildMachinePanel(PlayerSnapshot player) {
        VBox panel = new VBox(6);
        panel.setAlignment(Pos.CENTER);
        panel.getStyleClass().add("machine-panel");
        if (!player.active()) {
            panel.getStyleClass().add("machine-panel-out");
        }

        Label name = new Label(player.name());
        name.getStyleClass().add("machine-name");

        HBox cards = new HBox(3);
        cards.setAlignment(Pos.CENTER);
        int count = Math.max(player.hiddenCards(), 0);
        for (int i = 0; i < count; i++) {
            ImageView back = CardImageProvider.cardBackView(MACHINE_CARD_WIDTH, MACHINE_CARD_HEIGHT);
            cards.getChildren().add(back);
        }

        panel.getChildren().addAll(name, cards);
        return panel;
    }

    private VBox buildHumanCardSlot(String cardText, int index) {
        VBox slot = new VBox(4);
        slot.setAlignment(Pos.CENTER);
        slot.getStyleClass().add("human-card-slot");

        StackPane cardPane = new StackPane();
        ImageView cardImage = CardImageProvider.cardView(cardText, CARD_WIDTH, CARD_HEIGHT);
        cardImage.setUserData(cardText);
        cardPane.getChildren().add(cardImage);
        cardPane.getStyleClass().add("card-face");

        Label valueLabel = new Label(buildPointHint(cardText));
        valueLabel.getStyleClass().add("card-point-label");

        slot.getChildren().addAll(cardPane, valueLabel);
        slot.setOnMouseClicked(event -> {
            selectedHumanCardIndex = index;
            updateStatus("Carta seleccionada " + (index + 1));
            highlightSelectedCard();
        });
        return slot;
    }

    private String buildPointHint(String cardText) {
        Rank rank = parseRankFromShort(cardText);
        if (rank == Rank.NINE) {
            return "0";
        }
        if (rank == Rank.ACE) {
            int currentSum = parseTableSum();
            int value = currentSum + 10 <= GameRules.TABLE_LIMIT ? 10 : 1;
            return "+" + value;
        }
        int value = rank.getGameValue();
        boolean subtract = subtractRadio.isSelected();
        if (subtract) {
            return "-" + value;
        }
        return "+" + value;
    }

    private int parseTableSum() {
        try {
            return Integer.parseInt(tableSumLabel.getText().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Rank parseRankFromShort(String cardText) {
        int dash = cardText.indexOf('-');
        String label = dash > 0 ? cardText.substring(0, dash) : cardText;
        for (Rank rank : Rank.values()) {
            if (rank.getLabel().equals(label)) {
                return rank;
            }
        }
        return Rank.ACE;
    }

    private void highlightSelectedCard() {
        for (int i = 0; i < humanCardsBox.getChildren().size(); i++) {
            VBox slot = (VBox) humanCardsBox.getChildren().get(i);
            StackPane cardPane = (StackPane) slot.getChildren().getFirst();
            Label valueLabel = (Label) slot.getChildren().get(1);

            if (i == selectedHumanCardIndex) {
                cardPane.getStyleClass().add("selected-card");
            } else {
                cardPane.getStyleClass().remove("selected-card");
            }

            if (slot.getChildren().size() > 1 && i < humanCardsBox.getChildren().size()) {
                String cardText = extractCardTextFromSlot(slot);
                if (cardText != null) {
                    valueLabel.setText(buildPointHint(cardText));
                }
            }
        }
    }

    private String extractCardTextFromSlot(VBox slot) {
        StackPane cardPane = (StackPane) slot.getChildren().getFirst();
        if (cardPane.getChildren().isEmpty()) {
            return null;
        }
        ImageView iv = (ImageView) cardPane.getChildren().getFirst();
        Object userData = iv.getUserData();
        return userData instanceof String s ? s : null;
    }

    private void renderAlivePlayers(List<PlayerSnapshot> players) {
        alivePlayersBox.getChildren().clear();
        for (PlayerSnapshot player : players) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("alive-row");

            Label icon = new Label(player.active() ? "\u2665" : "\u2716");
            icon.getStyleClass().add(player.active() ? "alive-icon" : "dead-icon");

            String displayName = player.human() ? "Tu" : player.name();
            Label name = new Label(displayName);
            name.getStyleClass().add(player.active() ? "alive-name" : "dead-name");

            row.getChildren().addAll(icon, name);
            alivePlayersBox.getChildren().add(row);
        }
    }

    private void updateSumBoxStyle(int tableSum) {
        sumBox.getStyleClass().removeAll("sum-safe", "sum-warning", "sum-danger");
        if (tableSum <= 35) {
            sumBox.getStyleClass().add("sum-safe");
        } else if (tableSum <= 45) {
            sumBox.getStyleClass().add("sum-warning");
        } else {
            sumBox.getStyleClass().add("sum-danger");
        }
    }

    private String formatTurnName(String turn) {
        if ("Humano".equals(turn)) {
            return "TU TURNO";
        }
        return turn;
    }

    private String toShortFromDisplay(String display) {
        if (display.contains(" of ")) {
            String[] parts = display.split(" of ");
            String rank = parts[0].trim();
            String suit = parts[1].trim();
            char suitCode = switch (suit) {
                case "Hearts" -> 'H';
                case "Diamonds" -> 'D';
                case "Clubs" -> 'C';
                default -> 'S';
            };
            return rank + "-" + suitCode;
        }
        return display;
    }

    private int extractGameValue(String display) {
        String shortForm = toShortFromDisplay(display);
        return parseRankFromShort(shortForm).getGameValue();
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
                drawButton.setDisable(true);
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
