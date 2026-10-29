package com.minebridge.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayMessageFormatterTest {

    @Test
    void removesMinecraftFormattingAndLineControlsButPreservesUnicode() {
        assertEquals(
                "Player: Hello 😀 🌉",
                RelayMessageFormatter.formatForDiscord(
                        "{player}: {message}",
                        "§aPlayer",
                        "§x§f§f§0§0§a§aHello\n😀 🌉"));
    }

    @Test
    void neutralizesMassMentionsAndLeavesOtherTextReadable() {
        String formatted = RelayMessageFormatter.formatForDiscord(
                "{player}: {message}",
                "Server",
                "@everyone @HERE <@12345678901234567>");

        assertEquals(
                "Server: @\u200Beveryone @\u200BHERE \\<@12345678901234567\\>",
                formatted);
    }

    @Test
    void escapesUserSuppliedDiscordMarkdownWithoutTouchingTemplateFormatting() {
        assertEquals(
                "**Player**: \\*bold\\* \\[link\\]\\(https://example.invalid\\)",
                RelayMessageFormatter.formatForDiscord(
                        "**{player}**: {message}",
                        "Player",
                        "*bold* [link](https://example.invalid)"));
    }

    @Test
    void placeholdersAreExpandedOnceSoUserTextCannotInjectTemplateFields() {
        assertEquals(
                "{message}: actual",
                RelayMessageFormatter.formatForDiscord(
                        "{player}: {message}", "{message}", "actual"));
        assertEquals(
                "Name: {player}",
                RelayMessageFormatter.formatForMinecraft(
                        "{player}: {message}", "Name", "{player}"));
    }

    @Test
    void rejectsBlankAndOversizedContentWithoutTruncating() {
        assertEquals("", RelayMessageFormatter.sanitizeText(null));
        assertEquals("", RelayMessageFormatter.sanitizeText(" \t\n "));
        assertTrue(RelayMessageFormatter.isWithinDiscordLimit(
                "😀".repeat(RelayMessageFormatter.MAX_MESSAGE_LENGTH / 2)));
        assertFalse(RelayMessageFormatter.isWithinDiscordLimit(
                "x".repeat(RelayMessageFormatter.MAX_MESSAGE_LENGTH + 1)));
        assertEquals(
                RelayMessageFormatter.MAX_MESSAGE_LENGTH + 1,
                RelayMessageFormatter.formatForDiscord(
                        "{message}", "", "x".repeat(
                                RelayMessageFormatter.MAX_MESSAGE_LENGTH + 1)).length());
    }

    @Test
    void removesUnpairedHighAndLowSurrogates() {
        assertEquals("beforeafter",
                RelayMessageFormatter.sanitizeText("before\uD800after"));
        assertEquals("beforeafter",
                RelayMessageFormatter.sanitizeText("before\uDC00after"));
    }

    @Test
    void preservesValidSurrogatePairsAndOtherUnicode() {
        String text = "café \uD83D\uDE00 🌉";

        assertEquals(text, RelayMessageFormatter.sanitizeText(text));
        assertEquals(text, RelayMessageFormatter.formatForDiscord("{message}", "", text));
    }

    @Test
    void removesMalformedSurrogatesFromMixedUnicodeWithoutDamagingValidPairs() {
        assertEquals(
                "A\uD83D\uDE00éB",
                RelayMessageFormatter.sanitizeText(
                        "A\uD800\uD83D\uDE00é\uDC00B"));
    }
}
