package com.minebridge.achievement;

import com.minebridge.config.ConfigManager.AchievementSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancementAnnouncementPolicyTest {

    @Test
    void respectsGlobalCategoryAndChallengeToggles() {
        AdvancementContext story = new AdvancementContext(
                "Alex", "Stone Age", "", AdvancementCategory.STORY, false);
        AdvancementContext challenge = new AdvancementContext(
                "Alex", "Challenge", "", AdvancementCategory.NETHER, true);

        assertTrue(AdvancementAnnouncementPolicy.enabled(
                settings(true, true, Map.of()), story));
        assertFalse(AdvancementAnnouncementPolicy.enabled(
                settings(false, true, Map.of()), story));
        assertFalse(AdvancementAnnouncementPolicy.enabled(
                settings(true, false, Map.of()), challenge));
        assertFalse(AdvancementAnnouncementPolicy.enabled(
                settings(true, true, Map.of("story", false)), story));
    }

    private static AchievementSettings settings(
            boolean enabled, boolean challengesEnabled, Map<String, Boolean> categories) {
        return new AchievementSettings(
                enabled, true, true, challengesEnabled, "",
                "{title}", "{title}", "{title}", "{description}",
                "{title}", "{description}",
                0x43A047, categories);
    }
}
