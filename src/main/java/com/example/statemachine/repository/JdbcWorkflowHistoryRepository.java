package com.example.statemachine.repository;

import com.example.statemachine.constants.SqlQueryConstants;
import com.example.statemachine.enums.SqlQueryFunction;
import com.example.statemachine.constants.DatabaseConstants;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Append-only records of applied events; participates in the caller's workflow transaction.
 */
@Repository
public class JdbcWorkflowHistoryRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWorkflowHistoryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordTransition(String referenceId, String definitionId, String sourceState,
                                 String event, String targetState, String requestId, String idempotencyKey) {

        Map<String, Object> parameters = new HashMap<>();
        parameters.put(DatabaseConstants.PARAM_REFERENCE_ID, referenceId);
        parameters.put(DatabaseConstants.PARAM_DEFINITION_ID, definitionId);
        parameters.put(DatabaseConstants.PARAM_SOURCE_STATE, sourceState);
        parameters.put(DatabaseConstants.PARAM_EVENT, event);
        parameters.put(DatabaseConstants.PARAM_TARGET_STATE, targetState);
        parameters.put(DatabaseConstants.PARAM_REQUEST_ID, requestId);
        parameters.put(DatabaseConstants.PARAM_IDEMPOTENCY_KEY, idempotencyKey);

        final String sql =
                SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.TRANSITION_HISTORY_PARENT, SqlQueryConstants.INSERT_TRANSITION_HISTORY);

        jdbcTemplate.update(sql, parameters);
    }
}
