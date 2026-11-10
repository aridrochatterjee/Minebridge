package com.minebridge.discord;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordServiceDeliveryTest {

    @Test
    void reportsSynchronousDiscordSubmissionFailuresWithoutThrowing() {
        AtomicBoolean reported = new AtomicBoolean();

        boolean submitted = DiscordService.submitSafely(
                () -> {
                    throw new IllegalStateException("test failure");
                },
                exception -> reported.set(exception instanceof IllegalStateException));

        assertFalse(submitted);
        assertTrue(reported.get());
    }

    @Test
    void acceptsSuccessfulAsynchronousRequestSubmission() {
        AtomicBoolean submitted = new AtomicBoolean();

        boolean accepted = DiscordService.submitSafely(
                () -> submitted.set(true),
                exception -> {
                    throw new AssertionError("Unexpected submission failure", exception);
                });

        assertTrue(accepted);
        assertTrue(submitted.get());
    }
}
