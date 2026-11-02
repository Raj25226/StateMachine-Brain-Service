package com.example.statemachine.dto.swagger;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "GetWorkflowStateRequest",
        description = "GET_STATE read-only inspection; payload must be {}.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
)
public record GetStateRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        GetStateMetadata metadata,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        EmptyPayload payload
) {}
