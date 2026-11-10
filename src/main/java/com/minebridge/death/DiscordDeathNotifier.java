package com.minebridge.death;

import com.minebridge.config.ConfigManager.DeathSettings;
import com.minebridge.discord.DiscordService;

public final class DiscordDeathNotifier {

    private final DiscordService discord;

    public DiscordDeathNotifier(DiscordService discord) {
        this.discord = discord;
    }

    public void notify(
            DeathSettings settings,
            String fallbackChannelId,
            DeathContext context,
            String message) {
        String channelId = settings.discordChannelId().isBlank()
                ? fallbackChannelId : settings.discordChannelId();
        String description = DeathMessageFormatter.formatDiscord(
                settings.embedDescription(), context, message);
        if (settings.embedEnabled()) {
            String title = DeathMessageFormatter.formatDiscord(
                    settings.embedTitle(), context, message);
            discord.sendDeathEmbed(channelId, title, description, settings.embedColor());
        } else {
            discord.sendDeathMessage(channelId, description);
        }
    }
}
