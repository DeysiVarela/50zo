package com.cincuentazo.model;

import java.util.List;

/**
 * Rule helpers for Cincuentazo.
 */
public final class GameRules {
    public static final int TABLE_LIMIT = 50;

    private GameRules() {
    }

    public static boolean isMoveValid(Move move, int currentSum) {
        int next = applyMove(move, currentSum);
        return next <= TABLE_LIMIT;
    }

    public static int applyMove(Move move, int currentSum) {
        int value = move.card().getGameValue();
        return move.operation() == Operation.ADD ? currentSum + value : currentSum - value;
    }

    public static boolean hasAnyValidMove(List<Card> cards, int currentSum) {
        for (Card card : cards) {
            Move addMove = new Move(card, Operation.ADD);
            Move subtractMove = new Move(card, Operation.SUBTRACT);
            if (isMoveValid(addMove, currentSum) || isMoveValid(subtractMove, currentSum)) {
                return true;
            }
        }
        return false;
    }
}
