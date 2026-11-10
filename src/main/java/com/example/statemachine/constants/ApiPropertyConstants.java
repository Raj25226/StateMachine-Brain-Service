package com.example.statemachine.constants;

/** JSON property names shared by API validation, documentation and metadata handling. */
public final class ApiPropertyConstants {
    private ApiPropertyConstants() {}

    public static final String METADATA = "metadata";
    public static final String PAYLOAD = "payload";
    public static final String ERROR = "error";
    public static final String OPERATION = "operation";
    public static final String REQUEST_ID = "requestId";
    public static final String REFERENCE_ID = "referenceId";
    public static final String EVENT = "event";
    public static final String IDEMPOTENCY_KEY = "idempotencyKey";
    public static final String WORKFLOW_KEY = "workflowKey";
}
