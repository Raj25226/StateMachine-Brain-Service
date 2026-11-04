package com.example.statemachine.enums;

/** Result of processing one event before API policy maps it to a response or failure. */
public enum WorkflowEventProcessingOutcome {
    APPLIED, EVENT_NOT_ALLOWED, GUARD_REJECTED
}
