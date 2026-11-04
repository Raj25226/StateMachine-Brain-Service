package com.example.statemachine.constants;

/** JDBC result-column labels and named SQL parameter keys. */
public final class DatabaseConstants {
    private DatabaseConstants() { }

    public static final String COLUMN_ACTION_CODE = "action_code";
    public static final String COLUMN_CONTEXT_FORMAT_VERSION = "context_format_version";
    public static final String COLUMN_CURRENT_STATE = "current_state";
    public static final String COLUMN_DEFINITION_ID = "definition_id";
    public static final String COLUMN_EVENT_CODE = "event_code";
    public static final String COLUMN_EXECUTION_ORDER = "execution_order";
    public static final String COLUMN_GUARD_CODE = "guard_code";
    public static final String COLUMN_HTTP_STATUS = "http_status";
    public static final String COLUMN_INITIAL_STATE = "initial_state";
    public static final String COLUMN_MACHINE_CONTEXT = "machine_context";
    public static final String COLUMN_PHASE = "phase";
    public static final String COLUMN_REFERENCE_ID = "reference_id";
    public static final String COLUMN_REQUEST_FINGERPRINT = "request_fingerprint";
    public static final String COLUMN_RESPONSE_JSON = "response_json";
    public static final String COLUMN_SOURCE_STATE = "source_state";
    public static final String COLUMN_STATE_CODE = "state_code";
    public static final String COLUMN_TARGET_STATE = "target_state";
    public static final String COLUMN_TERMINAL_STATE = "terminal_state";
    public static final String COLUMN_TRANSITION_ID = "transition_id";
    public static final String COLUMN_TRANSITION_KIND = "transition_kind";
    public static final String COLUMN_VERSION = "version";
    public static final String COLUMN_WORKFLOW_KEY = "workflow_key";
    public static final String PARAM_CONTEXT = "context";
    public static final String PARAM_DEFINITION = "definition";
    public static final String PARAM_DEFINITION_ID = "definitionId";
    public static final String PARAM_EVENT = "event";
    public static final String PARAM_FINGERPRINT = "fingerprint";
    public static final String PARAM_IDEMPOTENCY_KEY = "idempotencyKey";
    public static final String PARAM_KEY = "key";
    public static final String PARAM_REFERENCE = "reference";
    public static final String PARAM_REFERENCE_ID = "referenceId";
    public static final String PARAM_REQUEST_ID = "requestId";
    public static final String PARAM_RESPONSE = "response";
    public static final String PARAM_SOURCE_STATE = "sourceState";
    public static final String PARAM_STATE = "state";
    public static final String PARAM_STATUS = "status";
    public static final String PARAM_TARGET_STATE = "targetState";
    public static final String PARAM_WORKFLOW_KEY = "workflowKey";
}
