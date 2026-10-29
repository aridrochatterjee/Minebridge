package com.minebridge.listener;

import com.minebridge.discord.DiscordService;
import com.minebridge.discord.RelayMessageFormatter;
import com.minebridge.config.ConfigManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;

public final class PlayerEventListener implements Listener {

    private final DiscordService discord;
    private final ConfigManager config;

    public PlayerEventListener(DiscordService discord, ConfigManager config) {
        this.discord = discord;
        this.config = config;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (config.eventsEnabled()) {
            discord.send(RelayMessageFormatter.formatForDiscord(
                    config.message(
                            "messages.player-join",
                            "🟢 **{player}** joined the server."),
                    event.getPlayer().getName(),
                    "",
                    Instant.now().toString()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (config.eventsEnabled()) {
            discord.send(RelayMessageFormatter.formatForDiscord(
                    config.message(
                            "messages.player-leave",
                            "🔴 **{player}** left the server."),
                    event.getPlayer().getName(),
                    "",
                    Instant.now().toString()));
        }
    }
}
