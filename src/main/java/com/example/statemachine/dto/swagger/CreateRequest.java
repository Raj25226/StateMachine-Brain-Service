package com.example.statemachine.dto.swagger;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "CreateWorkflowRequest",
        description = "CREATE a fresh persisted workflow; payload must be {}.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
)
public record CreateRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CreateMetadata metadata,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        EmptyPayload payload
) {}
