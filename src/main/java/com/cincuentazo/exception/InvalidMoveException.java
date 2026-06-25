package com.cincuentazo.exception;

/**
 * Checked exception for invalid user or machine moves.
 */
public class InvalidMoveException extends Exception {
    public InvalidMoveException(String message) {
        super(message);
    }
}
