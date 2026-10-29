package com.minebridge.command;

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

    public static boolean canExecute(String subcommand, boolean canAdminister) {
        return subcommand == null
                || !"reload".equals(subcommand.toLowerCase(Locale.ROOT))
                || canAdminister;
    }
}
