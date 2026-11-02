package com.example.statemachine.dto.workflow;

import java.util.List;

public record State(
        String code,
        boolean initial,
        boolean terminal,
        List<ActionBinding> actions
) {}
