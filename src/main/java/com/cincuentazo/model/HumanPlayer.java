package com.cincuentazo.model;

/**
 * Human player model.
 */
public final class HumanPlayer extends Player {

    public HumanPlayer(String name) {
        super(name);
    }

    @Override
    public boolean isHuman() {
        return true;
    }
}
