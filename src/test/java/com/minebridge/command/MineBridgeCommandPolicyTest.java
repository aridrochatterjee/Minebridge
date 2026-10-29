package com.minebridge.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineBridgeCommandPolicyTest {

    @Test
    void hidesReloadFromUsersWithoutTheAdminPermission() {
        assertEquals(
                java.util.List.of("help", "status"),
                MineBridgeCommandPolicy.visibleCommands(false));
        assertEquals(
                java.util.List.of("help", "status", "reload"),
                MineBridgeCommandPolicy.visibleCommands(true));
        assertFalse(MineBridgeCommandPolicy.canExecute("reload", false));
        assertFalse(MineBridgeCommandPolicy.canExecute("RELOAD", false));
        assertTrue(MineBridgeCommandPolicy.canExecute("reload", true));
        assertTrue(MineBridgeCommandPolicy.canExecute("status", false));
    }

    @Test
    void exposesServerCommandsOnlyWhenEnabledAndKeepsReloadAdminOnly() {
        assertEquals(
                java.util.List.of("help", "status", "server", "players"),
                MineBridgeCommandPolicy.visibleCommands(false, true, true));
        assertEquals(
                java.util.List.of("help", "status", "players", "reload"),
                MineBridgeCommandPolicy.visibleCommands(true, false, true));
        assertEquals(
                java.util.List.of("help", "status"),
                MineBridgeCommandPolicy.visibleCommands(false, false, false));

        assertTrue(MineBridgeCommandPolicy.canExecute("server", false, true, true));
        assertTrue(MineBridgeCommandPolicy.canExecute("players", false, true, true));
        assertFalse(MineBridgeCommandPolicy.canExecute("server", false, false, true));
        assertFalse(MineBridgeCommandPolicy.canExecute("players", false, true, false));
        assertFalse(MineBridgeCommandPolicy.canExecute("reload", false, true, true));
        assertTrue(MineBridgeCommandPolicy.canExecute("reload", true, true, true));
        assertTrue(MineBridgeCommandPolicy.canExecute("status", false, false, false));
    }
}
