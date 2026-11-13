package com.example.statemachine.repository;

import lombok.AllArgsConstructor;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.support.DefaultExtendedState;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;

/**
 * Restores flat machines from the instance's relational state FK; no serialized context is stored.
 */
@Component
@AllArgsConstructor
public class JdbcWorkflowStateMachinePersistence
        implements StateMachinePersist<String, String, String> {
    private final JdbcWorkflowInstanceRepository workflowInstanceRepository;

    @Override
    public void write(StateMachineContext<String, String> context, String referenceId) {
        if (context.getChilds() != null && !context.getChilds().isEmpty()) {
            throw new IllegalStateException(
                    "Nested/parallel machine persistence is not supported.");
        }
        if (context.getExtendedState() != null
                && !context.getExtendedState().getVariables().isEmpty()) {
            throw new IllegalStateException(
                    "Durable workflow values require explicit database columns; extended variables cannot be persisted.");
        }
        workflowInstanceRepository.updateCurrentState(referenceId, context.getState());
    }

    @Override
    public StateMachineContext<String, String> read(String referenceId) {
        return new DefaultStateMachineContext<>(
                workflowInstanceRepository.findByReferenceId(referenceId).currentState(),
                null,
                null,
                new DefaultExtendedState());
    }
}
