package com.example.statemachine.dto.swagger;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "SubmitEventWorkflowMetadata",
        description = "SUBMIT_EVENT metadata. ONLY send when the corresponding external business action is confirmed complete. Do NOT send workflowKey.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
)
public record SubmitEventMetadata(
        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"SUBMIT_EVENT"},
            description = "MUST be SUBMIT_EVENT (case-sensitive).",
            example = "SUBMIT_EVENT")
        String operation,

        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 128,
            description = "Unique correlation ID for this call; NOT for deduplication.",
            example = "req-udyam-1")
        String requestId,

        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 100,
            description = "Existing application reference whose state may advance.",
            example = "APP-101")
        String referenceId,

        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 100,
            description =
                    "Exact case-sensitive event code. Seeded onboarding-v1"
                        + " mappings: OTP_VERIFIED: verified OTP, when at"
                        + " OTP_VERIFICATION, -> PROFILE_DETAILS;"
                        + " PROFILE_SUBMITTED: saved profile, when at"
                        + " PROFILE_DETAILS, -> ACCOUNT_STATEMENT_UPLOAD;"
                        + " ACCOUNT_STATEMENT_UPLOADED: statement was uploaded,"
                        + " when at ACCOUNT_STATEMENT_UPLOAD, ->"
                        + " UDYAM_DOCUMENT_UPLOAD; UDYAM_DOCUMENT_UPLOADED: Udyam"
                        + " registration document was uploaded/updated by the"
                        + " consuming service, when at UDYAM_DOCUMENT_UPLOAD, ->"
                        + " COMPLETED. This is NOT a command to perform an Udyam"
                        + " upload. Other database definitions may introduce other"
                        + " event codes; GET_STATE provides availableTransitions.",
            example = "UDYAM_DOCUMENT_UPLOADED")
        String event,

        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 128,
            description = "Required retry key bound to THIS event and reference; repeat"
                        + " unchanged only for retries of the same logical"
                        + " completion event.",
            example = "APP-101-udyam-upload-v1")
        String idempotencyKey
) {}
