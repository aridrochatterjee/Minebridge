package com.minebridge.discord;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboundSendGateTest {

    @Test
    void successfulReplacementKeepsTheInFlightSendGateUntilItsCallback() {
        OutboundSendGate<Object> gate = new OutboundSendGate<>();
        Object previousClient = new Object();
        Object replacementClient = new Object();

        long firstSend = gate.tryBegin(previousClient);

        assertNull(gate.clientReplaced(previousClient));
        assertEquals(0, gate.tryBegin(replacementClient));
        assertSame(previousClient, gate.finish(firstSend));

        assertTrue(gate.tryBegin(replacementClient) > 0);
    }

    @Test
    void callbackAfterReplacementRetiresOnlyItsOriginalClient() {
        OutboundSendGate<Object> gate = new OutboundSendGate<>();
        Object previousClient = new Object();
        Object replacementClient = new Object();
        long send = gate.tryBegin(previousClient);

        assertNull(gate.clientReplaced(previousClient));

        Object retired = gate.finish(send);

        assertSame(previousClient, retired);
        assertTrue(gate.tryBegin(replacementClient) > 0);
    }

    @Test
    void lateOldCallbackCannotReleaseANewerSendsGate() throws Exception {
        OutboundSendGate<Object> gate = new OutboundSendGate<>();
        Object client = new Object();
        long oldSend = gate.tryBegin(client);
        CountDownLatch callbackReady = new CountDownLatch(1);
        CountDownLatch deliverLateCallback = new CountDownLatch(1);
        AtomicReference<Object> callbackResult = new AtomicReference<>();

        Thread lateCallback = new Thread(() -> {
            callbackReady.countDown();
            await(deliverLateCallback);
            callbackResult.set(gate.finish(oldSend));
        });
        lateCallback.start();
        try {
            callbackReady.await();
            assertNull(gate.finish(oldSend));
            long newerSend = gate.tryBegin(client);
            assertTrue(newerSend > oldSend);

            deliverLateCallback.countDown();
            lateCallback.join();

            assertNull(callbackResult.get());
            assertEquals(0, gate.tryBegin(client));
            assertNull(gate.finish(newerSend));
            assertTrue(gate.tryBegin(client) > newerSend);
        } finally {
            deliverLateCallback.countDown();
            lateCallback.join();
        }
    }

    @Test
    void queuedMessagesRemainFifoAcrossClientReplacement() {
        BlockingMessageQueue queue = new BlockingMessageQueue(3);
        OutboundSendGate<Object> gate = new OutboundSendGate<>();
        Object previousClient = new Object();
        Object replacementClient = new Object();

        assertTrue(queue.offer("first"));
        assertTrue(queue.offer("second"));
        long firstSend = gate.tryBegin(previousClient);
        assertEquals("first", queue.poll());

        assertNull(gate.clientReplaced(previousClient));
        assertEquals(0, gate.tryBegin(replacementClient));
        assertSame(previousClient, gate.finish(firstSend));

        long secondSend = gate.tryBegin(replacementClient);
        assertEquals("second", queue.poll());
        assertNull(gate.finish(secondSend));
        assertTrue(queue.isEmpty());
    }

    @Test
    void failedReplacementLeavesThePreviousClientsSendGateUsable() {
        OutboundSendGate<Object> gate = new OutboundSendGate<>();
        Object activeClient = new Object();
        long send = gate.tryBegin(activeClient);

        assertNull(gate.finish(send));

        long nextSend = gate.tryBegin(activeClient);
        assertTrue(nextSend > send);
    }

    @Test
    void shutdownInvalidatesAnInFlightCallbackAndReturnsRetiredClient() {
        OutboundSendGate<Object> gate = new OutboundSendGate<>();
        BlockingMessageQueue queue = new BlockingMessageQueue(2);
        Object previousClient = new Object();
        Object replacementClient = new Object();
        long send = gate.tryBegin(previousClient);
        assertTrue(queue.offer("in flight"));
        assertNull(gate.clientReplaced(previousClient));

        assertSame(previousClient, gate.close());
        queue.close();

        assertNull(gate.finish(send));
        assertEquals(0, gate.tryBegin(replacementClient));
        assertFalse(queue.offer("after shutdown"));
        assertTrue(queue.isEmpty());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
