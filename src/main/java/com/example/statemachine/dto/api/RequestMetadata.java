package com.example.statemachine.dto.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record RequestMetadata(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"CREATE", "GET_STATE", "SUBMIT_EVENT"})
        String operation,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 128)
        String requestId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 100)
        String referenceId,

        @Schema(description = "Required only for SUBMIT_EVENT; defined by the instance's" + " workflow version", maxLength = 100)
        String event,

        @Schema(description = "Required for CREATE and SUBMIT_EVENT; forbidden for GET_STATE", minLength = 1, maxLength = 128)
        String idempotencyKey,

        @Schema(description = "Allowed only for CREATE; defaults to onboarding", minLength = 1, maxLength = 100)
        String workflowKey
) { }
