package com.minebridge.achievement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class AdvancementAnnouncementDeduplicator {

    private static final long DEFAULT_WINDOW_MILLIS = 10_000L;
    private static final int DEFAULT_CAPACITY = 4096;

    private final long windowMillis;
    private final int capacity;
    private final LinkedHashMap<Key, Long> announcements = new LinkedHashMap<>();

    public AdvancementAnnouncementDeduplicator() {
        this(DEFAULT_WINDOW_MILLIS, DEFAULT_CAPACITY);
    }

    AdvancementAnnouncementDeduplicator(long windowMillis, int capacity) {
        if (windowMillis < 0 || capacity < 1) {
            throw new IllegalArgumentException("Invalid advancement deduplication limits.");
        }
        this.windowMillis = windowMillis;
        this.capacity = capacity;
    }

    public synchronized boolean alreadyAnnounced(UUID playerId, String advancementKey) {
        long now = System.currentTimeMillis();
        announcements.entrySet().removeIf(entry -> now - entry.getValue() > windowMillis);
        Key key = new Key(playerId, advancementKey);
        Long previous = announcements.get(key);
        if (previous != null && now - previous <= windowMillis) {
            return true;
        }
        announcements.put(key, now);
        while (announcements.size() > capacity) {
            announcements.remove(announcements.keySet().iterator().next());
        }
        return false;
    }

    private record Key(UUID playerId, String advancementKey) {
    }
}
