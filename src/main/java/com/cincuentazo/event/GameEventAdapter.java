package com.cincuentazo.event;

import com.cincuentazo.service.GameSnapshot;

/**
 * Adapter class with no-op default handlers.
 */
public class GameEventAdapter implements GameEventListener {
    @Override
    public void onGameStateChanged(GameSnapshot snapshot) {
    }

    @Override
    public void onTurnMessage(String message) {
    }

    @Override
    public void onGameOver(String winnerName) {
    }

    @Override
    public void onError(String message, Throwable throwable) {
    }
}
