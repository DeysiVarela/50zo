package com.cincuentazo.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardTest {

    @Test
    void shouldRenderLocalizedCardNamesInSpanish() {
        Card card = new Card(Rank.ACE, Suit.HEARTS);

        assertEquals("As de Corazones", card.toString());
    }
}
