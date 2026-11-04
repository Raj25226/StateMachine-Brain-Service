package com.example.statemachine.dto.workflow;

import java.util.List;

public record WorkflowDefinition(
        Integer id,
        String workflowKey,
        int version,
        List<WorkflowStateDefinition> states,
        List<WorkflowTransitionDefinition> transitions
) {

    public WorkflowStateDefinition getState(String stateCode) {
        return states.stream()
                .filter(workflowState -> workflowState.code().equals(stateCode))
                .findFirst()
                .orElseThrow(
                        () ->  new IllegalStateException("State is missing from the workflow definition: " + stateCode)
                );
    }
}
