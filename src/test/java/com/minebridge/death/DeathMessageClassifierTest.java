package com.minebridge.death;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeathMessageClassifierTest {

    @Test
    void playerKillTakesPrecedenceOverTheDamageCause() {
        assertEquals(
                DeathCategory.PLAYER_KILL,
                DeathMessageClassifier.classify(DamageCause.FALL, true, false));
    }

    @Test
    void mobKillIsClassifiedSeparatelyFromEnvironmentalDamage() {
        assertEquals(
                DeathCategory.MOB_KILL,
                DeathMessageClassifier.classify(DamageCause.ENTITY_ATTACK, false, true));
    }

    @Test
    void classifiesEnvironmentalCauses() {
        assertEquals(DeathCategory.FALL, DeathMessageClassifier.classify(
                DamageCause.FALL, false, false));
        assertEquals(DeathCategory.LAVA, DeathMessageClassifier.classify(
                DamageCause.LAVA, false, false));
        assertEquals(DeathCategory.FIRE, DeathMessageClassifier.classify(
                DamageCause.FIRE_TICK, false, false));
        assertEquals(DeathCategory.DROWNING, DeathMessageClassifier.classify(
                DamageCause.DROWNING, false, false));
        assertEquals(DeathCategory.VOID, DeathMessageClassifier.classify(
                DamageCause.VOID, false, false));
        assertEquals(DeathCategory.EXPLOSION, DeathMessageClassifier.classify(
                DamageCause.ENTITY_EXPLOSION, false, false));
        assertEquals(DeathCategory.ENVIRONMENTAL, DeathMessageClassifier.classify(
                DamageCause.LIGHTNING, false, false));
        assertEquals(DeathCategory.ENVIRONMENTAL, DeathMessageClassifier.classify(
                DamageCause.POISON, false, false));
    }

    @Test
    void unknownAndMissingDamageCausesUseOtherCategory() {
        assertEquals(
                DeathCategory.OTHER,
                DeathMessageClassifier.classify(DamageCause.CUSTOM, false, false));
        assertEquals(
                DeathCategory.OTHER,
                DeathMessageClassifier.classify(null, false, false));
    }
}
