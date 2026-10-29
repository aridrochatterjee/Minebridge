package com.minebridge.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MineBridgeCommandPolicy {

    private MineBridgeCommandPolicy() {
    }

    public static List<String> visibleCommands(boolean canAdminister) {
        return canAdminister
                ? List.of("help", "status", "reload")
                : List.of("help", "status");
    }

    public static List<String> visibleCommands(
            boolean canAdminister, boolean serverStatusEnabled, boolean playerListEnabled) {
        ArrayList<String> commands = new ArrayList<>(List.of("help", "status"));
        if (serverStatusEnabled) {
            commands.add("server");
        }
        if (playerListEnabled) {
            commands.add("players");
        }
        if (canAdminister) {
            commands.add("reload");
        }
        return List.copyOf(commands);
    }

    public static boolean canExecute(String subcommand, boolean canAdminister) {
        return subcommand == null
                || !"reload".equals(subcommand.toLowerCase(Locale.ROOT))
                || canAdminister;
    }

    public static boolean canExecute(
            String subcommand,
            boolean canAdminister,
            boolean serverStatusEnabled,
            boolean playerListEnabled) {
        if (!canExecute(subcommand, canAdminister)) {
            return false;
        }
        if ("server".equalsIgnoreCase(subcommand)) {
            return serverStatusEnabled;
        }
        if ("players".equalsIgnoreCase(subcommand)) {
            return playerListEnabled;
        }
        return true;
    }

}
