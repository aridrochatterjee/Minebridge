package com.minebridge.listener;

public final class PlayerEventClassifier {

    private PlayerEventClassifier() {
    }

    public static boolean isFirstJoin(boolean hasPlayedBefore) {
        return !hasPlayedBefore;
    }
}
