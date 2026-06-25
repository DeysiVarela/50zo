package com.cincuentazo.event;

import com.cincuentazo.service.GameSnapshot;

/**
 * Contract for game state events.
 */
public interface GameEventListener {
    void onGameStateChanged(GameSnapshot snapshot);

    void onTurnMessage(String message);

    void onGameOver(String winnerName);

    void onError(String message, Throwable throwable);
}
