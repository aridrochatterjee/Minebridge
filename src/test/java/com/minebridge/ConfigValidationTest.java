package com.minebridge;

import com.minebridge.config.ConfigManager;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidationTest {

    @Test
    void validatesTokenWithoutRequiringOrExposingARealToken() {
        assertFalse(ConfigManager.isValidBotToken(null));
        assertFalse(ConfigManager.isValidBotToken(""));
        assertFalse(ConfigManager.isValidBotToken("PUT_YOUR_BOT_TOKEN_HERE"));
        assertFalse(ConfigManager.isValidBotToken("token with whitespace"));
        assertFalse(ConfigManager.isValidBotToken("x".repeat(513)));
        assertTrue(ConfigManager.isValidBotToken("test-token-value"));
    }

    @Test
    void validatesDiscordSnowflakeChannelIds() {
        assertFalse(ConfigManager.isValidChannelId(null));
        assertFalse(ConfigManager.isValidChannelId(""));
        assertFalse(ConfigManager.isValidChannelId("not-a-channel"));
        assertFalse(ConfigManager.isValidChannelId("1234567890123456"));
        assertTrue(ConfigManager.isValidChannelId("12345678901234567"));
        assertTrue(ConfigManager.isValidChannelId("12345678901234567890"));
    }

    @Test
    void reportsActionableConfigurationErrorsWithoutEchoingTheToken() {
        String suppliedToken = "private-test-token";

        var errors = ConfigManager.validateConfiguration(suppliedToken, "invalid");

        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().contains("discord.channel-id"));
        assertFalse(errors.toString().contains(suppliedToken));
    }

    @Test
    void reportsBothMissingCredentialAndChannelConfiguration() {
        var errors = ConfigManager.validateConfiguration("", "");

        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains("MINEBRIDGE_BOT_TOKEN"));
        assertTrue(errors.get(1).contains("discord.channel-id"));
    }

    @Test
    void nonBlankEnvironmentTokenTakesPrecedenceWithoutEchoingEitherValue() {
        String environmentToken = "private-environment-token";
        String configuredToken = "private-configured-token";

        assertEquals(
                environmentToken,
                ConfigManager.resolveBotToken("  " + environmentToken + "  ", configuredToken));
        assertEquals(
                configuredToken,
                ConfigManager.resolveBotToken(" ", configuredToken));
    }

    @Test
    void rejectsMalformedFeatureToggles() {
        var errors = ConfigManager.validateFeatureToggles("true", null);

        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains("features.chat-relay"));
        assertTrue(errors.get(1).contains("features.join-leave-notifications"));
    }

    @Test
    void rejectsMissingNonStringAndOversizedTemplates() {
        var errors = ConfigManager.validateMessageTemplates(Map.of(
                "messages.minecraft-to-discord", "x".repeat(2001),
                "messages.discord-to-minecraft", 12,
                "messages.player-join", "Joined",
                "messages.player-leave", "Left"));

        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains("messages.minecraft-to-discord"));
        assertTrue(errors.get(1).contains("messages.discord-to-minecraft"));
    }

    @Test
    void resolvesConfigTokenWhenEnvironmentValueIsEmpty() {
        assertEquals("configured-token", ConfigManager.resolveBotToken("", " configured-token "));
        assertEquals("", ConfigManager.resolveBotToken(null, null));
    }

    @Test
    void candidateValidationUsesEnvironmentTokenAndPreservesTheConfigFormat() {
        YamlConfiguration configuration = validConfiguration();
        configuration.set("discord.bot-token", 42);
        ConfigManager manager = new ConfigManager(null);

        var candidate = manager.candidateFrom(configuration, "environment-token");

        assertTrue(candidate.valid());
        assertEquals("environment-token", candidate.settings().botToken());
        assertEquals("12345678901234567", candidate.settings().channelId());
        assertTrue(candidate.settings().chatEnabled());
        assertTrue(candidate.settings().eventsEnabled());
    }

    @Test
    void candidateValidationReportsInvalidTypesAndLengthsWithoutExposingSecrets() {
        YamlConfiguration configuration = validConfiguration();
        String secret = "private-config-token";
        configuration.set("discord.bot-token", secret);
        configuration.set("features.chat-relay", "enabled");
        configuration.set("messages.player-join", "x".repeat(2001));
        ConfigManager manager = new ConfigManager(null);

        var candidate = manager.candidateFrom(configuration, null);

        assertFalse(candidate.valid());
        assertTrue(candidate.errors().stream()
                .anyMatch(error -> error.contains("features.chat-relay")));
        assertTrue(candidate.errors().stream()
                .anyMatch(error -> error.contains("messages.player-join")));
        assertFalse(candidate.errors().toString().contains(secret));
    }

    @Test
    void missingSettingsAndMalformedYamlAreRejected() {
        ConfigManager manager = new ConfigManager(null);
        var missing = manager.candidateFrom(new YamlConfiguration(), null);

        assertFalse(missing.valid());
        assertTrue(missing.errors().stream()
                .anyMatch(error -> error.contains("discord.channel-id")));
        assertTrue(missing.errors().stream()
                .anyMatch(error -> error.contains("features.chat-relay")));
        assertThrows(InvalidConfigurationException.class,
                () -> new YamlConfiguration().loadFromString("discord: ["));
    }

    @Test
    void invalidCandidateCannotReplaceCommittedConfiguration() {
        ConfigManager manager = new ConfigManager(null);
        var valid = manager.candidateFrom(validConfiguration(), null);
        manager.commit(valid);
        var invalid = manager.candidateFrom(new YamlConfiguration(), null);

        assertThrows(IllegalArgumentException.class, () -> manager.commit(invalid));
        assertEquals("configured-test-token", manager.currentSettings().botToken());
        assertEquals("12345678901234567", manager.currentSettings().channelId());
    }

    private static YamlConfiguration validConfiguration() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("discord.bot-token", "configured-test-token");
        configuration.set("discord.channel-id", "12345678901234567");
        configuration.set("features.chat-relay", true);
        configuration.set("features.join-leave-notifications", true);
        Map<String, String> messages = Map.of(
                "messages.minecraft-to-discord", "**{player}**: {message}",
                "messages.discord-to-minecraft", "&9[Discord] &f{player}: {message}",
                "messages.player-join", "🟢 **{player}** joined the server.",
                "messages.player-leave", "🔴 **{player}** left the server.");
        messages.forEach(configuration::set);
        return configuration;
    }
}
