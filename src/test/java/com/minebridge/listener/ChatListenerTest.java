package com.minebridge.listener;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatListenerTest {

    private static final String TEMPLATE = "**{player}**: {message}";
    private static final String TIMESTAMP = "2026-10-30T12:00:00Z";

    @Test
    void doesNotEnqueueEmptyChatContent() {
        List<String> sent = new ArrayList<>();

        assertFalse(ChatListener.relayIfNonBlank(
                TEMPLATE, "Alex", "", TIMESTAMP, sent::add));

        assertEquals(List.of(), sent);
    }

    @Test
    void doesNotEnqueueWhitespaceOnlyChatContent() {
        List<String> sent = new ArrayList<>();

        assertFalse(ChatListener.relayIfNonBlank(
                TEMPLATE, "Alex", " \t\r\n ", TIMESTAMP, sent::add));

        assertEquals(List.of(), sent);
    }

    @Test
    void relaysNormalTextWithExistingFormatting() {
        List<String> sent = new ArrayList<>();

        assertTrue(ChatListener.relayIfNonBlank(
                TEMPLATE, "Alex", "hello world", TIMESTAMP, sent::add));

        assertEquals(List.of("**Alex**: hello world"), sent);
    }

    @Test
    void acceptsSurroundingWhitespaceAndPreservesExistingFormatterBehavior() {
        List<String> sent = new ArrayList<>();

        assertTrue(ChatListener.relayIfNonBlank(
                TEMPLATE, "Alex", "  hello world  ", TIMESTAMP, sent::add));

        assertEquals(List.of("**Alex**: hello world"), sent);
    }

    @Test
    void doesNotEnqueueNullContent() {
        List<String> sent = new ArrayList<>();

        assertFalse(ChatListener.relayIfNonBlank(
                TEMPLATE, "Alex", null, TIMESTAMP, sent::add));

        assertEquals(List.of(), sent);
    }
}
