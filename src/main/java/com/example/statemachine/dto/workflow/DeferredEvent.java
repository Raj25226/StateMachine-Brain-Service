package com.example.statemachine.dto.workflow;

public record DeferredEvent(
        String state,
        String event
) {}
