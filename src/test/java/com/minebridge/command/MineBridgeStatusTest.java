package com.minebridge.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MineBridgeStatusTest {

    @Test
    void formatsConnectionChannelQueueUptimeAndFeatureStates() {
        MineBridgeStatus status = new MineBridgeStatus(
                "CONNECTED", true, 7, 100, 3_661_000, true, false);

        var lines = status.formatLines();

        assertTrue(lines.stream().anyMatch(line -> line.contains("CONNECTED")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Available")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("7/100")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("1h 1m 1s")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Chat relay: §aEnabled")));
        assertTrue(lines.stream().anyMatch(
                line -> line.contains("Join/leave notifications: §cDisabled")));
    }

    @Test
    void displaysDisconnectedAndUnavailableStateWithoutSecrets() {
        var lines = new MineBridgeStatus(
                "DISCONNECTED", false, 0, 100, 0, false, false).formatLines();

        assertTrue(lines.stream().anyMatch(line -> line.contains("DISCONNECTED")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Unavailable")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("0s")));
    }
}
