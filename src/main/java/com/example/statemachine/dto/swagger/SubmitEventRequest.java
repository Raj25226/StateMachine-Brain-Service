package com.example.statemachine.dto.swagger;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "SubmitWorkflowEventRequest",
        description = "SUBMIT_EVENT records a completed external business milestone, not the business" + " action itself; payload must be {}.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
)
public record SubmitEventRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SubmitEventMetadata metadata,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        EmptyPayload payload
) {}
