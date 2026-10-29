package com.minebridge.command;

import com.minebridge.MineBridgePlugin;
import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.ReloadResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class MineBridgeCommand implements CommandExecutor, TabCompleter {

    private final MineBridgePlugin plugin;
    private final DiscordService discord;
    private final ConfigManager config;

    public MineBridgeCommand(MineBridgePlugin plugin, DiscordService discord) {
        this.plugin = plugin;
        this.discord = discord;
        this.config = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(
            CommandSender sender, Command command, String label, String[] args) {

        String subcommand = args.length == 0
                ? "help" : args[0].toLowerCase(Locale.ROOT);
        boolean canAdminister = sender.hasPermission("minebridge.admin");
        ConfigManager.Settings settings = config.currentSettings();
        boolean serverStatusEnabled = settings.serverStatusEnabled();
        boolean playerListEnabled = settings.playerListEnabled();
        if (!MineBridgeCommandPolicy.canExecute(
                subcommand, canAdminister, serverStatusEnabled, playerListEnabled)) {
            if ("server".equals(subcommand) && !serverStatusEnabled) {
                sender.sendMessage("§cThe server status command is disabled in configuration.");
                return true;
            }
            if ("players".equals(subcommand) && !playerListEnabled) {
                sender.sendMessage("§cThe player list command is disabled in configuration.");
                return true;
            }
            sender.sendMessage("§cUnknown command. Use /minebridge help");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (args.length > 1) {
                sender.sendMessage("§cUsage: /minebridge <"
                        + String.join("|", MineBridgeCommandPolicy.visibleCommands(
                                canAdminister, serverStatusEnabled, playerListEnabled))
                        + ">");
                return true;
            }
            sender.sendMessage("§bMineBridge §7Commands:");
            sender.sendMessage("§f/minebridge status §7- Check Discord connection");
            if (serverStatusEnabled) {
                sender.sendMessage("§f/minebridge server §7- Show online count and capacity");
            }
            if (playerListEnabled) {
                sender.sendMessage("§f/minebridge players §7- Show online players");
            }
            if (canAdminister) {
                sender.sendMessage("§f/minebridge reload §7- Reload configuration");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            if (args.length > 1) {
                sender.sendMessage("§cUsage: /minebridge status");
                return true;
            }
            discord.getStatus(plugin.getUptimeMillis()).formatLines()
                    .forEach(sender::sendMessage);
            return true;
        }

        if (args[0].equalsIgnoreCase("server")) {
            if (args.length > 1) {
                sender.sendMessage("§cUsage: /minebridge server");
                return true;
            }
            int online = plugin.getServer().getOnlinePlayers().size();
            int max = plugin.getServer().getMaxPlayers();
            String template = config.color(settings.messages().getOrDefault(
                    "messages.server-status",
                    "&7Server &8| &a{online}&7/&f{max} players online"));
            sender.sendMessage(ServerInfoFormatter.formatServerStatus(
                    template, online, max));
            return true;
        }

        if (args[0].equalsIgnoreCase("players")) {
            if (args.length > 1) {
                sender.sendMessage("§cUsage: /minebridge players");
                return true;
            }
            List<String> names = plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            int online = names.size();
            int max = plugin.getServer().getMaxPlayers();
            String template = config.color(settings.messages().getOrDefault(
                    "messages.player-list",
                    "&7Online players (&f{online}&7/&f{max}&7): {players}"));
            sender.sendMessage(ServerInfoFormatter.formatPlayerList(
                    template, online, max, names));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (args.length > 1) {
                sender.sendMessage("§cUsage: /minebridge reload");
                return true;
            }
            sender.sendMessage("§eMineBridge reload requested; waiting for Discord connection...");
            plugin.reloadMineBridge((ReloadResult result) -> sender.sendMessage(
                    (result.successful() ? "§a" : "§c") + result.message()));
            return true;
        }

        sender.sendMessage("§cUnknown command. Use /minebridge help");
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            ConfigManager.Settings settings = config.currentSettings();
            return MineBridgeCommandPolicy.visibleCommands(
                            sender.hasPermission("minebridge.admin"),
                            settings.serverStatusEnabled(),
                            settings.playerListEnabled()).stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
