package com.minebridge.discord;

import com.minebridge.config.ConfigManager;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordReloadConcurrencyTest {

    @Test
    void olderReloadCannotActivateAfterNewerRequestCompletesFirst() throws Exception {
        ReloadRequestGate gate = new ReloadRequestGate();
        FakeClient active = new FakeClient();
        active.activate();
        FakeClient older = new FakeClient();
        FakeClient newer = new FakeClient();
        long oldRequest = gate.nextRequest();
        CountDownLatch oldReady = new CountDownLatch(1);
        CountDownLatch allowOldCompletion = new CountDownLatch(1);
        AtomicReference<FakeClient> activeClient = new AtomicReference<>(active);
        AtomicBoolean oldActivated = new AtomicBoolean();

        Thread oldCompletion = new Thread(() -> {
            oldReady.countDown();
            await(allowOldCompletion);
            oldActivated.set(gate.runIfCurrent(
                    oldRequest, () -> activeClient.set(older)));
        });
        oldCompletion.start();
        try {
            oldReady.await();
            long newRequest = gate.nextRequest();
            assertTrue(gate.runIfCurrent(newRequest, () -> activeClient.set(newer)));
        } finally {
            allowOldCompletion.countDown();
            oldCompletion.join();
        }

        if (!oldActivated.get()) {
            older.shutdown();
        }
        assertFalse(oldActivated.get());
        assertSame(newer, activeClient.get());
        assertTrue(older.closed());
        assertFalse(active.closed());
        assertTrue(active.active());
        assertFalse(newer.closed());
    }

    @Test
    void newerFailedRequestStillInvalidatesOlderPendingCandidate() {
        ReloadRequestGate gate = new ReloadRequestGate();
        FakeClient active = new FakeClient();
        active.activate();
        FakeClient olderPending = new FakeClient();
        olderPending.activate();
        long olderRequest = gate.nextRequest();

        long newerFailedRequest = gate.nextRequest();
        assertTrue(gate.isCurrent(newerFailedRequest));
        assertFalse(gate.runIfCurrent(olderRequest, olderPending::activate));

        olderPending.shutdown();
        assertTrue(olderPending.closed());
        assertFalse(active.closed());
        assertTrue(active.active());
        assertFalse(olderPending.active());
    }

    @Test
    void failedReloadLeavesPreviousClientAndOutboundSendActive() {
        ReloadRequestGate reloads = new ReloadRequestGate();
        ActiveClientSnapshot<FakeClient> activeSnapshot = new ActiveClientSnapshot<>();
        OutboundSendGate<FakeClient> sendGate = new OutboundSendGate<>();
        FakeClient active = new FakeClient();
        FakeClient failedCandidate = new FakeClient();
        ConfigManager.Settings activeSettings = settings("active", "active template");
        ConfigManager.Settings candidateSettings = settings("candidate", "candidate template");
        active.activate();
        activeSnapshot.activate(active, activeSettings);
        long activeSend = sendGate.tryBegin(active);
        long staleRequest = reloads.nextRequest();
        long failedRequest = reloads.nextRequest();

        assertFalse(reloads.runIfCurrent(staleRequest, () -> {
            activeSnapshot.activate(failedCandidate, candidateSettings);
            sendGate.clientReplaced(active);
        }));
        assertTrue(reloads.isCurrent(failedRequest));
        assertSame(activeSettings, activeSnapshot.capture(active));
        assertNull(activeSnapshot.capture(failedCandidate));
        assertEquals(0, sendGate.tryBegin(active));
        assertNull(sendGate.finish(activeSend));
        assertTrue(sendGate.tryBegin(active) > activeSend);
        assertFalse(active.closed());
        assertFalse(failedCandidate.active());
    }

    @Test
    void shutdownInvalidatesPendingRequestAndCandidate() {
        ReloadRequestGate gate = new ReloadRequestGate();
        FakeClient active = new FakeClient();
        active.activate();
        FakeClient pending = new FakeClient();
        pending.activate();
        long request = gate.nextRequest();

        gate.nextRequest();
        boolean activated = gate.runIfCurrent(request, pending::activate);
        if (!activated) {
            pending.shutdown();
        }

        assertFalse(activated);
        assertTrue(pending.closed());
        assertFalse(active.closed());
        assertTrue(active.active());
    }

    @Test
    void acceptedIncomingEventRetainsItsSettingsAcrossClientSwap() {
        ActiveClientSnapshot<Object> active = new ActiveClientSnapshot<>();
        Object oldClient = new Object();
        Object newClient = new Object();
        ConfigManager.Settings oldSettings = settings("old-channel", "old template");
        ConfigManager.Settings newSettings = settings("new-channel", "new template");
        active.activate(oldClient, oldSettings);

        ConfigManager.Settings acceptedBeforeSwap = active.capture(oldClient);
        active.activate(newClient, newSettings);

        assertSame(oldSettings, acceptedBeforeSwap);
        assertEquals("old-channel", acceptedBeforeSwap.channelId());
        assertEquals(
                "old template",
                acceptedBeforeSwap.messages().get("messages.discord-to-minecraft"));
        assertNull(active.capture(oldClient));
        assertSame(newSettings, active.capture(newClient));
    }

    @Test
    void staleCandidateCleanupDoesNotCloseTheActiveClient() {
        ReloadRequestGate gate = new ReloadRequestGate();
        FakeClient active = new FakeClient();
        active.activate();
        FakeClient staleCandidate = new FakeClient();
        long staleRequest = gate.nextRequest();
        gate.nextRequest();

        boolean activated = gate.runIfCurrent(staleRequest, () -> active.replaceWith(staleCandidate));
        if (!activated) {
            staleCandidate.shutdown();
        }

        assertFalse(activated);
        assertFalse(active.closed());
        assertTrue(staleCandidate.closed());
        assertFalse(active.replaced());
        assertTrue(active.active());
    }

    @Test
    void discardedStaleCandidateWithoutActiveClientReportsDisconnected() {
        assertEquals(
                "DISCONNECTED",
                DiscordService.stateAfterPendingDiscard(
                        null, null, "CONNECTING"));
    }

    @Test
    void discardedStaleCandidatePreservesActiveClientState() {
        assertEquals(
                "CONNECTED",
                DiscordService.stateAfterPendingDiscard(
                        "CONNECTED", null, "CONNECTING"));
    }

    @Test
    void newerPendingCandidateStateIsNotOverwritten() {
        assertEquals(
                "CONNECTING",
                DiscordService.stateAfterPendingDiscard(
                        null, "CONNECTING", "DISCONNECTED"));
    }

    private static ConfigManager.Settings settings(String channel, String template) {
        return new ConfigManager.Settings(
                "test-token",
                channel,
                true,
                true,
                Map.of("messages.discord-to-minecraft", template));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static final class FakeClient {
        private boolean closed;
        private boolean active;
        private boolean replaced;

        void activate() {
            active = true;
        }

        void replaceWith(FakeClient replacement) {
            replaced = true;
            replacement.activate();
        }

        void shutdown() {
            closed = true;
            active = false;
        }

        boolean closed() {
            return closed;
        }

        boolean active() {
            return active;
        }

        boolean replaced() {
            return replaced;
        }
    }
}
