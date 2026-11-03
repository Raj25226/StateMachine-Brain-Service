package com.example.statemachine.service;

import com.example.statemachine.dto.workflow.Command;
import com.example.statemachine.enums.WorkflowOperation;
import com.example.statemachine.dto.api.Response;
import com.example.statemachine.dto.api.ResponseMetadata;
import com.example.statemachine.dto.api.WorkflowDetails;
import com.example.statemachine.exception.WorkflowFailure;
import com.example.statemachine.repository.JdbcWorkflowReplayRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import com.example.statemachine.dto.persistence.Replay;
import com.example.statemachine.dto.api.RequestMetadata;


import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowCommandService {
    private final WorkflowExecutionService workflowExecutionService;
    private final JdbcWorkflowReplayRepository replayRepository;
    private final ObjectMapper objectMapper;

    public WorkflowCommandService(WorkflowExecutionService workflowExecutionService,
            JdbcWorkflowReplayRepository replayRepository, ObjectMapper objectMapper) {
        this.workflowExecutionService = workflowExecutionService;
        this.replayRepository = replayRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Response> executeCommand(Command command, String fingerprint) {
        String idempotencyKey = command.metadata().idempotencyKey();
        Replay saved = replayRepository.findAndLockByIdempotencyKey(idempotencyKey);

        if (!fingerprint.equals(saved.fingerprint())) {
            throw WorkflowFailure.conflict(
                    "IDEMPOTENCY_CONFLICT",
                    "This idempotency idempotencyKey is bound to different request inputs."
                );
        }

        if (saved.response() != null) {
            try {
                Response original = objectMapper.readValue(saved.response(), Response.class);
                ResponseMetadata m = original.metadata();
                return ResponseEntity.status(saved.status())
                        .body(
                            new Response(
                                new ResponseMetadata(
                                    m.operation(),
                                    command.metadata().requestId(),
                                    m.referenceId(),
                                    m.event(),
                                    m.idempotencyKey(),
                                    m.workflowKey(),
                                    m.status(),
                                    m.processedAt(),
                                    true
                                ),
                                original.payload(),
                                null
                            )
                        );
            } catch (Exception ex) {
                throw new IllegalStateException("Cannot restore saved response.", ex);
            }
        }

        ResponseEntity<Response> response = dispatch(command);
        try {
            replayRepository.updateReplay(
                    idempotencyKey,
                    objectMapper.writeValueAsString(response.getBody()),
                    response.getStatusCode().value()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot save replay response.", ex);
        }
        return response;
    }

    public ResponseEntity<Response> dispatch(Command command) {
        RequestMetadata metaData = command.metadata();
        WorkflowDetails workflowDetails;
        try {
            workflowDetails =
                    switch (command.operation()) {
                        case CREATE -> workflowExecutionService.createWorkflow(metaData.referenceId(), metaData.workflowKey());
                        case GET_STATE -> workflowExecutionService.getWorkflowState(metaData.referenceId());
                        case SUBMIT_EVENT -> workflowExecutionService.submitEvent(metaData);
                    };
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Workflow execution failed.", ex);
        }

        ResponseMetadata metadata = new ResponseMetadata(
                                        metaData.operation(),
                                        metaData.requestId(),
                                        metaData.referenceId(),
                                        metaData.event(),
                                        metaData.idempotencyKey(),
                                        workflowDetails.workflowKey(),
                                        "SUCCESS",
                                        Instant.now(),
                                        metaData.idempotencyKey() == null ? null : false
                                    );

        return ResponseEntity.status( command.operation() == WorkflowOperation.CREATE ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new Response(metadata, workflowDetails, null));
    }


}
