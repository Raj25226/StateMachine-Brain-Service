package com.example.statemachine.dto.workflow;

import java.util.List;

public record WorkflowDefinition(
        String id,
        String workflowKey,
        int version,
        List<State> states,
        List<Transition> transitions,
        List<DeferredEvent> deferredEvents
) {

    public State getState(String stateCode) {
        return states.stream()
                .filter(s -> s.code().equals(stateCode))
                .findFirst()
                .orElseThrow(
                        () ->  new IllegalStateException("State is missing from the workflow definition: " + stateCode)
                );
    }
}
