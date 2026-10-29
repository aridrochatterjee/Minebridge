package com.minebridge.discord;

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
                && messageChannelId.equals(configuredChannelId);
    }

    public static String sanitizeIncoming(String content) {
        String sanitized = RelayMessageFormatter.sanitizeText(content);
        return sanitized.length() <= RelayMessageFormatter.MAX_MESSAGE_LENGTH
                ? sanitized : "";
    }
}
