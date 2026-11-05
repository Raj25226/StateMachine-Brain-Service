package com.example.statemachine.constants;

/** Keys persisted in machine-context variables; keep values stable for existing workflow snapshots. */
public final class WorkflowContextConstants {
    private WorkflowContextConstants() {}

    public static final String LAST_ENTERED_STATE = "lastEnteredState";
    public static final String LAST_EXITED_STATE = "lastExitedState";
    public static final String LAST_PROCESSED_EVENT = "lastProcessedEvent";
}
