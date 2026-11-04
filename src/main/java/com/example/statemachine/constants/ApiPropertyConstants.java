package com.example.statemachine.constants;

/** JSON property names shared by API validation, documentation and metadata handling. */
public final class ApiPropertyConstants {
    private ApiPropertyConstants() { }

    public static final String METADATA = "metadata";
    public static final String PAYLOAD = "payload";
    public static final String ERROR = "error";
    public static final String OPERATION = "operation";
    public static final String REQUEST_ID = "requestId";
    public static final String REFERENCE_ID = "referenceId";
    public static final String EVENT = "event";
    public static final String IDEMPOTENCY_KEY = "idempotencyKey";
    public static final String WORKFLOW_KEY = "workflowKey";
    public static final String STATUS = "status";
    public static final String PROCESSED_AT = "processedAt";
    public static final String IDEMPOTENCY_REPLAYED = "idempotencyReplayed";
    public static final String DEFINITION_VERSION = "definitionVersion";
    public static final String CURRENT_STATE = "currentState";
    public static final String NEXT_EVENT = "nextEvent";
    public static final String NEXT_STATE = "nextState";
    public static final String COMPLETED = "completed";
    public static final String AVAILABLE_TRANSITIONS = "availableTransitions";
    public static final String EVENT_OUTCOME = "eventOutcome";
    public static final String CODE = "code";
    public static final String DESCRIPTION = "description";
}
