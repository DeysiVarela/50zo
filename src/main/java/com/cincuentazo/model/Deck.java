package com.cincuentazo.model;

import com.cincuentazo.exception.DeckExhaustedException;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

/**
 * Deck with draw and recycle operations.
 */
public final class Deck {
    // FIFO queue storage for the deck.
    private final Queue<Card> cards = new ArrayDeque<>();
    private final SecureRandom random = new SecureRandom();

    public Deck() {
        resetStandard52();
    }

    public void resetStandard52() {
        List<Card> allCards = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                allCards.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(allCards, random);
        cards.clear();
        cards.addAll(allCards);
    }

    public Card draw() {
        Card card = cards.poll();
        if (card == null) {
            throw new DeckExhaustedException("Deck has no cards left");
        }
        return card;
    }

    public int size() {
        return cards.size();
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public void recycle(List<Card> discardedCards) {
        if (discardedCards == null || discardedCards.isEmpty()) {
            return;
        }
        List<Card> shuffled = new ArrayList<>(discardedCards);
        Collections.shuffle(shuffled, random);
        for (Card card : shuffled) {
            cards.offer(card);
        }
    }
}
