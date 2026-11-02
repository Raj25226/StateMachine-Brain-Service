package com.example.statemachine.dto.persistence;

public record Instance(
        String referenceId,
        String definitionId,
        String state,
        String context,
        int formatVersion
) {}
