package com.example.statemachine.dto.api;

/** Command envelope. Business events are completed by the consuming service. */
public record WorkflowRequest(WorkflowRequestMetadata metadata, EmptyWorkflowPayload payload) { }
