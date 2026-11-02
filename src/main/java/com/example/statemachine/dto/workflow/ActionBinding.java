package com.example.statemachine.dto.workflow;

public record ActionBinding(
        String code,
        String phase,
        int order
) {}
