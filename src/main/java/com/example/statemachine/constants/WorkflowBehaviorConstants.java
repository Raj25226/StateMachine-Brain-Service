package com.example.statemachine.constants;

/** Registered Java behavior identifiers; values must match the database action names. */
public final class WorkflowBehaviorConstants {
    private WorkflowBehaviorConstants() {}

    public static final String RECORD_STATE_ENTRY = "recordStateEntry";
    public static final String RECORD_STATE_EXIT = "recordStateExit";
    public static final String OBSERVE_STATE = "observeState";
    public static final String RECORD_TRANSITION = "recordTransition";
}
