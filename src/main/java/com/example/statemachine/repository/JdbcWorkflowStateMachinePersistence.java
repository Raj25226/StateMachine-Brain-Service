package com.example.statemachine.repository;

import com.example.statemachine.dto.persistence.StoredWorkflowContext;
import com.example.statemachine.dto.persistence.WorkflowInstance;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.support.DefaultExtendedState;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;

/** Versioned JSON context for this application's flat, single-region machines. */

@Component
public class JdbcWorkflowStateMachinePersistence implements StateMachinePersist<String, String, String> {

    private final JdbcWorkflowInstanceRepository workflowInstanceRepository;
    private final Gson gson;

    public JdbcWorkflowStateMachinePersistence(JdbcWorkflowInstanceRepository workflowInstanceRepository, Gson gson) {
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.gson = gson;
    }

    @Override
    public void write(StateMachineContext<String, String> stateMachineContext, String referenceId) throws Exception {

        if (stateMachineContext.getChilds() != null && !stateMachineContext.getChilds().isEmpty()) {
            throw new IllegalStateException("Nested/parallel machine contexts are not supported by context format 1.");
        }

        Map<String, Object> extendedStateVariables = new HashMap<>();
        if (stateMachineContext.getExtendedState() != null) {
            stateMachineContext.getExtendedState()
                .getVariables()
                .forEach( (key, value) -> {
                        if (!(key instanceof String)) {
                            throw new IllegalStateException("Extended-state keys must be strings.");
                        }
                        extendedStateVariables.put((String) key, value);
                    }
                );
        }
        workflowInstanceRepository.updateWorkflowContext(
            referenceId,
            stateMachineContext.getState(),
            gson.toJson(new StoredWorkflowContext(1, stateMachineContext.getState(), extendedStateVariables))
        );
    }

    @Override
    public StateMachineContext<String, String> read(String referenceId) throws Exception {
        return deserializeContext(workflowInstanceRepository.findByReferenceId(referenceId));
    }

    public StateMachineContext<String, String> deserializeContext(WorkflowInstance workflowInstance) throws Exception {

        if (workflowInstance.contextFormatVersion() != 1) {
            throw new IllegalStateException("Unsupported context format.");
        }

        StoredWorkflowContext storedContext = workflowInstance.machineContextJson() == null
                        ? new StoredWorkflowContext(1, workflowInstance.currentState(), Map.of())
                        : gson.fromJson(workflowInstance.machineContextJson(), StoredWorkflowContext.class);

        if (storedContext == null || storedContext.formatVersion() != 1
                || !workflowInstance.currentState().equals(storedContext.state()) || storedContext.variables() == null) {
            throw new IllegalStateException("Persisted context does not match current_state.");
        }

        Map<Object, Object> extendedStateVariables = new HashMap<>(storedContext.variables());
        return new DefaultStateMachineContext<>(
                storedContext.state(),
                null,
                null,
                new DefaultExtendedState(extendedStateVariables)
        );
    }

}
