package com.example.statemachine.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.example.statemachine.constants.RedisConstants;
import com.example.statemachine.dto.persistence.WorkflowIdempotencyClaim;
import com.example.statemachine.enums.IdempotencyClaimOutcome;
import com.example.statemachine.exception.WorkflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Run against a disposable Redis instance by setting REDIS_INTEGRATION_PORT. */
@EnabledIfEnvironmentVariable(named = "REDIS_INTEGRATION_PORT", matches = ".+")
class RedisWorkflowIdempotencyIntegrationTest {
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate template;
    private RedisWorkflowIdempotencyRepository repository;
    private final String key = "test-" + UUID.randomUUID();

    @BeforeEach
    void connect() {
        connectionFactory =
                new LettuceConnectionFactory(
                        "127.0.0.1", Integer.parseInt(System.getenv("REDIS_INTEGRATION_PORT")));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        template = new StringRedisTemplate(connectionFactory);
        repository = new RedisWorkflowIdempotencyRepository(template);
    }

    @AfterEach
    void disconnect() {
        template.delete(RedisConstants.IDEMPOTENCY_KEY_PREFIX + key);
        connectionFactory.destroy();
    }

    @Test
    void checksFingerprintsOwnershipAndSuccessfulReplayAtomically() {
        assertEquals(
                IdempotencyClaimOutcome.CLAIMED,
                repository.claim(key, "fingerprint", "owner-1").outcome());
        assertEquals(
                IdempotencyClaimOutcome.CONFLICT,
                repository.claim(key, "different", "owner-2").outcome());
        assertEquals(
                IdempotencyClaimOutcome.IN_PROGRESS,
                repository.claim(key, "fingerprint", "owner-2").outcome());
        assertThrows(WorkflowException.class, () -> repository.complete(key, "owner-2", "{}", 201));
        repository.complete(key, "owner-1", "{\"preserved\":true}", 201);
        WorkflowIdempotencyClaim replay = repository.claim(key, "fingerprint", "owner-3");
        assertEquals(IdempotencyClaimOutcome.REPLAY, replay.outcome());
        assertEquals("{\"preserved\":true}", replay.responseJson());
        assertEquals(201, replay.httpStatus());
        assertEquals(-1L, template.getExpire(RedisConstants.IDEMPOTENCY_KEY_PREFIX + key));
    }

    @Test
    void rollbackAllowsRetryButStaleOwnersCannotModifyItsResult() {
        repository.claim(key, "fingerprint", "owner-1");
        repository.markRetryable(key, "wrong-owner");
        assertEquals(
                IdempotencyClaimOutcome.IN_PROGRESS,
                repository.claim(key, "fingerprint", "owner-2").outcome());
        repository.markRetryable(key, "owner-1");
        assertEquals(
                IdempotencyClaimOutcome.CONFLICT,
                repository.claim(key, "different", "owner-2").outcome());
        assertEquals(
                IdempotencyClaimOutcome.CLAIMED,
                repository.claim(key, "fingerprint", "owner-2").outcome());
        assertThrows(WorkflowException.class, () -> repository.complete(key, "owner-1", "{}", 200));
        repository.markRetryable(key, "owner-1");
        repository.complete(key, "owner-2", "{}", 200);
        assertEquals(
                IdempotencyClaimOutcome.REPLAY,
                repository.claim(key, "fingerprint", "owner-3").outcome());
    }

    @Test
    void completedClaimsExpireWithoutReplayOrStaleOwnersRefreshingTheTtl() throws Exception {
        RedisWorkflowIdempotencyRepository expiring =
                new RedisWorkflowIdempotencyRepository(template, 1);
        String redisKey = RedisConstants.IDEMPOTENCY_KEY_PREFIX + key;
        expiring.claim(key, "fingerprint", "owner-1");
        assertEquals(-1L, template.getExpire(redisKey));
        expiring.complete(key, "owner-1", "{}", 200);
        assertTrue(template.getExpire(redisKey) > 0 && template.getExpire(redisKey) <= 60);
        template.expire(redisKey, java.time.Duration.ofSeconds(10));
        assertEquals(
                IdempotencyClaimOutcome.REPLAY,
                expiring.claim(key, "fingerprint", "owner-2").outcome());
        assertEquals(
                IdempotencyClaimOutcome.CONFLICT,
                expiring.claim(key, "different", "owner-2").outcome());
        expiring.markRetryable(key, "owner-1");
        assertThrows(WorkflowException.class, () -> expiring.complete(key, "owner-1", "{}", 200));
        assertTrue(template.getExpire(redisKey) > 0 && template.getExpire(redisKey) <= 10);
        // Accelerate the clock on this disposable test key rather than waiting a minute.
        template.expire(redisKey, java.time.Duration.ofMillis(1));
        for (int attempt = 0;
                attempt < 100 && Boolean.TRUE.equals(template.hasKey(redisKey));
                attempt++) {
            Thread.sleep(10);
        }
        assertFalse(template.hasKey(redisKey));
        assertEquals(
                IdempotencyClaimOutcome.CLAIMED,
                expiring.claim(key, "different", "owner-3").outcome());
        assertEquals(-1L, template.getExpire(redisKey));
    }

    @Test
    void retryableClaimsExpireButReclaimedExecutionIsPersistent() {
        RedisWorkflowIdempotencyRepository expiring =
                new RedisWorkflowIdempotencyRepository(template, 2);
        String redisKey = RedisConstants.IDEMPOTENCY_KEY_PREFIX + key;
        expiring.claim(key, "fingerprint", "owner-1");
        expiring.markRetryable(key, "wrong-owner");
        assertEquals(-1L, template.getExpire(redisKey));
        expiring.markRetryable(key, "owner-1");
        assertTrue(template.getExpire(redisKey) > 0 && template.getExpire(redisKey) <= 120);
        assertEquals(
                IdempotencyClaimOutcome.CLAIMED,
                expiring.claim(key, "fingerprint", "owner-2").outcome());
        assertEquals(-1L, template.getExpire(redisKey));
        expiring.markRetryable(key, "owner-1");
        assertEquals(
                IdempotencyClaimOutcome.IN_PROGRESS,
                expiring.claim(key, "fingerprint", "owner-3").outcome());
        assertEquals(-1L, template.getExpire(redisKey));
        // Switching back to the default -1 keeps finalized records persistent.
        repository.complete(key, "owner-2", "{}", 200);
        assertEquals(-1L, template.getExpire(redisKey));
    }

    @Test
    void concurrentRequestsHaveOnlyOneOwner() throws Exception {
        List<Callable<IdempotencyClaimOutcome>> attempts = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            String owner = "owner-" + index;
            attempts.add(() -> repository.claim(key, "fingerprint", owner).outcome());
        }
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            long claimed = 0;
            for (Future<IdempotencyClaimOutcome> result : executor.invokeAll(attempts)) {
                if (result.get() == IdempotencyClaimOutcome.CLAIMED) claimed++;
            }
            assertEquals(1, claimed);
        }
    }
}
