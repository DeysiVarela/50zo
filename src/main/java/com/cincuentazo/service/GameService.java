package com.cincuentazo.service;

import com.cincuentazo.event.GameEventListener;
import com.cincuentazo.exception.GameInitializationException;
import com.cincuentazo.exception.InvalidMoveException;
import com.cincuentazo.model.Card;
import com.cincuentazo.model.Deck;
import com.cincuentazo.model.GameRules;
import com.cincuentazo.model.HumanPlayer;
import com.cincuentazo.model.MachinePlayer;
import com.cincuentazo.model.Move;
import com.cincuentazo.model.Operation;
import com.cincuentazo.model.Player;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Core game service with turn loop and machine delays.
 */
public class GameService {
    // UI/controller listeners interested in game state and messages.
    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();
    // Single-thread loop that executes turn order sequentially.
    private final ExecutorService turnExecutor = Executors.newSingleThreadExecutor();
    // Scheduler used for machine delays (thinking/draw timing).
    private final ScheduledExecutorService machineScheduler = Executors.newSingleThreadScheduledExecutor();
    // Random source for delays and shuffling decisions.
    private final SecureRandom random = new SecureRandom();

    // Lock used to coordinate human move submission from UI thread.
    private final Object moveLock = new Object();

    private List<Player> players = new ArrayList<>();
    private Deck deck;
    // LIFO structure for table pile (last played card on top).
    private final Stack<Card> tablePile = new Stack<>();
    private final List<String> history = new ArrayList<>();

    private int tableSum;
    private int currentPlayerIndex;
    private boolean running;
    private boolean waitingHumanDraw;
    // Holds the move chosen by human until turn loop consumes it.
    private Move pendingHumanMove;
    private boolean pendingHumanDraw;

    /**
     * Registers a listener for game state and turn events.
     *
     * @param listener listener instance to add
     */
    public void addListener(GameEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener listener instance to remove
     */
    public void removeListener(GameEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Starts a new game with one human player and a configurable number of machine players.
     *
     * @param machineCount machine players count in range 1..3
     * @throws GameInitializationException when machineCount is out of range
     */
    public synchronized void startNewGame(int machineCount) throws GameInitializationException {
        // Validates setup range required by user story (1..3 machines).
        if (machineCount < 1 || machineCount > 3) {
            throw new GameInitializationException("Los jugadores maquina deben estar entre 1 y 3");
        }

        // Stops any waiting human turn from a previous game instance.
        running = false;
        synchronized (moveLock) {
            pendingHumanMove = null;
            pendingHumanDraw = false;
            moveLock.notifyAll();
        }

        // Rebuilds game state for a clean session.
        deck = new Deck();
        players = new ArrayList<>();
        tablePile.clear();
        history.clear();

        // Human always goes first in this implementation.
        players.add(new HumanPlayer("Humano"));
        for (int i = 1; i <= machineCount; i++) {
            players.add(new MachinePlayer("Maquina " + i));
        }

        // Deals 4 cards per player at game start.
        dealInitialCards(players);

        // Places first table card and initializes table sum.
        Card initialTableCard = deck.draw();
        tablePile.push(initialTableCard);
        tableSum = initialTableCard.getGameValue();
        history.add("Carta inicial en mesa: " + initialTableCard + " (suma=" + tableSum + ")");

        // Starts with player index 0 (human).
        currentPlayerIndex = 0;
        running = true;
        waitingHumanDraw = false;

        // Sends first state to UI and starts asynchronous turn loop.
        emitState();
        emitMessage("Juego iniciado. Comienza el humano.");
        turnExecutor.submit(this::runGameLoop);
    }

    public void submitHumanMove(int cardIndex, Operation operation) throws InvalidMoveException {
        synchronized (moveLock) {
            // Rejects actions if game already ended.
            if (!running) {
                throw new InvalidMoveException("El juego no esta en ejecucion");
            }
            // Human can only play during own turn.
            Player current = players.get(currentPlayerIndex);
            if (!current.isHuman()) {
                throw new InvalidMoveException("No es tu turno");
            }
            if (waitingHumanDraw) {
                throw new InvalidMoveException("Primero debes tomar una carta para terminar el turno");
            }
            // Guards against out-of-range card selection.
            if (cardIndex < 0 || cardIndex >= current.getHand().size()) {
                throw new InvalidMoveException("Indice de carta invalido");
            }

            // Builds candidate move using selected card and operation.
            Card card = current.getHand().get(cardIndex);
            Move move = new Move(card, operation);
            // Enforces main rule: table sum must not exceed 50.
            if (!GameRules.isMoveValid(move, tableSum)) {
                throw new InvalidMoveException("Esta jugada excede 50 en la suma de la mesa");
            }

            // Publishes move for turn loop and wakes waiting thread.
            pendingHumanMove = move;
            moveLock.notifyAll();
        }
    }

    /**
     * Signals that the human player wants to draw the mandatory replacement card.
     *
     * @throws InvalidMoveException when draw is requested outside the valid phase
     */
    public void submitHumanDraw() throws InvalidMoveException {
        synchronized (moveLock) {
            if (!running) {
                throw new InvalidMoveException("El juego no esta en ejecucion");
            }
            Player current = players.get(currentPlayerIndex);
            if (!current.isHuman()) {
                throw new InvalidMoveException("Solo el humano puede tomar carta en este turno");
            }
            if (!waitingHumanDraw) {
                throw new InvalidMoveException("Debes jugar una carta antes de tomar otra");
            }
            pendingHumanDraw = true;
            moveLock.notifyAll();
        }
    }

    /**
     * Indicates whether a game loop is currently running.
     *
     * @return true when game is active
     */
    public synchronized boolean isRunning() {
        return running;
    }

    /**
     * Returns an immutable snapshot to render current game state in the UI.
     *
     * @return game snapshot
     */
    public synchronized GameSnapshot getSnapshot() {
        return buildSnapshot();
    }

    /**
     * Stops game loop and shuts down executors.
     */
    public synchronized void stop() {
        // Marks game stopped and unblocks any waiting human-turn wait().
        running = false;
        synchronized (moveLock) {
            pendingHumanMove = null;
            pendingHumanDraw = false;
            moveLock.notifyAll();
        }
        // Requests shutdown of executors used by game loop and scheduler.
        turnExecutor.shutdownNow();
        machineScheduler.shutdownNow();
    }

    private void runGameLoop() {
        try {
            // Repeats until only one active player remains.
            while (isGameActive()) {
                Player player = players.get(currentPlayerIndex);
                // Skips players already eliminated.
                if (!player.isActive()) {
                    advanceTurn();
                    continue;
                }

                // Eliminates player if no valid move exists.
                if (!GameRules.hasAnyValidMove(player.getHand(), tableSum)) {
                    eliminatePlayer(player);
                    if (!isGameActive()) {
                        break;
                    }
                    advanceTurn();
                    continue;
                }

                if (player.isHuman()) {
                    // Human waits for UI-submitted move.
                    handleHumanTurn(player);
                } else {
                    // Machine auto-selects a move with delay.
                    handleMachineTurn(player);
                }

                // Sends refreshed state and rotates turn.
                emitState();
                advanceTurn();
            }

            // Resolves winner when loop exits.
            Player winner = players.stream().filter(Player::isActive).findFirst().orElse(null);
            if (winner != null) {
                emitMessage("Ganador: " + winner.getName());
                listeners.forEach(listener -> listener.onGameOver(winner.getName()));
            } else {
                emitMessage("El juego termino sin ganador.");
                listeners.forEach(listener -> listener.onGameOver("Sin ganador"));
            }
            running = false;
        } catch (Exception ex) {
            // Reports unexpected runtime errors to UI listeners.
            running = false;
            listeners.forEach(listener -> listener.onError("Error inesperado en el bucle del juego", ex));
        }
    }

    private boolean isGameActive() {
        if (!running) {
            return false;
        }
        // Game continues while more than one player is active.
        long alive = players.stream().filter(Player::isActive).count();
        return alive > 1;
    }

    private void dealInitialCards(List<Player> allPlayers) {
        // Round-robin dealing: 4 cards per player.
        for (int i = 0; i < 4; i++) {
            for (Player player : allPlayers) {
                player.addCard(deck.draw());
            }
        }
    }

    private void handleHumanTurn(Player human) throws InterruptedException, InvalidMoveException {
        emitMessage("Tu turno: elige carta y operacion.");
        Move move;

        synchronized (moveLock) {
            // Waits until UI thread submits a legal move.
            while (running && pendingHumanMove == null) {
                moveLock.wait();
            }
            if (!running) {
                return;
            }
            // Consumes pending move and clears buffer.
            move = pendingHumanMove;
            pendingHumanMove = null;
        }

        // Fails if hand changed and card no longer exists.
        if (!human.removeCard(move.card())) {
            throw new InvalidMoveException("La carta seleccionada ya no esta disponible");
        }

        // Applies move and draws replacement card immediately.
        applyMove(human, move);
        waitingHumanDraw = true;
        emitState();
        emitMessage("Ahora toma una carta para completar tu turno.");

        synchronized (moveLock) {
            while (running && !pendingHumanDraw) {
                moveLock.wait();
            }
            if (!running) {
                return;
            }
            pendingHumanDraw = false;
        }

        drawCardWithDelay(human, 0, 0);
        waitingHumanDraw = false;
    }

    private void handleMachineTurn(Player machine) throws Exception {
        emitMessage(machine.getName() + " esta pensando...");
        // Machine waits 2..4 seconds before deciding.
        int thinkDelay = random.nextInt(3) + 2;

        // Picks move asynchronously after delay.
        Move selectedMove = waitForMachineMove(machine, thinkDelay);
        machine.removeCard(selectedMove.card());
        applyMove(machine, selectedMove);

        // Machine waits 1..2 seconds before drawing.
        int drawDelay = random.nextInt(2) + 1;
        drawCardWithDelay(machine, drawDelay, drawDelay);
    }

    private Move waitForMachineMove(Player machine, int delaySeconds) throws Exception {
        // Mutable holders used by scheduled task callback.
        final Move[] holder = new Move[1];
        final Exception[] error = new Exception[1];
        final Object lock = new Object();

        // Schedules machine decision after configured delay.
        machineScheduler.schedule(() -> {
            synchronized (lock) {
                try {
                    holder[0] = chooseMachineMove(machine);
                } catch (Exception ex) {
                    error[0] = ex;
                } finally {
                    lock.notifyAll();
                }
            }
        }, delaySeconds, TimeUnit.SECONDS);

        synchronized (lock) {
            // Waits until either a move is selected or an error occurs.
            while (holder[0] == null && error[0] == null) {
                lock.wait();
            }
        }

        // Propagates machine-selection errors to caller.
        if (error[0] != null) {
            throw error[0];
        }
        return holder[0];
    }

    private Move chooseMachineMove(Player machine) throws InvalidMoveException {
        // Collects all legal add/subtract moves from current hand.
        List<Move> candidates = new ArrayList<>();
        for (Card card : machine.getHand()) {
            Move add = new Move(card, Operation.ADD);
            Move subtract = new Move(card, Operation.SUBTRACT);
            if (GameRules.isMoveValid(add, tableSum)) {
                candidates.add(add);
            }
            if (GameRules.isMoveValid(subtract, tableSum)) {
                candidates.add(subtract);
            }
        }

        if (candidates.isEmpty()) {
            throw new InvalidMoveException("La maquina no tiene jugadas validas");
        }

        // Heuristic 1: prefer highest safe addition.
        Optional<Move> bestAdd = candidates.stream()
                .filter(move -> move.operation() == Operation.ADD)
                .max(Comparator.comparingInt(move -> GameRules.applyMove(move, tableSum)));

        if (bestAdd.isPresent()) {
            return bestAdd.get();
        }

        // Heuristic 2: fallback to move closest to zero absolute sum.
        return candidates.stream()
                .min(Comparator.comparingInt(move -> Math.abs(GameRules.applyMove(move, tableSum))))
                .orElse(candidates.getFirst());
    }

    private void applyMove(Player player, Move move) {
        // Updates table sum and visible top card pile.
        tableSum = GameRules.applyMove(move, tableSum);
        tablePile.push(move.card());
        // Logs move for history panel and status message.
        history.add(player.getName() + " jugo " + move.card() + " con " + move.operation().getSymbol() + " -> suma=" + tableSum);
        emitMessage(player.getName() + " jugo " + move.card().toShortString() + " " + move.operation().getSymbol());
    }

    private void drawCardWithDelay(Player player, int minSeconds, int maxSeconds) throws InterruptedException {
        // Recycles table pile into deck if deck is exhausted.
        if (deck.isEmpty()) {
            deck.recycle(new ArrayList<>(tablePile));
            tablePile.clear();
        }

        // Applies optional draw delay for machine timing simulation.
        if (minSeconds > 0 || maxSeconds > 0) {
            int delay = minSeconds;
            if (maxSeconds > minSeconds) {
                delay = random.nextInt(maxSeconds - minSeconds + 1) + minSeconds;
            }
            Thread.sleep(delay * 1000L);
        }

        // Draws one card if deck still has cards.
        if (!deck.isEmpty()) {
            player.addCard(deck.draw());
            history.add(player.getName() + " tomo una carta");
        }
    }

    private void eliminatePlayer(Player player) {
        // Marks player inactive and returns remaining hand to deck.
        player.eliminate();
        List<Card> recycled = player.extractHand();
        deck.recycle(recycled);
        history.add(player.getName() + " eliminado (sin jugadas validas)");
        emitMessage(player.getName() + " ha sido eliminado.");
    }

    private void advanceTurn() {
        // Moves index circularly to next active player.
        int next = (currentPlayerIndex + 1) % players.size();
        int safety = 0;
        while (!players.get(next).isActive() && safety < players.size()) {
            next = (next + 1) % players.size();
            safety++;
        }
        currentPlayerIndex = next;
    }

    private void emitState() {
        // Builds immutable snapshot and notifies all listeners.
        GameSnapshot snapshot = buildSnapshot();
        listeners.forEach(listener -> listener.onGameStateChanged(snapshot));
    }

    private void emitMessage(String message) {
        // Broadcasts status/turn messages to subscribed listeners.
        listeners.forEach(listener -> listener.onTurnMessage(message));
    }

    private synchronized GameSnapshot buildSnapshot() {
        // Top card shown in UI table area.
        String topCard = tablePile.isEmpty() ? "-" : tablePile.peek().toString();
        List<PlayerSnapshot> playerSnapshots = new ArrayList<>();

        for (Player player : players) {
            if (player.isHuman()) {
                // Human cards are visible face-up.
                List<String> visible = player.getHand().stream().map(Card::toShortString).toList();
                playerSnapshots.add(new PlayerSnapshot(player.getName(), true, player.isActive(), visible, 0));
            } else {
                // Machine cards remain hidden (only count is exposed).
                playerSnapshots.add(new PlayerSnapshot(player.getName(), false, player.isActive(), List.of(), player.handSize()));
            }
        }

        // Name of player whose turn is currently active.
        String currentTurn = players.isEmpty() ? "-" : players.get(currentPlayerIndex).getName();
        // Returns immutable projection consumed by UI.
        List<String> tableCards = tablePile.stream().map(Card::toShortString).toList();
        return new GameSnapshot(
            tableSum,
            topCard,
            deck == null ? 0 : deck.size(),
            currentTurn,
            tableCards,
            waitingHumanDraw,
            playerSnapshots,
            List.copyOf(history)
        );
    }
}
