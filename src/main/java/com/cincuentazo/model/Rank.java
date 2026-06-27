package com.cincuentazo.model;

/**
 * Card ranks and game values.
 */
public enum Rank {
    ACE("As", 1),
    TWO("2", 2),
    THREE("3", 3),
    FOUR("4", 4),
    FIVE("5", 5),
    SIX("6", 6),
    SEVEN("7", 7),
    EIGHT("8", 8),
    NINE("9", 0),
    TEN("10", 10),
    JACK("J", 10),
    QUEEN("Q", 10),
    KING("K", 10);

    private final String label;
    private final int gameValue;

    Rank(String label, int gameValue) {
        this.label = label;
        this.gameValue = gameValue;
    }

    public String getLabel() {
        return label;
    }

    public int getGameValue() {
        return gameValue;
    }
}
