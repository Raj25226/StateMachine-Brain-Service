package com.example.statemachine.dto.api;


public record WorkflowResponse(
        WorkflowResponseMetadata metadata,

        WorkflowDetails payload,

        ApiError error
) { }
