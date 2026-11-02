package com.example.statemachine.dto.api;

public record ApiError(
        String code,
        String description
) { }
