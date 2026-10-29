package com.minebridge;

import com.minebridge.command.MineBridgeCommand;
import com.minebridge.config.ConfigManager;
import com.minebridge.discord.DiscordService;
import com.minebridge.discord.ReloadResult;
import com.minebridge.listener.ChatListener;
import com.minebridge.listener.PlayerEventListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class MineBridgePlugin extends JavaPlugin {

    private final long enabledAtNanos = System.nanoTime();
    private ConfigManager configManager;
    private DiscordService discordService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        ConfigManager.Candidate initialConfig = configManager.loadCandidate();
        discordService = new DiscordService(this, configManager);
        if (initialConfig.valid()) {
            configManager.commit(initialConfig);
            getServer().getScheduler().runTaskAsynchronously(
                    this, () -> discordService.start());
        } else {
            initialConfig.errors().forEach(message ->
                    getLogger().severe("MineBridge configuration: " + message));
            discordService.markConfigurationInvalid();
        }

        getServer().getScheduler().runTaskTimerAsynchronously(
                this, discordService::processOutgoing, 20L, 5L);

        getServer().getPluginManager().registerEvents(
                new ChatListener(this, discordService, configManager), this);

        getServer().getPluginManager().registerEvents(
                new PlayerEventListener(discordService, configManager), this);

        PluginCommand command = getCommand("minebridge");
        if (command != null) {
            MineBridgeCommand handler =
                    new MineBridgeCommand(this, discordService);
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

    public void reloadMineBridge(Consumer<ReloadResult> callback) {
        long requestId = discordService.beginReloadRequest();
        ConfigManager.Candidate candidate = configManager.loadCandidate();
        if (!candidate.valid()) {
            discordService.finishReloadRequest(requestId, "INVALID_CONFIGURATION");
            candidate.errors().forEach(message ->
                    getLogger().warning("MineBridge configuration: " + message));
            callback.accept(new ReloadResult(
                    false, String.join(" ", candidate.errors())));
            return;
        }

        try {
            getServer().getScheduler().runTaskAsynchronously(this, () ->
                    discordService.reload(requestId, candidate,
                            result -> deliverReloadResult(callback, result)));
        } catch (IllegalPluginAccessException exception) {
            discordService.finishReloadRequest(requestId, "DISCONNECTED");
            callback.accept(new ReloadResult(
                    false, "MineBridge is shutting down; Discord was not reloaded."));
        }
    }

    private void deliverReloadResult(
            Consumer<ReloadResult> callback, ReloadResult result) {
        if (!isEnabled()) {
            return;
        }
        try {
            getServer().getScheduler().runTask(this, () -> {
                if (isEnabled()) {
                    callback.accept(result);
                }
            });
        } catch (IllegalPluginAccessException exception) {
            if (isEnabled()) {
                getLogger().warning(
                        "Could not deliver the Discord reload result because the server scheduler is unavailable.");
            }
        }
    }

    public long getUptimeMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - enabledAtNanos);
    }

    public DiscordService getDiscordService() {
        return discordService;
    }
}
