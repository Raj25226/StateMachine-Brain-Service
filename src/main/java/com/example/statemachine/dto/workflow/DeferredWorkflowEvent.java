package com.example.statemachine.dto.workflow;

public record DeferredWorkflowEvent(
        String state,
        String event
) {}
