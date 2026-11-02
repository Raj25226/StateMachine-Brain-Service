package com.example.statemachine.dto.swagger;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "CreateWorkflowMetadata",
    description = "CREATE command metadata. No event field is permitted.",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE
)
public record CreateMetadata(
        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"CREATE"},
            description = "MUST be CREATE (case-sensitive).",
            example = "CREATE")
        String operation,

        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 128,
            description = "New correlation ID for this request. It is NOT a deduplication" + " key.",
            example = "req-create-1")
        String requestId,


        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 100,
            description =
                    "Unique application/workflow instance ID supplied by the"
                        + " consuming service. Do not invent one for an existing"
                        + " application.",
            example = "APP-101")
        String referenceId,

        @Schema(
            defaultValue = "onboarding",
            minLength = 1,
            maxLength = 100,
            description =
                    "Optional ONLY on CREATE. Active database workflow key;" + " omission defaults to onboarding.",
            example = "onboarding")
        String workflowKey,

        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 128,
            description =
                    "Required stable globally unique retry key for THIS CREATE."
                        + " Reuse only for identical CREATE retries; never for a"
                        + " distinct event.",
            example = "APP-101-create-v1")
        String idempotencyKey
) {}
