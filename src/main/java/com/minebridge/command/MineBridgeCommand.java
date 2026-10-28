package com.minebridge.command;

import com.minebridge.MineBridgePlugin;
import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class MineBridgeCommand implements CommandExecutor, TabCompleter {

    private final MineBridgePlugin plugin;
    private final ConfigManager config;
    private final DiscordService discord;

    public MineBridgeCommand(
            MineBridgePlugin plugin,
            ConfigManager config,
            DiscordService discord) {
        this.plugin = plugin;
        this.config = config;
        this.discord = discord;
    }

    @Override
    public boolean onCommand(
            CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§bMineBridge §7Commands:");
            sender.sendMessage("§f/minebridge status §7- Check Discord connection");
            sender.sendMessage("§f/minebridge reload §7- Reload configuration");
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§bMineBridge §7Discord connected: "
                    + (discord.isConnected() ? "§aYes" : "§cNo"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("minebridge.admin")) {
                sender.sendMessage("§cYou don't have permission.");
                return true;
            }

            plugin.reloadMineBridge();
            sender.sendMessage("§aMineBridge configuration reloaded.");
            return true;
        }

        sender.sendMessage("§cUnknown command. Use /minebridge help");
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("help", "status", "reload").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
