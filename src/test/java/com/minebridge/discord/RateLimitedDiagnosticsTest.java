package com.minebridge.discord;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitedDiagnosticsTest {

    @Test
    void rateLimitsEachDiagnosticCategoryIndependently() {
        AtomicLong clock = new AtomicLong(100);
        RateLimitedDiagnostics diagnostics =
                new RateLimitedDiagnostics(50, clock::get);

        assertTrue(diagnostics.shouldEmit("queue"));
        assertFalse(diagnostics.shouldEmit("queue"));
        assertTrue(diagnostics.shouldEmit("channel"));

        clock.addAndGet(50);
        assertTrue(diagnostics.shouldEmit("queue"));
    }
}
