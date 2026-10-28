package com.minebridge;

import com.minebridge.discord.BlockingMessageQueue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BlockingMessageQueue}.
 */
class BlockingMessageQueueTest {

    @Test
    void testOfferAndPoll() {
        BlockingMessageQueue queue = new BlockingMessageQueue(5);

        assertTrue(queue.offer("hello"));
        assertEquals("hello", queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void testCapacityLimit() {
        BlockingMessageQueue queue = new BlockingMessageQueue(2);

        assertTrue(queue.offer("a"));
        assertTrue(queue.offer("b"));
        assertFalse(queue.offer("c"), "message 'c' should be dropped when full");
        assertEquals("a", queue.poll());
        assertEquals("b", queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void testFlush() {
        BlockingMessageQueue queue = new BlockingMessageQueue(5);

        queue.offer("one");
        queue.offer("two");
        queue.flush();

        assertEquals(0, queue.size());
        assertNull(queue.poll());
    }

    @Test
    void testClear() {
        BlockingMessageQueue queue = new BlockingMessageQueue(5);

        queue.offer("x");
        queue.offer("y");
        queue.clear();

        assertEquals(0, queue.size());
        assertNull(queue.poll());
    }

    @Test
    void testSizeAndIsEmpty() {
        BlockingMessageQueue queue = new BlockingMessageQueue(10);

        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());

        queue.offer("msg");

        assertEquals(1, queue.size());
        assertFalse(queue.isEmpty());
    }

    @Test
    void testCloseClearsQueueAndRejectsFutureMessages() {
        BlockingMessageQueue queue = new BlockingMessageQueue(2);
        queue.offer("queued");

        queue.close();

        assertTrue(queue.isEmpty());
        assertFalse(queue.offer("after shutdown"));
        assertNull(queue.poll());
    }

    @Test
    void testCapacityMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new BlockingMessageQueue(0));
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        // Bounded concurrency to avoid deadlocks during the test.
        final int threads = 4;
        final int perThread = 100;
        BlockingMessageQueue queue = new BlockingMessageQueue(50);

        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) {
                    queue.offer("m" + j);
                }
            });
            workers[i].start();
        }

        for (Thread t : workers) {
            t.join();
        }

        // At most capacity messages were ever offered successfully.
        assertTrue(queue.size() <= 50);
    }
}
