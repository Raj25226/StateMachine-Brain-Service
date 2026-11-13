package com.example.statemachine.constants;

/** Stable API error codes returned to clients. */
public final class ApiErrorConstants {
    private ApiErrorConstants() {}

    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String WORKFLOW_ALREADY_EXISTS = "WORKFLOW_ALREADY_EXISTS";
    public static final String WORKFLOW_NOT_FOUND = "WORKFLOW_NOT_FOUND";
    public static final String WORKFLOW_DEFINITION_NOT_FOUND = "WORKFLOW_DEFINITION_NOT_FOUND";
    public static final String ENDPOINT_NOT_FOUND = "ENDPOINT_NOT_FOUND";
    public static final String EVENT_NOT_ALLOWED = "EVENT_NOT_ALLOWED";
    public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    public static final String IDEMPOTENCY_IN_PROGRESS = "IDEMPOTENCY_IN_PROGRESS";
    public static final String IDEMPOTENCY_UNAVAILABLE = "IDEMPOTENCY_UNAVAILABLE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
