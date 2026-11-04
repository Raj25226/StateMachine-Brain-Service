package com.example.statemachine.enums;

import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.workflow.WorkflowCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WorkflowRequestFingerprintFunctionTest {
    @Test
    void preservesPreviouslyStoredFingerprintFormat() {
        assertEquals("4273767d05a4a616ac97713757dce4f74bbde76fa97e3774e709aa2adecfb5c4",
                WorkflowRequestFingerprintFunction.SHA_256.apply(command("req-1", "key-1", "APP-101")));
    }

    @Test
    void excludesCorrelationAndRetryKeysButIncludesBusinessInputs() {
        String original = WorkflowRequestFingerprintFunction.SHA_256.apply(command("req-1", "key-1", "APP-101"));
        assertEquals(original, WorkflowRequestFingerprintFunction.SHA_256.apply(command("req-2", "key-2", "APP-101")));
        assertNotEquals(original, WorkflowRequestFingerprintFunction.SHA_256.apply(command("req-1", "key-1", "APP-102")));
    }

    private WorkflowCommand command(String requestId, String idempotencyKey, String referenceId) {
        return new WorkflowCommand(new WorkflowCommandMetadata("CREATE", requestId, referenceId, null, idempotencyKey, "onboarding"),
                WorkflowOperation.CREATE);
    }
}
