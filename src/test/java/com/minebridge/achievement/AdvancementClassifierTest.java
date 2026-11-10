package com.minebridge.achievement;

import org.bukkit.advancement.AdvancementDisplayType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancementClassifierTest {

    @Test
    void classifiesVanillaAdvancementRootsAndUnknownKeys() {
        assertEquals(AdvancementCategory.STORY,
                AdvancementClassifier.classify("minecraft:story/mine_stone"));
        assertEquals(AdvancementCategory.NETHER,
                AdvancementClassifier.classify("minecraft:nether/return_to_sender"));
        assertEquals(AdvancementCategory.END,
                AdvancementClassifier.classify("minecraft:end/kill_dragon"));
        assertEquals(AdvancementCategory.ADVENTURE,
                AdvancementClassifier.classify("minecraft:adventure/kill_a_mob"));
        assertEquals(AdvancementCategory.HUSBANDRY,
                AdvancementClassifier.classify("minecraft:husbandry/plant_seed"));
        assertEquals(AdvancementCategory.OTHER,
                AdvancementClassifier.classify("custom:quests/first"));
        assertEquals(AdvancementCategory.OTHER, AdvancementClassifier.classify(null));
    }

    @Test
    void recognizesChallengeDisplayType() {
        assertTrue(AdvancementClassifier.isChallenge(AdvancementDisplayType.CHALLENGE));
        assertFalse(AdvancementClassifier.isChallenge(AdvancementDisplayType.TASK));
        assertFalse(AdvancementClassifier.isChallenge(AdvancementDisplayType.GOAL));
        assertFalse(AdvancementClassifier.isChallenge(null));
    }

    @Test
    void extractsPlayerTitleDescriptionCategoryAndChallengeFromCompletionData() {
        AdvancementContext context = AdvancementClassifier.createContext(
                "Aridro",
                "minecraft:nether/obtain_ancient_debris",
                "Cover Me in Debris",
                "Obtain Netherite armor",
                AdvancementDisplayType.CHALLENGE);

        assertEquals("Aridro", context.player());
        assertEquals("Cover Me in Debris", context.title());
        assertEquals("Obtain Netherite armor", context.description());
        assertEquals(AdvancementCategory.NETHER, context.category());
        assertTrue(context.challenge());
    }
}
