package com.example.statemachine.repository;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowRepositoryWriteTest {
    private final NamedParameterJdbcTemplate noMatchingRows = new NamedParameterJdbcTemplate(new JdbcTemplate()) {
        @Override
        public int update(String sql, Map<String, ?> parameters) {
            return 0;
        }
    };

    @Test
    void rejectsCreateWhenInitialStateCannotBeResolvedToItsId() {
        JdbcWorkflowInstanceRepository repository = new JdbcWorkflowInstanceRepository(noMatchingRows);
        assertThrows(IllegalStateException.class, () -> repository.createWorkflowInstance("APP-101", 7, "MISSING"));
    }

    @Test
    void rejectsHistoryWhenInstanceOrStateIdsCannotBeResolved() {
        JdbcWorkflowHistoryRepository repository = new JdbcWorkflowHistoryRepository(noMatchingRows);
        assertThrows(IllegalStateException.class, () -> repository.recordTransition(
                "APP-101", 7, "START", "FINISH", "END", "req-1", "key-1"));
    }

    @Test
    void rejectsStateUpdateWhenTargetStateCannotBeResolved() {
        JdbcWorkflowInstanceRepository repository = new JdbcWorkflowInstanceRepository(noMatchingRows);
        assertThrows(IllegalStateException.class, () -> repository.updateCurrentState("APP-101", "MISSING"));
    }
}
