package com.example.statemachine.statemachine;

import com.example.statemachine.constants.WorkflowConstants;
import com.example.statemachine.dto.workflow.WorkflowActionBinding;
import com.example.statemachine.dto.workflow.WorkflowDefinition;
import com.example.statemachine.dto.workflow.WorkflowStateDefinition;
import com.example.statemachine.dto.workflow.WorkflowTransitionDefinition;
import com.example.statemachine.enums.LogValueSanitizerFunction;
import com.example.statemachine.repository.JdbcWorkflowDefinitionRepository;
import java.util.*;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.config.model.DefaultStateMachineModel;
import org.springframework.statemachine.config.model.StateData;
import org.springframework.statemachine.config.model.StateMachineModel;
import org.springframework.statemachine.config.model.StateMachineModelFactory;
import org.springframework.statemachine.config.model.StatesData;
import org.springframework.statemachine.config.model.TransitionData;
import org.springframework.statemachine.config.model.TransitionsData;
import org.springframework.statemachine.transition.TransitionKind;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@AllArgsConstructor
public class JdbcWorkflowModelFactory implements StateMachineModelFactory<String, String> {

    private final JdbcWorkflowDefinitionRepository definitionRepository;
    private final WorkflowBehaviorRegistry behaviorRegistry;

    @Override
    public StateMachineModel<String, String> build() {
        throw new IllegalStateException("A versioned definition ID is required.");
    }

    @Override
    public StateMachineModel<String, String> build(String stateMachineId) {

        // Spring StateMachine requires a string machine identifier at this framework boundary.
        Integer definitionId = Integer.valueOf(stateMachineId);
        WorkflowDefinition workflowDefinition =
                definitionRepository.loadWorkflowDefinition(definitionId);
        validateWorkflowStructure(workflowDefinition);

        List<StateData<String, String>> states =
                workflowDefinition.states().stream()
                        .map(
                                workflowStateDefinition -> {
                                    StateData<String, String> stateData =
                                            new StateData<String, String>(
                                                    workflowStateDefinition.code(),
                                                    workflowStateDefinition.initial());

                                    stateData.setEnd(workflowStateDefinition.terminal());
                                    stateData.setEntryActions(
                                            resolveStateActions(
                                                    workflowStateDefinition,
                                                    WorkflowConstants.ACTION_PHASE_ENTRY));
                                    stateData.setExitActions(
                                            resolveStateActions(
                                                    workflowStateDefinition,
                                                    WorkflowConstants.ACTION_PHASE_EXIT));
                                    stateData.setStateActions(
                                            resolveStateActions(
                                                    workflowStateDefinition,
                                                    WorkflowConstants.ACTION_PHASE_STATE));
                                    return stateData;
                                })
                        .toList();

        List<TransitionData<String, String>> transitions =
                workflowDefinition.transitions().stream()
                        .map(
                                workflowTransition ->
                                        new TransitionData<String, String>(
                                                workflowTransition.source(),
                                                workflowTransition.target(),
                                                workflowTransition.event(),
                                                resolveTransitionActions(
                                                        workflowTransition.actionNames()),
                                                null,
                                                TransitionKind.valueOf(workflowTransition.kind())))
                        .toList();

        return new DefaultStateMachineModel<>(
                null, new StatesData<>(states), new TransitionsData<>(transitions));
    }

    private Collection<Function<StateContext<String, String>, Mono<Void>>> resolveStateActions(
            WorkflowStateDefinition stateDefinition, String actionPhase) {

        List<Function<StateContext<String, String>, Mono<Void>>> orderedActions =
                stateDefinition.actions().stream()
                        .filter(actionBinding -> actionBinding.phase().equals(actionPhase))
                        .sorted(Comparator.comparingInt(WorkflowActionBinding::order))
                        .map(
                                actionBinding ->
                                        resolveActionWithErrorHandling(actionBinding.actionName()))
                        .toList();

        return orderedActions.isEmpty()
                ? List.of()
                : List.of(
                        stateContext ->
                                Flux.fromIterable(orderedActions)
                                        .concatMap(
                                                actionFunction ->
                                                        actionFunction.apply(stateContext))
                                        .then());
    }

    private Collection<Function<StateContext<String, String>, Mono<Void>>> resolveTransitionActions(
            List<String> actionNames) {

        List<Function<StateContext<String, String>, Mono<Void>>> orderedActions =
                actionNames.stream()
                        .map(behaviorCode -> resolveActionWithErrorHandling(behaviorCode))
                        .toList();

        return orderedActions.isEmpty()
                ? List.of()
                : List.of(
                        stateContext ->
                                Flux.fromIterable(orderedActions)
                                        .concatMap(
                                                actionFunction ->
                                                        actionFunction.apply(stateContext))
                                        .then());
    }

    private Function<StateContext<String, String>, Mono<Void>> resolveActionWithErrorHandling(
            String behaviorCode) {

        Function<StateContext<String, String>, Mono<Void>> actionFunction =
                behaviorRegistry.resolveActionByName(behaviorCode);
        return stateContext ->
                Mono.defer(() -> actionFunction.apply(stateContext))
                        .doOnError(
                                error -> {
                                    log.error(
                                            "Workflow action failed | action={}",
                                            LogValueSanitizerFunction.SANITIZE.apply(behaviorCode),
                                            error);
                                    stateContext
                                            .getStateMachine()
                                            .setStateMachineError(
                                                    new IllegalStateException(
                                                            "Workflow action failed: "
                                                                    + behaviorCode,
                                                            error));
                                });
    }

    private void validateWorkflowStructure(WorkflowDefinition workflowDefinition) {

        if (workflowDefinition.states().stream().filter(WorkflowStateDefinition::initial).count()
                != 1) {
            throw new IllegalStateException("Workflow must have exactly one initial state.");
        }

        if (workflowDefinition.states().stream().noneMatch(WorkflowStateDefinition::terminal)) {
            throw new IllegalStateException("Workflow must have a terminal state.");
        }

        for (WorkflowTransitionDefinition workflowTransition : workflowDefinition.transitions()) {
            if (workflowDefinition.getState(workflowTransition.source()).terminal()) {
                throw new IllegalStateException(
                        "Terminal states cannot have outgoing transitions.");
            }
            workflowDefinition.getState(workflowTransition.target());
        }

        Set<String> reachableStates = new HashSet<String>();

        // add initial state
        reachableStates.add(
                workflowDefinition.states().stream()
                        .filter(WorkflowStateDefinition::initial)
                        .findFirst()
                        .orElseThrow()
                        .code());

        boolean reachableStatesChanged;
        do {
            reachableStatesChanged = false;
            for (WorkflowTransitionDefinition workflowTransition :
                    workflowDefinition.transitions()) {
                if (reachableStates.contains(workflowTransition.source())) {
                    reachableStatesChanged |= reachableStates.add(workflowTransition.target());
                }
            }
        } while (reachableStatesChanged);

        if (reachableStates.size() != workflowDefinition.states().size()) {
            throw new IllegalStateException("Workflow contains unreachable states.");
        }
    }
}
