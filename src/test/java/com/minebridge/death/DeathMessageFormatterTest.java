package com.minebridge.death;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeathMessageFormatterTest {

    @Test
    void replacesMinecraftPlaceholdersAndAppliesColorCodes() {
        DeathContext context = new DeathContext(
                "Aridro", "Zombie", "entity attack", DeathCategory.MOB_KILL);

        assertEquals(
                "\u00a7c\u2620 Aridro was slain by Zombie",
                DeathMessageFormatter.formatMinecraft(
                        "&c\u2620 {victim} was slain by {killer}", context));
    }

    @Test
    void insertsPlaceholdersOnceWithoutInterpretingPlayerTextAsColorCodes() {
        DeathContext context = new DeathContext(
                "Name {killer}", "&cZombie", "fall damage", DeathCategory.PLAYER_KILL);

        assertEquals(
                "Name {killer} was killed by Zombie",
                DeathMessageFormatter.formatMinecraft(
                        "{victim} was killed by {killer}", context));
    }

    @Test
    void escapesDiscordMarkdownAndNeutralizesMentionsInDynamicValues() {
        DeathContext context = new DeathContext(
                "*Aridro* @everyone",
                "[Zombie](https://example.invalid)",
                "magic",
                DeathCategory.MOB_KILL);

        assertEquals(
                "\\*Aridro\\* @\u200Beveryone was slain by "
                        + "\\[Zombie\\]\\(https://example.invalid\\)",
                DeathMessageFormatter.formatDiscordMessage(
                        "{victim} was slain by {killer}", context));
    }

    @Test
    void formatsConfiguredEmbedTitleAndDescriptionPlaceholders() {
        DeathContext context = new DeathContext(
                "Alex", "Skeleton", "entity attack", DeathCategory.MOB_KILL);

        assertEquals(
                "Death: Alex",
                DeathMessageFormatter.formatDiscord(
                        "Death: {victim}", context, "Alex was slain by Skeleton"));
        assertEquals(
                "Alex was slain by Skeleton (Mob kill: entity attack)",
                DeathMessageFormatter.formatDiscord(
                        "{message} ({category}: {cause})",
                        context,
                        "Alex was slain by Skeleton"));
    }
}
