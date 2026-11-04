package com.example.statemachine.repository;

import com.example.statemachine.constants.SqlQueryConstants;
import com.example.statemachine.enums.SqlQueryFunction;
import com.example.statemachine.constants.DatabaseConstants;
import com.example.statemachine.dto.persistence.WorkflowIdempotencyRecord;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkflowIdempotencyRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWorkflowIdempotencyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByIdempotencyKey(String idempotencyKey) {
        final String sql = SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.IDEMPOTENCY_PARENT, SqlQueryConstants.EXISTS_BY_IDEMPOTENCY_KEY);
        Map<String, Object> parameters = Map.of(DatabaseConstants.PARAM_KEY, idempotencyKey);

        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, parameters, Boolean.class));
    }

    public void reserveIdempotencyKey(String idempotencyKey, String requestFingerprint) {
        final String sql = SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.IDEMPOTENCY_PARENT, SqlQueryConstants.RESERVE_IDEMPOTENCY_KEY);
        Map<String, Object> parameters = Map.of(DatabaseConstants.PARAM_KEY, idempotencyKey, DatabaseConstants.PARAM_FINGERPRINT, requestFingerprint);

        jdbcTemplate.update(sql, parameters);
    }

    public WorkflowIdempotencyRecord findAndLockByIdempotencyKey(String idempotencyKey) {
        final String sql = SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.IDEMPOTENCY_PARENT, SqlQueryConstants.FIND_AND_LOCK_IDEMPOTENCY_RECORD);
        Map<String, Object> parameters = Map.of(DatabaseConstants.PARAM_KEY, idempotencyKey);

        return jdbcTemplate.queryForObject(sql, parameters, (resultSet, rowNumber) ->
                new WorkflowIdempotencyRecord(
                        resultSet.getString(DatabaseConstants.COLUMN_REQUEST_FINGERPRINT),
                        resultSet.getString(DatabaseConstants.COLUMN_RESPONSE_JSON),
                        (Integer) resultSet.getObject(DatabaseConstants.COLUMN_HTTP_STATUS)
                )
            );
    }

    public void saveSuccessfulResponse(String idempotencyKey, String responseJson, int httpStatus) {
        final String sql = SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.IDEMPOTENCY_PARENT, SqlQueryConstants.SAVE_SUCCESSFUL_RESPONSE);
        Map<String, Object> parameters = Map.of(DatabaseConstants.PARAM_KEY, idempotencyKey, DatabaseConstants.PARAM_RESPONSE, responseJson, DatabaseConstants.PARAM_STATUS, httpStatus);

        jdbcTemplate.update(sql, parameters);
    }
}
