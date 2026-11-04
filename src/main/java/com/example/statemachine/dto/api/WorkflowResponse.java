package com.example.statemachine.dto.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowResponse(
        WorkflowResponseMetadata metadata,

        @JsonInclude(JsonInclude.Include.ALWAYS)
        WorkflowDetails payload,

        ApiError error
) { }
