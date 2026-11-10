package com.minebridge.achievement;

import com.minebridge.config.ConfigManager.AchievementSettings;

public final class AdvancementAnnouncementPolicy {

    private AdvancementAnnouncementPolicy() {
    }

    public static boolean enabled(AchievementSettings settings, AdvancementContext context) {
        return settings.enabled()
                && (!context.challenge() || settings.challengesEnabled())
                && settings.categories().getOrDefault(context.category().key(), true);
    }
}
