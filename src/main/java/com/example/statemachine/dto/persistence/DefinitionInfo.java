package com.example.statemachine.dto.persistence;

public record DefinitionInfo(
        String workflowKey,
        int version
) {}
