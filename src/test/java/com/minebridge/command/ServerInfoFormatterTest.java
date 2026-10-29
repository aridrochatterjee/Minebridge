package com.minebridge.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerInfoFormatterTest {

    @Test
    void formatsServerStatusWithOnlineCountAndMaximumCapacity() {
        assertEquals(
                "Server | 3/40 players online",
                ServerInfoFormatter.formatServerStatus(
                        "Server | {online}/{max} players online", 3, 40));
    }

    @Test
    void formatsPlayerListForEmptySingleAndMultiplePlayers() {
        String template = "Online ({online}/{max}): {players}";

        assertEquals(
                "Online (0/40): None",
                ServerInfoFormatter.formatPlayerList(template, 0, 40, List.of()));
        assertEquals(
                "Online (1/40): Alex",
                ServerInfoFormatter.formatPlayerList(template, 1, 40, List.of("Alex")));
        assertEquals(
                "Online (2/40): Alex, Sam",
                ServerInfoFormatter.formatPlayerList(
                        template, 2, 40, List.of("Alex", "Sam")));
    }

    @Test
    void playerNamesAreSanitizedAndNotReinterpretedAsPlaceholders() {
        assertEquals(
                "Players: {max}, Alex",
                ServerInfoFormatter.formatPlayerList(
                        "Players: {players}", 2, 40, List.of("{max}", "§aAlex")));
    }
}
