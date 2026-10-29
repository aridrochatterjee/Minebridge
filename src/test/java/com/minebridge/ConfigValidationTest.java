package com.minebridge;

import com.minebridge.config.ConfigManager;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

        var phaseSixErrors =
                ConfigManager.validateFeatureToggles(true, true, "true", null);
        assertEquals(2, phaseSixErrors.size());
        assertTrue(phaseSixErrors.get(0).contains("features.server-status"));
        assertTrue(phaseSixErrors.get(1).contains("features.player-list"));
    }

    @Test
    void rejectsMissingNonStringAndOversizedTemplates() {
        var errors = ConfigManager.validateMessageTemplates(Map.of(
                "messages.minecraft-to-discord", "x".repeat(2001),
                "messages.discord-to-minecraft", 12,
                "messages.player-join", "Joined",
                "messages.player-leave", "Left",
                "messages.server-status", "{online}/{max}",
                "messages.player-list", "{players}"));

        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains("messages.minecraft-to-discord"));
        assertTrue(errors.get(1).contains("messages.discord-to-minecraft"));
    }

    @Test
    void acceptsOnlyThePlaceholdersAllowedForEachTemplate() {
        assertTrue(ConfigManager.validateMessageTemplates(validTemplates()).isEmpty());

        Map<String, Object> templates = new HashMap<>(validTemplates());
        templates.put("messages.player-join", "{player} {message}");
        templates.put("messages.server-status", "{players}");
        templates.put("messages.player-list", "{timestamp}");
        String untrustedPlaceholder = "private-template-secret";
        templates.put("messages.discord-to-minecraft", "{" + untrustedPlaceholder + "}");

        var errors = ConfigManager.validateMessageTemplates(templates);

        assertEquals(4, errors.size());
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("messages.player-join")
                        && error.contains("unsupported placeholder")));
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("messages.server-status")
                        && error.contains("unsupported placeholder")));
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("messages.player-list")
                        && error.contains("unsupported placeholder")));
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("messages.discord-to-minecraft")
                        && error.contains("unsupported placeholder")));
        assertFalse(errors.toString().contains(untrustedPlaceholder));
    }

    @Test
    void rejectsMalformedBracesEmptyTemplatesAndOversizedTemplates() {
        Map<String, Object> templates = new HashMap<>(validTemplates());
        templates.put("messages.minecraft-to-discord", "prefix {player");
        templates.put("messages.discord-to-minecraft", "}");
        templates.put("messages.player-join", "  \t");
        templates.put("messages.player-leave", "x".repeat(2001));
        templates.put("messages.server-status", "{online} {max");
        templates.put("messages.player-list", "{players}}");

        var errors = ConfigManager.validateMessageTemplates(templates);

        assertEquals(6, errors.size());
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("messages.minecraft-to-discord")
                        && error.contains("malformed")));
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("messages.discord-to-minecraft")
                        && error.contains("malformed")));
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("messages.player-join")
                        && error.contains("empty")));
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("messages.player-leave")
                        && error.contains("2000-character")));
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("messages.server-status")
                        && error.contains("malformed")));
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("messages.player-list")
                        && error.contains("malformed")));
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
        assertTrue(candidate.settings().serverStatusEnabled());
        assertTrue(candidate.settings().playerListEnabled());
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

    @Test
    void invalidTemplateCandidateCannotReplaceCommittedConfiguration() {
        ConfigManager manager = new ConfigManager(null);
        var valid = manager.candidateFrom(validConfiguration(), null);
        manager.commit(valid);

        YamlConfiguration invalidConfiguration = validConfiguration();
        invalidConfiguration.set("messages.player-join", "Joined as {username}");
        invalidConfiguration.set("features.player-list", "enabled");
        var invalid = manager.candidateFrom(invalidConfiguration, null);

        assertFalse(invalid.valid());
        assertTrue(invalid.errors().stream()
                .anyMatch(error -> error.contains("messages.player-join")));
        assertTrue(invalid.errors().stream()
                .anyMatch(error -> error.contains("features.player-list")));
        assertThrows(IllegalArgumentException.class, () -> manager.commit(invalid));
        assertEquals(
                valid.settings().messages(),
                manager.currentSettings().messages());
        assertEquals("configured-test-token", manager.currentSettings().botToken());
        assertTrue(manager.currentSettings().serverStatusEnabled());
        assertTrue(manager.currentSettings().playerListEnabled());
    }

    @Test
    void legacyConfigurationUsesBundledPhaseSixDefaults() throws Exception {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream =
                     ConfigValidationTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(stream);
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        YamlConfiguration legacy = validConfiguration();
        legacy.set("features.server-status", null);
        legacy.set("features.player-list", null);
        legacy.set("messages.server-status", null);
        legacy.set("messages.player-list", null);
        legacy.setDefaults(defaults);

        var candidate = new ConfigManager(null).candidateFrom(legacy, null);

        assertTrue(candidate.valid(), candidate.errors().toString());
        assertTrue(candidate.settings().serverStatusEnabled());
        assertTrue(candidate.settings().playerListEnabled());
        assertEquals(
                "&7Server &8| &a{online}&7/&f{max} players online",
                candidate.settings().messages().get("messages.server-status"));
        assertEquals(
                "&7Online players (&f{online}&7/&f{max}&7): {players}",
                candidate.settings().messages().get("messages.player-list"));
    }

    @Test
    void originalSettingsConstructorRemainsAvailableWithEnabledPhaseSixFeatures() {
        Map<String, String> messages = Map.of("messages.player-join", "Joined");

        ConfigManager.Settings settings = new ConfigManager.Settings(
                "test-token", "12345678901234567", true, true, messages);

        assertEquals(messages, settings.messages());
        assertTrue(settings.serverStatusEnabled());
        assertTrue(settings.playerListEnabled());
    }

    private static Map<String, Object> validTemplates() {
        return Map.of(
                "messages.minecraft-to-discord", "{player}: {message} {timestamp}",
                "messages.discord-to-minecraft",
                "{player} {display_name} {username} {message} {timestamp}",
                "messages.player-join", "{player} {timestamp}",
                "messages.player-leave", "{player} {timestamp}",
                "messages.server-status", "{online}/{max}",
                "messages.player-list", "{online}/{max}: {players}");
    }

    private static YamlConfiguration validConfiguration() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("discord.bot-token", "configured-test-token");
        configuration.set("discord.channel-id", "12345678901234567");
        configuration.set("features.chat-relay", true);
        configuration.set("features.join-leave-notifications", true);
        configuration.set("features.server-status", true);
        configuration.set("features.player-list", true);
        Map<String, String> messages = Map.of(
                "messages.minecraft-to-discord", "**{player}**: {message}",
                "messages.discord-to-minecraft", "&9[Discord] &f{player}: {message}",
                "messages.player-join", "🟢 **{player}** joined the server.",
                "messages.player-leave", "🔴 **{player}** left the server.",
                "messages.server-status", "&7Server &8| &a{online}&7/&f{max} players online",
                "messages.player-list",
                "&7Online players (&f{online}&7/&f{max}&7): {players}");
        messages.forEach(configuration::set);
        return configuration;
    }
}
