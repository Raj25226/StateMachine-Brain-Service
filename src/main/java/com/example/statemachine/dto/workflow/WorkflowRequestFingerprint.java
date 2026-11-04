package com.example.statemachine.dto.workflow;

import com.example.statemachine.constants.ApiPropertyConstants;
import com.example.statemachine.enums.WorkflowOperation;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ApiPropertyConstants.OPERATION, ApiPropertyConstants.REFERENCE_ID, ApiPropertyConstants.EVENT, ApiPropertyConstants.WORKFLOW_KEY})
public record WorkflowRequestFingerprint(
        WorkflowOperation operation,
        String referenceId,
        String event,
        String workflowKey
) {}
