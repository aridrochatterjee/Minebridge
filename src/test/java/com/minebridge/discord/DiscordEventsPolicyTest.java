package com.minebridge.discord;

import com.minebridge.achievement.AdvancementCategory;
import com.minebridge.config.ConfigManager.DiscordEventsSettings;
import com.minebridge.death.DeathCategory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordEventsPolicyTest {

    @Test
    void independentlyDisablesJoinLeaveFirstJoinAndChallengeAnnouncements() {
        DiscordEventsSettings settings = settings(
                true, true, true, false, false, false, true, true, true,
                Map.of("story", false));

        assertTrue(DiscordEventsPolicy.playerEventEnabled(settings, true, false));
        assertFalse(DiscordEventsPolicy.playerEventEnabled(settings, false, false));
        assertTrue(DiscordEventsPolicy.playerEventEnabled(settings, true, true));
        assertTrue(DiscordEventsPolicy.achievementEnabled(
                settings, AdvancementCategory.NETHER, false));
        assertFalse(DiscordEventsPolicy.achievementEnabled(
                settings, AdvancementCategory.STORY, false));
        assertFalse(DiscordEventsPolicy.achievementEnabled(
                settings, AdvancementCategory.NETHER, true));
    }

    @Test
    void appliesDeathCategoryTogglesAndGlobalEmbedSwitches() {
        DiscordEventsSettings settings = settings(
                true, true, true, true, true, false, true, false, true, Map.of());

        assertTrue(DiscordEventsPolicy.deathEnabled(settings, DeathCategory.PLAYER_KILL));
        assertFalse(DiscordEventsPolicy.deathEnabled(settings, DeathCategory.MOB_KILL));
        assertTrue(DiscordEventsPolicy.deathEnabled(settings, DeathCategory.FALL));
        assertFalse(DiscordEventsPolicy.achievementEnabled(
                settings(false, true, true, true, true, true, true, true, true, Map.of()),
                AdvancementCategory.NETHER,
                false));
    }

    private static DiscordEventsSettings settings(
            boolean enabled,
            boolean embeds,
            boolean join,
            boolean leave,
            boolean firstJoin,
            boolean challenges,
            boolean playerKills,
            boolean mobKills,
            boolean environmentalDeaths,
            Map<String, Boolean> categories) {
        return new DiscordEventsSettings(
                enabled, embeds, "MineBridge | Minecraft Events",
                true, "", join, leave, firstJoin,
                true, "", categories, challenges,
                true, "", playerKills, mobKills, environmentalDeaths);
    }
}
