package com.minebridge.listener;

import com.minebridge.discord.DiscordEmbedFactory;
import com.minebridge.discord.DiscordEventDeduplicator;
import com.minebridge.discord.DiscordEventNotifier;
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
        this.notifier = new DiscordEventNotifier(discord, config);
        this.config = config;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ConfigManager.Settings current = config.currentSettings();
        ConfigManager.DiscordEventsSettings settings = current.discordEvents();
        var player = event.getPlayer();
        boolean firstJoin = !player.hasPlayedBefore();
        if (!config.eventsEnabled()
                || !settings.playerEventsEnabled()
                || (firstJoin && !settings.firstJoinEnabled())
                || (!firstJoin && !settings.joinEnabled())
                || deduplicator.alreadySent(player.getUniqueId(), firstJoin ? "first-join" : "join")) {
            return;
        }
        String template = firstJoin
                ? "{player} joined the server for the first time!"
                : "{player} joined the Minecraft server.";
        String description = RelayMessageFormatter.formatForDiscord(
                template, player.getName(), "", java.time.Instant.now().toString());
        var embed = DiscordEmbedFactory.playerJoined(
                player.getName(), description, firstJoin, notifier.footer());
        String channelId = settings.playerEventsChannelId().isBlank()
                ? current.channelId() : settings.playerEventsChannelId();
        notifier.send(channelId, embed);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ConfigManager.Settings current = config.currentSettings();
        ConfigManager.DiscordEventsSettings settings = current.discordEvents();
        var player = event.getPlayer();
        if (!config.eventsEnabled()
                || !settings.playerEventsEnabled()
                || !settings.leaveEnabled()
                || deduplicator.alreadySent(player.getUniqueId(), "leave")) {
            return;
        }
        String description = RelayMessageFormatter.formatForDiscord(
                "{player} left the Minecraft server.",
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
