package com.example.statemachine.dto.api;

public record AvailableWorkflowTransition(
        String event,
        String nextState
) { }
