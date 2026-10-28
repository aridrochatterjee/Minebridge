package com.minebridge;

import com.minebridge.command.MineBridgeCommand;
import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordService;
import com.minebridge.listener.ChatListener;
import com.minebridge.listener.PlayerEventListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MineBridgePlugin extends JavaPlugin {

    private ConfigManager configManager;
    private DiscordService discordService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        discordService = new DiscordService(this, configManager);

        getServer().getScheduler().runTaskAsynchronously(
                this, discordService::start);

        getServer().getScheduler().runTaskTimerAsynchronously(
                this, discordService::processOutgoing, 20L, 5L);

        getServer().getPluginManager().registerEvents(
                new ChatListener(this, discordService, configManager), this);

        getServer().getPluginManager().registerEvents(
                new PlayerEventListener(discordService, configManager), this);

        PluginCommand command = getCommand("minebridge");
        if (command != null) {
            MineBridgeCommand handler =
                    new MineBridgeCommand(this, configManager, discordService);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        } else {
            getLogger().severe("Command 'minebridge' is missing from plugin.yml!");
        }

        getLogger().info("MineBridge enabled.");
    }

    @Override
    public void onDisable() {
        if (discordService != null) {
            discordService.stop();
        }
    }

    public void reloadMineBridge() {
        reloadConfig();
        configManager.reload();
        getServer().getScheduler().runTaskAsynchronously(
                this, discordService::reload);
    }

    public DiscordService getDiscordService() {
        return discordService;
    }
}
