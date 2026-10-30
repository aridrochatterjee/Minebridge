package com.minebridge.discord;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;

final class InboundMessageProcessor<T> {

    static final int DEFAULT_CAPACITY = 100;
    static final int DEFAULT_BATCH_SIZE = 10;
    static final int DEFAULT_PER_USER_BURST = 3;
    static final long DEFAULT_PER_USER_REFILL_NANOS = 5_000_000_000L;
    static final int DEFAULT_GLOBAL_BURST = 10;
    static final long DEFAULT_GLOBAL_REFILL_NANOS = 1_000_000_000L;

    enum SubmissionResult {
        ACCEPTED,
        RATE_LIMITED,
        QUEUE_FULL,
        CLOSED,
        INVALID_MESSAGE,
        SCHEDULER_UNAVAILABLE
    }

    @FunctionalInterface
    interface DrainScheduler {
        boolean schedule(Runnable task);
    }

    record ChatMessage(
            String template,
            String player,
            String displayName,
            String username,
            String content,
            String timestamp) {
    }

    private final Object lock = new Object();
    private final int capacity;
    private final int batchSize;
    private final ArrayDeque<T> queue;
    private final InboundRateLimiter rateLimiter;
    private final DrainScheduler scheduler;
    private final Consumer<T> consumer;
    private boolean drainScheduled;
    private boolean closed;

    InboundMessageProcessor(
            int capacity,
            int batchSize,
            InboundRateLimiter rateLimiter,
            DrainScheduler scheduler,
            Consumer<T> consumer) {
        if (capacity < 1 || batchSize < 1) {
            throw new IllegalArgumentException("Inbound queue capacity and batch size must be positive.");
        }
        this.capacity = capacity;
        this.batchSize = batchSize;
        this.queue = new ArrayDeque<>(capacity);
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.consumer = Objects.requireNonNull(consumer);
    }

    SubmissionResult submit(String userId, T message) {
        if (message == null) {
            return SubmissionResult.INVALID_MESSAGE;
        }
        boolean scheduleDrain = false;
        synchronized (lock) {
            if (closed) {
                return SubmissionResult.CLOSED;
            }
            if (queue.size() >= capacity) {
                return SubmissionResult.QUEUE_FULL;
            }
            if (!rateLimiter.allow(userId)) {
                return SubmissionResult.RATE_LIMITED;
            }
            queue.addLast(message);
            if (!drainScheduled) {
                drainScheduled = true;
                scheduleDrain = true;
            }
        }

        if (scheduleDrain && !scheduler.schedule(this::drain)) {
            close();
            return SubmissionResult.SCHEDULER_UNAVAILABLE;
        }
        return SubmissionResult.ACCEPTED;
    }

    void close() {
        synchronized (lock) {
            closed = true;
            drainScheduled = false;
            queue.clear();
        }
    }

    int size() {
        synchronized (lock) {
            return queue.size();
        }
    }

    int capacity() {
        return capacity;
    }

    private void drain() {
        int processed = 0;
        while (processed < batchSize) {
            T message;
            synchronized (lock) {
                if (closed) {
                    drainScheduled = false;
                    return;
                }
                message = queue.pollFirst();
                if (message == null) {
                    drainScheduled = false;
                    return;
                }
            }
            try {
                consumer.accept(message);
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
            processed++;
        }

        synchronized (lock) {
            if (closed) {
                drainScheduled = false;
                return;
            }
            if (queue.isEmpty()) {
                drainScheduled = false;
                return;
            }
        }

        if (!scheduler.schedule(this::drain)) {
            close();
        }
    }
}
