package com.example.statemachine.logging;

import com.example.statemachine.constants.ApiPropertyConstants;
import com.example.statemachine.constants.RequestAttributeConstants;
import com.example.statemachine.constants.WorkflowConstants;
import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.api.WorkflowDetails;
import com.example.statemachine.dto.api.WorkflowResponse;
import com.example.statemachine.enums.LogValueSanitizerFunction;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlates HTTP calls without copying or logging OTPs, documents or raw request/response bodies.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class WorkflowRequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI()
                .startsWith(request.getContextPath() + WorkflowConstants.API_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        String correlationId = UUID.randomUUID().toString();
        long requestStartedAtNanos = System.nanoTime();
        MDC.put(WorkflowConstants.CORRELATION_ID_MDC_KEY, correlationId);
        MDC.remove(ApiPropertyConstants.REQUEST_ID);
        MDC.remove(ApiPropertyConstants.REFERENCE_ID);
        response.setHeader(WorkflowConstants.CORRELATION_ID_HEADER, correlationId);
        log.info(
                "Workflow HTTP received | correlationId={} | method={} | path={}",
                correlationId,
                LogValueSanitizerFunction.SANITIZE.apply(request.getMethod()),
                LogValueSanitizerFunction.SANITIZE.apply(request.getRequestURI()));
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            log.error("Workflow HTTP failed | correlationId={}", correlationId, exception);
            throw exception;
        } finally {
            try {
                WorkflowCommandMetadata metadata =
                        (WorkflowCommandMetadata)
                                request.getAttribute(RequestAttributeConstants.METADATA_ATTRIBUTE);
                WorkflowResponse workflowResponse =
                        (WorkflowResponse)
                                request.getAttribute(RequestAttributeConstants.RESPONSE_ATTRIBUTE);
                WorkflowDetails workflowDetails =
                        workflowResponse == null ? null : workflowResponse.payload();
                String errorCode =
                        workflowResponse == null || workflowResponse.error() == null
                                ? null
                                : workflowResponse.error().code();
                Boolean idempotencyReplayed =
                        workflowResponse == null
                                ? null
                                : workflowResponse.metadata().idempotencyReplayed();
                log.atLevel(
                                response.getStatus() >= 500
                                        ? Level.ERROR
                                        : response.getStatus() >= 400 ? Level.WARN : Level.INFO)
                        .log(
                                "Workflow HTTP completed | correlationId={} | requestId={}"
                                        + " | referenceId={} | operation={} | event={} | status={}"
                                        + " | currentState={} | eventOutcome={} | idempotencyReplayed={}"
                                        + " | errorCode={} | durationMs={}",
                                correlationId,
                                LogValueSanitizerFunction.SANITIZE.apply(
                                        metadata == null ? null : metadata.requestId()),
                                LogValueSanitizerFunction.SANITIZE.apply(
                                        metadata == null ? null : metadata.referenceId()),
                                LogValueSanitizerFunction.SANITIZE.apply(
                                        metadata == null ? null : metadata.operation()),
                                LogValueSanitizerFunction.SANITIZE.apply(
                                        metadata == null ? null : metadata.event()),
                                response.getStatus(),
                                LogValueSanitizerFunction.SANITIZE.apply(
                                        workflowDetails == null
                                                ? null
                                                : workflowDetails.currentState()),
                                workflowDetails == null ? null : workflowDetails.eventOutcome(),
                                idempotencyReplayed,
                                errorCode,
                                (System.nanoTime() - requestStartedAtNanos) / 1_000_000);
            } finally {
                if (previousContext == null) MDC.clear();
                else MDC.setContextMap(previousContext);
            }
        }
    }
}
