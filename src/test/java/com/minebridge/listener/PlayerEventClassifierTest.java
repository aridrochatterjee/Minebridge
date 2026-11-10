package com.minebridge.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerEventClassifierTest {

    @Test
    void identifiesFirstTimeAndReturningPlayers() {
        assertTrue(PlayerEventClassifier.isFirstJoin(false));
        assertFalse(PlayerEventClassifier.isFirstJoin(true));
    }
}
