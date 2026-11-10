package com.minebridge.achievement;

import com.minebridge.MineBridgePlugin;
import com.minebridge.config.ConfigManager;
import com.minebridge.config.ConfigManager.AchievementSettings;
import com.minebridge.discord.DiscordEmbedFactory;
import com.minebridge.discord.DiscordEventNotifier;
import com.minebridge.discord.DiscordEventsPolicy;
import com.minebridge.discord.DiscordService;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

public final class AdvancementAnnouncementManager {

    private final MineBridgePlugin plugin;
    private final ConfigManager config;
    private final DiscordEventNotifier notifier;
    private final AdvancementAnnouncementDeduplicator deduplicator;

    public AdvancementAnnouncementManager(
            MineBridgePlugin plugin, ConfigManager config, DiscordService discord) {
        this(plugin, config, new DiscordEventNotifier(discord, config),
                new AdvancementAnnouncementDeduplicator());
    }

    public AdvancementAnnouncementManager(
            MineBridgePlugin plugin, ConfigManager config, DiscordEventNotifier notifier) {
        this(plugin, config, notifier, new AdvancementAnnouncementDeduplicator());
    }

    AdvancementAnnouncementManager(
            MineBridgePlugin plugin,
            ConfigManager config,
            DiscordEventNotifier notifier,
            AdvancementAnnouncementDeduplicator deduplicator) {
        this.plugin = plugin;
        this.config = config;
        this.notifier = notifier;
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
            ConfigManager.DiscordEventsSettings eventSettings =
                    currentSettings.discordEvents();
            if (DiscordEventsPolicy.achievementEnabled(
                    eventSettings, context.category(), context.challenge())) {
                String title = AdvancementMessageFormatter.formatDiscord(
                        context.challenge()
                                ? settings.challengeEmbedTitle() : settings.embedTitle(),
                        context);
                String description = AdvancementMessageFormatter.formatDiscord(
                        context.challenge()
                                ? settings.challengeEmbedDescription()
                                : settings.embedDescription(),
                        context);
                int color = context.challenge()
                        ? DiscordEmbedFactory.CHALLENGE_COLOR
                        : settings.embedColor();
                var embed = DiscordEmbedFactory.advancement(
                        title,
                        description,
                        context.player(),
                        context.title(),
                        context.category().displayName(),
                        context.challenge(),
                        color,
                        notifier.footer(),
                        java.time.Instant.now());
                String channelId = eventSettings.achievementsChannelId().isBlank()
                        ? settings.discordChannelId().isBlank()
                                ? currentSettings.channelId()
                                : settings.discordChannelId()
                        : eventSettings.achievementsChannelId();
                notifier.send(channelId, embed);
            }
        }
    }
}
