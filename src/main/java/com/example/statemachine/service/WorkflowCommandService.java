package com.example.statemachine.service;

import com.example.statemachine.constants.ApiErrorConstants;
import com.example.statemachine.constants.WorkflowConstants;
import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.api.WorkflowDetails;
import com.example.statemachine.dto.api.WorkflowResponse;
import com.example.statemachine.dto.api.WorkflowResponseMetadata;
import com.example.statemachine.dto.persistence.WorkflowIdempotencyRecord;
import com.example.statemachine.dto.workflow.WorkflowCommand;
import com.example.statemachine.enums.WorkflowOperation;
import com.example.statemachine.exception.WorkflowException;
import com.example.statemachine.repository.JdbcWorkflowIdempotencyRepository;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class WorkflowCommandService {

    private final WorkflowExecutionService workflowExecutionService;
    private final JdbcWorkflowIdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public WorkflowCommandService(WorkflowExecutionService workflowExecutionService,
            JdbcWorkflowIdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.workflowExecutionService = workflowExecutionService;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<WorkflowResponse> executeIdempotentCommand(WorkflowCommand command, String requestFingerprint) {
        String idempotencyKey = command.metadata().idempotencyKey();
        WorkflowIdempotencyRecord idempotencyRecord = idempotencyRepository.findAndLockByIdempotencyKey(idempotencyKey);

        if (!requestFingerprint.equals(idempotencyRecord.requestFingerprint())) {
            log.debug("workflow_idempotency_conflict operation={}", command.operation());
            throw WorkflowException.conflict(
                    ApiErrorConstants.IDEMPOTENCY_CONFLICT,
                    "This idempotencyKey is bound to different request inputs.");
        }

        if (idempotencyRecord.responseJson() != null) {
            log.debug("workflow_idempotency_replay operation={} status={}", command.operation(), idempotencyRecord.httpStatus());
            return restoreIdempotentResponse(idempotencyRecord, command.metadata().requestId());
        }

        ResponseEntity<WorkflowResponse> workflowResponse = dispatchWorkflowCommand(command);
        try {
            idempotencyRepository.saveSuccessfulResponse(
                    idempotencyKey,
                    objectMapper.writeValueAsString(workflowResponse.getBody()),
                    workflowResponse.getStatusCode().value()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot save replay response.", exception);
        }
        return workflowResponse;
    }

    private ResponseEntity<WorkflowResponse> restoreIdempotentResponse(WorkflowIdempotencyRecord idempotencyRecord, String requestId) {
        try {
            WorkflowResponse originalResponse = objectMapper.readValue(idempotencyRecord.responseJson(), WorkflowResponse.class);
            WorkflowResponseMetadata responseMetadata = originalResponse.metadata();
            WorkflowResponseMetadata replayedResponseMetadata = new WorkflowResponseMetadata(
                    responseMetadata.operation(), requestId, responseMetadata.referenceId(), responseMetadata.event(),
                    responseMetadata.idempotencyKey(), responseMetadata.workflowKey(), responseMetadata.status(),
                    responseMetadata.processedAt(), true);
            return ResponseEntity.status(idempotencyRecord.httpStatus())
                    .body(new WorkflowResponse(replayedResponseMetadata, originalResponse.payload(), null));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot restore saved response.", exception);
        }
    }

    public ResponseEntity<WorkflowResponse> dispatchWorkflowCommand(WorkflowCommand command) {
        log.debug("workflow_command_dispatching operation={}", command.operation());
        WorkflowCommandMetadata requestMetadata = command.metadata();
        WorkflowDetails workflowDetails;
        try {
            workflowDetails = switch (command.operation()) {
                case CREATE -> workflowExecutionService.createWorkflow(
                        requestMetadata.referenceId(), requestMetadata.workflowKey());
                case GET_STATE -> workflowExecutionService.getWorkflowState(requestMetadata.referenceId());
                case SUBMIT_EVENT -> workflowExecutionService.submitEvent(requestMetadata);
            };
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Workflow execution failed.", exception);
        }

        WorkflowResponseMetadata responseMetadata = new WorkflowResponseMetadata(
                requestMetadata.operation(), requestMetadata.requestId(), requestMetadata.referenceId(),
                requestMetadata.event(), requestMetadata.idempotencyKey(), workflowDetails.workflowKey(),
                WorkflowConstants.STATUS_SUCCESS, Instant.now(), requestMetadata.idempotencyKey() == null ? null : false);
        HttpStatus httpStatus = command.operation() == WorkflowOperation.CREATE ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(httpStatus).body(new WorkflowResponse(responseMetadata, workflowDetails, null));
    }
}
