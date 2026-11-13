package com.example.statemachine.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.workflow.WorkflowStepContext;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AntiCorruptionLayerClientTest {
    @Test
    void sendsPinnedWorkflowContextAndRequiresExplicitSuccess() throws Exception {
        AtomicReference<String> body = new AtomicReference<String>();
        AtomicInteger status = new java.util.concurrent.atomic.AtomicInteger(200);
        AtomicReference<String> response = new AtomicReference<>("{\"status\":\"SUCCEEDED\"}");
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(
                "/api/steps/execute",
                exchange -> {
                    body.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status.get(), bytes.length);
                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(bytes);
                    }
                });
        server.start();
        try {
            AntiCorruptionLayerClient client =
                    new AntiCorruptionLayerClient(
                            new Gson(),
                            "http://localhost:" + server.getAddress().getPort(),
                            Duration.ofSeconds(2));
            WorkflowStepContext context =
                    new WorkflowStepContext(
                            new WorkflowCommandMetadata(
                                    "SUBMIT_EVENT", "req", "ref", "VERIFY_OTP", "key", null),
                            7,
                            "loan",
                            2);
            client.execute("verifyOtp", context);
            JsonObject sent = new Gson().fromJson(body.get(), com.google.gson.JsonObject.class);
            assertEquals("loan", sent.get("workflowKey").getAsString());
            assertEquals(2, sent.get("definitionVersion").getAsInt());
            assertEquals("key", sent.get("idempotencyKey").getAsString());
            assertEquals("verifyOtp", sent.get("actionName").getAsString());
            status.set(503);
            assertThrows(IllegalStateException.class, () -> client.execute("verifyOtp", context));
            status.set(200);
            response.set("{}");
            assertThrows(IllegalStateException.class, () -> client.execute("verifyOtp", context));
        } finally {
            server.stop(0);
        }
    }
}
