package com.minebridge.death;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

public final class DeathMessageClassifier {

    private DeathMessageClassifier() {
    }

    public static DeathCategory classify(
            DamageCause cause, boolean playerKiller, boolean mobKiller) {
        if (playerKiller) {
            return DeathCategory.PLAYER_KILL;
        }
        if (mobKiller) {
            return DeathCategory.MOB_KILL;
        }
        if (cause == null) {
            return DeathCategory.OTHER;
        }

        return switch (cause) {
            case FALL -> DeathCategory.FALL;
            case LAVA -> DeathCategory.LAVA;
            case FIRE, FIRE_TICK, HOT_FLOOR -> DeathCategory.FIRE;
            case DROWNING -> DeathCategory.DROWNING;
            case VOID -> DeathCategory.VOID;
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> DeathCategory.EXPLOSION;
            case SUFFOCATION, STARVATION, POISON, MAGIC, LIGHTNING,
                    CONTACT, CRAMMING, DRYOUT, FREEZE, THORNS ->
                    DeathCategory.ENVIRONMENTAL;
            default -> DeathCategory.OTHER;
        };
    }

    public static String describe(DamageCause cause) {
        if (cause == null) {
            return "an unknown cause";
        }
        return switch (cause) {
            case FALL -> "fall damage";
            case LAVA -> "lava";
            case FIRE, FIRE_TICK, HOT_FLOOR -> "fire";
            case DROWNING -> "drowning";
            case VOID -> "the void";
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "an explosion";
            case SUFFOCATION -> "suffocation";
            case STARVATION -> "starvation";
            case POISON -> "poison";
            case MAGIC -> "magic";
            case LIGHTNING -> "lightning";
            default -> cause.name().toLowerCase(java.util.Locale.ROOT)
                    .replace('_', ' ');
        };
    }
}
