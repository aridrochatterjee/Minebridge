package com.minebridge.listener;

import com.minebridge.discord.DiscordEmbedFactory;
import com.minebridge.discord.DiscordEventDeduplicator;
import com.minebridge.discord.DiscordEventNotifier;
import com.minebridge.discord.DiscordEventsPolicy;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.RelayMessageFormatter;
import com.minebridge.config.ConfigManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
public final class PlayerEventListener implements Listener {

    private final DiscordEventNotifier notifier;
    private final ConfigManager config;
    private final DiscordEventDeduplicator deduplicator = new DiscordEventDeduplicator();

    public PlayerEventListener(DiscordService discord, ConfigManager config) {
        this(new DiscordEventNotifier(discord, config), config);
    }

    public PlayerEventListener(DiscordEventNotifier notifier, ConfigManager config) {
        this.notifier = notifier;
        this.config = config;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ConfigManager.Settings current = config.currentSettings();
        ConfigManager.DiscordEventsSettings settings = current.discordEvents();
        var player = event.getPlayer();
        boolean firstJoin = PlayerEventClassifier.isFirstJoin(player.hasPlayedBefore());
        if (!current.eventsEnabled()
                || !DiscordEventsPolicy.playerEventEnabled(settings, true, firstJoin)
                || deduplicator.alreadySent(player.getUniqueId(), "join")) {
            return;
        }
        boolean welcome = firstJoin && settings.firstJoinEnabled();
        String template = welcome
                ? "{player} joined the server for the first time!"
                : config.message(
                        "messages.player-join",
                        "🟢 **{player}** joined the server.");
        String description = RelayMessageFormatter.formatForDiscord(
                template, player.getName(), "", java.time.Instant.now().toString());
        var embed = DiscordEmbedFactory.playerJoined(
                player.getName(), description, welcome, notifier.footer());
        String channelId = settings.playerEventsChannelId().isBlank()
                ? current.channelId() : settings.playerEventsChannelId();
        notifier.send(channelId, embed);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ConfigManager.Settings current = config.currentSettings();
        ConfigManager.DiscordEventsSettings settings = current.discordEvents();
        var player = event.getPlayer();
        if (!current.eventsEnabled()
                || !DiscordEventsPolicy.playerEventEnabled(settings, false, false)
                || deduplicator.alreadySent(player.getUniqueId(), "leave")) {
            return;
        }
        String description = RelayMessageFormatter.formatForDiscord(
                config.message(
                        "messages.player-leave",
                        "🔴 **{player}** left the server."),
                player.getName(),
                "",
                java.time.Instant.now().toString());
        notifier.send(
                settings.playerEventsChannelId().isBlank()
                        ? current.channelId() : settings.playerEventsChannelId(),
                DiscordEmbedFactory.playerLeft(
                        player.getName(), description, notifier.footer()));
    }
}
