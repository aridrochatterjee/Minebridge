package com.minebridge.discord;

final class OutboundSendGate<C> {

    private long nextSendId;
    private long activeSendId;
    private C activeClient;
    private C retiringClient;
    private boolean sending;
    private boolean closed;

    synchronized long tryBegin(C client) {
        if (closed || sending) {
            return 0;
        }
        sending = true;
        activeSendId = ++nextSendId;
        activeClient = client;
        return activeSendId;
    }

    synchronized C clientReplaced(C previousClient) {
        if (sending && activeClient == previousClient) {
            retiringClient = previousClient;
            return null;
        }
        return previousClient;
    }

    synchronized C finish(long sendId) {
        if (!sending || activeSendId != sendId) {
            return null;
        }
        sending = false;
        activeSendId = 0;
        activeClient = null;
        C retired = retiringClient;
        retiringClient = null;
        return retired;
    }

    synchronized C close() {
        closed = true;
        sending = false;
        activeSendId = 0;
        activeClient = null;
        nextSendId++;
        C retired = retiringClient;
        retiringClient = null;
        return retired;
    }
}
