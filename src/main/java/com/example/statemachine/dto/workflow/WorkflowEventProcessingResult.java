package com.example.statemachine.dto.workflow;

import com.example.statemachine.enums.WorkflowEventProcessingOutcome;

public record WorkflowEventProcessingResult(
        String previousState,
        String event,
        String currentState,
        WorkflowEventProcessingOutcome outcome
) { }
