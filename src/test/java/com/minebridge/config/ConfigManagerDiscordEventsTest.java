package com.minebridge.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerDiscordEventsTest {

    @Test
    void loadsUnifiedEventTogglesFooterAndPerModuleChannels() throws Exception {
        YamlConfiguration configuration = bundledDefaults();
        configuration.set("discord.bot-token", "test-token");
        configuration.set("discord.channel-id", "12345678901234567");
        configuration.set("discord-events.embeds.footer", "MineBridge Events");
        configuration.set("discord-events.player-events.first-join", false);
        configuration.set("discord-events.achievements.nether", false);
        configuration.set("discord-events.deaths.mob-kill", false);
        configuration.set("discord-events.deaths.channel-id", "12345678901234568");

        var candidate = new ConfigManager(null).candidateFrom(configuration, null);

        assertTrue(candidate.valid(), candidate.errors().toString());
        var settings = candidate.settings().discordEvents();
        assertEquals("MineBridge Events", settings.footer());
        assertFalse(settings.firstJoinEnabled());
        assertFalse(settings.achievementCategories().get("nether"));
        assertFalse(settings.mobKillsEnabled());
        assertEquals("12345678901234568", settings.deathsChannelId());
    }

    @Test
    void rejectsMalformedUnifiedEventChannelsAndBooleanSettings() throws Exception {
        YamlConfiguration configuration = bundledDefaults();
        configuration.set("discord.bot-token", "test-token");
        configuration.set("discord.channel-id", "12345678901234567");
        configuration.set("discord-events.player-events.join", "yes");
        configuration.set("discord-events.achievements.channel-id", "bad-channel");

        var candidate = new ConfigManager(null).candidateFrom(configuration, null);

        assertFalse(candidate.valid());
        assertTrue(candidate.errors().stream().anyMatch(
                error -> error.contains("discord-events.player-events.join")));
        assertTrue(candidate.errors().stream().anyMatch(
                error -> error.contains("discord-events.achievements.channel-id")));
    }

    private static YamlConfiguration bundledDefaults() throws Exception {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream =
                     ConfigManagerDiscordEventsTest.class.getResourceAsStream("/config.yml")) {
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return defaults;
    }
}
