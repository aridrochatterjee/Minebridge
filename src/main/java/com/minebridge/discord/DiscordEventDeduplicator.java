package com.minebridge.discord;

import java.util.LinkedHashMap;
import java.util.UUID;

public final class DiscordEventDeduplicator {

    private static final long DEFAULT_WINDOW_MILLIS = 10_000L;
    private static final int DEFAULT_CAPACITY = 4096;

    private final long windowMillis;
    private final int capacity;
    private final LinkedHashMap<Key, Long> events = new LinkedHashMap<>();

    public DiscordEventDeduplicator() {
        this(DEFAULT_WINDOW_MILLIS, DEFAULT_CAPACITY);
    }

    DiscordEventDeduplicator(long windowMillis, int capacity) {
        if (windowMillis < 0 || capacity < 1) {
            throw new IllegalArgumentException("Invalid Discord event deduplication limits.");
        }
        this.windowMillis = windowMillis;
        this.capacity = capacity;
    }

    public synchronized boolean alreadySent(UUID playerId, String eventType) {
        long now = System.currentTimeMillis();
        events.entrySet().removeIf(entry -> now - entry.getValue() > windowMillis);
        Key key = new Key(playerId, eventType);
        Long previous = events.get(key);
        if (previous != null && now - previous <= windowMillis) {
            return true;
        }
        events.put(key, now);
        while (events.size() > capacity) {
            events.remove(events.keySet().iterator().next());
        }
        return false;
    }

    private record Key(UUID playerId, String eventType) {
    }
}
