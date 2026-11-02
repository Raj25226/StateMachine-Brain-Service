package com.example.statemachine.dto.persistence;

public record Replay(
        String fingerprint,
        String response,
        Integer status
) {}
