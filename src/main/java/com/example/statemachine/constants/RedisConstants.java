package com.example.statemachine.constants;

/** Redis idempotency key namespace and atomic-operation resources. */
public final class RedisConstants {
    private RedisConstants() {}

    public static final String IDEMPOTENCY_KEY_PREFIX = "workflow:idempotency:";
    public static final String CLAIM_SCRIPT = "redis/idempotency-claim.lua";
    public static final String COMPLETE_SCRIPT = "redis/idempotency-complete.lua";
    public static final String RETRY_SCRIPT = "redis/idempotency-retry.lua";
    public static final long INFINITE_TTL_MINUTES = -1;
    public static final long MAX_TTL_MINUTES = Long.MAX_VALUE / 60_000;
    public static final String TTL_MINUTES_PROPERTY = "${workflow.idempotency.ttl-minutes:-1}";
    public static final String INVALID_TTL =
            "Idempotency TTL must be -1 or positive whole minutes within the supported range.";
    public static final String TTL_CONFIGURED = "workflow_idempotency_ttl_configured minutes={}";
}
