package com.example.statemachine.enums;

import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.api.WorkflowRequest;
import com.example.statemachine.dto.api.WorkflowRequestMetadata;
import java.util.function.Function;

/** Copies caller metadata without validation, normalization or dropping invalid values. */
public enum WorkflowRequestMetadataFunction
        implements Function<WorkflowRequest, WorkflowCommandMetadata> {
    EXTRACT;

    @Override
    public WorkflowCommandMetadata apply(WorkflowRequest request) {
        if (request == null || request.metadata() == null) {
            return null;
        }
        WorkflowRequestMetadata requestMetadata = request.metadata();
        return new WorkflowCommandMetadata(
                requestMetadata.getOperation(),
                requestMetadata.getRequestId(),
                requestMetadata.getReferenceId(),
                requestMetadata.getEvent(),
                requestMetadata.getIdempotencyKey(),
                requestMetadata.getWorkflowKey());
    }
}
