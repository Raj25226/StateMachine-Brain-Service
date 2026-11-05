package com.example.statemachine.repository;

import com.example.statemachine.dto.persistence.WorkflowInstance;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.statemachine.support.DefaultExtendedState;
import org.springframework.statemachine.support.DefaultStateMachineContext;

import static org.junit.jupiter.api.Assertions.*;

class JdbcWorkflowStateMachinePersistenceTest {
    private final CapturingInstanceRepository repository = new CapturingInstanceRepository();
    private final JdbcWorkflowStateMachinePersistence persistence = new JdbcWorkflowStateMachinePersistence(repository);

    @Test
    void restoresCurrentStateFromTheInstanceWithoutSerializedData() {
        var context = persistence.read("APP-101");
        assertEquals("PROFILE_DETAILS", context.getState());
        assertTrue(context.getExtendedState().getVariables().isEmpty());
        assertNull(context.getEvent());
    }

    @Test
    void updatesOnlyTheRelationalStateAndCanRestoreIt() {
        persistence.write(new DefaultStateMachineContext<>("COMPLETED", null, null,
                new DefaultExtendedState()), "APP-101");
        assertEquals("APP-101", repository.reference);
        assertEquals("COMPLETED", repository.state);
        assertEquals("COMPLETED", persistence.read("APP-101").getState());
    }

    @Test
    void rejectsExtendedVariablesRatherThanSilentlyDiscardingThem() {
        var context = new DefaultStateMachineContext<String, String>("COMPLETED", null, null,
                new DefaultExtendedState(new HashMap<>(Map.of("businessValue", "must-be-durable"))));
        assertThrows(IllegalStateException.class, () -> persistence.write(context, "APP-101"));
        assertNull(repository.reference);
    }

    @Test
    void rejectsChildContextsInsteadOfPersistingAnIncompleteMachine() {
        var context = new DefaultStateMachineContext<String, String>("PROFILE_DETAILS", null, null,
                new DefaultExtendedState());
        context.getChilds().add(new DefaultStateMachineContext<>("CHILD", null, null, new DefaultExtendedState()));
        assertThrows(IllegalStateException.class, () -> persistence.write(context, "APP-101"));
        assertNull(repository.reference);
    }

    private static class CapturingInstanceRepository extends JdbcWorkflowInstanceRepository {
        private String reference;
        private String state = "PROFILE_DETAILS";

        CapturingInstanceRepository() { super(null); }

        @Override
        public WorkflowInstance findByReferenceId(String reference) {
            return new WorkflowInstance(1, reference, 7, state);
        }

        @Override
        public void updateCurrentState(String reference, String state) {
            this.reference = reference;
            this.state = state;
        }
    }
}
