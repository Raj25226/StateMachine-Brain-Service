package com.example.statemachine.dto.persistence;

import java.util.Map;

public record StoredWorkflowContext(
        int formatVersion,
        String state,
        Map<String, Object> variables
) {}
