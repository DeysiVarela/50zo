package com.cincuentazo.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base player abstraction.
 */
public abstract class Player {
    private final String name;
    private final List<Card> hand;
    private boolean active;

    protected Player(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.hand = new ArrayList<>();
        this.active = true;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public void eliminate() {
        this.active = false;
    }

    public List<Card> getHand() {
        return Collections.unmodifiableList(hand);
    }

    public int handSize() {
        return hand.size();
    }

    public void addCard(Card card) {
        hand.add(Objects.requireNonNull(card, "card cannot be null"));
    }

    public boolean removeCard(Card card) {
        return hand.remove(card);
    }

    public List<Card> extractHand() {
        List<Card> extracted = new ArrayList<>(hand);
        hand.clear();
        return extracted;
    }

    public boolean isHuman() {
        return false;
    }
}
