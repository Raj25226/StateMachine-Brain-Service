package com.example.statemachine.repository;

import lombok.AllArgsConstructor;
import com.example.statemachine.constants.ApiErrorConstants;
import com.example.statemachine.constants.DatabaseConstants;
import com.example.statemachine.constants.SqlQueryConstants;
import com.example.statemachine.dto.persistence.WorkflowInstance;
import com.example.statemachine.enums.SqlQueryFunction;
import com.example.statemachine.exception.WorkflowException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@AllArgsConstructor
public class JdbcWorkflowInstanceRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void createWorkflowInstance(String referenceId, Integer definitionId, String currentState) {

        final String sql =   SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.WORKFLOW_INSTANCE_PARENT, SqlQueryConstants.INSERT_WORKFLOW_INSTANCE);
        Map<String,Object> parameters = Map.of(DatabaseConstants.PARAM_REFERENCE, referenceId, DatabaseConstants.PARAM_DEFINITION, definitionId, DatabaseConstants.PARAM_STATE, currentState);

        int insertedRowCount = jdbcTemplate.update(sql, parameters);
        if (insertedRowCount != 1) {
            throw new IllegalStateException("Initial workflow state was not found for the definition.");
        }
    }

    public WorkflowInstance findByReferenceId(String referenceId) {
        return readWorkflowInstance(referenceId, false);
    }

    public WorkflowInstance findAndLockByReferenceId(String referenceId) {
        return readWorkflowInstance(referenceId, true);
    }

    private WorkflowInstance readWorkflowInstance(String referenceId, boolean lockForUpdate) {
        final String sql = lockForUpdate
                ? SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.WORKFLOW_INSTANCE_PARENT, SqlQueryConstants.FIND_AND_LOCK_WORKFLOW_INSTANCE)
                : SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.WORKFLOW_INSTANCE_PARENT, SqlQueryConstants.FIND_WORKFLOW_INSTANCE);
        Map<String, Object> parameters = Map.of(DatabaseConstants.PARAM_REFERENCE, referenceId);

        return jdbcTemplate.query( sql, parameters, (resultSet, rowNumber) ->
                                    new WorkflowInstance(
                                        resultSet.getInt(DatabaseConstants.COLUMN_ID),
                                        resultSet.getString(DatabaseConstants.COLUMN_REFERENCE_ID),
                                        resultSet.getInt(DatabaseConstants.COLUMN_FK_ID_WORKFLOW_DEFINITION),
                                        resultSet.getString(DatabaseConstants.COLUMN_CURRENT_STATE)
                                    )
                )
                .stream()
                .findFirst()
                .orElseThrow( () -> new WorkflowException( HttpStatus.NOT_FOUND, ApiErrorConstants.WORKFLOW_NOT_FOUND,
                            "Workflow " + referenceId + " was not found.")
                );
    }

    public void updateCurrentState(String referenceId, String currentState) {
        final String sql = SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.WORKFLOW_INSTANCE_PARENT, SqlQueryConstants.UPDATE_CURRENT_STATE);

        Map<String, Object> parameters = Map.of(DatabaseConstants.PARAM_REFERENCE, referenceId, DatabaseConstants.PARAM_STATE, currentState);
        int updatedRowCount = jdbcTemplate.update(sql, parameters);

        if (updatedRowCount != 1){
            throw new IllegalStateException("Workflow disappeared while updating its current state.");
        }
    }
}
