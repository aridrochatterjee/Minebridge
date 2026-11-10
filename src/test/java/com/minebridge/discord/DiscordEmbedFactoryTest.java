package com.minebridge.discord;

import com.minebridge.achievement.AdvancementCategory;
import com.minebridge.achievement.AdvancementContext;
import com.minebridge.death.DeathCategory;
import com.minebridge.death.DeathContext;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordEmbedFactoryTest {

    @Test
    void createsJoinLeaveAndFirstJoinEmbeds() {
        MessageEmbed join = DiscordEmbedFactory.playerJoined(
                "Alex", "Alex joined the Minecraft server.", false,
                DiscordEmbedFactory.DEFAULT_FOOTER);
        assertEquals("PLAYER JOINED", join.getTitle());
        assertEquals("Alex joined the Minecraft server.", join.getDescription());
        assertEquals(DiscordEmbedFactory.JOIN_COLOR, join.getColor().getRGB() & 0xFFFFFF);
        assertEquals("Online", join.getFields().get(1).getValue());

        MessageEmbed leave = DiscordEmbedFactory.playerLeft(
                "Alex", "Alex left the Minecraft server.", DiscordEmbedFactory.DEFAULT_FOOTER);
        assertEquals("PLAYER LEFT", leave.getTitle());
        assertEquals("Offline", leave.getFields().get(1).getValue());
        assertEquals(DiscordEmbedFactory.LEAVE_COLOR, leave.getColor().getRGB() & 0xFFFFFF);

        MessageEmbed firstJoin = DiscordEmbedFactory.playerJoined(
                "Alex", "Alex joined for the first time!", true,
                DiscordEmbedFactory.DEFAULT_FOOTER);
        assertEquals("WELCOME TO THE SERVER", firstJoin.getTitle());
        assertEquals(
                DiscordEmbedFactory.FIRST_JOIN_COLOR,
                firstJoin.getColor().getRGB() & 0xFFFFFF);
    }

    @Test
    void createsAdvancementAndChallengeEmbedsWithTheExpectedDetails() {
        AdvancementContext regular = new AdvancementContext(
                "Aridro", "Stone Age", "Mine stone", AdvancementCategory.STORY, false);
        MessageEmbed regularEmbed = DiscordEmbedFactory.advancement(
                regular, "ADVANCEMENT UNLOCKED",
                "Aridro has unlocked Stone Age.", DiscordEmbedFactory.ADVANCEMENT_COLOR,
                DiscordEmbedFactory.DEFAULT_FOOTER);
        assertEquals("ADVANCEMENT UNLOCKED", regularEmbed.getTitle());
        assertEquals("Story", regularEmbed.getFields().get(2).getValue());
        assertEquals(3, regularEmbed.getFields().size());
        assertEquals(DiscordEmbedFactory.ADVANCEMENT_COLOR,
                regularEmbed.getColor().getRGB() & 0xFFFFFF);

        AdvancementContext challenge = new AdvancementContext(
                "Aridro", "Cover Me in Debris", "", AdvancementCategory.NETHER, true);
        MessageEmbed challengeEmbed = DiscordEmbedFactory.advancement(
                "CHALLENGE COMPLETED",
                "Aridro has completed Cover Me in Debris!",
                challenge.player(),
                challenge.title(),
                challenge.category().displayName(),
                challenge.challenge(),
                DiscordEmbedFactory.CHALLENGE_COLOR,
                DiscordEmbedFactory.DEFAULT_FOOTER,
                Instant.now());
        assertEquals("CHALLENGE COMPLETED", challengeEmbed.getTitle());
        assertEquals("Challenge", challengeEmbed.getFields().get(3).getValue());
    }

    @Test
    void createsDeathEmbedsAndOmitsMissingKiller() {
        DeathContext pvp = new DeathContext(
                "Alex", "Zombie", "entity attack", DeathCategory.MOB_KILL);
        MessageEmbed pvpEmbed = DiscordEmbedFactory.death(
                "PLAYER DEATH",
                "Alex was slain by Zombie.",
                pvp,
                DiscordEmbedFactory.DEATH_COLOR,
                DiscordEmbedFactory.DEFAULT_FOOTER);
        assertEquals("PLAYER DEATH", pvpEmbed.getTitle());
        assertEquals("Zombie", pvpEmbed.getFields().get(1).getValue());
        assertEquals(DiscordEmbedFactory.DEATH_COLOR,
                pvpEmbed.getColor().getRGB() & 0xFFFFFF);

        DeathContext environmental = new DeathContext(
                "Alex", "", "fall damage", DeathCategory.FALL);
        MessageEmbed environmentEmbed = DiscordEmbedFactory.death(
                "PLAYER DEATH", "Alex fell to their death.", environmental,
                DiscordEmbedFactory.DEATH_COLOR, DiscordEmbedFactory.DEFAULT_FOOTER);
        assertEquals(2, environmentEmbed.getFields().size());
        assertTrue(environmentEmbed.getFields().stream()
                .noneMatch(field -> field.getName().equals("Killer")));
        assertNotNull(environmentEmbed.getTimestamp());
        assertEquals(DiscordEmbedFactory.DEFAULT_FOOTER,
                environmentEmbed.getFooter().getText());
    }

    @Test
    void escapesDynamicMarkdownAndBuildsEmbedOnlyMessageWithNoAllowedMentions() {
        MessageEmbed embed = DiscordEmbedFactory.playerJoined(
                "@everyone *Alex*",
                DiscordEmbedFactory.discordText("@everyone *Alex* joined."),
                false,
                DiscordEmbedFactory.DEFAULT_FOOTER);
        assertTrue(embed.getFields().get(0).getValue().contains("@\u200Beveryone"));
        assertTrue(embed.getFields().get(0).getValue().contains("\\*Alex\\*"));

        var data = DiscordEmbedFactory.createMessageData(embed);
        assertEquals("", data.getContent());
        assertEquals(1, data.getEmbeds().size());
        assertTrue(data.getAllowedMentions().isEmpty());
    }
}
