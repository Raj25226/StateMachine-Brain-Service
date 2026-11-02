package com.example.statemachine.dto.api;

public record AvailableTransition(
        String event,
        String nextState
) { }
