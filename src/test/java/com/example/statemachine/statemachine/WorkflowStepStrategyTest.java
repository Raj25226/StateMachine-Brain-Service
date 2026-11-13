package com.example.statemachine.statemachine;

import static org.junit.jupiter.api.Assertions.*;

import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.dto.workflow.WorkflowDefinition;
import com.example.statemachine.dto.workflow.WorkflowStateDefinition;
import com.example.statemachine.dto.workflow.WorkflowStepContext;
import com.example.statemachine.dto.workflow.WorkflowTransitionDefinition;
import com.example.statemachine.enums.WorkflowEventProcessingOutcome;
import com.example.statemachine.repository.JdbcWorkflowDefinitionRepository;
import com.example.statemachine.repository.JdbcWorkflowStateMachinePersistence;
import com.example.statemachine.strategy.WorkflowStepStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;

class WorkflowStepStrategyTest {
    @Test
    void finaagAndLyntraExecuteDifferentGraphsWithASharedOtpStep() {
        List<String> executions = new ArrayList<>();
        List<String> actionNames =
                List.of(
                        "verifyOtp",
                        "updateProfile",
                        "uploadAccountStatement",
                        "uploadUdyamDocument",
                        "verifyIdentity",
                        "selectPlan");
        WorkflowStepStrategy[] strategies =
                actionNames.stream()
                        .map(
                                action ->
                                        strategy(
                                                action,
                                                command ->
                                                        executions.add(
                                                                command.workflowKey()
                                                                        + ":"
                                                                        + action)))
                        .toArray(WorkflowStepStrategy[]::new);
        WorkflowDefinition finaag =
                configuredDefinition(
                        1,
                        "finaag",
                        List.of(
                                "OTP_VERIFICATION",
                                "PROFILE_DETAILS",
                                "ACCOUNT_STATEMENT_UPLOAD",
                                "UDYAM_DOCUMENT_UPLOAD",
                                "COMPLETED"),
                        List.of(
                                "VERIFY_OTP",
                                "UPDATE_PROFILE",
                                "UPLOAD_ACCOUNT_STATEMENT",
                                "UPLOAD_UDYAM_DOCUMENT"),
                        actionNames.subList(0, 4));
        WorkflowDefinition lyntra =
                configuredDefinition(
                        2,
                        "lyntra",
                        List.of(
                                "OTP_VERIFICATION",
                                "IDENTITY_REVIEW",
                                "PLAN_SELECTION",
                                "COMPLETED"),
                        List.of("VERIFY_OTP", "VERIFY_IDENTITY", "SELECT_PLAN"),
                        List.of("verifyOtp", "verifyIdentity", "selectPlan"));
        try (AnnotationConfigApplicationContext context =
                applicationContext(Map.of(1, finaag, 2, lyntra), strategies)) {
            StateMachine<String, String> machine = machine(context, lyntra);
            try {
                machine.startReactively().block();
                assertEquals(
                        WorkflowEventProcessingOutcome.EVENT_NOT_ALLOWED,
                        new WorkflowEventProcessor()
                                .processEvent(
                                        machine,
                                        "UPDATE_PROFILE",
                                        stepContext(lyntra, "UPDATE_PROFILE"))
                                .outcome());
                assertTrue(executions.isEmpty());
            } finally {
                machine.stopReactively().block();
            }
            runWorkflow(context, finaag);
            runWorkflow(context, lyntra);
        }
        assertEquals(
                List.of(
                        "finaag:verifyOtp",
                        "finaag:updateProfile",
                        "finaag:uploadAccountStatement",
                        "finaag:uploadUdyamDocument",
                        "lyntra:verifyOtp",
                        "lyntra:verifyIdentity",
                        "lyntra:selectPlan"),
                executions);
    }

    private WorkflowDefinition configuredDefinition(
            int id, String key, List<String> codes, List<String> events, List<String> actions) {
        List<WorkflowStateDefinition> states = new ArrayList<WorkflowStateDefinition>();
        List<WorkflowTransitionDefinition> transitions =
                new ArrayList<WorkflowTransitionDefinition>();
        for (int i = 0; i < codes.size(); i++) {
            states.add(
                    new WorkflowStateDefinition(
                            codes.get(i), i == 0, i == codes.size() - 1, List.of()));
            if (i < events.size()) {
                transitions.add(
                        new WorkflowTransitionDefinition(
                                i + 1,
                                codes.get(i),
                                events.get(i),
                                codes.get(i + 1),
                                "EXTERNAL",
                                List.of(actions.get(i), "recordTransition")));
            }
        }
        return new WorkflowDefinition(id, key, 1, states, transitions);
    }

    @Test
    void rejectedEventsDoNotRunStrategiesAndActionFailuresReachTheCaller() {
        List<String> executions = new ArrayList<>();
        WorkflowDefinition definition =
                definition(1, "failure-test", List.of("failingStep", "laterStep"));
        WorkflowStepStrategy failing =
                strategy(
                        "failingStep",
                        command -> {
                            executions.add("failed");
                            throw new Exception("External service rejected the request");
                        });
        WorkflowStepStrategy later = strategy("laterStep", command -> executions.add("later"));
        try (AnnotationConfigApplicationContext context =
                applicationContext(Map.of(1, definition), failing, later)) {
            StateMachine<String, String> machine = machine(context, definition);
            try {
                machine.startReactively().block();
                assertTrue(
                        executions.isEmpty(),
                        "Starting a workflow must not execute a business step");
                WorkflowEventProcessor processor = new WorkflowEventProcessor();
                assertEquals(
                        WorkflowEventProcessingOutcome.EVENT_NOT_ALLOWED,
                        processor
                                .processEvent(
                                        machine, "EVENT_1", stepContext(definition, "EVENT_1"))
                                .outcome());
                assertTrue(executions.isEmpty());
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                processor.processEvent(
                                        machine, "EVENT_0", stepContext(definition, "EVENT_0")));
                assertEquals(List.of("failed"), executions);
                assertTrue(machine.hasStateMachineError());
            } finally {
                machine.stopReactively().block();
            }
        }
    }

    @Test
    void missingCommandContextFailsRatherThanRunningABusinessStep() {
        List<String> executions = new ArrayList<>();
        WorkflowDefinition definition = definition(1, "context-test", List.of("customStep"));
        try (AnnotationConfigApplicationContext context =
                applicationContext(
                        Map.of(1, definition),
                        strategy("customStep", command -> executions.add("executed")))) {
            StateMachine<String, String> machine = machine(context, definition);
            try {
                machine.startReactively().block();
                assertThrows(
                        IllegalStateException.class,
                        () -> new WorkflowEventProcessor().processEvent(machine, "EVENT_0"));
                assertTrue(executions.isEmpty());
            } finally {
                machine.stopReactively().block();
            }
        }
    }

    @Test
    void duplicateOrReservedStrategyNamesFailAtRegistration() {
        WorkflowStepStrategy first = strategy("duplicate", command -> {});
        WorkflowStepStrategy second = strategy("duplicate", command -> {});
        WorkflowBehaviorRegistry duplicateRegistry =
                new WorkflowBehaviorRegistry(List.of(first, second));
        assertThrows(IllegalStateException.class, duplicateRegistry::registerStepStrategies);
        WorkflowBehaviorRegistry reservedRegistry =
                new WorkflowBehaviorRegistry(List.of(strategy("recordTransition", command -> {})));
        assertThrows(IllegalStateException.class, reservedRegistry::registerStepStrategies);
        WorkflowBehaviorRegistry blankRegistry =
                new WorkflowBehaviorRegistry(List.of(strategy(" ", command -> {})));
        assertThrows(IllegalStateException.class, blankRegistry::registerStepStrategies);
    }

    private void runWorkflow(
            AnnotationConfigApplicationContext context, WorkflowDefinition definition) {
        StateMachine<String, String> machine = machine(context, definition);
        try {
            machine.startReactively().block();
            for (WorkflowTransitionDefinition transition : definition.transitions()) {
                assertEquals(
                        WorkflowEventProcessingOutcome.APPLIED,
                        new WorkflowEventProcessor()
                                .processEvent(
                                        machine,
                                        transition.event(),
                                        stepContext(definition, transition.event()))
                                .outcome());
                assertEquals(transition.target(), machine.getState().getId());
            }
            assertTrue(machine.isComplete());
        } finally {
            machine.stopReactively().block();
        }
    }

    @SuppressWarnings("unchecked")
    private StateMachine<String, String> machine(
            AnnotationConfigApplicationContext context, WorkflowDefinition definition) {
        return context.getBean(StateMachineFactory.class)
                .getStateMachine(definition.id().toString());
    }

    private WorkflowStepContext stepContext(WorkflowDefinition definition, String event) {
        return new WorkflowStepContext(
                new WorkflowCommandMetadata(
                        "SUBMIT_EVENT", "request-1", "APP-101", event, "key-" + event, null),
                definition.id(),
                definition.workflowKey(),
                definition.version());
    }

    private WorkflowDefinition definition(int id, String key, List<String> orderedActions) {
        List<WorkflowStateDefinition> states = new ArrayList<>();
        List<WorkflowTransitionDefinition> transitions = new ArrayList<>();
        for (int index = 0; index <= orderedActions.size(); index++) {
            states.add(
                    new WorkflowStateDefinition(
                            "STATE_" + index,
                            index == 0,
                            index == orderedActions.size(),
                            List.of()));
            if (index < orderedActions.size()) {
                transitions.add(
                        new WorkflowTransitionDefinition(
                                index + 1,
                                "STATE_" + index,
                                "EVENT_" + index,
                                "STATE_" + (index + 1),
                                "EXTERNAL",
                                List.of(orderedActions.get(index), "recordTransition")));
            }
        }
        return new WorkflowDefinition(id, key, 1, states, transitions);
    }

    private AnnotationConfigApplicationContext applicationContext(
            Map<Integer, WorkflowDefinition> definitions, WorkflowStepStrategy... strategies) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(
                JdbcWorkflowDefinitionRepository.class,
                () ->
                        new JdbcWorkflowDefinitionRepository(null) {
                            @Override
                            public WorkflowDefinition loadWorkflowDefinition(Integer id) {
                                return definitions.get(id);
                            }
                        });
        for (int index = 0; index < strategies.length; index++) {
            WorkflowStepStrategy strategy = strategies[index];
            context.registerBean("strategy" + index, WorkflowStepStrategy.class, () -> strategy);
        }
        context.registerBean(
                WorkflowBehaviorRegistry.class,
                () -> new WorkflowBehaviorRegistry(List.of(strategies)));
        context.register(JdbcWorkflowModelFactory.class, WorkflowEngineConfiguration.class);
        context.registerBean(
                JdbcWorkflowStateMachinePersistence.class,
                () -> new JdbcWorkflowStateMachinePersistence(null));
        context.refresh();
        return context;
    }

    private WorkflowStepStrategy strategy(String name, StepExecution execution) {
        return new WorkflowStepStrategy() {
            @Override
            public String actionName() {
                return name;
            }

            @Override
            public void execute(WorkflowStepContext context) throws Exception {
                execution.execute(context);
            }
        };
    }

    @FunctionalInterface
    private interface StepExecution {
        void execute(WorkflowStepContext context) throws Exception;
    }
}
