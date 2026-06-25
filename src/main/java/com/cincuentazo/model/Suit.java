package com.cincuentazo.model;

/**
 * Card suits.
 */
public enum Suit {
    HEARTS("Hearts"),
    DIAMONDS("Diamonds"),
    CLUBS("Clubs"),
    SPADES("Spades");

    private final String label;

    Suit(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
