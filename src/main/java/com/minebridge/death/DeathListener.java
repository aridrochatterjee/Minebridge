package com.minebridge.death;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class DeathListener implements Listener {

    private final DeathMessageManager manager;

    public DeathListener(DeathMessageManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        manager.handleDeath(event);
    }
}
