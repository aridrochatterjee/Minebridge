package com.minebridge.command;

import java.util.ArrayList;
import java.util.List;

public record MineBridgeStatus(
        String discordState,
        boolean channelAvailable,
        int outgoingQueueSize,
        int outgoingQueueCapacity,
        long uptimeMillis,
        boolean chatRelayEnabled,
        boolean joinLeaveNotificationsEnabled) {

    public List<String> formatLines() {
        List<String> lines = new ArrayList<>();
        lines.add("§bMineBridge §7Status:");
        lines.add("§7Discord: " + colorForConnection(discordState) + discordState);
        lines.add("§7Configured channel: "
                + (channelAvailable ? "§aAvailable" : "§cUnavailable"));
        lines.add("§7Outgoing queue: §f" + outgoingQueueSize
                + "/" + outgoingQueueCapacity);
        lines.add("§7Plugin uptime: §f" + formatUptime(uptimeMillis));
        lines.add("§7Chat relay: "
                + (chatRelayEnabled ? "§aEnabled" : "§cDisabled"));
        lines.add("§7Join/leave notifications: "
                + (joinLeaveNotificationsEnabled ? "§aEnabled" : "§cDisabled"));
        return List.copyOf(lines);
    }

    private static String colorForConnection(String state) {
        return "CONNECTED".equals(state) ? "§a" : "§c";
    }

    private static String formatUptime(long uptimeMillis) {
        long totalSeconds = Math.max(0, uptimeMillis) / 1000;
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
