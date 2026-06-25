package com.cincuentazo.service;

import com.cincuentazo.model.Card;
import com.cincuentazo.model.GameRules;
import com.cincuentazo.model.Move;
import com.cincuentazo.model.Operation;
import com.cincuentazo.model.Rank;
import com.cincuentazo.model.Suit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameRulesTest {

    @Test
    void shouldValidateMoveAgainstLimit50() {
        Card king = new Card(Rank.KING, Suit.SPADES);
        Move addMove = new Move(king, Operation.ADD);

        assertTrue(GameRules.isMoveValid(addMove, 40));
        assertFalse(GameRules.isMoveValid(addMove, 45));
    }

    @Test
    void shouldDetectAtLeastOneValidMove() {
        List<Card> hand = List.of(
                new Card(Rank.KING, Suit.HEARTS),
                new Card(Rank.QUEEN, Suit.CLUBS),
                new Card(Rank.JACK, Suit.DIAMONDS)
        );

        assertTrue(GameRules.hasAnyValidMove(hand, 49));
    }
}
