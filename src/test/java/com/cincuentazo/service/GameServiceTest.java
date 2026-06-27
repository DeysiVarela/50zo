package com.cincuentazo.service;

import com.cincuentazo.exception.GameInitializationException;
import com.cincuentazo.exception.InvalidMoveException;
import com.cincuentazo.model.Operation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameServiceTest {

    @Test
    void shouldStartGameWithRequestedMachinePlayers() throws Exception {
        GameService service = new GameService();
        service.startNewGame(2);

        GameSnapshot snapshot = service.getSnapshot();

        assertEquals(3, snapshot.players().size());
        assertEquals(4, snapshot.players().getFirst().visibleCards().size());
        assertTrue(snapshot.tableSum() >= 0);

        service.stop();
    }

    @Test
    void shouldRestartGameAfterStop() throws Exception {
        GameService service = new GameService();
        service.startNewGame(1);
        service.stop();

        service.startNewGame(2);
        GameSnapshot snapshot = service.getSnapshot();

        assertEquals(3, snapshot.players().size());
        assertTrue(snapshot.tableSum() >= 0);

        service.stop();
    }

    @Test
    void shouldAdvanceToMachineTurnAfterHumanMoveWithoutManualDraw() throws Exception {
        GameService service = new GameService();
        service.startNewGame(1);

        boolean moveSubmitted = false;
        for (int index = 0; index < 4; index++) {
            try {
                service.submitHumanMove(index, Operation.ADD);
                moveSubmitted = true;
                break;
            } catch (InvalidMoveException ignored) {
            }
        }

        assertTrue(moveSubmitted, "Debe existir al menos una jugada humana valida");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        String currentTurn = service.getSnapshot().currentTurn();
        while (System.nanoTime() < deadline && "Humano".equals(currentTurn)) {
            Thread.sleep(50);
            currentTurn = service.getSnapshot().currentTurn();
        }

        assertTrue(!"Humano".equals(currentTurn), "El turno debe pasar a la maquina tras la jugada humana");
        service.stop();
    }

    @Test
    void shouldReturnToHumanTurnAfterMachineTurn() throws Exception {
        GameService service = new GameService();
        AtomicReference<GameSnapshot> lastSnapshot = new AtomicReference<>();
        service.addListener(new com.cincuentazo.event.GameEventAdapter() {
            @Override
            public void onGameStateChanged(GameSnapshot snapshot) {
                lastSnapshot.set(snapshot);
            }
        });
        service.startNewGame(1);

        boolean moveSubmitted = false;
        for (int index = 0; index < 4; index++) {
            try {
                service.submitHumanMove(index, Operation.ADD);
                moveSubmitted = true;
                break;
            } catch (InvalidMoveException ignored) {
            }
        }

        assertTrue(moveSubmitted, "Debe existir al menos una jugada humana valida");

        Thread.sleep(TimeUnit.SECONDS.toMillis(6));
        GameSnapshot snapshot = lastSnapshot.get();

        assertNotNull(snapshot, "Debe emitirse al menos un snapshot de estado");
        assertEquals("Humano", snapshot.currentTurn(), "El turno debe volver al humano tras la jugada de la maquina");
        service.stop();
    }

    @Test
    void shouldRejectInvalidMachinePlayerCount() {
        GameService service = new GameService();

        assertThrows(GameInitializationException.class, () -> service.startNewGame(0));
        service.stop();
    }
}
