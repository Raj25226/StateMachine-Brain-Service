package com.example.statemachine.dto.swagger;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "EmptyWorkflowPayload",
        description = "Required literal empty JSON object {}. No OTP, file data, profile data, Udyam",
        type = "object",
        maxProperties = 0,
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
)
public record EmptyPayload() {

}
