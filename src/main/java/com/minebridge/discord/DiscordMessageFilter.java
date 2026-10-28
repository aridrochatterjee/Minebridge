package com.minebridge.discord;

import java.util.Objects;

public final class DiscordMessageFilter {

    private DiscordMessageFilter() {
    }

    public static boolean shouldRelay(
            boolean chatEnabled,
            boolean bot,
            boolean webhook,
            boolean guildMessage,
            String messageChannelId,
            String configuredChannelId) {
        return chatEnabled
                && !bot
                && !webhook
                && guildMessage
                && messageChannelId != null
                && Objects.equals(messageChannelId, configuredChannelId);
    }

    public static String sanitizeIncoming(String content) {
        if (content == null) {
            return "";
        }
        String sanitized = content
                .replaceAll("\u00a7.", "")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
        return sanitized.length() <= 2000 ? sanitized : "";
    }
}
