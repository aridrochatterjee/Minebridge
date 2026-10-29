package com.minebridge.command;

import com.minebridge.MineBridgePlugin;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.ReloadResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public final class MineBridgeCommand implements CommandExecutor, TabCompleter {

    private final MineBridgePlugin plugin;
    private final DiscordService discord;

    public MineBridgeCommand(MineBridgePlugin plugin, DiscordService discord) {
        this.plugin = plugin;
        this.discord = discord;
    }

    @Override
    public boolean onCommand(
            CommandSender sender, Command command, String label, String[] args) {

        String subcommand = args.length == 0
                ? "help" : args[0].toLowerCase(Locale.ROOT);
        boolean canAdminister = sender.hasPermission("minebridge.admin");
        if (!MineBridgeCommandPolicy.canExecute(subcommand, canAdminister)) {
            sender.sendMessage("§cUnknown command. Use /minebridge help");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (args.length > 1) {
                sender.sendMessage("§cUsage: /minebridge <help|status"
                        + (canAdminister ? "|reload" : "") + ">");
                return true;
            }
            sender.sendMessage("§bMineBridge §7Commands:");
            sender.sendMessage("§f/minebridge status §7- Check Discord connection");
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
            return MineBridgeCommandPolicy.visibleCommands(
                            sender.hasPermission("minebridge.admin")).stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
