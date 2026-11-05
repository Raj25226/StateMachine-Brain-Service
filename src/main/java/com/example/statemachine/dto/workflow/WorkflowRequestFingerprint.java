package com.example.statemachine.dto.workflow;

import com.example.statemachine.enums.WorkflowOperation;

public record WorkflowRequestFingerprint(
        WorkflowOperation operation,
        String referenceId,
        String event,
        String workflowKey
) {}
