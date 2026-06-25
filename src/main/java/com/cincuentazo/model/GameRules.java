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
        // Calcula la suma resultante al aplicar la jugada.
        int next = applyMove(move, currentSum);
        // La jugada es valida solo si no rompe el limite de 50.
        return next <= TABLE_LIMIT;
    }

    public static int applyMove(Move move, int currentSum) {
        // Usa el valor de juego del rango de la carta.
        int value = move.card().getGameValue();
        // Suma o resta segun la operacion elegida.
        return move.operation() == Operation.ADD ? currentSum + value : currentSum - value;
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
