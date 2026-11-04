package com.example.statemachine.enums;

import com.example.statemachine.config.GsonConfiguration;
import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.workflow.WorkflowCommand;
import com.google.gson.Gson;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WorkflowRequestFingerprintFunctionTest {
    private final Gson gson = new GsonConfiguration().gson();
    @Test
    void preservesPreviouslyStoredFingerprintFormat() {
        assertEquals("4273767d05a4a616ac97713757dce4f74bbde76fa97e3774e709aa2adecfb5c4",
                WorkflowRequestFingerprintFunction.SHA_256.apply(command("req-1", "key-1", "APP-101"), gson));
    }

    @Test
    void excludesCorrelationAndRetryKeysButIncludesBusinessInputs() {
        String original = WorkflowRequestFingerprintFunction.SHA_256.apply(command("req-1", "key-1", "APP-101"), gson);
        assertEquals(original, WorkflowRequestFingerprintFunction.SHA_256.apply(command("req-2", "key-2", "APP-101"), gson));
        assertNotEquals(original, WorkflowRequestFingerprintFunction.SHA_256.apply(command("req-1", "key-1", "APP-102"), gson));
    }

    static Stream<Arguments> previouslyStoredEscapedFingerprints() {
        return Stream.of(
                Arguments.of("APP/<tag>&='value'", "a9c37e49e5bc3b3438ce0a41856a3204aecbc1b1068168e55c9b8c718ef1b68b"),
                Arguments.of("APP-" + (char) 11 + "-" + (char) 0x2028 + "-" + (char) 0x2029,
                        "c925f7200616f3a352c7389431a982324e4fb64e14308e7f28f42500c1e9e5b3"),
                Arguments.of("APP-\\u2028-\\u000b", "2f9b60c36c6727a6a19062a74362ec8c35f139fef1d5a454dd8c7213da3aa9ef"));
    }

    @ParameterizedTest
    @MethodSource("previouslyStoredEscapedFingerprints")
    void preservesEscapingUsedByExistingFingerprintReservations(String referenceId, String expectedFingerprint) {
        assertEquals(expectedFingerprint,
                WorkflowRequestFingerprintFunction.SHA_256.apply(command("req-1", "key-1", referenceId), gson));
    }

    private WorkflowCommand command(String requestId, String idempotencyKey, String referenceId) {
        return new WorkflowCommand(new WorkflowCommandMetadata("CREATE", requestId, referenceId, null, idempotencyKey, "onboarding"),
                WorkflowOperation.CREATE);
    }
}
