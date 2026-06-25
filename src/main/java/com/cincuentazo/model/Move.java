package com.cincuentazo.model;

import java.util.Objects;

/**
 * Player move with selected card and operation.
 */
public record Move(Card card, Operation operation) {
    public Move {
        Objects.requireNonNull(card, "card cannot be null");
        Objects.requireNonNull(operation, "operation cannot be null");
    }
}
