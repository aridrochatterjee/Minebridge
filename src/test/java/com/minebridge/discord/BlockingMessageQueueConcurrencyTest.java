package com.minebridge.discord;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockingMessageQueueConcurrencyTest {

    @Test
    void concurrentOfferAndPollDoNotCorruptTheQueue() throws InterruptedException {
        final int threads = 8;
        final int perThread = 2000;
        final int capacity = 50;
        BlockingMessageQueue queue = new BlockingMessageQueue(capacity);

        AtomicInteger offers = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                try {
                    ready.countDown();
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        queue.offer("msg-" + (offers.incrementAndGet()));
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                } finally {
                    finished.countDown();
                }
            }).start();
        }

        ready.await();
        start.countDown();
        finished.await();

        assertEquals(capacity, queue.size(), "the queue must never hold more than its capacity");
        assertFalse(queue.isEmpty(), "the queue should still contain messages");
    }

    @Test
    void closeWhileNotFullDoesNotAbandonFutureOffersSafely() throws InterruptedException {
        BlockingMessageQueue queue = new BlockingMessageQueue(1);
        assertTrue(queue.offer("one"));
        CountDownLatch pollerReady = new CountDownLatch(1);
        CountDownLatch allowPoll = new CountDownLatch(1);
        CountDownLatch pollerFinished = new CountDownLatch(1);
        AtomicReference<String> consumed = new AtomicReference<>();
        new Thread(() -> {
            try {
                pollerReady.countDown();
                allowPoll.await();
                consumed.set(queue.poll());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            } finally {
                pollerFinished.countDown();
            }
        }).start();

        pollerReady.await();
        allowPoll.countDown();
        pollerFinished.await();
        queue.close();
        assertFalse(queue.offer("after-close"));

        assertEquals("one", consumed.get());
    }
}
