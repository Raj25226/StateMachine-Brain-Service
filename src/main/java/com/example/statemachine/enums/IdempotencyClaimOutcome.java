package com.example.statemachine.enums;

public enum IdempotencyClaimOutcome {
    CLAIMED,
    REPLAY,
    CONFLICT,
    IN_PROGRESS
}
