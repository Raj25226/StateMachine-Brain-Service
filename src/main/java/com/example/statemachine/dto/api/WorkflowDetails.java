package com.example.statemachine.dto.api;

import com.example.statemachine.enums.WorkflowEventOutcome;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowDetails(
        String referenceId,
        String workflowKey,
        int definitionVersion,
        String currentState,
        String nextEvent,
        String nextState,
        boolean completed,
        List<AvailableWorkflowTransition> availableTransitions,
        WorkflowEventOutcome eventOutcome,
        List<String> pendingEvents
) { }
