package com.minebridge.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerAchievementSettingsTest {

    @Test
    void loadsAchievementSettingsAndSupportsDisabledCategoriesAndChallenges() throws Exception {
        YamlConfiguration configuration = bundledDefaults();
        configuration.set("discord.bot-token", "test-token");
        configuration.set("discord.channel-id", "12345678901234567");
        configuration.set("achievements.categories.end", false);
        configuration.set("achievements.challenges.enabled", false);
        configuration.set("achievements.discord.channel-id", "12345678901234568");

        var candidate = new ConfigManager(null).candidateFrom(configuration, null);

        assertTrue(candidate.valid(), candidate.errors().toString());
        var settings = candidate.settings().achievements();
        assertFalse(settings.categories().get("end"));
        assertFalse(settings.challengesEnabled());
        assertEquals("12345678901234568", settings.discordChannelId());
        assertEquals(0x43A047, settings.embedColor());
        assertEquals("&a✦ {player} has made the advancement [{title}]!",
                settings.messageTemplate());
    }

    @Test
    void rejectsMalformedAchievementToggleChannelAndTemplates() throws Exception {
        YamlConfiguration configuration = bundledDefaults();
        configuration.set("discord.bot-token", "test-token");
        configuration.set("discord.channel-id", "12345678901234567");
        configuration.set("achievements.categories.story", "yes");
        configuration.set("achievements.discord.channel-id", "invalid");
        configuration.set("achievements.messages.normal", "{unknown}");

        var candidate = new ConfigManager(null).candidateFrom(configuration, null);

        assertFalse(candidate.valid());
        assertTrue(candidate.errors().stream().anyMatch(
                error -> error.contains("achievements.categories.story")));
        assertTrue(candidate.errors().stream().anyMatch(
                error -> error.contains("achievements.discord.channel-id")));
        assertTrue(candidate.errors().stream().anyMatch(
                error -> error.contains("achievements.messages.normal")
                        && error.contains("unsupported placeholder")));
    }

    @Test
    void validatesSupportedAchievementTemplatePlaceholders() {
        Map<String, Object> templates = new HashMap<>(Map.of(
                "normal", "{player} {title} {description} {category} {challenge}",
                "challenge", "{player} {title} {category}",
                "discord.title", "{challenge}: {title}",
                "discord.description", "{player}: {description} ({category})"));
        assertTrue(ConfigManager.validateAchievementTemplates(templates).isEmpty());
        templates.put("discord.description", "{player");
        assertTrue(ConfigManager.validateAchievementTemplates(templates).stream()
                .anyMatch(error -> error.contains("discord.description")
                        && error.contains("malformed")));
    }

    private static YamlConfiguration bundledDefaults() throws Exception {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream =
                     ConfigManagerAchievementSettingsTest.class.getResourceAsStream("/config.yml")) {
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return defaults;
    }
}
