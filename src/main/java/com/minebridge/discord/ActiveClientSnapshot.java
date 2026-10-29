package com.minebridge.discord;

import com.minebridge.config.ConfigManager;

final class ActiveClientSnapshot<C> {

    private C activeClient;
    private ConfigManager.Settings settings;

    synchronized void activate(C client, ConfigManager.Settings newSettings) {
        activeClient = client;
        settings = newSettings;
    }

    synchronized ConfigManager.Settings capture(C client) {
        return client == activeClient ? settings : null;
    }

    synchronized void clear() {
        activeClient = null;
        settings = null;
    }
}
