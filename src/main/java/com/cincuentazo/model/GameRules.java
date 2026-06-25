package com.cincuentazo.model;

import java.util.List;

/**
 * Rule helpers for Cincuentazo.
 */
public final class GameRules {
    // Main game rule: table sum must never exceed this value.
    public static final int TABLE_LIMIT = 50;

    private GameRules() {
        // Utility class; no instances allowed.
    }

    public static boolean isMoveValid(Move move, int currentSum) {
        // Computes resulting sum after applying move.
        int next = applyMove(move, currentSum);
        // Move is valid only if it does not break the 50-limit rule.
        return next <= TABLE_LIMIT;
    }

    public static int applyMove(Move move, int currentSum) {
        // Uses rank game value from selected card.
        int value = move.card().getGameValue();
        // Adds or subtracts according to chosen operation.
        return move.operation() == Operation.ADD ? currentSum + value : currentSum - value;
    }

    public static boolean hasAnyValidMove(List<Card> cards, int currentSum) {
        // Tests each card with both operations to see if at least one is legal.
        for (Card card : cards) {
            Move addMove = new Move(card, Operation.ADD);
            Move subtractMove = new Move(card, Operation.SUBTRACT);
            if (isMoveValid(addMove, currentSum) || isMoveValid(subtractMove, currentSum)) {
                // Early exit when a playable move is found.
                return true;
            }
        }
        // No card in hand can be played without exceeding limit.
        return false;
    }
}
