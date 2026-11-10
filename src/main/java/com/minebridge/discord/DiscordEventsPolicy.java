package com.minebridge.discord;

import com.minebridge.achievement.AdvancementCategory;
import com.minebridge.death.DeathCategory;
import com.minebridge.config.ConfigManager.DiscordEventsSettings;

public final class DiscordEventsPolicy {

    private DiscordEventsPolicy() {
    }

    public static boolean playerEventEnabled(
            DiscordEventsSettings settings, boolean join, boolean firstJoin) {
        return settings.enabled()
                && settings.embedsEnabled()
                && settings.playerEventsEnabled()
                && (firstJoin
                        ? settings.firstJoinEnabled() || settings.joinEnabled()
                        : join ? settings.joinEnabled() : settings.leaveEnabled());
    }

    public static boolean achievementEnabled(
            DiscordEventsSettings settings,
            AdvancementCategory category,
            boolean challenge) {
        return settings.enabled()
                && settings.embedsEnabled()
                && settings.achievementsEnabled()
                && settings.achievementCategories().getOrDefault(category.key(), true)
                && (!challenge || settings.challengesEnabled());
    }

    public static boolean deathEnabled(
            DiscordEventsSettings settings, DeathCategory category) {
        boolean categoryEnabled = switch (category) {
            case PLAYER_KILL -> settings.playerKillsEnabled();
            case MOB_KILL -> settings.mobKillsEnabled();
            default -> settings.environmentalDeathsEnabled();
        };
        return settings.enabled()
                && settings.embedsEnabled()
                && settings.deathsEnabled()
                && categoryEnabled;
    }
}
