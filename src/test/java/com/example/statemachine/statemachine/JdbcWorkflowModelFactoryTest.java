package com.example.statemachine.statemachine;

import com.example.statemachine.dto.workflow.WorkflowDefinition;
import com.example.statemachine.dto.workflow.WorkflowStateDefinition;
import com.example.statemachine.dto.workflow.WorkflowTransitionDefinition;
import com.example.statemachine.repository.JdbcWorkflowDefinitionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcWorkflowModelFactoryTest {
    @Test
    void convertsFrameworkMachineIdentifierToIntegerDefinitionId() {
        CapturingDefinitionRepository definitionRepository = new CapturingDefinitionRepository();
        JdbcWorkflowModelFactory modelFactory = new JdbcWorkflowModelFactory(
                definitionRepository, new WorkflowBehaviorRegistry());

        assertNotNull(modelFactory.build("7"));
        assertEquals(Integer.valueOf(7), definitionRepository.requestedDefinitionId);
        assertEquals(Integer.valueOf(19), definitionRepository.definition.transitions().getFirst().id());
    }

    @Test
    void rejectsNonNumericFrameworkIdentifiers() {
        JdbcWorkflowModelFactory modelFactory = new JdbcWorkflowModelFactory(
                new CapturingDefinitionRepository(), new WorkflowBehaviorRegistry());
        assertThrows(NumberFormatException.class, () -> modelFactory.build("legacy-definition-key"));
    }

    private static class CapturingDefinitionRepository extends JdbcWorkflowDefinitionRepository {
        private Integer requestedDefinitionId;
        private final WorkflowDefinition definition = new WorkflowDefinition(
                7, "onboarding", 1,
                List.of(new WorkflowStateDefinition("START", true, false, List.of()),
                        new WorkflowStateDefinition("END", false, true, List.of())),
                List.of(new WorkflowTransitionDefinition(19, "START", "FINISH", "END", "EXTERNAL", null, List.of())),
                List.of());

        CapturingDefinitionRepository() {
            super(null);
        }

        @Override
        public WorkflowDefinition loadWorkflowDefinition(Integer definitionId) {
            requestedDefinitionId = definitionId;
            return definition;
        }
    }
}
