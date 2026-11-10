package com.minebridge.discord;

import com.minebridge.config.ConfigManager;
import net.dv8tion.jda.api.entities.MessageEmbed;

public final class DiscordEventNotifier {

    private final DiscordService discord;
    private final ConfigManager config;

    public DiscordEventNotifier(DiscordService discord, ConfigManager config) {
        this.discord = discord;
        this.config = config;
    }

    public void send(String channelId, MessageEmbed embed) {
        ConfigManager.DiscordEventsSettings settings =
                config.currentSettings().discordEvents();
        if (!settings.enabled() || !settings.embedsEnabled() || embed == null) {
            return;
        }
        discord.sendEventEmbed(channelId, embed);
    }

    public String footer() {
        return config.currentSettings().discordEvents().footer();
    }
}
