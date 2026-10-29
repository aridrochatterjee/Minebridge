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
}
