package com.minebridge.discord;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A bounded, thread-safe message queue for outgoing Discord messages.
 *
 * Capacity limits prevent unbounded memory growth.
 * When full, new offers return false (non-blocking).
 * Supports clearing during reload and closing during plugin shutdown.
 */
public class BlockingMessageQueue {

    private final int capacity;
    private final ArrayDeque<String> queue;
    private final ReentrantLock lock;
    private final Condition notFull;
    private final Condition notEmpty;
    private boolean closed;

    public BlockingMessageQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Queue capacity must be positive.");
        }
        this.capacity = capacity;
        this.queue = new ArrayDeque<>(capacity);
        this.lock = new ReentrantLock();
        this.notFull = lock.newCondition();
        this.notEmpty = lock.newCondition();
    }

    /**
     * Offer a message to the queue. Returns true if added, false if queue is full.
     * This is a non-blocking operation that avoids flooding.
     */
    public boolean offer(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        lock.lock();
        try {
            if (closed || queue.size() >= capacity) {
                return false;
            }
            queue.addLast(message);
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Offer a message with a timeout. Returns true if added within timeout.
     */
    public boolean offer(String message, long timeoutMs) throws InterruptedException {
        if (message == null || message.isBlank()) {
            return false;
        }
        lock.lock();
        try {
            while (!closed && queue.size() >= capacity) {
                if (!notFull.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                    return false; // Timeout waiting for space
                }
            }
            if (closed) {
                return false;
            }
            queue.addLast(message);
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Poll a message from the queue. Returns null if empty (non-blocking).
     */
    public String poll() {
        lock.lock();
        try {
            if (queue.isEmpty()) {
                return null;
            }
            String message = queue.removeFirst();
            notFull.signal();
            return message;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Flush all messages from the queue. Useful when reconnecting.
     */
    public void flush() {
        lock.lock();
        try {
            queue.clear();
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clear all messages from the queue.
     */
    public void clear() {
        lock.lock();
        try {
            queue.clear();
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            closed = true;
            queue.clear();
            notFull.signalAll();
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Current size of the queue (for monitoring).
     */
    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if the queue is empty.
     */
    public boolean isEmpty() {
        lock.lock();
        try {
            return queue.isEmpty();
        } finally {
            lock.unlock();
        }
    }
}