package com.example.statemachine.dto.workflow;

import java.util.List;

public record WorkflowStateDefinition(
        String code, boolean initial, boolean terminal, List<WorkflowActionBinding> actions) {}
