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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Core game service with turn loop and machine delays.
 */
public class GameService {
    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService turnExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService machineScheduler = Executors.newSingleThreadScheduledExecutor();
    private final SecureRandom random = new SecureRandom();

    private final Object moveLock = new Object();

    private List<Player> players = new ArrayList<>();
    private Deck deck;
    private final List<Card> tablePile = new ArrayList<>();
    private final List<String> history = new ArrayList<>();

    private int tableSum;
    private int currentPlayerIndex;
    private boolean running;
    private Move pendingHumanMove;

    public void addListener(GameEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
    }

    public void removeListener(GameEventListener listener) {
        listeners.remove(listener);
    }

    public synchronized void startNewGame(int machineCount) throws GameInitializationException {
        if (machineCount < 1 || machineCount > 3) {
            throw new GameInitializationException("Los jugadores maquina deben estar entre 1 y 3");
        }

        running = false;
        synchronized (moveLock) {
            pendingHumanMove = null;
            moveLock.notifyAll();
        }

        deck = new Deck();
        players = new ArrayList<>();
        tablePile.clear();
        history.clear();

        players.add(new HumanPlayer("Humano"));
        for (int i = 1; i <= machineCount; i++) {
            players.add(new MachinePlayer("Maquina " + i));
        }

        dealInitialCards(players);

        Card initialTableCard = deck.draw();
        tablePile.add(initialTableCard);
        tableSum = initialTableCard.getGameValue();
        history.add("Carta inicial en mesa: " + initialTableCard + " (suma=" + tableSum + ")");

        currentPlayerIndex = 0;
        running = true;

        emitState();
        emitMessage("Juego iniciado. Comienza el humano.");
        turnExecutor.submit(this::runGameLoop);
    }

    public void submitHumanMove(int cardIndex, Operation operation) throws InvalidMoveException {
        synchronized (moveLock) {
            if (!running) {
                throw new InvalidMoveException("El juego no esta en ejecucion");
            }
            Player current = players.get(currentPlayerIndex);
            if (!current.isHuman()) {
                throw new InvalidMoveException("No es tu turno");
            }
            if (cardIndex < 0 || cardIndex >= current.getHand().size()) {
                throw new InvalidMoveException("Indice de carta invalido");
            }

            Card card = current.getHand().get(cardIndex);
            Move move = new Move(card, operation);
            if (!GameRules.isMoveValid(move, tableSum)) {
                throw new InvalidMoveException("Esta jugada excede 50 en la suma de la mesa");
            }

            pendingHumanMove = move;
            moveLock.notifyAll();
        }
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized GameSnapshot getSnapshot() {
        return buildSnapshot();
    }

    public synchronized void stop() {
        running = false;
        synchronized (moveLock) {
            pendingHumanMove = null;
            moveLock.notifyAll();
        }
        turnExecutor.shutdownNow();
        machineScheduler.shutdownNow();
    }

    private void runGameLoop() {
        try {
            while (isGameActive()) {
                Player player = players.get(currentPlayerIndex);
                if (!player.isActive()) {
                    advanceTurn();
                    continue;
                }

                if (!GameRules.hasAnyValidMove(player.getHand(), tableSum)) {
                    eliminatePlayer(player);
                    if (!isGameActive()) {
                        break;
                    }
                    advanceTurn();
                    continue;
                }

                if (player.isHuman()) {
                    handleHumanTurn(player);
                } else {
                    handleMachineTurn(player);
                }

                emitState();
                advanceTurn();
            }

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
            running = false;
            listeners.forEach(listener -> listener.onError("Error inesperado en el bucle del juego", ex));
        }
    }

    private boolean isGameActive() {
        if (!running) {
            return false;
        }
        long alive = players.stream().filter(Player::isActive).count();
        return alive > 1;
    }

    private void dealInitialCards(List<Player> allPlayers) {
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
            while (running && pendingHumanMove == null) {
                moveLock.wait();
            }
            if (!running) {
                return;
            }
            move = pendingHumanMove;
            pendingHumanMove = null;
        }

        if (!human.removeCard(move.card())) {
            throw new InvalidMoveException("La carta seleccionada ya no esta disponible");
        }

        applyMove(human, move);
        drawCardWithDelay(human, 0, 0);
    }

    private void handleMachineTurn(Player machine) throws Exception {
        emitMessage(machine.getName() + " esta pensando...");
        int thinkDelay = random.nextInt(3) + 2;

        Move selectedMove = waitForMachineMove(machine, thinkDelay);
        machine.removeCard(selectedMove.card());
        applyMove(machine, selectedMove);

        int drawDelay = random.nextInt(2) + 1;
        drawCardWithDelay(machine, drawDelay, drawDelay);
    }

    private Move waitForMachineMove(Player machine, int delaySeconds) throws Exception {
        final Move[] holder = new Move[1];
        final Exception[] error = new Exception[1];
        final Object lock = new Object();

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
            while (holder[0] == null && error[0] == null) {
                lock.wait();
            }
        }

        if (error[0] != null) {
            throw error[0];
        }
        return holder[0];
    }

    private Move chooseMachineMove(Player machine) throws InvalidMoveException {
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

        Optional<Move> bestAdd = candidates.stream()
                .filter(move -> move.operation() == Operation.ADD)
                .max(Comparator.comparingInt(move -> GameRules.applyMove(move, tableSum)));

        if (bestAdd.isPresent()) {
            return bestAdd.get();
        }

        return candidates.stream()
                .min(Comparator.comparingInt(move -> Math.abs(GameRules.applyMove(move, tableSum))))
                .orElse(candidates.getFirst());
    }

    private void applyMove(Player player, Move move) {
        tableSum = GameRules.applyMove(move, tableSum);
        tablePile.add(move.card());
        history.add(player.getName() + " jugo " + move.card() + " con " + move.operation().getSymbol() + " -> suma=" + tableSum);
        emitMessage(player.getName() + " jugo " + move.card().toShortString() + " " + move.operation().getSymbol());
    }

    private void drawCardWithDelay(Player player, int minSeconds, int maxSeconds) throws InterruptedException {
        if (deck.isEmpty()) {
            deck.recycle(tablePile);
            tablePile.clear();
        }

        if (minSeconds > 0 || maxSeconds > 0) {
            int delay = minSeconds;
            if (maxSeconds > minSeconds) {
                delay = random.nextInt(maxSeconds - minSeconds + 1) + minSeconds;
            }
            Thread.sleep(delay * 1000L);
        }

        if (!deck.isEmpty()) {
            player.addCard(deck.draw());
            history.add(player.getName() + " tomo una carta");
        }
    }

    private void eliminatePlayer(Player player) {
        player.eliminate();
        List<Card> recycled = player.extractHand();
        deck.recycle(recycled);
        history.add(player.getName() + " eliminado (sin jugadas validas)");
        emitMessage(player.getName() + " ha sido eliminado.");
    }

    private void advanceTurn() {
        int next = (currentPlayerIndex + 1) % players.size();
        int safety = 0;
        while (!players.get(next).isActive() && safety < players.size()) {
            next = (next + 1) % players.size();
            safety++;
        }
        currentPlayerIndex = next;
    }

    private void emitState() {
        GameSnapshot snapshot = buildSnapshot();
        listeners.forEach(listener -> listener.onGameStateChanged(snapshot));
    }

    private void emitMessage(String message) {
        listeners.forEach(listener -> listener.onTurnMessage(message));
    }

    private synchronized GameSnapshot buildSnapshot() {
        String topCard = tablePile.isEmpty() ? "-" : tablePile.getLast().toString();
        List<PlayerSnapshot> playerSnapshots = new ArrayList<>();

        for (Player player : players) {
            if (player.isHuman()) {
                List<String> visible = player.getHand().stream().map(Card::toShortString).toList();
                playerSnapshots.add(new PlayerSnapshot(player.getName(), true, player.isActive(), visible, 0));
            } else {
                playerSnapshots.add(new PlayerSnapshot(player.getName(), false, player.isActive(), List.of(), player.handSize()));
            }
        }

        String currentTurn = players.isEmpty() ? "-" : players.get(currentPlayerIndex).getName();
        return new GameSnapshot(tableSum, topCard, deck == null ? 0 : deck.size(), currentTurn, playerSnapshots, List.copyOf(history));
    }
}
