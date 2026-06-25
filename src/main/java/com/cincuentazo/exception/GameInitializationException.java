package com.cincuentazo.exception;

/**
 * Checked exception used when game setup fails.
 */
public class GameInitializationException extends Exception {
    public GameInitializationException(String message) {
        super(message);
    }
}
