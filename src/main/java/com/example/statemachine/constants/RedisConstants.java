package com.example.statemachine.constants;

/** Redis idempotency key namespace and atomic-operation resources. */
public final class RedisConstants {
    private RedisConstants() { }

    public static final String IDEMPOTENCY_KEY_PREFIX = "workflow:idempotency:";
    public static final String CLAIM_SCRIPT = "redis/idempotency-claim.lua";
    public static final String COMPLETE_SCRIPT = "redis/idempotency-complete.lua";
    public static final String RETRY_SCRIPT = "redis/idempotency-retry.lua";
}
