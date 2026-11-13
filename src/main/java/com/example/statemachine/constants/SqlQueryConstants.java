package com.example.statemachine.constants;

/** Classpath resource, parent groups and query-key suffixes for the runtime JDBC queries. */
public final class SqlQueryConstants {
    private SqlQueryConstants() {}

    public static final String RESOURCE_NAME = "sql-queries.yml";

    public static final String WORKFLOW_DEFINITION_PARENT = "workflow-definition";
    public static final String TRANSITION_HISTORY_PARENT = "transition-history";
    public static final String WORKFLOW_INSTANCE_PARENT = "workflow-instance";

    public static final String FIND_LATEST_ACTIVE_DEFINITION_ID = ".find-latest-active-id";
    public static final String FIND_DEFINITION_METADATA = ".find-metadata";
    public static final String FIND_STATE_ACTIONS = ".find-state-actions";
    public static final String FIND_STATES = ".find-states";
    public static final String FIND_TRANSITION_ACTIONS = ".find-transition-actions";
    public static final String FIND_TRANSITIONS = ".find-transitions";
    public static final String INSERT_TRANSITION_HISTORY = ".insert";
    public static final String INSERT_WORKFLOW_INSTANCE = ".insert";
    public static final String FIND_AND_LOCK_WORKFLOW_INSTANCE = ".find-and-lock-by-reference";
    public static final String FIND_WORKFLOW_INSTANCE = ".find-by-reference";
    public static final String UPDATE_CURRENT_STATE = ".update-current-state";
}
