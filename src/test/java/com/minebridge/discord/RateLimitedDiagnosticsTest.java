package com.minebridge.discord;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitedDiagnosticsTest {

    @Test
    void zeroIntervalEmitsEveryCallRegardlessOfClockMoves() {
        AtomicLong clock = new AtomicLong(100);
        RateLimitedDiagnostics diagnostics = new RateLimitedDiagnostics(0, clock::get);

        for (int i = 0; i < 10; i++) {
            assertTrue(diagnostics.shouldEmit("any"), "zero interval should emit every call");
        }
    }

    @Test
    void negativeIntervalConstructorThrows() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimitedDiagnostics(-1));
    }

    @Test
    void firstCallAlwaysEmitsRegardlessOfHighInitialClockValue() {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE);
        RateLimitedDiagnostics diagnostics = new RateLimitedDiagnostics(1_000_000, clock::get);

        assertTrue(diagnostics.shouldEmit("first"),
                "the very first diagnostic must always be emitted");
    }

    @Test
    void allowsSubsequentCallsWithinIntervalAndReturnsFalseForLater() {
        AtomicLong clock = new AtomicLong(100);
        RateLimitedDiagnostics diagnostics = new RateLimitedDiagnostics(100, clock::get);

        assertTrue(diagnostics.shouldEmit("same"));
        assertFalse(diagnostics.shouldEmit("same"),
                "second call within the same interval must not be emitted");
        clock.addAndGet(99);
        assertFalse(diagnostics.shouldEmit("same"),
                "call 99 nanos later must still be suppressed");
        clock.addAndGet(1);
        assertTrue(diagnostics.shouldEmit("same"),
                "call after crossing the interval threshold is emitted once");
    }
}
