package com.example.statemachine.config;

import com.example.statemachine.dto.api.WorkflowRequest;
import com.example.statemachine.dto.workflow.WorkflowCommand;
import com.example.statemachine.enums.WorkflowOperation;
import com.example.statemachine.enums.WorkflowRequestMetadataFunction;
import com.example.statemachine.exception.WorkflowException;
import com.example.statemachine.validation.WorkflowRequestValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowRequestBindingTest {
    private final ObjectMapper mapper = requestMapper();
    private final WorkflowRequestValidator validator = new WorkflowRequestValidator();

    private static ObjectMapper requestMapper() {
        JsonMapper.Builder builder = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
        new JacksonConfiguration().strictRequestBinding().customize(builder);
        return builder.build();
    }

    @Test
    void createDefaultsToOnboarding() {
        WorkflowCommand command = command("""
                {"metadata":{"operation":"CREATE","requestId":"r","referenceId":"a","idempotencyKey":"k"},"payload":{}}
                """);
        assertEquals(WorkflowOperation.CREATE, command.operation());
        assertEquals("onboarding", command.metadata().workflowKey());
        assertEquals("k", command.metadata().idempotencyKey());
    }

    @Test
    void createUsesExplicitWorkflowKey() {
        WorkflowCommand command = command("""
                {"metadata":{"operation":"CREATE","requestId":"r","referenceId":"a","idempotencyKey":"k","workflowKey":"custom"},"payload":{}}
                """);
        assertEquals("custom", command.metadata().workflowKey());
    }

    @Test
    void getStateNeedsNoIdempotencyKey() {
        WorkflowCommand command = command("""
                {"metadata":{"operation":"GET_STATE","requestId":"r","referenceId":"a"},"payload":{}}
                """);
        assertEquals(WorkflowOperation.GET_STATE, command.operation());
        assertNull(command.metadata().idempotencyKey());
    }

    @Test
    void submitEventPreservesEvent() {
        WorkflowCommand command = command("""
                {"metadata":{"operation":"SUBMIT_EVENT","requestId":"r","referenceId":"a","event":"OTP_VERIFIED","idempotencyKey":"k"},"payload":{}}
                """);
        assertEquals(WorkflowOperation.SUBMIT_EVENT, command.operation());
        assertEquals("OTP_VERIFIED", command.metadata().event());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"operation\":\"GET_STATE\",\"requestId\":12,\"referenceId\":\"a\"}",
            "{\"operation\":\"GET_STATE\",\"requestId\":true,\"referenceId\":\"a\"}",
            "{\"operation\":\"GET_STATE\",\"requestId\":1.5,\"referenceId\":\"a\"}",
            "{\"operation\":\"GET_STATE\",\"requestId\":[],\"referenceId\":\"a\"}",
            "{\"operation\":\"GET_STATE\",\"requestId\":{},\"referenceId\":\"a\"}",
            "{\"operation\":\"GET_STATE\",\"requestId\":null,\"referenceId\":\"a\"}",
            "{\"operation\":\"GET_STATE\",\"requestId\":\"r\",\"referenceId\":\"a\",\"event\":null}",
            "{\"operation\":\"GET_STATE\",\"requestId\":\"r\",\"referenceId\":\"a\",\"idempotencyKey\":null}",
            "{\"operation\":\"CREATE\",\"requestId\":\"r\",\"referenceId\":\"a\",\"workflowKey\":null}",
            "{\"operation\":\"GET_STATE\",\"requestId\":\"r\",\"referenceId\":\"a\",\"unknown\":\"x\"}"
    })
    void rejectsInvalidMetadataDuringBinding(String metadata) {
        assertThrows(RuntimeException.class,
                () -> mapper.readValue("{\"metadata\":" + metadata + ",\"payload\":{}}", WorkflowRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"metadata\":{\"operation\":\"GET_STATE\",\"requestId\":\"r\",\"referenceId\":\"a\"},\"payload\":{\"file\":\"data\"}}",
            "{\"metadata\":{},\"payload\":[],\"unknown\":1}",
            "{\"metadata\":{},\"payload\":\"\"}",
            "{\"metadata\":{},\"payload\":[]}",
            "{\"metadata\":{},\"payload\":{},\"extra\":true}",
            "{\"metadata\":{},\"payload\":{},\"payload\":{}}",
            "{\"metadata\":{},\"payload\":{}} {}"
    })
    void rejectsInvalidEnvelopeDuringBinding(String json) {
        assertThrows(RuntimeException.class, () -> mapper.readValue(json, WorkflowRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"metadata\":null,\"payload\":{}}",
            "{\"metadata\":{},\"payload\":{}}",
            "{\"metadata\":{\"operation\":\"GET_STATE\",\"requestId\":\"r\",\"referenceId\":\"a\"}}",
            "{\"metadata\":{\"operation\":\"GET_STATE\",\"requestId\":\"r\",\"referenceId\":\"a\"},\"payload\":null}",
            "{\"metadata\":{\"operation\":\"CREATE\",\"requestId\":\"r\",\"referenceId\":\"a\"},\"payload\":{}}",
            "{\"metadata\":{\"operation\":\"SUBMIT_EVENT\",\"requestId\":\"r\",\"referenceId\":\"a\",\"idempotencyKey\":\"k\"},\"payload\":{}}",
            "{\"metadata\":{\"operation\":\"GET_STATE\",\"requestId\":\" \",\"referenceId\":\"a\"},\"payload\":{}}",
            "{\"metadata\":{\"operation\":\"get_state\",\"requestId\":\"r\",\"referenceId\":\"a\"},\"payload\":{}}"
    })
    void rejectsMissingOrInvalidRequiredFields(String json) {
        WorkflowRequest request = mapper.readValue(json, WorkflowRequest.class);
        assertThrows(WorkflowException.class, () -> validator.validate(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"operation\":\"GET_STATE\",\"requestId\":\"r\",\"referenceId\":\"a\",\"event\":\"OTP_VERIFIED\"}",
            "{\"operation\":\"GET_STATE\",\"requestId\":\"r\",\"referenceId\":\"a\",\"idempotencyKey\":\"k\"}",
            "{\"operation\":\"GET_STATE\",\"requestId\":\"r\",\"referenceId\":\"a\",\"workflowKey\":\"onboarding\"}",
            "{\"operation\":\"CREATE\",\"requestId\":\"r\",\"referenceId\":\"a\",\"idempotencyKey\":\"k\",\"event\":\"OTP_VERIFIED\"}",
            "{\"operation\":\"SUBMIT_EVENT\",\"requestId\":\"r\",\"referenceId\":\"a\",\"event\":\"OTP_VERIFIED\",\"idempotencyKey\":\"k\",\"workflowKey\":\"onboarding\"}"
    })
    void rejectsFieldsThatDoNotApplyToOperation(String metadata) {
        WorkflowRequest request = mapper.readValue("{\"metadata\":" + metadata + ",\"payload\":{}}", WorkflowRequest.class);
        assertThrows(WorkflowException.class, () -> validator.validate(request));
    }

    @Test
    void errorMetadataPreservesCallerValuesWithoutValidatingOrTruncating() {
        String requestId = "r".repeat(129);
        String json = "{\"metadata\":{\"operation\":\"UNKNOWN\",\"requestId\":\"" + requestId
                + "\",\"referenceId\":\"not-yet-validated\"},\"payload\":{}}";
        WorkflowRequest request = mapper.readValue(json, WorkflowRequest.class);
        assertThrows(WorkflowException.class, () -> validator.validate(request));
        assertEquals(requestId, WorkflowRequestMetadataFunction.EXTRACT.apply(request).requestId());
        assertEquals("UNKNOWN", WorkflowRequestMetadataFunction.EXTRACT.apply(request).operation());
        assertEquals("not-yet-validated", WorkflowRequestMetadataFunction.EXTRACT.apply(request).referenceId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABCDE1234F", "9876543210", "person@example.com", "format-rules-pending"})
    void referenceFormatValidationIsDeferred(String referenceId) {
        String json = "{\"metadata\":{\"operation\":\"GET_STATE\",\"requestId\":\"r\",\"referenceId\":\""
                + referenceId + "\"},\"payload\":{}}";
        assertEquals(referenceId, command(json).metadata().referenceId());
    }

    @Test
    void validationDoesNotImposeStorageLengthLimits() {
        String json = "{\"metadata\":{\"operation\":\"CREATE\",\"requestId\":\"" + "r".repeat(129)
                + "\",\"referenceId\":\"" + "a".repeat(101)
                + "\",\"idempotencyKey\":\"" + "k".repeat(129)
                + "\",\"workflowKey\":\"" + "w".repeat(101) + "\"},\"payload\":{}}";
        assertDoesNotThrow(() -> command(json));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE_ALL", "create", "GET", "SUBMIT", "UNKNOWN"})
    void operationMustBeSupported(String operation) {
        String json = "{\"metadata\":{\"operation\":\"" + operation
                + "\",\"requestId\":\"r\",\"referenceId\":\"a\"},\"payload\":{}}";
        WorkflowException failure = assertThrows(WorkflowException.class, () -> command(json));
        assertEquals("INVALID_REQUEST", failure.code());
        for (WorkflowOperation supported : WorkflowOperation.values()) {
            assertTrue(failure.getMessage().contains(supported.name()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"operation", "requestId", "referenceId", "idempotencyKey", "workflowKey"})
    void createRejectsBlankRequiredOrSuppliedFields(String field) {
        String json = """
                {"metadata":{"operation":"CREATE","requestId":"r","referenceId":"a","idempotencyKey":"k","workflowKey":"onboarding"},"payload":{}}
                """;
        WorkflowRequest request = mapper.readValue(json, WorkflowRequest.class);
        switch (field) {
            case "operation" -> request.metadata().setOperation(" ");
            case "requestId" -> request.metadata().setRequestId(" ");
            case "referenceId" -> request.metadata().setReferenceId(" ");
            case "idempotencyKey" -> request.metadata().setIdempotencyKey(" ");
            case "workflowKey" -> request.metadata().setWorkflowKey(" ");
        }
        assertThrows(WorkflowException.class, () -> validator.validate(request));
    }

    @Test
    void submitEventRejectsBlankEvent() {
        WorkflowRequest request = mapper.readValue("""
                {"metadata":{"operation":"SUBMIT_EVENT","requestId":"r","referenceId":"a","idempotencyKey":"k","event":" "},"payload":{}}
                """, WorkflowRequest.class);
        assertThrows(WorkflowException.class, () -> validator.validate(request));
    }

    private WorkflowCommand command(String json) {
        return validator.validate(mapper.readValue(json, WorkflowRequest.class));
    }
}
