package com.example.statemachine.exception;

import com.example.statemachine.constants.ApiErrorConstants;
import com.example.statemachine.constants.RequestAttributeConstants;
import com.example.statemachine.constants.WorkflowConstants;
import com.example.statemachine.dto.api.ApiError;
import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.api.WorkflowResponse;
import com.example.statemachine.dto.api.WorkflowResponseMetadata;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class WorkflowExceptionHandler {

    @ExceptionHandler(WorkflowException.class)
    public ResponseEntity<WorkflowResponse> handleWorkflowException(WorkflowException workflowException, HttpServletRequest httpRequest) {
        return buildRejectedResponse(httpRequest, workflowException.status(), workflowException.code(), workflowException.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<WorkflowResponse> handleDuplicateKey(DuplicateKeyException exception, HttpServletRequest httpRequest) {
        return buildRejectedResponse(httpRequest, HttpStatus.CONFLICT, ApiErrorConstants.WORKFLOW_ALREADY_EXISTS,
                "This referenceId already has a workflow.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<WorkflowResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception, HttpServletRequest httpRequest) {
        return buildRejectedResponse(httpRequest, HttpStatus.BAD_REQUEST, ApiErrorConstants.INVALID_REQUEST,
                "Provide a valid JSON request body.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<WorkflowResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest httpRequest) {
        return buildRejectedResponse(httpRequest, HttpStatus.UNSUPPORTED_MEDIA_TYPE, ApiErrorConstants.INVALID_REQUEST,
                "Use Content-Type application/json.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<WorkflowResponse> handleUnsupportedMethod(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest httpRequest) {
        return buildRejectedResponse(httpRequest, HttpStatus.METHOD_NOT_ALLOWED, ApiErrorConstants.INVALID_REQUEST,
                "Use POST /api/workflows.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<WorkflowResponse> handleResourceNotFound(
            NoResourceFoundException exception, HttpServletRequest httpRequest) {
        return buildRejectedResponse(httpRequest, HttpStatus.NOT_FOUND, ApiErrorConstants.ENDPOINT_NOT_FOUND, "Endpoint not found.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<WorkflowResponse> handleUnexpectedException(Exception exception, HttpServletRequest httpRequest) {
        log.error("workflow_request_failed errorCode=INTERNAL_ERROR", exception);
        return buildErrorResponse(httpRequest, HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorConstants.INTERNAL_ERROR,
                "An unexpected error occurred.");
    }

    private ResponseEntity<WorkflowResponse> buildRejectedResponse(
            HttpServletRequest httpRequest, HttpStatus httpStatus, String errorCode, String errorDescription) {
        log.warn("workflow_request_rejected status={} errorCode={}", httpStatus.value(), errorCode);
        return buildErrorResponse(httpRequest, httpStatus, errorCode, errorDescription);
    }

    private ResponseEntity<WorkflowResponse> buildErrorResponse(
            HttpServletRequest httpRequest, HttpStatus httpStatus, String errorCode, String errorDescription) {
        WorkflowCommandMetadata requestMetadata = (WorkflowCommandMetadata) httpRequest.getAttribute(RequestAttributeConstants.METADATA_ATTRIBUTE);
        WorkflowResponseMetadata responseMetadata =
                new WorkflowResponseMetadata(
                        requestMetadata == null ? null : requestMetadata.operation(),
                        requestMetadata == null ? null : requestMetadata.requestId(),
                        requestMetadata == null ? null : requestMetadata.referenceId(),
                        requestMetadata == null ? null : requestMetadata.event(),
                        requestMetadata == null ? null : requestMetadata.idempotencyKey(),
                        requestMetadata == null ? null : requestMetadata.workflowKey(),
                        WorkflowConstants.STATUS_FAILED,
                        Instant.now(),
                        null);
        WorkflowResponse errorResponse = new WorkflowResponse(responseMetadata, null, new ApiError(errorCode, errorDescription));
        httpRequest.setAttribute(RequestAttributeConstants.RESPONSE_ATTRIBUTE, errorResponse);
        return ResponseEntity.status(httpStatus).body(errorResponse);
    }
}
