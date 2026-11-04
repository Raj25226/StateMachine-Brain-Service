package com.example.statemachine.dto.workflow;

public record WorkflowActionBinding(
        String code,
        String phase,
        int order
) {}
