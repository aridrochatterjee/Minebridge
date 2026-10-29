package com.minebridge.config;

import com.minebridge.MineBridgePlugin;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConfigManager {

    private final MineBridgePlugin plugin;
    private volatile Settings settings;

    private record Settings(
            String botToken,
            String channelId,
            boolean chatEnabled,
            boolean eventsEnabled,
            Map<String, String> messages) {
    }

    public ConfigManager(MineBridgePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String[] messageKeys = {
                "messages.minecraft-to-discord",
                "messages.discord-to-minecraft",
                "messages.player-join",
                "messages.player-leave"
        };
        Map<String, String> messages = new java.util.HashMap<>();
        for (String key : messageKeys) {
            String value = plugin.getConfig().getString(key);
            if (value != null) {
                messages.put(key, value);
            }
        }

        settings = new Settings(
                plugin.getConfig().getString("discord.bot-token", ""),
                plugin.getConfig().getString("discord.channel-id", ""),
                plugin.getConfig().getBoolean("features.chat-relay", true),
                plugin.getConfig().getBoolean(
                        "features.join-leave-notifications", true),
                Map.copyOf(messages));
    }

    public String getBotToken() {
        String env = System.getenv("MINEBRIDGE_BOT_TOKEN");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }

        String configured = settings.botToken();
        return configured == null ? "" : configured.trim();
    }

    public String getChannelId() {
        String channelId = settings.channelId();
        return channelId == null ? "" : channelId.trim();
    }

    public static boolean isValidBotToken(String token) {
        return token != null
                && !token.isBlank()
                && token.length() <= 512
                && !token.equals("PUT_YOUR_BOT_TOKEN_HERE")
                && token.chars().noneMatch(Character::isWhitespace);
    }

    public static boolean isValidChannelId(String channelId) {
        return channelId != null && channelId.matches("\\d{17,20}");
    }

    public List<String> validationErrors() {
        return validateConfiguration(getBotToken(), getChannelId());
    }

    public static List<String> validateConfiguration(String token, String channelId) {
        List<String> errors = new ArrayList<>();
        if (!isValidBotToken(token)) {
            errors.add(
                    "Discord bot token is missing or invalid; set MINEBRIDGE_BOT_TOKEN "
                            + "or discord.bot-token.");
        }
        if (!isValidChannelId(channelId)) {
            errors.add(
                    "Discord channel ID is missing or invalid; set discord.channel-id "
                            + "to the configured text channel's 17-20 digit ID.");
        }
        return List.copyOf(errors);
    }

    public boolean chatEnabled() {
        return settings.chatEnabled();
    }

    public boolean eventsEnabled() {
        return settings.eventsEnabled();
    }

    public String message(String key, String fallback) {
        return settings.messages().getOrDefault(key, fallback);
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
