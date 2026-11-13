package com.example.statemachine.dto.workflow;

import com.example.statemachine.dto.api.WorkflowCommandMetadata;
import com.example.statemachine.enums.WorkflowOperation;

public record WorkflowCommand(WorkflowCommandMetadata metadata, WorkflowOperation operation) {}
