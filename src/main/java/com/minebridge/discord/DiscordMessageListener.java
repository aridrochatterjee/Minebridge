package com.minebridge.discord;

import com.minebridge.MineBridgePlugin;
import com.minebridge.config.ConfigManager;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public final class DiscordMessageListener extends ListenerAdapter {

    private final MineBridgePlugin plugin;
    private final DiscordService discord;
    private final ConfigManager config;

    public DiscordMessageListener(
            MineBridgePlugin plugin, DiscordService discord, ConfigManager config) {
        this.plugin = plugin;
        this.discord = discord;
        this.config = config;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!config.chatEnabled()
                || event.getAuthor().isBot()
                || event.isWebhookMessage()
                || !event.isFromGuild()
                || !event.getChannel().getId().equals(config.getChannelId())) {
            return;
        }

        String content = event.getMessage().getContentDisplay().trim();
        if (content.isEmpty()) {
            return;
        }

        String template = config.message(
                "messages.discord-to-minecraft",
                "&9[Discord] &f{player}: {message}");

        String formatted = config.color(template
                .replace("{player}", event.getAuthor().getEffectiveName())
                .replace("{message}", content));

        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.getServer().broadcastMessage(formatted));
    }
}
