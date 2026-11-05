package com.example.statemachine.repository;

import com.example.statemachine.constants.RedisConstants;
import com.example.statemachine.enums.IdempotencyClaimOutcome;
import com.example.statemachine.exception.WorkflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** Run against a disposable Redis instance by setting REDIS_INTEGRATION_PORT. */
@EnabledIfEnvironmentVariable(named = "REDIS_INTEGRATION_PORT", matches = ".+")
class RedisWorkflowIdempotencyIntegrationTest {
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate template;
    private RedisWorkflowIdempotencyRepository repository;
    private final String key = "test-" + UUID.randomUUID();

    @BeforeEach
    void connect() {
        connectionFactory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("REDIS_INTEGRATION_PORT")));
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
        assertEquals(IdempotencyClaimOutcome.CLAIMED, repository.claim(key, "fingerprint", "owner-1").outcome());
        assertEquals(IdempotencyClaimOutcome.CONFLICT, repository.claim(key, "different", "owner-2").outcome());
        assertEquals(IdempotencyClaimOutcome.IN_PROGRESS, repository.claim(key, "fingerprint", "owner-2").outcome());
        assertThrows(WorkflowException.class, () -> repository.complete(key, "owner-2", "{}", 201));
        repository.complete(key, "owner-1", "{\"preserved\":true}", 201);
        var replay = repository.claim(key, "fingerprint", "owner-3");
        assertEquals(IdempotencyClaimOutcome.REPLAY, replay.outcome());
        assertEquals("{\"preserved\":true}", replay.responseJson());
        assertEquals(201, replay.httpStatus());
        assertEquals(-1L, template.getExpire(RedisConstants.IDEMPOTENCY_KEY_PREFIX + key));
    }

    @Test
    void rollbackAllowsRetryButStaleOwnersCannotModifyItsResult() {
        repository.claim(key, "fingerprint", "owner-1");
        repository.markRetryable(key, "wrong-owner");
        assertEquals(IdempotencyClaimOutcome.IN_PROGRESS, repository.claim(key, "fingerprint", "owner-2").outcome());
        repository.markRetryable(key, "owner-1");
        assertEquals(IdempotencyClaimOutcome.CONFLICT, repository.claim(key, "different", "owner-2").outcome());
        assertEquals(IdempotencyClaimOutcome.CLAIMED, repository.claim(key, "fingerprint", "owner-2").outcome());
        assertThrows(WorkflowException.class, () -> repository.complete(key, "owner-1", "{}", 200));
        repository.markRetryable(key, "owner-1");
        repository.complete(key, "owner-2", "{}", 200);
        assertEquals(IdempotencyClaimOutcome.REPLAY, repository.claim(key, "fingerprint", "owner-3").outcome());
    }

    @Test
    void concurrentRequestsHaveOnlyOneOwner() throws Exception {
        List<Callable<IdempotencyClaimOutcome>> attempts = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            String owner = "owner-" + index;
            attempts.add(() -> repository.claim(key, "fingerprint", owner).outcome());
        }
        try (var executor = Executors.newFixedThreadPool(8)) {
            long claimed = 0;
            for (var result : executor.invokeAll(attempts)) {
                if (result.get() == IdempotencyClaimOutcome.CLAIMED) claimed++;
            }
            assertEquals(1, claimed);
        }
    }
}
