package com.cincuentazo.service;

import java.util.List;

/**
 * Read-only game state for the view layer.
 */
public record GameSnapshot(
        int tableSum,
        String topCard,
        int deckSize,
        String currentTurn,
        List<PlayerSnapshot> players,
        List<String> history
) {
}
