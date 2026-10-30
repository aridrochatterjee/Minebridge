package com.minebridge.config;

import org.junit.jupiter.api.Test;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTokenPrecedenceTest {

    @Test
    void environmentTokenTakesPrecedenceOverBlankConfiguredToken() {
        assertEquals("env-token",
                ConfigManager.resolveBotToken("env-token", "configured-token"));
    }

    @Test
    void environmentTokenTakesPrecedenceOverBlankConfiguredTokenTrimmed() {
        assertEquals("env-token",
                ConfigManager.resolveBotToken("   env-token   ", " configured-token "));
    }

    @Test
    void configuredTokenUsedWhenEnvironmentIsBlank() {
        assertEquals("configured-token",
                ConfigManager.resolveBotToken("", "configured-token"));
        assertEquals("configured-token",
                ConfigManager.resolveBotToken(null, "configured-token"));
    }

    @Test
    void bothEmptyResultsInBlank() {
        assertEquals("", ConfigManager.resolveBotToken(null, null));
        assertEquals("", ConfigManager.resolveBotToken("", ""));
    }

    @Test
    void loadedCandidateReportsValidOnlyWhenAtLeastOneTokenIsPresentAndValidChannelIdExists() {
        YamlConfiguration configuration = validConfiguration();
        configuration.set("discord.bot-token", "private-configured-token");
        configuration.set("discord.channel-id", "12345678901234567");

        ConfigManager manager = new ConfigManager(null);
        ConfigManager.Candidate candidate = manager.candidateFrom(configuration, null);

        assertTrue(candidate.valid());
        assertEquals("private-configured-token", candidate.settings().botToken());
    }

    @Test
    void invalidChannelIdRejectsEvenWithValidToken() {
        YamlConfiguration configuration = validConfiguration();
        configuration.set("discord.bot-token", "valid-token");
        configuration.set("discord.channel-id", "bad-channel");

        var candidate = new ConfigManager(null).candidateFrom(configuration, null);

        assertFalse(candidate.valid());
        assertTrue(candidate.errors().stream().anyMatch(error ->
                error.contains("channel-id")));
    }

    @Test
    void realSecretIsNeverEchoedInValidationErrors() {
        String secret = "my-actual-private-secret-value";
        List<String> errors = ConfigManager.validateConfiguration(secret, "12345678901234567");

        assertFalse(errors.toString().contains(secret),
                "the validation errors must never echo the secret value");
    }

    private static YamlConfiguration validConfiguration() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("discord.bot-token", "configured-test-token");
        configuration.set("discord.channel-id", "12345678901234567");
        configuration.set("features.chat-relay", true);
        configuration.set("features.join-leave-notifications", true);
        configuration.set("features.server-status", true);
        configuration.set("features.player-list", true);
        Map.of(
                "messages.minecraft-to-discord", "{player}: {message}",
                "messages.discord-to-minecraft", "{player}: {message}",
                "messages.player-join", "{player}",
                "messages.player-leave", "{player}",
                "messages.server-status", "{online}/{max}",
                "messages.player-list", "{online}/{max}: {players}")
                .forEach(configuration::set);
        return configuration;
    }
}
