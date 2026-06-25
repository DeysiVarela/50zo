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
    // Listeners de UI/controlador interesados en estado y mensajes.
    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();
    // Loop de un solo hilo que ejecuta turnos en orden.
    private final ExecutorService turnExecutor = Executors.newSingleThreadExecutor();
    // Scheduler usado para demoras de maquina (pensar/tomar).
    private final ScheduledExecutorService machineScheduler = Executors.newSingleThreadScheduledExecutor();
    // Fuente aleatoria para demoras y decisiones de mezcla.
    private final SecureRandom random = new SecureRandom();

    // Lock para coordinar envio de jugadas humanas desde UI.
    private final Object moveLock = new Object();

    private List<Player> players = new ArrayList<>();
    private Deck deck;
    // Estructura LIFO para pila de mesa (ultima carta arriba).
    private final Stack<Card> tablePile = new Stack<>();
    private final List<String> history = new ArrayList<>();

    private int tableSum;
    private int currentPlayerIndex;
    private boolean running;
    private boolean waitingHumanDraw;
    // Guarda la jugada humana hasta que el loop de turnos la procese.
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
        // Valida rango requerido por la historia de usuario (1..3).
        if (machineCount < 1 || machineCount > 3) {
            throw new GameInitializationException("Los jugadores maquina deben estar entre 1 y 3");
        }

        // Detiene cualquier espera de turno humano de una partida previa.
        running = false;
        synchronized (moveLock) {
            pendingHumanMove = null;
            pendingHumanDraw = false;
            moveLock.notifyAll();
        }

        // Reconstruye el estado para una sesion limpia.
        deck = new Deck();
        players = new ArrayList<>();
        tablePile.clear();
        history.clear();

        // El humano siempre inicia en esta implementacion.
        players.add(new HumanPlayer("Humano"));
        for (int i = 1; i <= machineCount; i++) {
            players.add(new MachinePlayer("Maquina " + i));
        }

        // Reparte 4 cartas por jugador al iniciar.
        dealInitialCards(players);

        // Coloca la primera carta en mesa e inicializa la suma.
        Card initialTableCard = deck.draw();
        tablePile.push(initialTableCard);
        tableSum = initialTableCard.getGameValue();
        history.add("Carta inicial en mesa: " + initialTableCard + " (suma=" + tableSum + ")");

        // Comienza en el indice 0 (humano).
        currentPlayerIndex = 0;
        running = true;
        waitingHumanDraw = false;

        // Envia primer estado a UI e inicia loop de turnos asincrono.
        emitState();
        emitMessage("Juego iniciado. Comienza el humano.");
        turnExecutor.submit(this::runGameLoop);
    }

    public void submitHumanMove(int cardIndex, Operation operation) throws InvalidMoveException {
        synchronized (moveLock) {
            // Rechaza acciones si la partida ya termino.
            if (!running) {
                throw new InvalidMoveException("El juego no esta en ejecucion");
            }
            // El humano solo puede jugar en su propio turno.
            Player current = players.get(currentPlayerIndex);
            if (!current.isHuman()) {
                throw new InvalidMoveException("No es tu turno");
            }
            if (waitingHumanDraw) {
                throw new InvalidMoveException("Primero debes tomar una carta para terminar el turno");
            }
            // Protege contra seleccion de carta fuera de rango.
            if (cardIndex < 0 || cardIndex >= current.getHand().size()) {
                throw new InvalidMoveException("Indice de carta invalido");
            }

            // Construye jugada candidata con carta y operacion.
            Card card = current.getHand().get(cardIndex);
            Move move = new Move(card, operation);
            // Aplica regla principal: la suma de mesa no supera 50.
            if (!GameRules.isMoveValid(move, tableSum)) {
                throw new InvalidMoveException("Esta jugada excede 50 en la suma de la mesa");
            }

            // Publica jugada para el loop y despierta el hilo en espera.
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
        // Marca juego detenido y desbloquea cualquier wait() pendiente.
        running = false;
        synchronized (moveLock) {
            pendingHumanMove = null;
            pendingHumanDraw = false;
            moveLock.notifyAll();
        }
        // Solicita apagado de executors del loop y scheduler.
        turnExecutor.shutdownNow();
        machineScheduler.shutdownNow();
    }

    private void runGameLoop() {
        try {
            // Repite hasta que quede un solo jugador activo.
            while (isGameActive()) {
                Player player = players.get(currentPlayerIndex);
                // Omite jugadores ya eliminados.
                if (!player.isActive()) {
                    advanceTurn();
                    continue;
                }

                // Elimina jugador si no tiene jugadas validas.
                if (!GameRules.hasAnyValidMove(player.getHand(), tableSum)) {
                    eliminatePlayer(player);
                    if (!isGameActive()) {
                        break;
                    }
                    advanceTurn();
                    continue;
                }

                if (player.isHuman()) {
                    // El humano espera una jugada enviada desde UI.
                    handleHumanTurn(player);
                } else {
                    // La maquina selecciona jugada automaticamente con demora.
                    handleMachineTurn(player);
                }

                // Envia estado actualizado y rota turno.
                emitState();
                advanceTurn();
            }

            // Resuelve ganador cuando finaliza el loop.
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
            // Reporta errores inesperados a listeners de UI.
            running = false;
            listeners.forEach(listener -> listener.onError("Error inesperado en el bucle del juego", ex));
        }
    }

    private boolean isGameActive() {
        if (!running) {
            return false;
        }
        // El juego continua mientras haya mas de un jugador activo.
        long alive = players.stream().filter(Player::isActive).count();
        return alive > 1;
    }

    private void dealInitialCards(List<Player> allPlayers) {
        // Reparto round-robin: 4 cartas por jugador.
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
            // Espera hasta que UI envie una jugada legal.
            while (running && pendingHumanMove == null) {
                moveLock.wait();
            }
            if (!running) {
                return;
            }
            // Consume la jugada pendiente y limpia el buffer.
            move = pendingHumanMove;
            pendingHumanMove = null;
        }

        // Falla si la mano cambio y la carta ya no existe.
        if (!human.removeCard(move.card())) {
            throw new InvalidMoveException("La carta seleccionada ya no esta disponible");
        }

        // Aplica jugada y luego exige tomar carta para cerrar turno.
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
        // La maquina espera 2..4 segundos antes de decidir.
        int thinkDelay = random.nextInt(3) + 2;

        // Elige jugada de forma asincrona tras la demora.
        Move selectedMove = waitForMachineMove(machine, thinkDelay);
        machine.removeCard(selectedMove.card());
        applyMove(machine, selectedMove);

        // La maquina espera 1..2 segundos antes de tomar carta.
        int drawDelay = random.nextInt(2) + 1;
        drawCardWithDelay(machine, drawDelay, drawDelay);
    }

    private Move waitForMachineMove(Player machine, int delaySeconds) throws Exception {
        // Contenedores mutables usados por el callback programado.
        final Move[] holder = new Move[1];
        final Exception[] error = new Exception[1];
        final Object lock = new Object();

        // Programa decision de maquina tras la demora configurada.
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
            // Espera hasta tener jugada o error.
            while (holder[0] == null && error[0] == null) {
                lock.wait();
            }
        }

        // Propaga errores de seleccion de maquina al llamador.
        if (error[0] != null) {
            throw error[0];
        }
        return holder[0];
    }

    private Move chooseMachineMove(Player machine) throws InvalidMoveException {
        // Recolecta todas las jugadas legales de suma/resta de la mano.
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

        // Heuristica 1: prioriza la suma segura mas alta.
        Optional<Move> bestAdd = candidates.stream()
                .filter(move -> move.operation() == Operation.ADD)
                .max(Comparator.comparingInt(move -> GameRules.applyMove(move, tableSum)));

        if (bestAdd.isPresent()) {
            return bestAdd.get();
        }

        // Heuristica 2: fallback a jugada con suma mas cercana a cero.
        return candidates.stream()
                .min(Comparator.comparingInt(move -> Math.abs(GameRules.applyMove(move, tableSum))))
                .orElse(candidates.getFirst());
    }

    private void applyMove(Player player, Move move) {
        // Actualiza suma de mesa y pila visible de cartas.
        tableSum = GameRules.applyMove(move, tableSum);
        tablePile.push(move.card());
        // Registra jugada para historial y mensaje de estado.
        history.add(player.getName() + " jugo " + move.card() + " con " + move.operation().getSymbol() + " -> suma=" + tableSum);
        emitMessage(player.getName() + " jugo " + move.card().toShortString() + " " + move.operation().getSymbol());
    }

    private void drawCardWithDelay(Player player, int minSeconds, int maxSeconds) throws InterruptedException {
        // Recicla pila de mesa al mazo si el mazo se agota.
        if (deck.isEmpty()) {
            deck.recycle(new ArrayList<>(tablePile));
            tablePile.clear();
        }

        // Aplica demora opcional para simular tiempos de maquina.
        if (minSeconds > 0 || maxSeconds > 0) {
            int delay = minSeconds;
            if (maxSeconds > minSeconds) {
                delay = random.nextInt(maxSeconds - minSeconds + 1) + minSeconds;
            }
            Thread.sleep(delay * 1000L);
        }

        // Toma una carta si aun hay cartas en el mazo.
        if (!deck.isEmpty()) {
            player.addCard(deck.draw());
            history.add(player.getName() + " tomo una carta");
        }
    }

    private void eliminatePlayer(Player player) {
        // Marca jugador inactivo y devuelve su mano restante al mazo.
        player.eliminate();
        List<Card> recycled = player.extractHand();
        deck.recycle(recycled);
        history.add(player.getName() + " eliminado (sin jugadas validas)");
        emitMessage(player.getName() + " ha sido eliminado.");
    }

    private void advanceTurn() {
        // Mueve el indice circularmente al siguiente jugador activo.
        int next = (currentPlayerIndex + 1) % players.size();
        int safety = 0;
        while (!players.get(next).isActive() && safety < players.size()) {
            next = (next + 1) % players.size();
            safety++;
        }
        currentPlayerIndex = next;
    }

    private void emitState() {
        // Construye snapshot inmutable y notifica listeners.
        GameSnapshot snapshot = buildSnapshot();
        listeners.forEach(listener -> listener.onGameStateChanged(snapshot));
    }

    private void emitMessage(String message) {
        // Publica mensajes de estado/turno a listeners suscritos.
        listeners.forEach(listener -> listener.onTurnMessage(message));
    }

    private synchronized GameSnapshot buildSnapshot() {
        // Carta superior mostrada en la seccion de mesa.
        String topCard = tablePile.isEmpty() ? "-" : tablePile.peek().toString();
        List<PlayerSnapshot> playerSnapshots = new ArrayList<>();

        for (Player player : players) {
            if (player.isHuman()) {
                // Las cartas humanas se muestran boca arriba.
                List<String> visible = player.getHand().stream().map(Card::toShortString).toList();
                playerSnapshots.add(new PlayerSnapshot(player.getName(), true, player.isActive(), visible, 0));
            } else {
                // Las cartas de maquina permanecen ocultas (solo cantidad).
                playerSnapshots.add(new PlayerSnapshot(player.getName(), false, player.isActive(), List.of(), player.handSize()));
            }
        }

        // Nombre del jugador con turno activo.
        String currentTurn = players.isEmpty() ? "-" : players.get(currentPlayerIndex).getName();
        // Retorna proyeccion inmutable consumida por la UI.
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
