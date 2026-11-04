package com.example.statemachine.repository;

import com.example.statemachine.constants.ApiErrorConstants;
import com.example.statemachine.constants.DatabaseConstants;
import com.example.statemachine.constants.SqlQueryConstants;
import com.example.statemachine.dto.persistence.WorkflowDefinitionMetadata;
import com.example.statemachine.dto.workflow.WorkflowActionBinding;
import com.example.statemachine.dto.workflow.WorkflowDefinition;
import com.example.statemachine.dto.workflow.WorkflowStateDefinition;
import com.example.statemachine.dto.workflow.WorkflowTransitionDefinition;
import com.example.statemachine.enums.SqlQueryFunction;
import com.example.statemachine.exception.WorkflowException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkflowDefinitionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWorkflowDefinitionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Integer findLatestActiveDefinitionId(String workflowKey) {
        final String sql = SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_LATEST_ACTIVE_DEFINITION_ID);

        final Map<String, ?> parameters = Map.of(DatabaseConstants.PARAM_WORKFLOW_KEY, workflowKey);

        final List<Integer> definitionIds = jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) -> {
            return resultSet.getInt(DatabaseConstants.COLUMN_ID);
        });

        if (definitionIds.isEmpty()) {
            throw new WorkflowException(
                    HttpStatus.NOT_FOUND,
                    ApiErrorConstants.WORKFLOW_DEFINITION_NOT_FOUND,
                    "No active workflow definition exists for " + workflowKey + "."
            );
        }

        return definitionIds.getFirst();
    }

    public WorkflowDefinition loadWorkflowDefinition(Integer definitionId) {
        final Map<String, ?> parameters = Map.of(DatabaseConstants.PARAM_DEFINITION_ID, definitionId);

        final WorkflowDefinitionMetadata definitionMetadata = loadDefinitionInfo(definitionId, parameters);
        final Map<String, List<WorkflowActionBinding>> stateActions = loadStateActions(parameters);
        final List<WorkflowStateDefinition> states = loadStates(parameters, stateActions);
        final Map<Integer, List<String>> transitionActions = loadTransitionActions(parameters);
        final List<WorkflowTransitionDefinition> transitions = loadTransitions(parameters, transitionActions);

        return new WorkflowDefinition(
                definitionId,
                definitionMetadata.workflowKey(),
                definitionMetadata.version(),
                states,
                transitions
        );
    }

    private WorkflowDefinitionMetadata loadDefinitionInfo(Integer definitionId, Map<String, ?> parameters) {
        final String sql = SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_DEFINITION_METADATA);

        final List<WorkflowDefinitionMetadata> definitionMetadataRows = jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) -> {
            return new WorkflowDefinitionMetadata(
                    resultSet.getString(DatabaseConstants.COLUMN_WORKFLOW_KEY),
                    resultSet.getInt(DatabaseConstants.COLUMN_VERSION)
            );
        });

        if (definitionMetadataRows.isEmpty()) {
            throw new IllegalStateException("Workflow definition not found: " + definitionId);
        }

        return definitionMetadataRows.getFirst();
    }

    private Map<String, List<WorkflowActionBinding>> loadStateActions(Map<String, ?> parameters) {

        String sql = SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_STATE_ACTIONS);

        Map<String, List<WorkflowActionBinding>> actionsByState = new HashMap<>();

        RowCallbackHandler rowCallbackHandler = resultSet -> {

            String stateCode = resultSet.getString(DatabaseConstants.COLUMN_STATE_CODE);
            WorkflowActionBinding actionBinding =
                    new WorkflowActionBinding(
                        resultSet.getInt(DatabaseConstants.COLUMN_FK_ID_WORKFLOW_ACTION),
                        resultSet.getString(DatabaseConstants.COLUMN_ACTION_NAME),
                        resultSet.getString(DatabaseConstants.COLUMN_PHASE),
                        resultSet.getInt(DatabaseConstants.COLUMN_EXECUTION_ORDER)
                    );

            List<WorkflowActionBinding> stateActions = actionsByState.computeIfAbsent(stateCode, key -> new ArrayList<>());
            stateActions.add(actionBinding);
        };

        jdbcTemplate.query(sql, parameters, rowCallbackHandler);

        return actionsByState;
    }

    private List<WorkflowStateDefinition> loadStates(Map<String, ?> parameters, Map<String, List<WorkflowActionBinding>> actionsByState) {
        final String sql = SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_STATES);

        List<WorkflowStateDefinition> stateDefinitions = jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) -> {
            final String stateCode = resultSet.getString(DatabaseConstants.COLUMN_STATE_CODE);
            final List<WorkflowActionBinding> actions = List.copyOf(actionsByState.getOrDefault(stateCode, List.of()));

            return new WorkflowStateDefinition(
                    stateCode,
                    resultSet.getBoolean(DatabaseConstants.COLUMN_INITIAL_STATE),
                    resultSet.getBoolean(DatabaseConstants.COLUMN_TERMINAL_STATE),
                    actions
            );
        });
        return stateDefinitions;
    }

    private Map<Integer, List<String>> loadTransitionActions(Map<String, ?> parameters) {

        String sql = SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_TRANSITION_ACTIONS);

        Map<Integer, List<String>> actionsByTransition = new HashMap<>();

        RowCallbackHandler rowCallbackHandler = resultSet -> {
            Integer transitionId = resultSet.getInt(DatabaseConstants.COLUMN_FK_ID_WORKFLOW_TRANSITION);
            String actionName = resultSet.getString(DatabaseConstants.COLUMN_ACTION_NAME);

            List<String> actionNames = actionsByTransition.computeIfAbsent(transitionId, key -> new ArrayList<>());
            actionNames.add(actionName);
        };

        jdbcTemplate.query(sql, parameters, rowCallbackHandler);

        return actionsByTransition;
    }

    private List<WorkflowTransitionDefinition> loadTransitions(Map<String, ?> parameters, Map<Integer, List<String>> actionsByTransition) {
        final String sql = SqlQueryFunction.GET_QUERY.apply(SqlQueryConstants.WORKFLOW_DEFINITION_PARENT, SqlQueryConstants.FIND_TRANSITIONS);

        List<WorkflowTransitionDefinition> transitionDefinitions = jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) -> {
            Integer transitionId = resultSet.getInt(DatabaseConstants.COLUMN_ID);
            List<String> transitionActions = List.copyOf(actionsByTransition.getOrDefault(transitionId, List.of()));

            return new WorkflowTransitionDefinition(
                    transitionId,
                    resultSet.getString(DatabaseConstants.COLUMN_SOURCE_STATE),
                    resultSet.getString(DatabaseConstants.COLUMN_EVENT_CODE),
                    resultSet.getString(DatabaseConstants.COLUMN_TARGET_STATE),
                    resultSet.getString(DatabaseConstants.COLUMN_TRANSITION_KIND),
                    transitionActions
            );
        });

        return transitionDefinitions;
    }
}
