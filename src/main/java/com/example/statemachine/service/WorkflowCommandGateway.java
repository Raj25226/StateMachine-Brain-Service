package com.example.statemachine.service;

import lombok.AllArgsConstructor;
import com.example.statemachine.dto.api.WorkflowResponse;
import com.example.statemachine.dto.workflow.WorkflowCommand;
import com.example.statemachine.enums.WorkflowRequestFingerprintFunction;
import com.example.statemachine.repository.JdbcWorkflowIdempotencyRepository;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/** Command gateway: dispatch and retry policy */
@Slf4j
@Service
@AllArgsConstructor
public class WorkflowCommandGateway {

    private final WorkflowCommandService workflowCommandService;
    private final WorkflowIdempotencyService workflowIdempotencyService;
    private final JdbcWorkflowIdempotencyRepository idempotencyRepository;
    private final Gson gson;

    public ResponseEntity<WorkflowResponse> executeWorkflowCommand(WorkflowCommand command) {
        String idempotencyKey = command.metadata().idempotencyKey();
        if (idempotencyKey == null) {
            return workflowCommandService.dispatchWorkflowCommand(command);
        }

        String requestFingerprint = WorkflowRequestFingerprintFunction.SHA_256.apply(command, gson);
        try {
            workflowIdempotencyService.reserveIdempotencyKey(idempotencyKey, requestFingerprint);
        } catch (DuplicateKeyException concurrentReservation) {
            if (!idempotencyRepository.existsByIdempotencyKey(idempotencyKey)) {
                throw concurrentReservation;
            }
            log.debug("workflow_idempotency_reservation_raced operation={}", command.operation());
        }
        return workflowCommandService.executeIdempotentCommand(command, requestFingerprint);
    }

}
