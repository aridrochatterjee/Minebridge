package com.minebridge.discord;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboundMessageProcessorTest {

    @Test
    void deliversAcceptedMessagesInFifoOrder() {
        FakeScheduler scheduler = new FakeScheduler();
        List<String> delivered = new ArrayList<>();
        InboundMessageProcessor<String> processor = processor(
                4, 4, scheduler, delivered::add, new AtomicLong());

        assertEquals(InboundMessageProcessor.SubmissionResult.ACCEPTED,
                processor.submit("user-1", "first"));
        assertEquals(InboundMessageProcessor.SubmissionResult.ACCEPTED,
                processor.submit("user-2", "second"));

        scheduler.runNext();

        assertEquals(List.of("first", "second"), delivered);
        assertEquals(0, processor.size());
        assertEquals(0, scheduler.pendingTasks());
    }

    @Test
    void enforcesCapacityAndDropsNewestMessageWhenFull() {
        FakeScheduler scheduler = new FakeScheduler();
        List<String> delivered = new ArrayList<>();
        InboundMessageProcessor<String> processor = processor(
                2, 2, scheduler, delivered::add, new AtomicLong());

        assertEquals(InboundMessageProcessor.SubmissionResult.ACCEPTED,
                processor.submit("user", "first"));
        assertEquals(InboundMessageProcessor.SubmissionResult.ACCEPTED,
                processor.submit("user", "second"));
        assertEquals(InboundMessageProcessor.SubmissionResult.QUEUE_FULL,
                processor.submit("user", "dropped"));
        assertEquals(2, processor.size());
        assertEquals(2, processor.capacity());

        scheduler.runNext();

        assertEquals(List.of("first", "second"), delivered);
    }

    @Test
    void appliesPerUserTokenBucketLimitUsingInjectedMonotonicClock() {
        AtomicLong clock = new AtomicLong();
        InboundRateLimiter limiter = new InboundRateLimiter(
                2, 10, 100, 1_000, clock::get);

        assertTrue(limiter.allow("user-1"));
        assertTrue(limiter.allow("user-1"));
        assertFalse(limiter.allow("user-1"));
        assertTrue(limiter.allow("user-2"));
        clock.set(4);
        assertFalse(limiter.allow("user-1"));
        clock.set(5);
        assertTrue(limiter.allow("user-1"));
        clock.set(10);
        assertTrue(limiter.allow("user-1"));
    }

    @Test
    void appliesGlobalLimitWithoutConsumingTheRejectedUsersToken() {
        AtomicLong clock = new AtomicLong();
        InboundRateLimiter limiter = new InboundRateLimiter(
                5, 100, 1, 10, clock::get);

        assertTrue(limiter.allow("user-1"));
        assertFalse(limiter.allow("user-2"));
        clock.set(10);
        assertTrue(limiter.allow("user-2"));
    }

    @Test
    void concurrentUsersShareOneBoundedQueueAndOneScheduledDrain() throws Exception {
        int threadCount = 8;
        int messagesPerThread = 20;
        FakeScheduler scheduler = new FakeScheduler();
        InboundRateLimiter limiter = new InboundRateLimiter(
                100, 1_000, 1_000, 1_000, () -> 0);
        InboundMessageProcessor<String> processor = new InboundMessageProcessor<>(
                threadCount * messagesPerThread,
                10,
                limiter,
                scheduler::schedule,
                ignored -> { });
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        AtomicInteger accepted = new AtomicInteger();

        for (int thread = 0; thread < threadCount; thread++) {
            int user = thread;
            Thread worker = new Thread(() -> {
                ready.countDown();
                await(start);
                for (int message = 0; message < messagesPerThread; message++) {
                    if (processor.submit("user-" + user, "message-" + message)
                            == InboundMessageProcessor.SubmissionResult.ACCEPTED) {
                        accepted.incrementAndGet();
                    }
                }
            });
            workers.add(worker);
            worker.start();
        }

        ready.await();
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }

        assertEquals(threadCount * messagesPerThread, accepted.get());
        assertEquals(threadCount * messagesPerThread, processor.size());
        assertEquals(1, scheduler.pendingTasks());
        assertEquals(1, scheduler.maxPendingTasks());
    }

    @Test
    void schedulesNoMoreThanOneDrainAndProcessesOnlyTheConfiguredBatchPerTick() {
        FakeScheduler scheduler = new FakeScheduler();
        List<String> delivered = new ArrayList<>();
        InboundMessageProcessor<String> processor = processor(
                8, 2, scheduler, delivered::add, new AtomicLong());
        for (int i = 0; i < 5; i++) {
            assertEquals(InboundMessageProcessor.SubmissionResult.ACCEPTED,
                    processor.submit("user-" + i, "message-" + i));
        }
        assertEquals(1, scheduler.pendingTasks());
        assertEquals(1, scheduler.scheduleCount());

        scheduler.runNext();
        assertEquals(List.of("message-0", "message-1"), delivered);
        assertEquals(1, scheduler.pendingTasks());
        assertEquals(2, scheduler.scheduleCount());
        assertEquals(1, scheduler.maxPendingTasks());

        scheduler.runNext();
        assertEquals(List.of("message-0", "message-1", "message-2", "message-3"), delivered);
        assertEquals(1, scheduler.pendingTasks());
        scheduler.runNext();

        assertEquals(
                List.of("message-0", "message-1", "message-2", "message-3", "message-4"),
                delivered);
        assertEquals(0, scheduler.pendingTasks());
        assertEquals(1, scheduler.maxPendingTasks());
    }

    @Test
    void shutdownDiscardsQueuedMessagesAndRejectsFutureMessages() {
        FakeScheduler scheduler = new FakeScheduler();
        List<String> delivered = new ArrayList<>();
        InboundMessageProcessor<String> processor = processor(
                4, 2, scheduler, delivered::add, new AtomicLong());
        processor.submit("user-1", "queued-1");
        processor.submit("user-2", "queued-2");

        processor.close();
        assertEquals(InboundMessageProcessor.SubmissionResult.CLOSED,
                processor.submit("user-3", "after-shutdown"));
        scheduler.runNext();

        assertTrue(delivered.isEmpty());
        assertEquals(0, processor.size());
        assertEquals(0, scheduler.pendingTasks());
    }

    @Test
    void shutdownDuringDrainStopsTheRestOfTheCurrentBatch() {
        FakeScheduler scheduler = new FakeScheduler();
        List<String> delivered = new ArrayList<>();
        AtomicReference<InboundMessageProcessor<String>> processorRef =
                new AtomicReference<>();
        InboundMessageProcessor<String> processor = processor(
                4,
                4,
                scheduler,
                message -> {
                    delivered.add(message);
                    processorRef.get().close();
                },
                new AtomicLong());
        processorRef.set(processor);
        processor.submit("user-1", "first");
        processor.submit("user-2", "second");

        scheduler.runNext();

        assertEquals(List.of("first"), delivered);
        assertEquals(0, processor.size());
        assertEquals(0, scheduler.pendingTasks());
    }

    @Test
    void schedulerRejectionClosesProcessorAndDropsQueuedWork() {
        List<String> delivered = new ArrayList<>();
        InboundMessageProcessor<String> processor = new InboundMessageProcessor<>(
                4,
                2,
                limiter(new AtomicLong()),
                ignored -> false,
                delivered::add);

        assertEquals(InboundMessageProcessor.SubmissionResult.SCHEDULER_UNAVAILABLE,
                processor.submit("user", "not-scheduled"));
        assertEquals(InboundMessageProcessor.SubmissionResult.CLOSED,
                processor.submit("user", "after-rejection"));
        assertTrue(delivered.isEmpty());
        assertEquals(0, processor.size());
    }

    private static InboundMessageProcessor<String> processor(
            int capacity,
            int batchSize,
            FakeScheduler scheduler,
            java.util.function.Consumer<String> consumer,
            AtomicLong clock) {
        return new InboundMessageProcessor<>(
                capacity,
                batchSize,
                limiter(clock),
                scheduler::schedule,
                consumer);
    }

    private static InboundRateLimiter limiter(AtomicLong clock) {
        return new InboundRateLimiter(100, 1_000, 1_000, 1_000, clock::get);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static final class FakeScheduler {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private int scheduleCount;
        private int maxPendingTasks;

        synchronized boolean schedule(Runnable task) {
            tasks.addLast(task);
            scheduleCount++;
            maxPendingTasks = Math.max(maxPendingTasks, tasks.size());
            return true;
        }

        void runNext() {
            Runnable task;
            synchronized (this) {
                task = tasks.pollFirst();
            }
            if (task != null) {
                task.run();
            }
        }

        synchronized int pendingTasks() {
            return tasks.size();
        }

        synchronized int scheduleCount() {
            return scheduleCount;
        }

        synchronized int maxPendingTasks() {
            return maxPendingTasks;
        }
    }
}
