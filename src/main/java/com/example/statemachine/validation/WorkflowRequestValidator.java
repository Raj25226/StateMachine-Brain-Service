package com.example.statemachine.validation;

import com.example.statemachine.constants.ApiPropertyConstants;
import com.example.statemachine.constants.WorkflowConstants;
import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.api.WorkflowRequest;
import com.example.statemachine.dto.api.WorkflowRequestMetadata;
import com.example.statemachine.dto.workflow.WorkflowCommand;
import com.example.statemachine.enums.RequestMetadataValidationConsumer;
import com.example.statemachine.enums.WorkflowOperation;
import com.example.statemachine.enums.WorkflowOperationParserFunction;
import com.example.statemachine.exception.WorkflowException;
import org.springframework.stereotype.Component;

@Component
public class WorkflowRequestValidator {

    public WorkflowCommand validate(WorkflowRequest request) {
        if (request == null || request.metadata() == null) {
            throw WorkflowException.invalid(
                    ApiPropertyConstants.METADATA + " must be a JSON object.");
        }
        if (request.payload() == null) {
            throw WorkflowException.invalid(
                    ApiPropertyConstants.PAYLOAD + " must be an empty JSON object.");
        }

        WorkflowRequestMetadata metadata = request.metadata();
        WorkflowOperation operation =
                WorkflowOperationParserFunction.PARSE.apply(metadata.getOperation());
        RequestMetadataValidationConsumer.REQUIRE_NON_BLANK.accept(
                metadata.getRequestId(), ApiPropertyConstants.REQUEST_ID);
        RequestMetadataValidationConsumer.REQUIRE_NON_BLANK.accept(
                metadata.getReferenceId(), ApiPropertyConstants.REFERENCE_ID);
        // TODO: Add PAN, mobile or email validation once supported reference types are defined.

        switch (operation) {
            case CREATE -> {
                RequestMetadataValidationConsumer.FORBID_FIELD.accept(
                        metadata.getEvent(), ApiPropertyConstants.EVENT);
                RequestMetadataValidationConsumer.REQUIRE_NON_BLANK.accept(
                        metadata.getIdempotencyKey(), ApiPropertyConstants.IDEMPOTENCY_KEY);
                if (metadata.getWorkflowKey() != null) {
                    RequestMetadataValidationConsumer.REQUIRE_NON_BLANK.accept(
                            metadata.getWorkflowKey(), ApiPropertyConstants.WORKFLOW_KEY);
                }
            }
            case GET_STATE -> {
                RequestMetadataValidationConsumer.FORBID_FIELD.accept(
                        metadata.getEvent(), ApiPropertyConstants.EVENT);
                RequestMetadataValidationConsumer.FORBID_FIELD.accept(
                        metadata.getWorkflowKey(), ApiPropertyConstants.WORKFLOW_KEY);
                RequestMetadataValidationConsumer.FORBID_FIELD.accept(
                        metadata.getIdempotencyKey(), ApiPropertyConstants.IDEMPOTENCY_KEY);
            }
            case SUBMIT_EVENT -> {
                RequestMetadataValidationConsumer.REQUIRE_NON_BLANK.accept(
                        metadata.getEvent(), ApiPropertyConstants.EVENT);
                RequestMetadataValidationConsumer.REQUIRE_NON_BLANK.accept(
                        metadata.getIdempotencyKey(), ApiPropertyConstants.IDEMPOTENCY_KEY);
                RequestMetadataValidationConsumer.FORBID_FIELD.accept(
                        metadata.getWorkflowKey(), ApiPropertyConstants.WORKFLOW_KEY);
            }
        }

        String workflowKey =
                operation == WorkflowOperation.CREATE
                        ? (metadata.getWorkflowKey() == null
                                ? WorkflowConstants.DEFAULT_WORKFLOW_KEY
                                : metadata.getWorkflowKey())
                        : null;
        return new WorkflowCommand(
                new WorkflowCommandMetadata(
                        metadata.getOperation(),
                        metadata.getRequestId(),
                        metadata.getReferenceId(),
                        metadata.getEvent(),
                        metadata.getIdempotencyKey(),
                        workflowKey),
                operation);
    }
}
