package com.example.statemachine.dto.persistence;

import com.example.statemachine.enums.IdempotencyClaimOutcome;

public record WorkflowIdempotencyClaim(
        IdempotencyClaimOutcome outcome, String responseJson, Integer httpStatus) {}
