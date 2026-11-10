package com.example.statemachine.exception;

import com.example.statemachine.constants.ApiErrorConstants;
import org.springframework.http.HttpStatus;

public class WorkflowException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public WorkflowException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static WorkflowException invalid(String message) {
        return new WorkflowException(
                HttpStatus.BAD_REQUEST, ApiErrorConstants.INVALID_REQUEST, message);
    }

    public static WorkflowException conflict(String code, String message) {
        return new WorkflowException(HttpStatus.CONFLICT, code, message);
    }
}
