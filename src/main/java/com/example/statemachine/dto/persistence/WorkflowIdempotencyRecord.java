package com.example.statemachine.dto.persistence;

public record WorkflowIdempotencyRecord(
        String requestFingerprint,
        String responseJson,
        Integer httpStatus
) {}
