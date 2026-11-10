package com.example.statemachine.dto.api;

import java.time.Instant;

public record WorkflowResponseMetadata(
        String operation,
        String requestId,
        String referenceId,
        String event,
        String idempotencyKey,
        String workflowKey,
        String status,
        Instant processedAt,
        Boolean idempotencyReplayed) {}
