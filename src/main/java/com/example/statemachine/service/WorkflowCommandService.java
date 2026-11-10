package com.example.statemachine.service;

import com.example.statemachine.constants.WorkflowConstants;
import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.api.WorkflowDetails;
import com.example.statemachine.dto.api.WorkflowResponse;
import com.example.statemachine.dto.api.WorkflowResponseMetadata;
import com.example.statemachine.dto.workflow.WorkflowCommand;
import com.example.statemachine.enums.WorkflowOperation;
import com.example.statemachine.repository.RedisWorkflowIdempotencyRepository;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
@AllArgsConstructor
public class WorkflowCommandService {

    private final WorkflowExecutionService workflowExecutionService;
    private final RedisWorkflowIdempotencyRepository idempotencyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ResponseEntity<WorkflowResponse> executeTransactionalCommand(
            WorkflowCommand command, String ownerToken) {
        // Redis retries become eligible only after JDBC confirms that the transaction rolled back.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            idempotencyRepository.markRetryable(
                                    command.metadata().idempotencyKey(), ownerToken);
                        }
                    }
                });
        return dispatchWorkflowCommand(command);
    }

    public ResponseEntity<WorkflowResponse> dispatchWorkflowCommand(WorkflowCommand command) {
        log.debug("workflow_command_dispatching operation={}", command.operation());
        WorkflowCommandMetadata requestMetadata = command.metadata();
        WorkflowDetails workflowDetails;
        try {
            workflowDetails =
                    switch (command.operation()) {
                        case CREATE ->
                                workflowExecutionService.createWorkflow(
                                        requestMetadata.referenceId(),
                                        requestMetadata.workflowKey());
                        case GET_STATE ->
                                workflowExecutionService.getWorkflowState(
                                        requestMetadata.referenceId());
                        case SUBMIT_EVENT -> workflowExecutionService.submitEvent(requestMetadata);
                    };
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Workflow execution failed.", exception);
        }

        WorkflowResponseMetadata responseMetadata =
                new WorkflowResponseMetadata(
                        requestMetadata.operation(),
                        requestMetadata.requestId(),
                        requestMetadata.referenceId(),
                        requestMetadata.event(),
                        requestMetadata.idempotencyKey(),
                        workflowDetails.workflowKey(),
                        WorkflowConstants.STATUS_SUCCESS,
                        Instant.now(),
                        requestMetadata.idempotencyKey() == null ? null : false);
        HttpStatus httpStatus =
                command.operation() == WorkflowOperation.CREATE
                        ? HttpStatus.CREATED
                        : HttpStatus.OK;
        return ResponseEntity.status(httpStatus)
                .body(new WorkflowResponse(responseMetadata, workflowDetails, null));
    }
}
