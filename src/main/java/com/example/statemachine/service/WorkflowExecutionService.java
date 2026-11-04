package com.example.statemachine.service;

import com.example.statemachine.constants.ApiErrorConstants;
import com.example.statemachine.constants.WorkflowContextConstants;
import com.example.statemachine.dto.api.AvailableWorkflowTransition;
import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.api.WorkflowDetails;
import com.example.statemachine.dto.persistence.WorkflowInstance;
import com.example.statemachine.dto.workflow.WorkflowDefinition;
import com.example.statemachine.dto.workflow.WorkflowEventProcessingResult;
import com.example.statemachine.enums.LogValueSanitizerFunction;
import com.example.statemachine.enums.WorkflowEventOutcome;
import com.example.statemachine.exception.WorkflowException;
import com.example.statemachine.repository.JdbcWorkflowDefinitionRepository;
import com.example.statemachine.repository.JdbcWorkflowHistoryRepository;
import com.example.statemachine.repository.JdbcWorkflowInstanceRepository;
import com.example.statemachine.repository.JdbcWorkflowStateMachinePersistence;
import com.example.statemachine.statemachine.WorkflowEventProcessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class WorkflowExecutionService {

    private final JdbcWorkflowDefinitionRepository workflowDefinitionRepository;
    private final JdbcWorkflowInstanceRepository workflowInstanceRepository;
    private final StateMachineFactory<String, String> stateMachineFactory;
    private final StateMachinePersister<String, String, String> stateMachinePersister;
    private final JdbcWorkflowStateMachinePersistence workflowStateMachinePersistence;
    private final JdbcWorkflowHistoryRepository workflowHistoryRepository;
    private final WorkflowEventProcessor workflowEventProcessor;

    public WorkflowExecutionService(
            JdbcWorkflowDefinitionRepository workflowDefinitionRepository,
            JdbcWorkflowInstanceRepository workflowInstanceRepository,
            StateMachineFactory<String, String> stateMachineFactory,
            StateMachinePersister<String, String, String> stateMachinePersister,
            JdbcWorkflowStateMachinePersistence workflowStateMachinePersistence,
            JdbcWorkflowHistoryRepository workflowHistoryRepository,
            WorkflowEventProcessor workflowEventProcessor) {

        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.stateMachineFactory = stateMachineFactory;
        this.stateMachinePersister = stateMachinePersister;
        this.workflowStateMachinePersistence = workflowStateMachinePersistence;
        this.workflowHistoryRepository = workflowHistoryRepository;
        this.workflowEventProcessor = workflowEventProcessor;
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowDetails createWorkflow(String referenceId, String workflowKey) throws Exception {

        Integer definitionId = workflowDefinitionRepository.findLatestActiveDefinitionId(workflowKey);
        WorkflowDefinition workflowDefinition = workflowDefinitionRepository.loadWorkflowDefinition(definitionId);

        StateMachine<String, String> stateMachine = stateMachineFactory.getStateMachine(definitionId.toString());
        try {

            stateMachine.startReactively().block();
            workflowEventProcessor.throwIfStateMachineFailed(stateMachine);

            workflowInstanceRepository.createWorkflowInstance(referenceId, definitionId, stateMachine.getState().getId());
            stateMachinePersister.persist(stateMachine, referenceId);
            log.debug("workflow_created definition={} state={}",
                    definitionId, LogValueSanitizerFunction.SANITIZE.apply(stateMachine.getState().getId()));
            return buildWorkflowDetails(
                    referenceId,
                    workflowDefinition,
                    stateMachine.getState().getId(),
                    null,
                    getPendingEventNames(stateMachine.getExtendedState().getVariables()));
        } finally {
            stateMachine.stopReactively().block();
        }
    }

    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public WorkflowDetails getWorkflowState(String referenceId) throws Exception {

        WorkflowInstance workflowInstance = workflowInstanceRepository.findByReferenceId(referenceId);
        WorkflowDefinition workflowDefinition = workflowDefinitionRepository.loadWorkflowDefinition(workflowInstance.definitionId());

        StateMachineContext<String, String> stateMachineContext = workflowStateMachinePersistence.deserializeContext(workflowInstance);
        log.debug("workflow_state_loaded definition={} state={}",
                workflowInstance.definitionId(), LogValueSanitizerFunction.SANITIZE.apply(stateMachineContext.getState()));
        return buildWorkflowDetails(
                referenceId,
                workflowDefinition,
                stateMachineContext.getState(),
                null,
                getPendingEventNames(stateMachineContext.getExtendedState().getVariables()));
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowDetails submitEvent(WorkflowCommandMetadata commandMetadata) throws Exception {

        WorkflowInstance workflowInstance = workflowInstanceRepository.findAndLockByReferenceId(commandMetadata.referenceId());
        WorkflowDefinition workflowDefinition = workflowDefinitionRepository.loadWorkflowDefinition(workflowInstance.definitionId());

        StateMachine<String, String> stateMachine = stateMachineFactory.getStateMachine(workflowInstance.definitionId().toString());

        try {
            stateMachinePersister.restore(stateMachine, commandMetadata.referenceId());
            workflowEventProcessor.throwIfStateMachineFailed(stateMachine);

            String eventCode = commandMetadata.event();

            if (workflowDefinition.transitions().stream().noneMatch(transition -> transition.event().equals(eventCode))) {
                throw WorkflowException.invalid("Unknown event for this workflow definition: " + eventCode);
            }

            WorkflowEventOutcome eventOutcome;
            if (isEventDeferred(workflowDefinition, stateMachine.getState().getId(), eventCode)) {
                // Keep this event pending until the workflow can handle it.
                LinkedHashSet<String> pendingEventNames = new LinkedHashSet<>( getPendingEventNames(stateMachine.getExtendedState().getVariables()));
                pendingEventNames.add(eventCode);

                updatePendingEventNames(stateMachine, pendingEventNames);
                eventOutcome = WorkflowEventOutcome.DEFERRED;
                log.debug("workflow_event_deferred event={} state={} pendingCount={}",
                        LogValueSanitizerFunction.SANITIZE.apply(eventCode), LogValueSanitizerFunction.SANITIZE.apply(stateMachine.getState().getId()), pendingEventNames.size());

            } else {
                // Apply the event and save its transition history.
                processAndRecordEvent(stateMachine, eventCode, workflowDefinition.id(), commandMetadata);
                eventOutcome = WorkflowEventOutcome.APPLIED;
                // Process pending events that can run in the new state.
                applyPendingEvents(stateMachine, workflowDefinition, commandMetadata);
            }

            if (workflowDefinition.getState(stateMachine.getState().getId()).terminal()) {
                // Clear pending events when the workflow is complete.
                updatePendingEventNames(stateMachine, Set.of());
            }

            stateMachinePersister.persist(stateMachine, commandMetadata.referenceId());

            return buildWorkflowDetails(
                    commandMetadata.referenceId(),
                    workflowDefinition,
                    stateMachine.getState().getId(),
                    eventOutcome,
                    getPendingEventNames(stateMachine.getExtendedState().getVariables()));
        } finally {
            stateMachine.stopReactively().block();
        }
    }

    private void processAndRecordEvent(
            StateMachine<String, String> stateMachine,
            String eventCode,
            Integer definitionId,
            WorkflowCommandMetadata commandMetadata) {

        WorkflowEventProcessingResult eventResult = workflowEventProcessor.processEvent(stateMachine, eventCode);

        log.debug("workflow_event_processed event={} sourceState={} targetState={} outcome={}",
                LogValueSanitizerFunction.SANITIZE.apply(eventCode), LogValueSanitizerFunction.SANITIZE.apply(eventResult.previousState()), LogValueSanitizerFunction.SANITIZE.apply(eventResult.currentState()), eventResult.outcome());

        switch (eventResult.outcome()) {
            case EVENT_NOT_ALLOWED -> throw WorkflowException.conflict(
                                    ApiErrorConstants.EVENT_NOT_ALLOWED,
                                    eventCode + " is not allowed while the workflow is " + eventResult.currentState() + "."
                                    );

            case GUARD_REJECTED -> throw WorkflowException.conflict(
                            ApiErrorConstants.GUARD_REJECTED,
                            "A workflow guard rejected " + eventCode + ".");

            case APPLIED -> workflowHistoryRepository.recordTransition(
                            commandMetadata.referenceId(),
                            definitionId,
                            eventResult.previousState(),
                            eventResult.event(),
                            eventResult.currentState(),
                            commandMetadata.requestId(),
                            commandMetadata.idempotencyKey());
        }
    }

    private void applyPendingEvents(StateMachine<String, String> stateMachine, WorkflowDefinition workflowDefinition, WorkflowCommandMetadata commandMetadata) {

        LinkedHashSet<String> pendingEventNames = new LinkedHashSet<>(getPendingEventNames(stateMachine.getExtendedState().getVariables()));
        boolean eventApplied;

        do {
            eventApplied = false;
            for (String eventCode : List.copyOf(pendingEventNames)) {
                String currentState = stateMachine.getState().getId();
                if (workflowDefinition.getState(currentState).terminal()) {
                    pendingEventNames.clear();
                    break;
                }
                boolean transitionConfigured =
                        workflowDefinition.transitions().stream()
                                .anyMatch(
                                        transition ->
                                                transition.source().equals(currentState)
                                                        && transition.event().equals(eventCode));
                if (!isEventDeferred(workflowDefinition, currentState, eventCode) && transitionConfigured) {
                    log.debug("workflow_deferred_event_replaying event={} state={}",
                            LogValueSanitizerFunction.SANITIZE.apply(eventCode), LogValueSanitizerFunction.SANITIZE.apply(currentState));
                    processAndRecordEvent(stateMachine, eventCode, workflowDefinition.id(), commandMetadata);
                    pendingEventNames.remove(eventCode);
                    eventApplied = true;
                    break;
                }
            }
        } while (eventApplied && !pendingEventNames.isEmpty());
        updatePendingEventNames(stateMachine, pendingEventNames);
    }

    private boolean isEventDeferred(WorkflowDefinition workflowDefinition, String stateCode, String eventCode) {
        return workflowDefinition.deferredEvents().stream()
                .anyMatch(
                        deferred ->
                                deferred.state().equals(stateCode) && deferred.event().equals(eventCode));
    }

    private List<String> getPendingEventNames(Map<Object, Object> extendedStateVariables) {
        Object pendingEventValue = extendedStateVariables.get(WorkflowContextConstants.PENDING_EVENTS);
        return pendingEventValue instanceof Collection<?> eventNames
                ? eventNames.stream().map(String.class::cast).toList()
                : List.of();
    }

    private void updatePendingEventNames(StateMachine<String, String> stateMachine, Collection<String> eventNames) {
        if (eventNames.isEmpty()) {
            stateMachine.getExtendedState().getVariables().remove(WorkflowContextConstants.PENDING_EVENTS);
        }
        else{
            stateMachine.getExtendedState().getVariables().put(WorkflowContextConstants.PENDING_EVENTS, new ArrayList<>(eventNames));
        }
    }

    private WorkflowDetails buildWorkflowDetails(
            String referenceId,
            WorkflowDefinition workflowDefinition,
            String stateCode,
            WorkflowEventOutcome eventOutcome,
            List<String> pendingEventNames) {

        List<AvailableWorkflowTransition> availableTransitions =
                        workflowDefinition.transitions()
                        .stream()
                        .filter(transition -> transition.source().equals(stateCode))
                        .map( transition ->
                                new AvailableWorkflowTransition(transition.event(), transition.target())
                        )
                        .toList();

        AvailableWorkflowTransition nextTransition = availableTransitions.size() == 1 ? availableTransitions.getFirst() : null;

        return new WorkflowDetails(
                referenceId,
                workflowDefinition.workflowKey(),
                workflowDefinition.version(),
                stateCode,
                nextTransition == null ? null : nextTransition.event(),
                nextTransition == null ? null : nextTransition.nextState(),
                workflowDefinition.getState(stateCode).terminal(),
                availableTransitions,
                eventOutcome,
                pendingEventNames
        );
    }
}
