package com.example.statemachine.dto.persistence;

public record WorkflowInstance(
        String referenceId,
        String definitionId,
        String currentState,
        String machineContextJson,
        int contextFormatVersion
) {}
