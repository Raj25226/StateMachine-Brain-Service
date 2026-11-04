package com.example.statemachine.dto.workflow;

import java.util.List;

public record WorkflowTransitionDefinition(
        Integer id,
        String source,
        String event,
        String target,
        String kind,
        String guard,
        List<String> actions
) {}
