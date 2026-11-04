package com.example.statemachine.dto.persistence;

public record WorkflowIdempotencyRecord(
        Integer id,
        String requestFingerprint,
        String responseJson,
        Integer httpStatus
) {}
