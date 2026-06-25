package com.cincuentazo.model;

/**
 * Operation to apply card value to table sum.
 */
public enum Operation {
    ADD("+"),
    SUBTRACT("-");

    private final String symbol;

    Operation(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
