package com.example.statemachine.dto.persistence;

public record WorkflowDefinitionMetadata(
        String workflowKey,
        int version
) {}
