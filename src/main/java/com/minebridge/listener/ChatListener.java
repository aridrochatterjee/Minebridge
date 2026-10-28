package com.minebridge.listener;

import com.minebridge.MineBridgePlugin;
import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class ChatListener implements Listener {

    private final MineBridgePlugin plugin;
    private final DiscordService discord;
    private final ConfigManager config;

    public ChatListener(
            MineBridgePlugin plugin,
            DiscordService discord,
            ConfigManager config) {
        this.plugin = plugin;
        this.discord = discord;
        this.config = config;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!config.chatEnabled()) {
            return;
        }

        String player = event.getPlayer().getName();
        String message = event.getMessage();

        // Queue the message for async sending — never blocks the main thread.
        String template = config.message(
                "messages.minecraft-to-discord", "**{player}**: {message}");

        discord.send(template
                .replace("{player}", player)
                .replace("{message}", message));
    }
}
