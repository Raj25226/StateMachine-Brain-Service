package com.example.statemachine.dto.api;

import com.example.statemachine.enums.WorkflowEventOutcome;
import java.util.List;

public record WorkflowDetails(
        String referenceId,
        String workflowKey,
        int definitionVersion,
        String currentState,
        String nextEvent,
        String nextState,
        boolean completed,
        List<AvailableWorkflowTransition> availableTransitions,
        WorkflowEventOutcome eventOutcome) {}
