package com.example.statemachine.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.example.statemachine.constants.RedisConstants;
import org.junit.jupiter.api.Test;

class RedisWorkflowIdempotencyConfigurationTest {
    @Test
    void acceptsInfiniteOrPositiveMinutesAndRejectsInvalidRanges() {
        assertDoesNotThrow(() -> new RedisWorkflowIdempotencyRepository(null, -1));
        assertDoesNotThrow(() -> new RedisWorkflowIdempotencyRepository(null, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RedisWorkflowIdempotencyRepository(null, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RedisWorkflowIdempotencyRepository(null, -2));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RedisWorkflowIdempotencyRepository(
                                null, RedisConstants.MAX_TTL_MINUTES + 1));
    }
}
