package com.cincuentazo.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeckTest {

    @Test
    void shouldDraw52UniqueCardsFromFreshDeck() {
        Deck deck = new Deck();
        Set<String> cards = new HashSet<>();

        for (int i = 0; i < 52; i++) {
            cards.add(deck.draw().toString());
        }

        assertEquals(52, cards.size());
        assertEquals(0, deck.size());
    }
}
