package com.example.statemachine.enums;

import com.example.statemachine.constants.WorkflowConstants;
import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.workflow.WorkflowCommand;
import com.example.statemachine.dto.workflow.WorkflowRequestFingerprint;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.BiFunction;

/** Computes the stable request fingerprint used by existing idempotency reservations. */
public enum WorkflowRequestFingerprintFunction
        implements BiFunction<WorkflowCommand, Gson, String> {
    SHA_256;

    @Override
    public String apply(WorkflowCommand command, Gson gson) {
        WorkflowCommandMetadata commandMetadata = command.metadata();
        try {
            WorkflowRequestFingerprint fingerprintInput =
                    new WorkflowRequestFingerprint(
                            command.operation(),
                            commandMetadata.referenceId(),
                            commandMetadata.event(),
                            commandMetadata.workflowKey());
            String fingerprintJson = gson.toJson(fingerprintInput);
            // Keep existing retry fingerprints stable across JSON libraries: uppercase control
            // escapes and literal Unicode line separators match the established wire format.
            StringBuilder canonicalJson = new StringBuilder();
            for (int index = 0; index < fingerprintJson.length(); index++) {
                char character = fingerprintJson.charAt(index);
                if (character == '\\' && index + 1 < fingerprintJson.length()) {
                    char escape = fingerprintJson.charAt(++index);
                    if (escape == 'u') {
                        String hexadecimal = fingerprintJson.substring(index + 1, index + 5);
                        char decoded = (char) Integer.parseInt(hexadecimal, 16);
                        if (decoded == '\u2028' || decoded == '\u2029') {
                            canonicalJson.append(decoded);
                        } else {
                            canonicalJson
                                    .append('\\')
                                    .append('u')
                                    .append(hexadecimal.toUpperCase(Locale.ROOT));
                        }
                        index += 4;
                    } else {
                        canonicalJson.append('\\').append(escape);
                    }
                } else {
                    canonicalJson.append(character);
                }
            }
            byte[] serializedFingerprint =
                    canonicalJson.toString().getBytes(StandardCharsets.UTF_8);
            byte[] fingerprintDigest =
                    MessageDigest.getInstance(WorkflowConstants.FINGERPRINT_ALGORITHM)
                            .digest(serializedFingerprint);
            return HexFormat.of().formatHex(fingerprintDigest);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot fingerprint command.", exception);
        }
    }
}
