package com.minebridge.command;

import com.minebridge.discord.RelayMessageFormatter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ServerInfoFormatter {

    private ServerInfoFormatter() {
    }

    public static String formatServerStatus(String template, int online, int max) {
        return replacePlaceholders(template, Map.of(
                "online", Integer.toString(online),
                "max", Integer.toString(max)));
    }

    public static String formatPlayerList(
            String template, int online, int max, List<String> playerNames) {
        String players = playerNames.isEmpty()
                ? "None"
                : playerNames.stream()
                        .map(RelayMessageFormatter::sanitizeText)
                        .collect(Collectors.joining(", "));
        return replacePlaceholders(template, Map.of(
                "online", Integer.toString(online),
                "max", Integer.toString(max),
                "players", players));
    }

    private static String replacePlaceholders(
            String template, Map<String, String> placeholders) {
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
