package com.example.statemachine.dto.workflow;

public record WorkflowActionBinding(Integer actionId, String actionName, String phase, int order) {}
