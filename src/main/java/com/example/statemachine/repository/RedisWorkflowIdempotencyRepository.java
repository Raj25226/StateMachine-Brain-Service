package com.example.statemachine.repository;

import com.example.statemachine.constants.ApiErrorConstants;
import com.example.statemachine.constants.RedisConstants;
import com.example.statemachine.dto.persistence.WorkflowIdempotencyClaim;
import com.example.statemachine.enums.IdempotencyClaimOutcome;
import com.example.statemachine.exception.WorkflowException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class RedisWorkflowIdempotencyRepository {
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> claimScript;
    private final DefaultRedisScript<Long> completeScript;
    private final DefaultRedisScript<Long> retryScript;

    public RedisWorkflowIdempotencyRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        claimScript = new DefaultRedisScript<>();
        claimScript.setLocation(new ClassPathResource(RedisConstants.CLAIM_SCRIPT));
        claimScript.setResultType(List.class);
        completeScript = new DefaultRedisScript<>();
        completeScript.setLocation(new ClassPathResource(RedisConstants.COMPLETE_SCRIPT));
        completeScript.setResultType(Long.class);
        retryScript = new DefaultRedisScript<>();
        retryScript.setLocation(new ClassPathResource(RedisConstants.RETRY_SCRIPT));
        retryScript.setResultType(Long.class);
    }

    public WorkflowIdempotencyClaim claim(
            String idempotencyKey, String fingerprint, String ownerToken) {
        try {
            List<?> result =
                    redisTemplate.execute(
                            claimScript, key(idempotencyKey), fingerprint, ownerToken);
            if (result == null || result.isEmpty()) {
                throw new IllegalStateException("Redis returned an empty idempotency claim.");
            }
            IdempotencyClaimOutcome outcome =
                    IdempotencyClaimOutcome.valueOf((String) result.getFirst());
            return outcome == IdempotencyClaimOutcome.REPLAY
                    ? new WorkflowIdempotencyClaim(
                            outcome,
                            (String) result.get(1),
                            Integer.valueOf((String) result.get(2)))
                    : new WorkflowIdempotencyClaim(outcome, null, null);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    public void complete(
            String idempotencyKey, String ownerToken, String responseJson, int httpStatus) {
        try {
            Long completed =
                    redisTemplate.execute(
                            completeScript,
                            key(idempotencyKey),
                            ownerToken,
                            responseJson,
                            Integer.toString(httpStatus));
            if (!Long.valueOf(1).equals(completed)) {
                throw new WorkflowException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        ApiErrorConstants.IDEMPOTENCY_UNAVAILABLE,
                        "Idempotency ownership was lost after processing. Do not use a new retry key.");
            }
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    public void markRetryable(String idempotencyKey, String ownerToken) {
        try {
            redisTemplate.execute(retryScript, key(idempotencyKey), ownerToken);
        } catch (DataAccessException exception) {
            // Keep PROCESSING if recovery storage fails; automatic re-execution would be unsafe.
            log.error("workflow_idempotency_rollback_recovery_failed", exception);
        }
    }

    private List<String> key(String idempotencyKey) {
        return List.of(RedisConstants.IDEMPOTENCY_KEY_PREFIX + idempotencyKey);
    }

    private WorkflowException unavailable(DataAccessException exception) {
        log.error("workflow_idempotency_store_unavailable", exception);
        return new WorkflowException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorConstants.IDEMPOTENCY_UNAVAILABLE,
                "Idempotency storage is unavailable. Retry with the same idempotency key.");
    }
}
