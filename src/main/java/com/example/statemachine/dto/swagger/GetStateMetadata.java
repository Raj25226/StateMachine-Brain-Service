package com.example.statemachine.dto.swagger;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "GetStateWorkflowMetadata",
    description = "GET_STATE metadata. Do NOT send event, workflowKey, or idempotencyKey.",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE
)
public record GetStateMetadata(
        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"GET_STATE"},
            description = "MUST be GET_STATE (case-sensitive).",
            example = "GET_STATE"
        )
        String operation,

        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 128,
            description = "Correlation ID for this read.",
            example = "req-state-1"
        )
        String requestId,

        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 100,
            description = "Existing application reference to inspect. Must be a real" + " known instance.",
            example = "APP-101")
        String referenceId
) {}
