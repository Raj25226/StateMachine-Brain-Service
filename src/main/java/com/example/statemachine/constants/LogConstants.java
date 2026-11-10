package com.example.statemachine.constants;

/** Bounds and replacements for untrusted log metadata. */
public final class LogConstants {
    private LogConstants() {}

    public static final int MAX_VALUE_LENGTH = 1100000;
    public static final String MISSING_VALUE = "-";
    public static final String CONTROL_CHARACTER_PATTERN = "[\\p{Cntrl}]";
    public static final String CONTROL_CHARACTER_REPLACEMENT = "_";
}
