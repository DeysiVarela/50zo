package com.cincuentazo.model;

/**
 * Card suits.
 */
public enum Suit {
    HEARTS("Corazones"),
    DIAMONDS("Diamantes"),
    CLUBS("Tréboles"),
    SPADES("Picas");

    private final String label;

    Suit(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
