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

class ConfigManagerDeathSettingsTest {

    @Test
    void loadsDeathSettingsAndAllowsIndependentCategoryDisabling() throws Exception {
        YamlConfiguration configuration = bundledDefaults();
        configuration.set("discord.bot-token", "test-token");
        configuration.set("discord.channel-id", "12345678901234567");
        configuration.set("death-messages.categories.fall", false);
        configuration.set("death-messages.minecraft.enabled", false);
        configuration.set("death-messages.discord.channel-id", "12345678901234568");

        var candidate = new ConfigManager(null).candidateFrom(configuration, null);

        assertTrue(candidate.valid(), candidate.errors().toString());
        var deathSettings = candidate.settings().deathMessages();
        assertFalse(deathSettings.categories().get("fall"));
        assertFalse(deathSettings.minecraftEnabled());
        assertEquals("12345678901234568", deathSettings.discordChannelId());
        assertEquals(0xD32F2F, deathSettings.embedColor());
        assertTrue(deathSettings.embedDescription().contains("Category: {category}"));
        assertTrue(deathSettings.embedDescription().contains("Cause: {cause}"));
    }

    @Test
    void rejectsInvalidDeathToggleChannelAndTemplatePlaceholders() throws Exception {
        YamlConfiguration configuration = bundledDefaults();
        configuration.set("discord.bot-token", "test-token");
        configuration.set("discord.channel-id", "12345678901234567");
        configuration.set("death-messages.categories.fall", "yes");
        configuration.set("death-messages.discord.channel-id", "not-a-channel");
        configuration.set("death-messages.messages.other", "{unknown}");

        var candidate = new ConfigManager(null).candidateFrom(configuration, null);

        assertFalse(candidate.valid());
        assertTrue(candidate.errors().stream().anyMatch(
                error -> error.contains("death-messages.categories.fall")));
        assertTrue(candidate.errors().stream().anyMatch(
                error -> error.contains("death-messages.discord.channel-id")));
        assertTrue(candidate.errors().stream().anyMatch(
                error -> error.contains("death-messages.messages.other")
                        && error.contains("unsupported placeholder")));
    }

    @Test
    void rejectsNonStringEmbedColor() throws Exception {
        YamlConfiguration configuration = bundledDefaults();
        configuration.set("discord.bot-token", "test-token");
        configuration.set("discord.channel-id", "12345678901234567");
        configuration.set("death-messages.discord.color", 123456);

        var candidate = new ConfigManager(null).candidateFrom(configuration, null);

        assertFalse(candidate.valid());
        assertTrue(candidate.errors().stream().anyMatch(
                error -> error.contains("death-messages.discord.color")));
    }

    @Test
    void rejectsMissingAndMalformedDeathTemplates() {
        Map<String, Object> templates = new HashMap<>();
        for (String category : new String[] {
                "player-kill", "mob-kill", "fall", "lava", "fire",
                "drowning", "void", "explosion", "environmental", "other"}) {
            templates.put(category, "{victim} {cause}");
        }
        templates.put("other", "{victim");
        templates.put("discord.title", " ");
        templates.put("discord.description", "{message} {invalid}");

        var errors = ConfigManager.validateDeathMessageTemplates(templates);

        assertEquals(3, errors.size());
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("messages.other") && error.contains("malformed")));
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("discord.title") && error.contains("empty")));
        assertTrue(errors.stream().anyMatch(error ->
                error.contains("discord.description") && error.contains("unsupported")));
    }

    private static YamlConfiguration bundledDefaults() throws Exception {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream =
                     ConfigManagerDeathSettingsTest.class.getResourceAsStream("/config.yml")) {
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return defaults;
    }
}
