package com.minebridge.discord;

import com.minebridge.MineBridgePlugin;
import com.minebridge.config.ConfigManager;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public final class DiscordMessageListener extends ListenerAdapter {

    private final MineBridgePlugin plugin;
    private final ConfigManager config;

    public DiscordMessageListener(
            MineBridgePlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!DiscordMessageFilter.shouldRelay(
                config.chatEnabled(),
                event.getAuthor().isBot(),
                event.isWebhookMessage(),
                event.isFromGuild(),
                event.getChannel().getId(),
                config.getChannelId())) {
            return;
        }

        String content = DiscordMessageFilter.sanitizeIncoming(
                event.getMessage().getContentDisplay());
        if (content.isEmpty()) {
            return;
        }

        String player = DiscordMessageFilter.sanitizeIncoming(
                event.getAuthor().getEffectiveName());
        if (player.isEmpty()) {
            return;
        }

        String template = config.message(
                "messages.discord-to-minecraft",
                "&9[Discord] &f{player}: {message}");

        String formatted = RelayMessageFormatter.formatForMinecraft(
                config.color(template), player, content);

        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.getServer().broadcastMessage(formatted));
        }
    }

}
