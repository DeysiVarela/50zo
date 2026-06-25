package com.cincuentazo.service;

import java.util.List;

/**
 * Read-only projection of a player for UI rendering.
 */
public record PlayerSnapshot(
        String name,
        boolean human,
        boolean active,
        List<String> visibleCards,
        int hiddenCards
) {
}
