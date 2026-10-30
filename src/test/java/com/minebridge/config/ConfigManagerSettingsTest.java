package com.minebridge.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigManagerSettingsTest {

    @Test
    void settingsTakeAnImmutableSnapshotOfMessageTemplates() {
        Map<String, String> templates = new HashMap<>();
        templates.put("messages.player-join", "{player} joined");

        ConfigManager.Settings settings = new ConfigManager.Settings(
                "token", "12345678901234567", true, true, templates);
        templates.put("messages.player-join", "changed externally");

        assertEquals("{player} joined", settings.messages().get("messages.player-join"));
        assertThrows(UnsupportedOperationException.class,
                () -> settings.messages().put("messages.player-leave", "{player} left"));
    }
}
