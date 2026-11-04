package com.example.statemachine.dto.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowResponseMetadata(
        String operation,
        String requestId,
        String referenceId,
        String event,
        String idempotencyKey,
        String workflowKey,
        String status,
        Instant processedAt,
        Boolean idempotencyReplayed
) { }
