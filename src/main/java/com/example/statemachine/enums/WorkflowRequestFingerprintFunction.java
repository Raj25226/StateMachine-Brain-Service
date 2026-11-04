package com.example.statemachine.enums;

import com.example.statemachine.constants.WorkflowConstants;
import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.workflow.WorkflowCommand;
import com.example.statemachine.dto.workflow.WorkflowRequestFingerprint;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Function;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/** Computes the stable request fingerprint used by existing idempotency reservations. */
public enum WorkflowRequestFingerprintFunction implements Function<WorkflowCommand, String> {
    SHA_256;

    // Pin the existing fingerprint wire format independently of HTTP mapper defaults.
    private static final JsonMapper FINGERPRINT_JSON_MAPPER =
            JsonMapper.builder()
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false)
                    .configure(JsonWriteFeature.ESCAPE_FORWARD_SLASHES, false)
                    .build();

    @Override
    public String apply(WorkflowCommand command) {
        WorkflowCommandMetadata commandMetadata = command.metadata();
        try {
            WorkflowRequestFingerprint fingerprintInput = new WorkflowRequestFingerprint(
                    command.operation(), commandMetadata.referenceId(), commandMetadata.event(), commandMetadata.workflowKey());
            byte[] serializedFingerprint = FINGERPRINT_JSON_MAPPER.writeValueAsBytes(fingerprintInput);
            byte[] fingerprintDigest = MessageDigest.getInstance(WorkflowConstants.FINGERPRINT_ALGORITHM).digest(serializedFingerprint);
            return HexFormat.of().formatHex(fingerprintDigest);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot fingerprint command.", exception);
        }
    }

}
