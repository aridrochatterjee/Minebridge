package com.minebridge.death;

import com.minebridge.MineBridgePlugin;
import com.minebridge.config.ConfigManager;
import com.minebridge.config.ConfigManager.DeathSettings;
import com.minebridge.discord.DiscordService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class DeathMessageManager {

    private final MineBridgePlugin plugin;
    private final ConfigManager config;
    private final DiscordDeathNotifier discordNotifier;

    public DeathMessageManager(
            MineBridgePlugin plugin, ConfigManager config, DiscordService discord) {
        this.plugin = plugin;
        this.config = config;
        this.discordNotifier = new DiscordDeathNotifier(discord);
    }

    public void handleDeath(PlayerDeathEvent event) {
        ConfigManager.Settings currentSettings = config.currentSettings();
        DeathSettings settings = currentSettings.deathMessages();
        if (!settings.enabled()) {
            return;
        }

        Player victim = event.getEntity();
        EntityDamageEvent lastDamage = victim.getLastDamageCause();
        Entity attacker = attackerFrom(lastDamage);
        Player killer = victim.getKiller();
        if (killer == null && attacker instanceof Player attackerPlayer) {
            killer = attackerPlayer;
        }
        boolean playerKiller = killer != null;
        boolean mobKiller = !playerKiller && attacker instanceof LivingEntity;
        EntityDamageEvent.DamageCause damageCause =
                lastDamage == null ? null : lastDamage.getCause();
        DeathCategory category = DeathMessageClassifier.classify(
                damageCause, playerKiller, mobKiller);
        String killerName = playerKiller
                ? killer.getName()
                : mobKiller ? displayEntity(attacker) : "";
        DeathContext context = new DeathContext(
                victim.getName(),
                killerName,
                DeathMessageClassifier.describe(damageCause),
                category);
        String template = settings.messages().getOrDefault(
                category.key(), settings.messages().getOrDefault("other", "&c☠ {victim} died"));
        String minecraftMessage = DeathMessageFormatter.formatMinecraft(template, context);

        if (DeathMessagePolicy.shouldBroadcastMinecraft(settings, category)) {
            event.setDeathMessage(null);
            plugin.getServer().broadcastMessage(minecraftMessage);
        }
        if (DeathMessagePolicy.shouldNotifyDiscord(settings, category)) {
            discordNotifier.notify(
                    settings,
                    currentSettings.channelId(),
                    context,
                    DeathMessageFormatter.formatDiscordMessage(template, context));
        }
    }

    private static Entity attackerFrom(EntityDamageEvent damageEvent) {
        if (!(damageEvent instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    private static String displayEntity(Entity entity) {
        if (entity.getCustomName() != null && !entity.getCustomName().isBlank()) {
            return entity.getCustomName();
        }
        String name = entity.getType().name().toLowerCase(java.util.Locale.ROOT)
                .replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
