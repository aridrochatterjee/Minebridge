package com.minebridge.discord;

import com.minebridge.achievement.AdvancementContext;
import com.minebridge.death.DeathContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public final class DiscordEmbedFactory {

    public static final String DEFAULT_FOOTER = "MineBridge | Minecraft Events";
    public static final int JOIN_COLOR = 0x43A047;
    public static final int LEAVE_COLOR = 0x757575;
    public static final int FIRST_JOIN_COLOR = 0x8E44AD;
    public static final int ADVANCEMENT_COLOR = 0x2196F3;
    public static final int CHALLENGE_COLOR = 0xF9A825;
    public static final int DEATH_COLOR = 0xD32F2F;

    private DiscordEmbedFactory() {
    }

    public static MessageEmbed playerJoined(
            String player, String description, boolean firstJoin, String footer) {
        return base(
                firstJoin ? "WELCOME TO THE SERVER" : "PLAYER JOINED",
                description,
                firstJoin ? FIRST_JOIN_COLOR : JOIN_COLOR,
                footer,
                Instant.now())
                .addField("Player", limit(discordText(player), 512), true)
                .addField("Status", "Online", true)
                .build();
    }

    public static MessageEmbed playerLeft(String player, String description, String footer) {
        return base("PLAYER LEFT", description, LEAVE_COLOR, footer, Instant.now())
                .addField("Player", discordText(player), true)
                .addField("Status", "Offline", true)
                .build();
    }

    public static MessageEmbed advancement(
            String title,
            String description,
            String player,
            String advancement,
            String category,
            boolean challenge,
            int color,
            String footer,
            Instant timestamp) {
        EmbedBuilder builder = base(title, description, color, footer, timestamp)
                .addField("Player", limit(discordText(player), 512), true)
                .addField("Advancement", limit(discordText(advancement), 512), true)
                .addField("Category", limit(discordText(category), 512), true);
        if (challenge) {
            builder.addField("Type", "Challenge", true);
        }
        return builder.build();
    }

    public static MessageEmbed advancement(
            AdvancementContext context,
            String title,
            String description,
            int color,
            String footer) {
        return advancement(
                title,
                description,
                context.player(),
                context.title(),
                context.category().displayName(),
                context.challenge(),
                color,
                footer,
                Instant.now());
    }

    public static MessageEmbed death(
            String title,
            String description,
            DeathContext context,
            int color,
            String footer) {
        EmbedBuilder builder = base(title, description, color, footer, Instant.now())
                .addField("Victim", limit(discordText(context.victim()), 512), true);
        if (!context.killer().isBlank()) {
            builder.addField("Killer", limit(discordText(context.killer()), 512), true);
        }
        builder.addField("Cause", limit(discordText(context.cause()), 512), true);
        return builder.build();
    }

    public static MessageCreateData createMessageData(MessageEmbed embed) {
        return MessageCreateBuilder.from(MessageCreateData.fromEmbeds(embed))
                .setAllowedMentions(List.of())
                .build();
    }

    public static EmbedBuilder base(
            String title,
            String description,
            int color,
            String footer,
            Instant timestamp) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(limit(safe(title), 256))
                .setDescription(limit(safe(description), 2048))
                .setColor(new Color(color & 0xFFFFFF))
                .setTimestamp(timestamp == null ? Instant.now() : timestamp);
        builder.setFooter(footer == null || footer.isBlank()
                ? DEFAULT_FOOTER : limit(safe(footer), 512));
        return builder;
    }

    public static String discordText(String value) {
        return RelayMessageFormatter.escapeDiscordUserText(value);
    }

    private static String safe(String value) {
        return RelayMessageFormatter.sanitizeText(value);
    }

    private static String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
