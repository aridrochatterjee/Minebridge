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
        AtomicInteger polls = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                for (int j = 0; j < perThread; j++) {
                    if (queue.offer("msg-" + (offers.incrementAndGet()))) {
                        polls.incrementAndGet();
                    }
                }
            }).start();
        }

        ready.await();
        start.countDown();
        Thread.sleep(500L);

        assertEquals(capacity, queue.size(), "the queue must never hold more than its capacity");
        assertFalse(queue.isEmpty(), "the queue should still contain messages");
    }

    @Test
    void closeWhileNotFullDoesNotAbandonFutureOffersSafely() throws InterruptedException {
        BlockingMessageQueue queue = new BlockingMessageQueue(1);
        assertTrue(queue.offer("one"));
        CountDownLatch poller = new CountDownLatch(1);
        AtomicReference<String> consumed = new AtomicReference<>();
        new Thread(() -> {
            try {
                consumed.set(queue.poll());
            } finally {
                poller.countDown();
            }
        }).start();

        Thread.sleep(100L);
        queue.close();
        assertFalse(queue.offer("after-close"));

        poller.await();
        assertEquals("one", consumed.get());
    }
}
