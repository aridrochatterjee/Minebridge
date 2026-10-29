package com.minebridge;

import com.minebridge.config.ConfigManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
