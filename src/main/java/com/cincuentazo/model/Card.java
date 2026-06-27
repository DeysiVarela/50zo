package com.cincuentazo.model;

import java.util.Objects;

/**
 * Immutable card model.
 */
public final class Card {
    private final Rank rank;
    private final Suit suit;

    public Card(Rank rank, Suit suit) {
        this.rank = Objects.requireNonNull(rank, "rank cannot be null");
        this.suit = Objects.requireNonNull(suit, "suit cannot be null");
    }

    public Rank getRank() {
        return rank;
    }

    public Suit getSuit() {
        return suit;
    }

    public int getGameValue() {
        return rank.getGameValue();
    }

    public String toShortString() {
        return rank.getLabel() + "-" + suit.getLabel().charAt(0);
    }

    @Override
    public String toString() {
        return rank.getLabel() + " de " + suit.getLabel();
    }
}
