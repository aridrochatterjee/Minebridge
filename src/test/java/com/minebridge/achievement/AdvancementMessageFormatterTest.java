package com.minebridge.achievement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancementMessageFormatterTest {

    private final AdvancementContext normal = new AdvancementContext(
            "Aridro",
            "Stone Age",
            "Mine stone with your new pickaxe",
            AdvancementCategory.STORY,
            false);

    @Test
    void formatsMinecraftAdvancementAndChallengeTemplatesWithColor() {
        assertEquals(
                "\u00a7a\u2726 Aridro has made the advancement [Stone Age]!",
                AdvancementMessageFormatter.formatMinecraft(
                        "&a\u2726 {player} has made the advancement [{title}]!", normal));

        AdvancementContext challenge = new AdvancementContext(
                "Aridro", "Cover Me in Debris", "", AdvancementCategory.NETHER, true);
        assertEquals(
                "\u00a76\uD83C\uDFC6 Aridro has completed the challenge [Cover Me in Debris]!",
                AdvancementMessageFormatter.formatMinecraft(
                        "&6\uD83C\uDFC6 {player} has completed the challenge [{title}]!",
                        challenge));
    }

    @Test
    void escapesDiscordMarkdownAndNeutralizesMentions() {
        AdvancementContext unsafe = new AdvancementContext(
                "*Aridro* @everyone",
                "[Stone](https://example.invalid)",
                "Description _text_",
                AdvancementCategory.STORY,
                false);

        assertEquals(
                "\\*Aridro\\* @\u200Beveryone unlocked \\[Stone\\]"
                        + "\\(https://example.invalid\\): Description \\_text\\_",
                AdvancementMessageFormatter.formatDiscord(
                        "{player} unlocked {title}: {description}", unsafe));
    }

    @Test
    void buildsDiscordEmbedWithPlayerAdvancementDescriptionCategoryAndChallenge() {
        var embed = AdvancementMessageFormatter.createEmbed(
                "✦ {challenge} unlocked",
                "{description}",
                normal,
                0x43A047);

        assertEquals("✦ Advancement unlocked", embed.getTitle());
        assertEquals("Mine stone with your new pickaxe", embed.getDescription());
        assertEquals(0x43A047, embed.getColor().getRGB() & 0xFFFFFF);
        assertEquals(4, embed.getFields().size());
        assertEquals("Player", embed.getFields().get(0).getName());
        assertEquals("Aridro", embed.getFields().get(0).getValue());
        assertEquals("Advancement", embed.getFields().get(3).getValue());
        assertTrue(embed.getFields().stream()
                .anyMatch(field -> field.getName().equals("Category")
                        && field.getValue().equals("Story")));

        AdvancementContext challenge = new AdvancementContext(
                "Aridro", "Cover Me in Debris", "", AdvancementCategory.NETHER, true);
        var challengeEmbed = AdvancementMessageFormatter.createEmbed(
                "🏆 {challenge} complete",
                "{description}",
                challenge,
                0x43A047);
        assertEquals("🏆 Challenge complete", challengeEmbed.getTitle());
        assertEquals("Challenge", challengeEmbed.getFields().get(3).getValue());
    }
}
