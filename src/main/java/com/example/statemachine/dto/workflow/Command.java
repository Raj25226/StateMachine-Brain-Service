package com.example.statemachine.dto.workflow;

import com.example.statemachine.dto.api.RequestMetadata;

import com.example.statemachine.enums.WorkflowOperation;

public record Command(RequestMetadata metadata, WorkflowOperation operation) {
}
