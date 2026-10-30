package com.minebridge.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineBridgeCommandPolicyEdgeCasesTest {

    @Test
    void reloadCannotExecuteFromNonAdminRegardlessOfServerAndPlayerToggles() {
        assertFalse(MineBridgeCommandPolicy.canExecute(
                "reload", false, true, true));
        assertFalse(MineBridgeCommandPolicy.canExecute(
                "reload", false, false, false));
    }

    @Test
    void emptySubcommandAlwaysAllowsStatus() {
        assertTrue(MineBridgeCommandPolicy.canExecute(
                "", false, false, false));
        assertTrue(MineBridgeCommandPolicy.canExecute("", true, false, false));
    }

    @Test
    void serverCancelledByDisabledFeatureDoesNotAffectPlayers() {
        assertFalse(MineBridgeCommandPolicy.canExecute(
                "server", false, false, true));
        assertTrue(MineBridgeCommandPolicy.canExecute(
                "players", false, true, true));
    }

    @Test
    void playersCancelledByDisabledFeatureDoesNotAffectServer() {
        assertFalse(MineBridgeCommandPolicy.canExecute(
                "players", false, true, false));
        assertFalse(MineBridgeCommandPolicy.canExecute(
                "server", false, false, true));
    }

    @Test
    void visibleCommandsOrderChangesDependOnFeatureFlagsButNotAdminOnlyFlag() {
        List<String> featuresEnabled = List.of("help", "status", "server");
        assertEquals(featuresEnabled,
                MineBridgeCommandPolicy.visibleCommands(false, true, false));

        List<String> playerDisabled = List.of("help", "status", "server", "reload");
        assertEquals(playerDisabled,
                MineBridgeCommandPolicy.visibleCommands(true, true, false));
    }
}
