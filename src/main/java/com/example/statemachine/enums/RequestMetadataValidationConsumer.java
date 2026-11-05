package com.example.statemachine.enums;

import com.example.statemachine.constants.ApiPropertyConstants;
import com.example.statemachine.exception.WorkflowException;
import java.util.function.BiConsumer;

/** Applies field-level validation to a value and its API property name. */
public enum RequestMetadataValidationConsumer implements BiConsumer<String, String> {
    REQUIRE_NON_BLANK {
        @Override
        public void accept(String value, String propertyName) {
            if (!StringValidationPredicate.NON_BLANK.test(value)) {
                throw WorkflowException.invalid(ApiPropertyConstants.METADATA + "." + propertyName + " must be a nonblank string.");
            }
        }
    },
    FORBID_FIELD {
        @Override
        public void accept(String value, String propertyName) {
            if (value != null) {
                throw WorkflowException.invalid(ApiPropertyConstants.METADATA + "." + propertyName + " is not allowed for this operation.");
            }
        }
    }
}
