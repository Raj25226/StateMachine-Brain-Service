package com.example.statemachine.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.example.statemachine.config.GsonConfiguration;
import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.workflow.WorkflowCommand;
import com.google.gson.Gson;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WorkflowRequestFingerprintFunctionTest {
    private final Gson gson = new GsonConfiguration().gson();

    @Test
    void usesStableFinaagFingerprintFormat() {
        assertEquals(
                "2e8042563f4ad596b2dba4a3a16633483c347d15c0c85605c17f3ff4d1fc14f7",
                WorkflowRequestFingerprintFunction.SHA_256.apply(
                        command("req-1", "key-1", "APP-101"), gson));
    }

    @Test
    void excludesCorrelationAndRetryKeysButIncludesBusinessInputs() {
        String original =
                WorkflowRequestFingerprintFunction.SHA_256.apply(
                        command("req-1", "key-1", "APP-101"), gson);
        assertEquals(
                original,
                WorkflowRequestFingerprintFunction.SHA_256.apply(
                        command("req-2", "key-2", "APP-101"), gson));
        assertNotEquals(
                original,
                WorkflowRequestFingerprintFunction.SHA_256.apply(
                        command("req-1", "key-1", "APP-102"), gson));
    }

    static Stream<Arguments> escapedFinaagFingerprints() {
        return Stream.of(
                Arguments.of(
                        "APP/<tag>&='value'",
                        "4dc6839610faa7a4fde92048a947b6bdab39ea367ab83bf2b8236571c3f70616"),
                Arguments.of(
                        "APP-" + (char) 11 + "-" + (char) 0x2028 + "-" + (char) 0x2029,
                        "2283067d694e5e91b5ab43a20f2e95d4d22e1cd3c350257c72c3c4784eac7049"),
                Arguments.of(
                        "APP-\\u2028-\\u000b",
                        "e09c64c7df1524d35900a73f494b5708b14a11abb4652f45d71c41407dbdf8a6"));
    }

    @ParameterizedTest
    @MethodSource("escapedFinaagFingerprints")
    void usesStableEscapingInFinaagFingerprints(String referenceId, String expectedFingerprint) {
        assertEquals(
                expectedFingerprint,
                WorkflowRequestFingerprintFunction.SHA_256.apply(
                        command("req-1", "key-1", referenceId), gson));
    }

    private WorkflowCommand command(String requestId, String idempotencyKey, String referenceId) {
        return new WorkflowCommand(
                new WorkflowCommandMetadata(
                        "CREATE", requestId, referenceId, null, idempotencyKey, "finaag"),
                WorkflowOperation.CREATE);
    }
}
