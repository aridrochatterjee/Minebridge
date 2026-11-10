package com.minebridge.discord;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordEventDeduplicatorTest {

    @Test
    void suppressesRepeatNotificationsPerPlayerAndEventType() {
        DiscordEventDeduplicator deduplicator = new DiscordEventDeduplicator(10_000, 10);
        UUID playerId = UUID.randomUUID();

        assertFalse(deduplicator.alreadySent(playerId, "join"));
        assertTrue(deduplicator.alreadySent(playerId, "join"));
        assertFalse(deduplicator.alreadySent(playerId, "leave"));
        assertFalse(deduplicator.alreadySent(UUID.randomUUID(), "join"));
    }
}
