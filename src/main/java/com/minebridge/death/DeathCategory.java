package com.minebridge.death;

public enum DeathCategory {
    PLAYER_KILL("player-kill", "Player kill"),
    MOB_KILL("mob-kill", "Mob kill"),
    FALL("fall", "Fall"),
    LAVA("lava", "Lava"),
    FIRE("fire", "Fire"),
    DROWNING("drowning", "Drowning"),
    VOID("void", "Void"),
    EXPLOSION("explosion", "Explosion"),
    ENVIRONMENTAL("environmental", "Environmental"),
    OTHER("other", "Other");

    private final String key;
    private final String displayName;

    DeathCategory(String key, String displayName) {
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
