package com.example.statemachine.service;

import com.example.statemachine.constants.LogConstants;
import com.example.statemachine.constants.WorkflowConstants;
import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.workflow.WorkflowStepContext;
import com.example.statemachine.enums.LogValueSanitizerFunction;
import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Synchronous boundary: only an explicit successful ACL acknowledgement permits progress. */
@Component
@Slf4j
public class AntiCorruptionLayerClient {
    private final Gson gson;
    private final URI endpoint;
    private final Duration timeout;
    private final HttpClient client;

    public AntiCorruptionLayerClient(
            Gson gson,
            @Value("${acl.base-url:http://localhost:8082}") String baseUrl,
            @Value("${acl.timeout:10s}") Duration timeout) {
        this.gson = gson;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/api/steps/execute");
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public void execute(String actionName, WorkflowStepContext context) throws Exception {
        String currentCorrelationId = MDC.get(WorkflowConstants.CORRELATION_ID_MDC_KEY);
        String correlationId =
                LogValueSanitizerFunction.SANITIZE.apply(
                        currentCorrelationId == null
                                ? UUID.randomUUID().toString()
                                : currentCorrelationId);
        String workflow = LogValueSanitizerFunction.SANITIZE.apply(context.workflowKey());
        String action = LogValueSanitizerFunction.SANITIZE.apply(actionName);
        long started = System.nanoTime();
        log.info(
                LogConstants.ACL_CALL_STARTED,
                correlationId,
                workflow,
                context.definitionVersion(),
                action,
                LogValueSanitizerFunction.SANITIZE.apply(context.metadata().requestId()));
        try {
            executeRemote(actionName, context, correlationId);
            log.info(
                    LogConstants.ACL_CALL_COMPLETED,
                    correlationId,
                    workflow,
                    context.definitionVersion(),
                    action,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } catch (Exception exception) {
            log.warn(
                    LogConstants.ACL_CALL_FAILED,
                    correlationId,
                    workflow,
                    context.definitionVersion(),
                    action,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private void executeRemote(String actionName, WorkflowStepContext context, String correlationId)
            throws Exception {
        WorkflowCommandMetadata metadata = context.metadata();
        StepInvocation invocation =
                new StepInvocation(
                        context.workflowKey(),
                        context.definitionVersion(),
                        actionName,
                        metadata.referenceId(),
                        metadata.event(),
                        metadata.requestId(),
                        metadata.idempotencyKey());
        HttpRequest request =
                HttpRequest.newBuilder(endpoint)
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .header(WorkflowConstants.CORRELATION_ID_HEADER, correlationId)
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(invocation)))
                        .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
        if (response.statusCode() != 200) {
            log.warn(LogConstants.ACL_HTTP_REJECTED, correlationId, response.statusCode());
            throw new IllegalStateException(
                    "ACL step execution failed with HTTP " + response.statusCode());
        }
        StepAcknowledgement result = gson.fromJson(response.body(), StepAcknowledgement.class);
        if (result == null || !"SUCCEEDED".equals(result.status())) {
            log.warn(LogConstants.ACL_ACKNOWLEDGEMENT_INVALID, correlationId);
            throw new IllegalStateException("ACL did not acknowledge successful step execution.");
        }
    }

    private record StepInvocation(
            String workflowKey,
            int definitionVersion,
            String actionName,
            String referenceId,
            String event,
            String requestId,
            String idempotencyKey) {}

    private record StepAcknowledgement(String status) {}
}
