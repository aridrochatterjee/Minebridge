package com.minebridge.discord;

import com.minebridge.MineBridgePlugin;
import com.minebridge.config.ConfigManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public final class DiscordService {

    private final MineBridgePlugin plugin;
    private final ConfigManager config;
    private volatile JDA jda;

    public DiscordService(MineBridgePlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean start() {
        String token = config.getBotToken();
        String channelId = config.getChannelId();

        if (token.isBlank() || token.equals("PUT_YOUR_BOT_TOKEN_HERE")
                || !channelId.matches("\\d{17,20}")) {
            plugin.getLogger().warning(
                    "Set a valid bot token and Discord channel ID in config.yml "
                            + "or configure MINEBRIDGE_BOT_TOKEN.");
            return false;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT)
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .setActivity(Activity.playing("Minecraft"))
                    .addEventListeners(new DiscordMessageListener(plugin, this, config))
                    .build();

            return true;
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().severe(
                    "Could not initialize Discord. Check the bot configuration.");
            return false;
        }
    }

    public void send(String message) {
        JDA current = jda;
        if (current == null || message == null || message.isBlank()) {
            return;
        }

        TextChannel channel = current.getTextChannelById(config.getChannelId());
        if (channel == null) {
            return;
        }

        channel.sendMessage(message).queue(
                success -> { },
                error -> plugin.getLogger().warning(
                        "Failed to send a message to the configured Discord channel."));
    }

    public boolean isConnected() {
        JDA current = jda;
        return current != null && current.getStatus()
                == net.dv8tion.jda.api.JDA.Status.CONNECTED;
    }

    public void restart() {
        stop();
        start();
    }

    public void stop() {
        JDA current = jda;
        jda = null;
        if (current != null) {
            current.shutdown();
        }
    }
}
