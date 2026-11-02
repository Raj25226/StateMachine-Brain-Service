package com.example.statemachine.dto.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record Request(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        RequestMetadata metadata,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Must be an empty object",
                additionalProperties = Schema.AdditionalPropertiesValue.FALSE
        )
        Map<String, Object> payload
) { }
