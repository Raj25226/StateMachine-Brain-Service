package com.example.statemachine.dto.persistence;

import java.util.Map;

public record StoredContext(
        int formatVersion,
        String state,
        Map<String, Object> variables
) {}
