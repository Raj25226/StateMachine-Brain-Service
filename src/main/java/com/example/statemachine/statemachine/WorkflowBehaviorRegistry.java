package com.example.statemachine.statemachine;

import com.example.statemachine.command.WorkflowStepCommand;
import com.example.statemachine.constants.WorkflowBehaviorConstants;
import com.example.statemachine.constants.WorkflowConstants;
import com.example.statemachine.dto.workflow.WorkflowStepContext;
import com.example.statemachine.enums.LogValueSanitizerFunction;
import com.example.statemachine.service.AntiCorruptionLayerClient;
import com.example.statemachine.strategy.WorkflowStepStrategy;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Actions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Explicit strategy registry: database action names select code; no expressions are evaluated. */
@Component
@Slf4j
public class WorkflowBehaviorRegistry {
    private final List<WorkflowStepStrategy> stepStrategies;
    private final AntiCorruptionLayerClient remoteClient;

    @Autowired
    public WorkflowBehaviorRegistry(
            List<WorkflowStepStrategy> strategies, AntiCorruptionLayerClient remoteClient) {
        this.stepStrategies = strategies;
        this.remoteClient = remoteClient;
    }

    public WorkflowBehaviorRegistry(List<WorkflowStepStrategy> strategies) {
        this(strategies, null);
    }

    private final Map<String, WorkflowStepStrategy> strategiesByName = new HashMap<>();

    private final Map<String, Function<StateContext<String, String>, Mono<Void>>> actions =
            Map.of(
                    WorkflowBehaviorConstants.RECORD_STATE_ENTRY,
                    Actions.from(
                            context ->
                                    log.debug(
                                            "Workflow state entered | state={}",
                                            LogValueSanitizerFunction.SANITIZE.apply(
                                                    context.getTarget().getId()))),
                    WorkflowBehaviorConstants.RECORD_STATE_EXIT,
                    Actions.from(
                            context ->
                                    log.debug(
                                            "Workflow state exited | state={}",
                                            LogValueSanitizerFunction.SANITIZE.apply(
                                                    context.getSource().getId()))),
                    WorkflowBehaviorConstants.RECORD_TRANSITION,
                    Actions.from(
                            context ->
                                    log.debug(
                                            "Workflow transition action | event={}",
                                            LogValueSanitizerFunction.SANITIZE.apply(
                                                    context.getEvent()))),
                    WorkflowBehaviorConstants.OBSERVE_STATE,
                    Actions.from(
                            context ->
                                    log.debug(
                                            "Workflow state activity | definition={}",
                                            LogValueSanitizerFunction.SANITIZE.apply(
                                                    context.getStateMachine().getId()))));

    @PostConstruct
    public void registerStepStrategies() {
        for (WorkflowStepStrategy strategy : stepStrategies) {
            String actionName = strategy.actionName();
            if (actionName == null || actionName.isBlank()) {
                throw new IllegalStateException("Workflow step action name must be nonblank.");
            }
            if (actions.containsKey(actionName)
                    || strategiesByName.putIfAbsent(actionName, strategy) != null) {
                throw new IllegalStateException("Duplicate workflow action: " + actionName);
            }
        }
    }

    public Function<StateContext<String, String>, Mono<Void>> resolveActionByName(
            String actionName) {
        Function<StateContext<String, String>, Mono<Void>> action = actions.get(actionName);
        if (action != null) {
            return action;
        }

        WorkflowStepStrategy strategy = strategiesByName.get(actionName);
        if (strategy == null && remoteClient == null) {
            throw new IllegalStateException("Unregistered workflow action: " + actionName);
        }

        return stateContext ->
                Mono.fromCallable(
                                () -> {
                                    WorkflowStepContext stepContext =
                                            stateContext
                                                    .getMessageHeaders()
                                                    .get(
                                                            WorkflowConstants
                                                                    .WORKFLOW_STEP_CONTEXT_HEADER,
                                                            WorkflowStepContext.class);
                                    if (stepContext == null) {
                                        throw new IllegalStateException(
                                                "Business step requires a submitted command context: "
                                                        + actionName);
                                    }
                                    if (remoteClient != null) {
                                        remoteClient.execute(actionName, stepContext);
                                    } else {
                                        new WorkflowStepCommand(strategy, stepContext).execute();
                                    }
                                    return true;
                                })
                        .then();
    }
}
