package com.example.statemachine.enums;

import com.example.statemachine.constants.LogConstants;
import java.util.function.Function;

/** Bounds untrusted metadata and prevents control characters from forging log lines. */
public enum LogValueSanitizerFunction implements Function<String, String> {
    SANITIZE;

    @Override
    public String apply(String value) {
        if (value == null) {
            return LogConstants.MISSING_VALUE;
        }
        return value.substring(0, Math.min(value.length(), LogConstants.MAX_VALUE_LENGTH))
                .replaceAll(
                        LogConstants.CONTROL_CHARACTER_PATTERN,
                        LogConstants.CONTROL_CHARACTER_REPLACEMENT);
    }
}
