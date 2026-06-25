package com.cincuentazo.exception;

/**
 * Unchecked exception thrown when the deck is empty.
 */
public class DeckExhaustedException extends RuntimeException {
    public DeckExhaustedException(String message) {
        super(message);
    }
}
