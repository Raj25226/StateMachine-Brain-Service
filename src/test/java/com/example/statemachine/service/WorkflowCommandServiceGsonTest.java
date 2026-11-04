package com.example.statemachine.service;

import com.example.statemachine.config.GsonConfiguration;
import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.api.WorkflowResponse;
import com.example.statemachine.dto.persistence.WorkflowIdempotencyRecord;
import com.example.statemachine.dto.workflow.WorkflowCommand;
import com.example.statemachine.enums.WorkflowOperation;
import com.example.statemachine.repository.JdbcWorkflowIdempotencyRepository;
import com.google.gson.Gson;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowCommandServiceGsonTest {
    @Test
    void replaysPreviouslyStoredResponseWithOriginalTimestampAndNewRequestId() {
        String savedResponse = """
                {"metadata":{"operation":"CREATE","requestId":"original-request","referenceId":"APP-101",
                "idempotencyKey":"key-1","workflowKey":"onboarding","status":"SUCCESS",
                "processedAt":"2026-11-04T06:00:00Z","idempotencyReplayed":false},
                "payload":{"referenceId":"APP-101","workflowKey":"onboarding","definitionVersion":1,
                "currentState":"OTP_VERIFICATION","nextEvent":"OTP_VERIFIED","nextState":"PROFILE_DETAILS",
                "completed":false,"availableTransitions":[{"event":"OTP_VERIFIED","nextState":"PROFILE_DETAILS"}],
                "pendingEvents":[]}}
                """;
        JdbcWorkflowIdempotencyRepository repository = new JdbcWorkflowIdempotencyRepository(null) {
            @Override
            public WorkflowIdempotencyRecord findAndLockByIdempotencyKey(String key) {
                return new WorkflowIdempotencyRecord(1, "fingerprint", savedResponse, 201);
            }
        };
        Gson gson = new GsonConfiguration().gson();
        WorkflowCommandService service = new WorkflowCommandService(null, repository, gson);
        WorkflowCommand command = new WorkflowCommand(new WorkflowCommandMetadata(
                "CREATE", "retry-request", "APP-101", null, "key-1", "onboarding"), WorkflowOperation.CREATE);
        var response = service.executeIdempotentCommand(command, "fingerprint");
        assertEquals(201, response.getStatusCode().value());
        WorkflowResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("retry-request", body.metadata().requestId());
        assertEquals(Instant.parse("2026-11-04T06:00:00Z"), body.metadata().processedAt());
        assertTrue(body.metadata().idempotencyReplayed());
        assertEquals("OTP_VERIFICATION", body.payload().currentState());
        assertEquals(body, gson.fromJson(gson.toJson(body), WorkflowResponse.class));
    }
}
