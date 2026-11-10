package com.minebridge.achievement;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

public final class AdvancementListener implements Listener {

    private final AdvancementAnnouncementManager manager;

    public AdvancementListener(AdvancementAnnouncementManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        manager.handleAdvancement(event);
    }
}
