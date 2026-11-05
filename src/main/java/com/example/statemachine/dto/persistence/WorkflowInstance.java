package com.example.statemachine.dto.persistence;

public record WorkflowInstance(
        Integer id,
        String referenceId,
        Integer definitionId,
        String currentState
) {}
