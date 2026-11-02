package com.example.statemachine.service;

import com.example.statemachine.dto.workflow.Fingerprint;
import com.example.statemachine.dto.workflow.Command;
import com.example.statemachine.enums.WorkflowOperation;
import com.example.statemachine.dto.api.Response;
import com.example.statemachine.dto.api.ResponseMetadata;
import com.example.statemachine.dto.api.WorkflowDetails;
import com.example.statemachine.exception.WorkflowFailure;
import com.example.statemachine.repository.JdbcWorkflowReplayRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import com.example.statemachine.dto.persistence.Replay;
import com.example.statemachine.dto.api.RequestMetadata;

/** Command gateway: dispatch and retry policy */
@Service
public class WorkflowCommandGateway {

    // Pin the existing fingerprint wire format independently of HTTP mapper defaults.
    private static final JsonMapper FINGERPRINT_JSON =
            JsonMapper.builder()
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false)
                    .configure(JsonWriteFeature.ESCAPE_FORWARD_SLASHES, false)
                    .build();

    private final WorkflowExecutionService workflowExecutionService;
    private final JdbcWorkflowReplayRepository replayRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final TransactionTemplate reservation;

    public WorkflowCommandGateway(
            WorkflowExecutionService workflowExecutionService,
            JdbcWorkflowReplayRepository replayRepository,
            ObjectMapper objectMapper,
            PlatformTransactionManager manager) {

        this.workflowExecutionService = workflowExecutionService;
        this.replayRepository = replayRepository;
        this.objectMapper = objectMapper;
        transaction = new TransactionTemplate(manager);
        reservation = new TransactionTemplate(manager);
        reservation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ResponseEntity<Response> execute(Command command) {

        String idempotencyKey = command.metadata().idempotencyKey();

        if (idempotencyKey == null) {
            return dispatch(command);
        }

        String fingerprint = calculateRequestFingerprint(command);

        try {
            reservation.executeWithoutResult(
                    status -> {
                        if (!replayRepository.exists(idempotencyKey))
                            replayRepository.reserve(idempotencyKey, fingerprint);
                    });
        } catch (DuplicateKeyException competingInsert) {
            if (!replayRepository.exists(idempotencyKey)) throw competingInsert;
        }

        return transaction.execute( status -> {
                    Replay saved = replayRepository.lock(idempotencyKey);

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
                                            ), original.payload(),
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
        );
    }

    private ResponseEntity<Response> dispatch(Command command) {
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


    private String calculateRequestFingerprint(Command command) {
        RequestMetadata m = command.metadata();
        try {
            return HexFormat.of()
                    .formatHex( MessageDigest.getInstance("SHA-256")
                        .digest( FINGERPRINT_JSON.writeValueAsBytes(
                                        new Fingerprint(
                                            command.operation(),
                                            m.referenceId(),
                                            m.event(),
                                            m.workflowKey()
                                        )
                                )
                        )
                    );
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot fingerprint command.", ex);
        }
    }

}
