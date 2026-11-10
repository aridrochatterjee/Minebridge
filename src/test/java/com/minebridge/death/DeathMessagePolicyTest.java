package com.minebridge.death;

import com.minebridge.config.ConfigManager.DeathSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathMessagePolicyTest {

    @Test
    void respectsCategoryAndIndependentNotificationToggles() {
        DeathSettings settings = settings(
                true, true, true, true, Map.of("fall", false));

        assertTrue(DeathMessagePolicy.shouldBroadcastMinecraft(
                settings, DeathCategory.PLAYER_KILL));
        assertFalse(DeathMessagePolicy.shouldBroadcastMinecraft(
                settings, DeathCategory.FALL));
        assertTrue(DeathMessagePolicy.shouldNotifyDiscord(
                settings, DeathCategory.PLAYER_KILL));
        assertFalse(DeathMessagePolicy.shouldNotifyDiscord(
                settings, DeathCategory.FALL));
    }

    @Test
    void canDisableMinecraftAndDiscordNotificationsSeparately() {
        DeathSettings minecraftDisabled = settings(
                true, false, true, true, Map.of());
        assertFalse(DeathMessagePolicy.shouldBroadcastMinecraft(
                minecraftDisabled, DeathCategory.OTHER));
        assertTrue(DeathMessagePolicy.shouldNotifyDiscord(
                minecraftDisabled, DeathCategory.OTHER));

        DeathSettings discordDisabled = settings(
                true, true, true, false, Map.of());
        assertTrue(DeathMessagePolicy.shouldBroadcastMinecraft(
                discordDisabled, DeathCategory.OTHER));
        assertFalse(DeathMessagePolicy.shouldNotifyDiscord(
                discordDisabled, DeathCategory.OTHER));

        DeathSettings moduleDisabled = settings(
                false, true, true, true, Map.of());
        assertFalse(DeathMessagePolicy.shouldBroadcastMinecraft(
                moduleDisabled, DeathCategory.OTHER));
        assertFalse(DeathMessagePolicy.shouldNotifyDiscord(
                moduleDisabled, DeathCategory.OTHER));
    }

    private static DeathSettings settings(
            boolean enabled,
            boolean minecraftEnabled,
            boolean minecraftBroadcast,
            boolean discordEnabled,
            Map<String, Boolean> categories) {
        return new DeathSettings(
                enabled,
                minecraftEnabled,
                minecraftBroadcast,
                discordEnabled,
                true,
                "",
                "Death",
                "{message}",
                0xD32F2F,
                categories,
                Map.of());
    }
}
