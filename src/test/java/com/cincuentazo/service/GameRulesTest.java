package com.cincuentazo.service;

import com.cincuentazo.model.Card;
import com.cincuentazo.model.GameRules;
import com.cincuentazo.model.Move;
import com.cincuentazo.model.Operation;
import com.cincuentazo.model.Rank;
import com.cincuentazo.model.Suit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameRulesTest {

    @Test
    void shouldValidateMoveAgainstLimit50() {
        Card king = new Card(Rank.KING, Suit.SPADES);
        Move addMove = new Move(king, Operation.ADD);
        Move subtractMove = new Move(king, Operation.SUBTRACT);

        assertTrue(GameRules.isMoveValid(subtractMove, 40));
        assertFalse(GameRules.isMoveValid(addMove, 40));
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

    @Test
    void shouldUseCardValueAndOperationSignForMoves() {
        Card nine = new Card(Rank.NINE, Suit.SPADES);
        Card jack = new Card(Rank.JACK, Suit.HEARTS);
        Card ace = new Card(Rank.ACE, Suit.CLUBS);
        Card two = new Card(Rank.TWO, Suit.DIAMONDS);

        assertEquals(10, GameRules.applyMove(new Move(nine, Operation.ADD), 10));
        assertEquals(0, GameRules.applyMove(new Move(jack, Operation.SUBTRACT), 10));
        assertEquals(20, GameRules.applyMove(new Move(ace, Operation.ADD), 10));
        assertEquals(9, GameRules.applyMove(new Move(ace, Operation.SUBTRACT), 10));
        assertEquals(12, GameRules.applyMove(new Move(two, Operation.ADD), 10));
    }

    @Test
    void shouldOnlyAllowValidOperationsForEachCard() {
        Card two = new Card(Rank.TWO, Suit.DIAMONDS);
        Card jack = new Card(Rank.JACK, Suit.HEARTS);
        Card ace = new Card(Rank.ACE, Suit.CLUBS);

        assertTrue(GameRules.isMoveValid(new Move(two, Operation.ADD), 10));
        assertFalse(GameRules.isMoveValid(new Move(two, Operation.SUBTRACT), 10));

        assertTrue(GameRules.isMoveValid(new Move(jack, Operation.SUBTRACT), 10));
        assertFalse(GameRules.isMoveValid(new Move(jack, Operation.ADD), 10));

        assertTrue(GameRules.isMoveValid(new Move(ace, Operation.ADD), 10));
        assertFalse(GameRules.isMoveValid(new Move(ace, Operation.SUBTRACT), 10));
    }

    @Test
    void shouldUseAceAsOneOrTenWhenAdding() {
        Card ace = new Card(Rank.ACE, Suit.CLUBS);

        assertEquals(20, GameRules.applyMove(new Move(ace, Operation.ADD), 10));
        assertEquals(46, GameRules.applyMove(new Move(ace, Operation.ADD), 45));
    }
}
