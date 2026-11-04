package com.example.statemachine.statemachine;

import com.example.statemachine.constants.WorkflowBehaviorConstants;
import com.example.statemachine.constants.WorkflowContextConstants;
import com.example.statemachine.enums.LogValueSanitizerFunction;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Actions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Explicit strategy registry: database names select code; no expressions are evaluated. */
@Component
@Slf4j
public class WorkflowBehaviorRegistry {

    private final Map<String, Function< StateContext<String, String>, Mono<Void> > > actions =
            Map.of(
                    WorkflowBehaviorConstants.RECORD_STATE_ENTRY,
                        Actions.from( stateContext -> {
                                    stateContext.getExtendedState()
                                            .getVariables()
                                            .put(WorkflowContextConstants.LAST_ENTERED_STATE, stateContext.getTarget().getId());
                                    log.debug("workflow_state_entered state={}", LogValueSanitizerFunction.SANITIZE.apply(stateContext.getTarget().getId()));

                                }
                        ),

                    WorkflowBehaviorConstants.RECORD_STATE_EXIT,
                        Actions.from(stateContext -> {
                                    stateContext.getExtendedState()
                                            .getVariables()
                                            .put(WorkflowContextConstants.LAST_EXITED_STATE, stateContext.getSource().getId());
                                    log.debug("workflow_state_exited state={}", LogValueSanitizerFunction.SANITIZE.apply(stateContext.getSource().getId()));
                                }
                        ),

                    WorkflowBehaviorConstants.RECORD_TRANSITION,
                        Actions.from( stateContext -> {
                                    stateContext.getExtendedState()
                                            .getVariables()
                                            .put(WorkflowContextConstants.LAST_PROCESSED_EVENT, stateContext.getEvent());
                                    log.debug("workflow_transition_action event={}", LogValueSanitizerFunction.SANITIZE.apply(stateContext.getEvent()));
                                }
                        ),

                    WorkflowBehaviorConstants.OBSERVE_STATE,
                        Actions.from( stateContext -> log.debug("workflow_state_activity definition={}", LogValueSanitizerFunction.SANITIZE.apply(stateContext.getStateMachine().getId())))
            );

    private final Map<String, Function<StateContext<String, String>, Mono<Boolean>>> guards =
            Map.of(WorkflowBehaviorConstants.ALWAYS_ALLOW, stateContext -> Mono.just(true));

    public Function<StateContext<String, String>, Mono<Void>> resolveActionByName(String actionName) {

        Function<StateContext<String, String>, Mono<Void>> action =
                actions.get(actionName);

        if (action == null) {
            throw new IllegalStateException("Unregistered workflow action: " + actionName);
        }

        return action;
    }

    public Function<StateContext<String, String>, Mono<Boolean>> resolveGuardByName(String guardName) {

        Function<StateContext<String, String>, Mono<Boolean>> guard =
                guards.get(guardName);

        if (guard == null) {
            throw new IllegalStateException("Unregistered workflow guard: " + guardName);
        }
        return guard;
    }

}

