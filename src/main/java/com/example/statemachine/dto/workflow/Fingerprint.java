package com.example.statemachine.dto.workflow;

import com.example.statemachine.enums.WorkflowOperation;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"operation", "referenceId", "event", "workflowKey"})
public record Fingerprint(
        WorkflowOperation operation,
        String referenceId,
        String event,
        String workflowKey
) {}
