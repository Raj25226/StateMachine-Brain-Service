package com.example.statemachine.dto.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Response(
        ResponseMetadata metadata,

        @JsonInclude(JsonInclude.Include.ALWAYS)
        WorkflowDetails payload,

        ApiError error
) { }
