package com.example.statemachine.serialization;

import static org.junit.jupiter.api.Assertions.*;

import com.example.statemachine.config.GsonConfiguration;
import com.example.statemachine.config.WorkflowJsonConfiguration;
import com.example.statemachine.dto.api.ApiError;
import com.example.statemachine.dto.api.WorkflowDetails;
import com.example.statemachine.dto.api.WorkflowRequest;
import com.example.statemachine.dto.api.WorkflowResponse;
import com.example.statemachine.dto.api.WorkflowResponseMetadata;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;

class WorkflowGsonSerializationTest {
    private final Gson gson = new GsonConfiguration().gson();

    @Test
    void omitsOptionalNullsButAlwaysWritesErrorPayload() {
        WorkflowResponseMetadata metadata =
                new WorkflowResponseMetadata(
                        "GET_STATE",
                        "req-1",
                        "APP-101",
                        null,
                        null,
                        "finaag",
                        "FAILED",
                        Instant.parse("2026-11-04T06:00:00Z"),
                        null);
        WorkflowResponse error =
                new WorkflowResponse(
                        metadata, null, new ApiError("WORKFLOW_NOT_FOUND", "Not found."));
        JsonObject errorJson = gson.fromJson(gson.toJson(error), JsonObject.class);
        assertTrue(errorJson.has("payload"));
        assertTrue(errorJson.get("payload").isJsonNull());
        assertFalse(errorJson.getAsJsonObject("metadata").has("event"));
        assertFalse(errorJson.getAsJsonObject("metadata").has("idempotencyReplayed"));
        assertEquals(error, gson.fromJson(gson.toJson(error), WorkflowResponse.class));
    }

    @Test
    void omitsTerminalNextFieldsAndRoundTripsIsoTimestamps() {
        WorkflowResponseMetadata metadata =
                new WorkflowResponseMetadata(
                        "GET_STATE",
                        "req-1",
                        "APP-101",
                        null,
                        null,
                        "finaag",
                        "SUCCESS",
                        Instant.parse("2026-11-04T06:00:00.123456789Z"),
                        null);
        WorkflowDetails details =
                new WorkflowDetails(
                        "APP-101", "finaag", 1, "COMPLETED", null, null, true, List.of(), null);
        WorkflowResponse response = new WorkflowResponse(metadata, details, null);
        JsonObject json = gson.fromJson(gson.toJson(response), JsonObject.class);
        assertFalse(json.has("error"));
        assertFalse(json.getAsJsonObject("payload").has("nextEvent"));
        assertFalse(json.getAsJsonObject("payload").has("nextState"));
        assertFalse(json.getAsJsonObject("payload").has("eventOutcome"));
        assertEquals(response, gson.fromJson(gson.toJson(response), WorkflowResponse.class));
    }

    @Test
    void requestAdapterRoundTripsOptionalFieldsWithoutIntroducingNulls() {
        WorkflowRequest request =
                gson.fromJson(
                        """
                {"metadata":{"operation":"GET_STATE","requestId":"req-1","referenceId":"APP-101"},"payload":{}}
                """,
                        WorkflowRequest.class);
        WorkflowRequest restored = gson.fromJson(gson.toJson(request), WorkflowRequest.class);
        assertEquals("GET_STATE", restored.metadata().getOperation());
        assertEquals("APP-101", restored.metadata().getReferenceId());
        assertNotNull(restored.payload());
    }

    @Test
    void prioritizesGsonForWorkflowEnvelopesWithoutInterceptingSwagger() {
        HttpMessageConverters.ServerBuilder builder =
                HttpMessageConverters.forServer().registerDefaults();
        new WorkflowJsonConfiguration(gson).configureMessageConverters(builder);
        WorkflowGsonHttpMessageConverter converter =
                assertInstanceOf(
                        WorkflowGsonHttpMessageConverter.class, builder.build().iterator().next());
        assertTrue(converter.canRead(WorkflowRequest.class, MediaType.APPLICATION_JSON));
        assertTrue(converter.canWrite(WorkflowResponse.class, MediaType.APPLICATION_JSON));
        assertFalse(converter.canWrite(String.class, MediaType.APPLICATION_JSON));
        assertFalse(
                converter.canWrite(
                        io.swagger.v3.oas.models.OpenAPI.class, MediaType.APPLICATION_JSON));
    }
}
