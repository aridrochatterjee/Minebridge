package com.minebridge.listener;

import com.minebridge.MineBridgePlugin;
import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.RelayMessageFormatter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.time.Instant;
import java.util.function.Consumer;

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

        String template = config.message(
                "messages.minecraft-to-discord", "**{player}**: {message}");

        relayIfNonBlank(
                template,
                event.getPlayer().getName(),
                event.getMessage(),
                Instant.now().toString(),
                discord::send);
    }

    static boolean relayIfNonBlank(
            String template,
            String player,
            String message,
            String timestamp,
            Consumer<String> sender) {
        if (message == null || message.isBlank()) {
            return false;
        }
        sender.accept(RelayMessageFormatter.formatForDiscord(
                template, player, message, timestamp));
        return true;
    }
}
