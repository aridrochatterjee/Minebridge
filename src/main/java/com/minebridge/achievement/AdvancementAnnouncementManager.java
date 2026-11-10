package com.minebridge.achievement;

import com.minebridge.MineBridgePlugin;
import com.minebridge.config.ConfigManager;
import com.minebridge.config.ConfigManager.AchievementSettings;
import com.minebridge.discord.DiscordService;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

public final class AdvancementAnnouncementManager {

    private final MineBridgePlugin plugin;
    private final ConfigManager config;
    private final DiscordService discord;
    private final AdvancementAnnouncementDeduplicator deduplicator;

    public AdvancementAnnouncementManager(
            MineBridgePlugin plugin, ConfigManager config, DiscordService discord) {
        this(plugin, config, discord, new AdvancementAnnouncementDeduplicator());
    }

    AdvancementAnnouncementManager(
            MineBridgePlugin plugin,
            ConfigManager config,
            DiscordService discord,
            AdvancementAnnouncementDeduplicator deduplicator) {
        this.plugin = plugin;
        this.config = config;
        this.discord = discord;
        this.deduplicator = deduplicator;
    }

    public void handleAdvancement(PlayerAdvancementDoneEvent event) {
        ConfigManager.Settings currentSettings = config.currentSettings();
        AchievementSettings settings = currentSettings.achievements();
        if (!settings.enabled()) {
            return;
        }

        Advancement advancement = event.getAdvancement();
        AdvancementDisplay display = advancement.getDisplay();
        if (display == null) {
            return;
        }
        AdvancementContext context = AdvancementClassifier.createContext(
                event.getPlayer().getName(),
                advancement.getKey().toString(),
                display.getTitle(),
                display.getDescription(),
                display.getType());
        if (!AdvancementAnnouncementPolicy.enabled(settings, context)) {
            return;
        }
        Player player = event.getPlayer();
        if (deduplicator.alreadyAnnounced(
                player.getUniqueId(), advancement.getKey().toString())) {
            return;
        }

        String template = context.challenge()
                ? settings.challengeMessageTemplate()
                : settings.messageTemplate();
        if (settings.minecraftEnabled()) {
            plugin.getServer().broadcastMessage(
                    AdvancementMessageFormatter.formatMinecraft(template, context));
        }
        if (settings.discordEnabled()) {
            discord.sendEmbed(
                    settings.discordChannelId().isBlank()
                            ? currentSettings.channelId() : settings.discordChannelId(),
                    AdvancementMessageFormatter.createEmbed(
                            settings.embedTitle(),
                            settings.embedDescription(),
                            context,
                            settings.embedColor()));
        }
    }
}
