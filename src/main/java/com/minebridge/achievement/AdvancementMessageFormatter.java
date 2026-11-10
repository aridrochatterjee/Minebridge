package com.minebridge.achievement;

import com.minebridge.discord.RelayMessageFormatter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.bukkit.ChatColor;

import java.awt.Color;
import java.util.Map;

public final class AdvancementMessageFormatter {

    private AdvancementMessageFormatter() {
    }

    public static String formatMinecraft(String template, AdvancementContext context) {
        String expanded = replace(template, context, false);
        return ChatColor.translateAlternateColorCodes('&', expanded);
    }

    public static String formatDiscord(String template, AdvancementContext context) {
        return replace(template, context, true);
    }

    public static MessageEmbed createEmbed(
            String titleTemplate,
            String descriptionTemplate,
            AdvancementContext context,
            int color) {
        String title = limit(formatDiscord(titleTemplate, context), 256);
        String description = limit(formatDiscord(descriptionTemplate, context), 4096);
        if (title.isBlank()) {
            title = "✦ Advancement unlocked";
        }
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description.isBlank() ? "\u200b" : description)
                .setColor(new Color(color))
                .addField("Player", limit(discordText(context.player()), 1024), true)
                .addField("Advancement", limit(discordText(context.title()), 1024), true)
                .addField("Category", context.category().displayName(), true)
                .addField("Type", context.typeLabel(), true)
                .build();
    }

    private static String replace(
            String template, AdvancementContext context, boolean escapeDiscordText) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Map<String, String> placeholders = Map.of(
                "player", safe(context.player(), escapeDiscordText),
                "title", safe(context.title(), escapeDiscordText),
                "description", safe(context.description(), escapeDiscordText),
                "category", safe(context.category().displayName(), escapeDiscordText),
                "challenge", context.challenge() ? "Challenge" : "Advancement");
        StringBuilder formatted = new StringBuilder(template.length());
        for (int index = 0; index < template.length();) {
            if (template.charAt(index) == '{') {
                int closingBrace = template.indexOf('}', index + 1);
                if (closingBrace >= 0) {
                    String replacement = placeholders.get(
                            template.substring(index + 1, closingBrace));
                    if (replacement != null) {
                        formatted.append(replacement);
                        index = closingBrace + 1;
                        continue;
                    }
                }
            }
            formatted.append(template.charAt(index++));
        }
        return formatted.toString();
    }

    private static String safe(String value, boolean escapeDiscordText) {
        String sanitized = RelayMessageFormatter.sanitizeText(value);
        if (escapeDiscordText) {
            return RelayMessageFormatter.escapeDiscordUserText(sanitized);
        }
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', sanitized));
    }

    private static String discordText(String value) {
        return RelayMessageFormatter.escapeDiscordUserText(value);
    }

    private static String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
