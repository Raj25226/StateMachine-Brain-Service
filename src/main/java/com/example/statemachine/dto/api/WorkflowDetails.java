package com.example.statemachine.dto.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import com.example.statemachine.enums.EventOutcome;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowDetails(
        String referenceId,
        String workflowKey,
        int definitionVersion,
        String currentState,
        String nextEvent,
        String nextState,
        boolean completed,
        List<AvailableTransition> availableTransitions,
        EventOutcome eventOutcome,
        List<String> pendingEvents
) { }
