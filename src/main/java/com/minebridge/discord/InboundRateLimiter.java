package com.minebridge.discord;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

final class InboundRateLimiter {

    private static final int MAX_TRACKED_USERS = 1024;

    private final int perUserCapacity;
    private final long perUserRefillNanos;
    private final int globalCapacity;
    private final long globalRefillNanos;
    private final LongSupplier clock;
    private final Map<String, TokenBucket> userBuckets =
            new LinkedHashMap<>(16, 0.75f, true);
    private final TokenBucket globalBucket;

    InboundRateLimiter(
            int perUserCapacity,
            long perUserRefillNanos,
            int globalCapacity,
            long globalRefillNanos,
            LongSupplier clock) {
        if (perUserCapacity < 1 || perUserRefillNanos < 1
                || globalCapacity < 1 || globalRefillNanos < 1) {
            throw new IllegalArgumentException("Rate-limit capacities and intervals must be positive.");
        }
        this.perUserCapacity = perUserCapacity;
        this.perUserRefillNanos = perUserRefillNanos;
        this.globalCapacity = globalCapacity;
        this.globalRefillNanos = globalRefillNanos;
        this.clock = clock;
        this.globalBucket = new TokenBucket(globalCapacity, globalRefillNanos, clock.getAsLong());
    }

    synchronized boolean allow(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        long now = clock.getAsLong();
        TokenBucket userBucket = userBuckets.computeIfAbsent(userId, ignored ->
                new TokenBucket(perUserCapacity, perUserRefillNanos, now));
        if (userBuckets.size() > MAX_TRACKED_USERS) {
            String eldest = userBuckets.keySet().iterator().next();
            userBuckets.remove(eldest);
        }

        if (!userBucket.canConsume(now) || !globalBucket.canConsume(now)) {
            return false;
        }
        userBucket.consume();
        globalBucket.consume();
        return true;
    }

    private static final class TokenBucket {

        private final double capacity;
        private final double tokensPerNano;
        private double tokens;
        private long lastRefillNanos;

        private TokenBucket(int capacity, long refillNanos, long now) {
            this.capacity = capacity;
            this.tokensPerNano = (double) capacity / refillNanos;
            this.tokens = capacity;
            this.lastRefillNanos = now;
        }

        private boolean canConsume(long now) {
            refill(now);
            return tokens >= 1.0;
        }

        private void consume() {
            tokens -= 1.0;
        }

        private void refill(long now) {
            long elapsed = now - lastRefillNanos;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed * tokensPerNano);
                lastRefillNanos = now;
            }
        }
    }
}
