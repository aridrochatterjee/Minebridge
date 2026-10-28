package com.minebridge.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordMessageFilteringTest {

    @Test
    void relaysOnlyEnabledHumanGuildMessagesFromTheConfiguredChannel() {
        assertTrue(DiscordMessageFilter.shouldRelay(
                true, false, false, true, "123", "123"));
        assertFalse(DiscordMessageFilter.shouldRelay(
                false, false, false, true, "123", "123"));
        assertFalse(DiscordMessageFilter.shouldRelay(
                true, true, false, true, "123", "123"));
        assertFalse(DiscordMessageFilter.shouldRelay(
                true, false, true, true, "123", "123"));
        assertFalse(DiscordMessageFilter.shouldRelay(
                true, false, false, false, "123", "123"));
        assertFalse(DiscordMessageFilter.shouldRelay(
                true, false, false, true, "456", "123"));
    }

    @Test
    void rejectsEmptyAndOversizedContentAndRemovesFormattingControls() {
        assertEquals("", DiscordMessageFilter.sanitizeIncoming(null));
        assertEquals("", DiscordMessageFilter.sanitizeIncoming(" \n "));
        assertEquals("hello world", DiscordMessageFilter.sanitizeIncoming(
                "hello\u00a7c\nworld"));
        assertEquals("", DiscordMessageFilter.sanitizeIncoming("x".repeat(2001)));
    }
}
