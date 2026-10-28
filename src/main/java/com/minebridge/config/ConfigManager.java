package com.minebridge.config;

import com.minebridge.MineBridgePlugin;
import org.bukkit.ChatColor;

public final class ConfigManager {

    private final MineBridgePlugin plugin;

    public ConfigManager(MineBridgePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        // Values are read directly from the refreshed Bukkit configuration.
    }

    public String getBotToken() {
        String env = System.getenv("MINEBRIDGE_BOT_TOKEN");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }

        return plugin.getConfig().getString("discord.bot-token", "").trim();
    }

    public String getChannelId() {
        return plugin.getConfig().getString("discord.channel-id", "").trim();
    }

    public boolean chatEnabled() {
        return plugin.getConfig().getBoolean("features.chat-relay", true);
    }

    public boolean eventsEnabled() {
        return plugin.getConfig().getBoolean(
                "features.join-leave-notifications", true);
    }

    public String message(String key, String fallback) {
        return plugin.getConfig().getString(key, fallback);
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
