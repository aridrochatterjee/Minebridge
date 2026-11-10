package com.minebridge.achievement;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancementAnnouncementDeduplicatorTest {

    @Test
    void suppressesDuplicatePlayerAdvancementAnnouncements() {
        AdvancementAnnouncementDeduplicator deduplicator =
                new AdvancementAnnouncementDeduplicator(10_000, 10);
        UUID playerId = UUID.randomUUID();

        assertFalse(deduplicator.alreadyAnnounced(playerId, "minecraft:story/root"));
        assertTrue(deduplicator.alreadyAnnounced(playerId, "minecraft:story/root"));
        assertFalse(deduplicator.alreadyAnnounced(playerId, "minecraft:story/mine_stone"));
        assertFalse(deduplicator.alreadyAnnounced(UUID.randomUUID(), "minecraft:story/root"));
    }

    @Test
    void boundsRememberedAnnouncements() {
        AdvancementAnnouncementDeduplicator deduplicator =
                new AdvancementAnnouncementDeduplicator(10_000, 1);
        UUID playerId = UUID.randomUUID();

        assertFalse(deduplicator.alreadyAnnounced(playerId, "first"));
        assertFalse(deduplicator.alreadyAnnounced(playerId, "second"));
        assertFalse(deduplicator.alreadyAnnounced(playerId, "first"));
    }
}
