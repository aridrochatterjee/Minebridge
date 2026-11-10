package com.minebridge.achievement;

import org.bukkit.advancement.AdvancementDisplayType;

import java.util.Locale;

public final class AdvancementClassifier {

    private AdvancementClassifier() {
    }

    public static AdvancementCategory classify(String advancementKey) {
        if (advancementKey == null || advancementKey.isBlank()) {
            return AdvancementCategory.OTHER;
        }
        int separator = advancementKey.indexOf(':');
        String path = separator >= 0
                ? advancementKey.substring(separator + 1) : advancementKey;
        int slash = path.indexOf('/');
        String root = (slash >= 0 ? path.substring(0, slash) : path)
                .toLowerCase(Locale.ROOT);
        for (AdvancementCategory category : AdvancementCategory.values()) {
            if (category.key().equals(root)) {
                return category;
            }
        }
        return AdvancementCategory.OTHER;
    }

    public static boolean isChallenge(AdvancementDisplayType displayType) {
        return displayType == AdvancementDisplayType.CHALLENGE;
    }

    public static AdvancementContext createContext(
            String player,
            String advancementKey,
            String title,
            String description,
            AdvancementDisplayType displayType) {
        return new AdvancementContext(
                player,
                title,
                description,
                classify(advancementKey),
                isChallenge(displayType));
    }
}
