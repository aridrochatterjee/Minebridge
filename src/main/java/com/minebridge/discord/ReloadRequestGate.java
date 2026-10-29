package com.minebridge.discord;

final class ReloadRequestGate {

    private long latestRequest;

    synchronized long nextRequest() {
        return ++latestRequest;
    }

    synchronized boolean isCurrent(long requestId) {
        return requestId == latestRequest;
    }

    synchronized boolean runIfCurrent(long requestId, Runnable action) {
        if (requestId != latestRequest) {
            return false;
        }
        action.run();
        return true;
    }
}
