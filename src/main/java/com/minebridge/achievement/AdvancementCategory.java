package com.minebridge.achievement;

public enum AdvancementCategory {
    STORY("story", "Story"),
    NETHER("nether", "Nether"),
    END("end", "End"),
    ADVENTURE("adventure", "Adventure"),
    HUSBANDRY("husbandry", "Husbandry"),
    OTHER("other", "Other");

    private final String key;
    private final String displayName;

    AdvancementCategory(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }
}
