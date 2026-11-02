package com.example.statemachine.statemachine;

import com.example.statemachine.dto.workflow.ActionBinding;
import com.example.statemachine.dto.workflow.State;
import com.example.statemachine.dto.workflow.WorkflowDefinition;
import com.example.statemachine.repository.JdbcWorkflowDefinitionRepository;
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
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import java.util.function.Function;
import com.example.statemachine.dto.workflow.Transition;
import com.example.statemachine.dto.workflow.DeferredEvent;

import java.util.*;

@Component
public class JdbcWorkflowModelFactory implements StateMachineModelFactory<String, String> {

    private final JdbcWorkflowDefinitionRepository definitions;
    private final WorkflowBehaviorRegistry behaviors;

    public JdbcWorkflowModelFactory(
            JdbcWorkflowDefinitionRepository definitions, WorkflowBehaviorRegistry behaviors) {
        this.definitions = definitions;
        this.behaviors = behaviors;
    }

    @Override
    public StateMachineModel<String, String> build() {
        throw new IllegalStateException("A versioned definition ID is required.");
    }

    @Override
    public StateMachineModel<String, String> build(String definitionId) {
        WorkflowDefinition workflowDefinition = definitions.loadWorkflowDefinition(definitionId);
        validateWorkflowStructure(workflowDefinition);

        List<StateData<String, String>> states =
                workflowDefinition.states().stream()
                .map( stateDefinition -> {

                            StateData<String, String> state =
                                new StateData<String, String>(stateDefinition.code(), stateDefinition.initial());

                            state.setEnd(stateDefinition.terminal());
                            state.setEntryActions(resolveStateActions(stateDefinition, "ENTRY"));
                            state.setExitActions(resolveStateActions(stateDefinition, "EXIT"));
                            state.setStateActions(resolveStateActions(stateDefinition, "STATE"));
                            state.setDeferred(
                                    workflowDefinition.deferredEvents().stream()
                                            .filter(d -> d.state().equals(stateDefinition.code()))
                                            .map(deferredEvent -> deferredEvent.event())
                                            .toList()
                            );
                            return state;
                        }
                )
                .toList();

        List<TransitionData<String, String>> transitions =
                workflowDefinition.transitions().stream()
                .map( transition -> new TransitionData<String, String>(
                        transition.source(),
                        transition.target(),
                        transition.event(),
                        resolveTransitionActions(transition.actions()),
                        transition.guard() == null ? null : resolveGuardWithErrorHandling(transition.guard()),
                        TransitionKind.valueOf(transition.kind())
                    )
                )
                .toList();

        return new DefaultStateMachineModel<>(
                null,
                new StatesData<>(states),
                new TransitionsData<>(transitions)
        );
    }

    private Collection<Function<StateContext<String, String>, Mono<Void>>> resolveStateActions(
            State state, String phase) {

        List<Function<StateContext<String, String>, Mono<Void>>> orderedAction =
                state.actions().stream()
                .filter(action -> action.phase().equals(phase))
                .sorted(Comparator.comparingInt(ActionBinding::order))
                .map(action -> resolveActionWithErrorHandling(action.code()))
                .toList();

        return orderedAction.isEmpty()
                ? List.of()
                : List.of( context ->
                            Flux.fromIterable(orderedAction)
                            .concatMap(action -> action.apply(context))
                            .then()
                );
    }

    private Collection<Function<StateContext<String, String>, Mono<Void>>> resolveTransitionActions(
            List<String> codes) {

        List<Function<StateContext<String, String>, Mono<Void>>> ordered =
                codes.stream()
                .map(code -> resolveActionWithErrorHandling(code))
                .toList();

        return ordered.isEmpty()
                ? List.of()
                : List.of( context ->
                        Flux.fromIterable(ordered)
                        .concatMap(action -> action.apply(context))
                        .then()
                );
    }

    private Function<StateContext<String, String>, Mono<Void>> resolveActionWithErrorHandling(String code) {

        Function<StateContext<String, String>, Mono<Void>> action = behaviors.resolveAction(code);
        return context -> Mono.defer(() -> action.apply(context))
                .doOnError(
                        error ->
                        context.getStateMachine()
                        .setStateMachineError(new IllegalStateException("Workflow action failed: " + code, error))
                );
    }

    private Function<StateContext<String, String>, Mono<Boolean>> resolveGuardWithErrorHandling(String code) {
        Function<StateContext<String, String>, Mono<Boolean>> guard = behaviors.resolveGuard(code);
        return context -> Mono.defer(() -> guard.apply(context))
                .doOnError( error ->
                        context.getStateMachine()
                        .setStateMachineError(new IllegalStateException("Workflow guard failed: " + code, error))
                );
    }

    private void validateWorkflowStructure(WorkflowDefinition workflowDefinition) {

        if (workflowDefinition.states().stream().filter(State::initial).count() != 1) {
            throw new IllegalStateException("Workflow must have exactly one initial state.");
        }

        if (workflowDefinition.states().stream().noneMatch(State::terminal)) {
            throw new IllegalStateException("Workflow must have a terminal state.");
        }

        for (Transition transition : workflowDefinition.transitions()) {
            if (workflowDefinition.getState(transition.source()).terminal()) {
                throw new IllegalStateException("Terminal states cannot have outgoing transitions.");
            }
            workflowDefinition.getState(transition.target());
        }

        Set<String> reachable = new HashSet<String>();

        //add initial state
        reachable.add(
                workflowDefinition.states()
                .stream()
                .filter(State::initial)
                .findFirst()
                .orElseThrow()
                .code()
        );

        boolean changed;
        do {
            changed = false;
            for (Transition t : workflowDefinition.transitions()) {
                if (reachable.contains(t.source())){
                    changed |= reachable.add(t.target());
                }
            }
        } while (changed);

        if (reachable.size() != workflowDefinition.states().size()) {
            throw new IllegalStateException("Workflow contains unreachable states.");
        }

        for (DeferredEvent deferred : workflowDefinition.deferredEvents()) {

            if (workflowDefinition.getState(deferred.state()).terminal()) {
                throw new IllegalStateException("Terminal states cannot defer events.");
            }

            if (workflowDefinition.transitions().stream()
                    .noneMatch(t -> t.event().equals(deferred.event()))) {
                throw new IllegalStateException("Deferred event has no transition handler: " + deferred.event());
            }
        }
    }
}
