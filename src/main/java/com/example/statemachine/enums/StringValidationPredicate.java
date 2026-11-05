package com.example.statemachine.enums;

import java.util.function.Predicate;

/** Reusable string validation predicates. */
public enum StringValidationPredicate implements Predicate<String> {
    NON_BLANK;

    @Override
    public boolean test(String value) {
        return value != null && !value.isBlank();
    }
}
