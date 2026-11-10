package com.minebridge.death;

import com.minebridge.config.ConfigManager.DeathSettings;

public final class DeathMessagePolicy {

    private DeathMessagePolicy() {
    }

    public static boolean categoryEnabled(DeathSettings settings, DeathCategory category) {
        return settings.categories().getOrDefault(category.key(), true);
    }

    public static boolean shouldBroadcastMinecraft(
            DeathSettings settings, DeathCategory category) {
        return settings.enabled()
                && settings.minecraftEnabled()
                && settings.minecraftBroadcast()
                && categoryEnabled(settings, category);
    }

    public static boolean shouldNotifyDiscord(
            DeathSettings settings, DeathCategory category) {
        return settings.enabled()
                && settings.discordEnabled()
                && categoryEnabled(settings, category);
    }
}
