package com.example.statemachine.enums;

import com.example.statemachine.constants.LogConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogValueSanitizerFunctionTest {
    @Test
    void sanitizesControlCharactersAndHandlesMissingValues() {
        assertEquals("request_forged_entry_", LogValueSanitizerFunction.SANITIZE.apply("request\nforged\rentry\t"));
        assertEquals("-", LogValueSanitizerFunction.SANITIZE.apply(null));
    }

    @Test
    void boundsUntrustedMetadata() {
        assertEquals("r".repeat(LogConstants.MAX_VALUE_LENGTH),
                LogValueSanitizerFunction.SANITIZE.apply("r".repeat(LogConstants.MAX_VALUE_LENGTH + 1)));
    }
}
