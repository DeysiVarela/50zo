package com.cincuentazo.service;

import com.cincuentazo.exception.GameInitializationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(snapshot.tableSum() > 0);

        service.stop();
    }

    @Test
    void shouldRejectInvalidMachinePlayerCount() {
        GameService service = new GameService();

        assertThrows(GameInitializationException.class, () -> service.startNewGame(0));
        service.stop();
    }
}
