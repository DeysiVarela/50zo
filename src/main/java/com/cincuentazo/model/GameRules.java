package com.cincuentazo.model;

import java.util.List;

/**
 * Rule helpers for Cincuentazo.
 */
public final class GameRules {
    // Regla principal: la suma de la mesa nunca debe superar este valor.
    public static final int TABLE_LIMIT = 50;

    private GameRules() {
        // Clase utilitaria; no se permiten instancias.
    }

    public static boolean isMoveValid(Move move, int currentSum) {
        if (!isOperationAllowed(move)) {
            return false;
        }
        int next = applyMove(move, currentSum);
        return next <= TABLE_LIMIT;
    }

    public static boolean isOperationAllowed(Move move) {
        Rank rank = move.card().getRank();
        return switch (rank) {
            case ACE -> move.operation() == Operation.ADD;
            case JACK, QUEEN, KING -> move.operation() == Operation.SUBTRACT;
            case NINE -> move.operation() == Operation.ADD;
            case TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, TEN -> move.operation() == Operation.ADD;
        };
    }

    public static int applyMove(Move move, int currentSum) {
        if (move.card().getRank() == Rank.NINE) {
            return currentSum;
        }
        if (move.card().getRank() == Rank.ACE && move.operation() == Operation.ADD) {
            return currentSum + getAceValue(currentSum);
        }
        int value = move.card().getGameValue();
        return move.operation() == Operation.ADD ? currentSum + value : currentSum - value;
    }

    private static int getAceValue(int currentSum) {
        return currentSum + 10 <= TABLE_LIMIT ? 10 : 1;
    }

    public static boolean hasAnyValidMove(List<Card> cards, int currentSum) {
        // Evalua cada carta con ambas operaciones para hallar una jugada legal.
        for (Card card : cards) {
            Move addMove = new Move(card, Operation.ADD);
            Move subtractMove = new Move(card, Operation.SUBTRACT);
            if (isMoveValid(addMove, currentSum) || isMoveValid(subtractMove, currentSum)) {
                // Corta apenas encuentra una jugada posible.
                return true;
            }
        }
        // No hay cartas jugables sin exceder el limite.
        return false;
    }
}
