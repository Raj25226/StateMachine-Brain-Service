package com.example.statemachine.logging;

import com.example.statemachine.dto.api.RequestMetadata;
import com.example.statemachine.dto.api.Response;
import com.example.statemachine.exception.WorkflowExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;
import java.util.Map;
import com.example.statemachine.dto.api.WorkflowDetails;

/**
 * Correlates HTTP calls without copying or logging OTPs, documents or raw request/response bodies.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class WorkflowRequestLoggingFilter extends OncePerRequestFilter {

    public static final String RESPONSE_ATTRIBUTE = WorkflowRequestLoggingFilter.class.getName() + ".response";
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowRequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    public static void logCommand(RequestMetadata metadata) {
        if (metadata == null) return;
        MDC.put("requestId", logValue(metadata.requestId()));
        MDC.put("referenceId", logValue(metadata.referenceId()));
        LOG.info(
                "workflow_command_received correlationId={} requestId={} referenceId={}"
                    + " operation={} event={}",
                MDC.get("correlationId"),
                logValue(metadata.requestId()),
                logValue(metadata.referenceId()),
                logValue(metadata.operation()),
                logValue(metadata.event()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        String correlationId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        MDC.put("correlationId", correlationId);
        MDC.remove("requestId");
        MDC.remove("referenceId");
        response.setHeader("X-Correlation-Id", correlationId);
        LOG.info(
                "workflow_http_received correlationId={} method={} path={}",
                correlationId,
                logValue(request.getMethod()),
                logValue(request.getRequestURI()));
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException failure) {
            LOG.error("workflow_http_failed correlationId={}", correlationId, failure);
            throw failure;
        } finally {
            try {
                RequestMetadata metadata =
                        (RequestMetadata)
                                request.getAttribute(WorkflowExceptionHandler.METADATA_ATTRIBUTE);
                Response result = (Response) request.getAttribute(RESPONSE_ATTRIBUTE);
                WorkflowDetails payload = result == null ? null : result.payload();
                String errorCode =
                        result == null || result.error() == null ? null : result.error().code();
                Boolean replayed = result == null ? null : result.metadata().idempotencyReplayed();
                LOG.atLevel(response.getStatus() >= 400 ? Level.WARN : Level.INFO)
                        .log(
                                "workflow_http_completed correlationId={} requestId={}"
                                    + " referenceId={} operation={} event={} status={}"
                                    + " currentState={} eventOutcome={} idempotencyReplayed={}"
                                    + " errorCode={} durationMs={}",
                                correlationId,
                                logValue(metadata == null ? null : metadata.requestId()),
                                logValue(metadata == null ? null : metadata.referenceId()),
                                logValue(metadata == null ? null : metadata.operation()),
                                logValue(metadata == null ? null : metadata.event()),
                                response.getStatus(),
                                logValue(payload == null ? null : payload.currentState()),
                                payload == null ? null : payload.eventOutcome(),
                                replayed,
                                errorCode,
                                (System.nanoTime() - started) / 1_000_000);
            } finally {
                if (previousContext == null) MDC.clear();
                else MDC.setContextMap(previousContext);
            }
        }
    }

    private static String logValue(String value) {
        if (value == null) return "-";
        String bounded = value.substring(0, Math.min(value.length(), 160));
        return bounded.replaceAll("[\\p{Cntrl}]", "_");
    }
}
