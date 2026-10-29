package com.minebridge.discord;

import java.util.Map;
import java.util.regex.Pattern;

public final class RelayMessageFormatter {

    public static final int MAX_MESSAGE_LENGTH = 2000;

    private static final Pattern MINECRAFT_FORMATTING =
            Pattern.compile("(?i)§(?:[0-9A-FK-OR]|x(?:§[0-9A-F]){6})");
    private static final Pattern DISCORD_MASS_MENTION =
            Pattern.compile("(?i)@(everyone|here)\\b");
    private static final Pattern DISCORD_MARKDOWN =
            Pattern.compile("([\\\\`*_~|><\\[\\]()])");

    private RelayMessageFormatter() {
    }

    public static String formatForDiscord(String template, String player, String message) {
        return formatForDiscord(template, player, message, "");
    }

    public static String formatForDiscord(
            String template, String player, String message, String timestamp) {
        return replacePlaceholders(template, Map.of(
                "player", escapeDiscordUserText(player),
                "message", escapeDiscordUserText(message),
                "timestamp", escapeDiscordUserText(timestamp)));
    }

    public static String formatForMinecraft(String template, String player, String message) {
        return formatForMinecraft(template, player, player, player, message, "");
    }

    public static String formatForMinecraft(
            String template,
            String player,
            String displayName,
            String username,
            String message,
            String timestamp) {
        return replacePlaceholders(template, Map.of(
                "player", sanitizeText(player),
                "display_name", sanitizeText(displayName),
                "username", sanitizeText(username),
                "message", sanitizeText(message),
                "timestamp", sanitizeText(timestamp)));
    }

    public static String sanitizeText(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String sanitized = MINECRAFT_FORMATTING.matcher(input).replaceAll("")
                .replace("§", "")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
        StringBuilder safe = new StringBuilder(sanitized.length());
        for (int index = 0; index < sanitized.length(); index++) {
            char current = sanitized.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 < sanitized.length()
                        && Character.isLowSurrogate(sanitized.charAt(index + 1))) {
                    safe.append(current).append(sanitized.charAt(++index));
                }
            } else if (!Character.isLowSurrogate(current)
                    && !Character.isISOControl(current)) {
                safe.append(current);
            }
        }
        return safe.toString().trim();
    }

    public static boolean isWithinDiscordLimit(String message) {
        return message != null && message.length() <= MAX_MESSAGE_LENGTH;
    }

    private static String escapeDiscordMassMentions(String input) {
        return DISCORD_MASS_MENTION.matcher(input)
                .replaceAll("@\u200B$1");
    }

    private static String escapeDiscordUserText(String input) {
        String sanitized = sanitizeText(input);
        String markdownEscaped = DISCORD_MARKDOWN.matcher(sanitized)
                .replaceAll("\\\\$1");
        return escapeDiscordMassMentions(markdownEscaped);
    }

    private static String replacePlaceholders(
            String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return "";
        }

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
            formatted.append(template.charAt(index));
            index++;
        }
        return formatted.toString();
    }
}
