package com.example.statemachine.dto.workflow;

import com.example.statemachine.statemachine.WorkflowEventProcessor.Outcome;

public record EventProcessingResult(
        String previousState,
        String event,
        String currentState,
        Outcome outcome
) { }
