package com.minebridge.death;

import com.minebridge.config.ConfigManager.DeathSettings;
import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordEmbedFactory;
import com.minebridge.discord.DiscordEventNotifier;

public final class DiscordDeathNotifier {

    private final DiscordEventNotifier notifier;

    public DiscordDeathNotifier(DiscordEventNotifier notifier) {
        this.notifier = notifier;
    }

    public void notify(
            DeathSettings settings,
            String channelId,
            DeathContext context,
            String message) {
        String description = DeathMessageFormatter.formatDiscord(
                settings.embedDescription(), context, message);
        var embed = DiscordEmbedFactory.death(
                DeathMessageFormatter.formatDiscord(
                        "PLAYER DEATH", context, message),
                description,
                context,
                settings.embedColor(),
                notifier.footer());
        notifier.send(channelId, embed);
    }
}
