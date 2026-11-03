package com.example.statemachine.service;

import com.example.statemachine.dto.workflow.Fingerprint;
import com.example.statemachine.dto.workflow.Command;
import com.example.statemachine.dto.api.Response;
import com.example.statemachine.repository.JdbcWorkflowReplayRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import java.security.MessageDigest;
import java.util.HexFormat;
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

    private final WorkflowCommandService commandService;
    private final IdempotencyService idempotencyService;
    private final JdbcWorkflowReplayRepository replayRepository;

    public WorkflowCommandGateway(WorkflowCommandService commandService,
            IdempotencyService idempotencyService,
            JdbcWorkflowReplayRepository replayRepository) {
        this.commandService = commandService;
        this.idempotencyService = idempotencyService;
        this.replayRepository = replayRepository;
    }

    public ResponseEntity<Response> execute(Command command) {
        String idempotencyKey = command.metadata().idempotencyKey();
        if (idempotencyKey == null) {
            return commandService.dispatch(command);
        }

        String fingerprint = calculateRequestFingerprint(command);
        try {
            idempotencyService.reserve(idempotencyKey, fingerprint);
        } catch (DuplicateKeyException competingInsert) {
            if (!replayRepository.exists(idempotencyKey)) {
                throw competingInsert;
            }
        }
        return commandService.executeCommand(command, fingerprint);
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
