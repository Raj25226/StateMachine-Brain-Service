package com.example.statemachine.dto.api;

/** Validated command metadata and recoverable metadata for error responses. */
public record WorkflowCommandMetadata(
        String operation,
        String requestId,
        String referenceId,
        String event,
        String idempotencyKey,
        String workflowKey
) { }
