package com.minebridge.discord;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

public final class RateLimitedDiagnostics {

    private final long intervalNanos;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Long> lastEmitted = new ConcurrentHashMap<>();

    public RateLimitedDiagnostics(long intervalNanos) {
        this(intervalNanos, System::nanoTime);
    }

    RateLimitedDiagnostics(long intervalNanos, LongSupplier clock) {
        if (intervalNanos < 0) {
            throw new IllegalArgumentException("Rate-limit interval cannot be negative.");
        }
        this.intervalNanos = intervalNanos;
        this.clock = clock;
    }

    public boolean shouldEmit(String category) {
        long now = clock.getAsLong();
        AtomicBoolean emit = new AtomicBoolean();
        lastEmitted.compute(category, (key, previous) -> {
            if (previous == null || now - previous >= intervalNanos) {
                emit.set(true);
                return now;
            }
            return previous;
        });
        return emit.get();
    }
}
