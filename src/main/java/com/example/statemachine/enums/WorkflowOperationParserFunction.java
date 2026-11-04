package com.example.statemachine.enums;

import com.example.statemachine.constants.ApiPropertyConstants;
import com.example.statemachine.exception.WorkflowException;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Validates and parses the case-sensitive API operation. */
public enum WorkflowOperationParserFunction implements Function<String, WorkflowOperation> {
    PARSE;

    @Override
    public WorkflowOperation apply(String operation) {
        RequestMetadataValidationConsumer.REQUIRE_NON_BLANK.accept(operation, ApiPropertyConstants.OPERATION);
        try {
            return WorkflowOperation.valueOf(operation);
        } catch (IllegalArgumentException exception) {
            String supportedOperations = Arrays.stream(WorkflowOperation.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw WorkflowException.invalid(ApiPropertyConstants.METADATA + "." + ApiPropertyConstants.OPERATION
                    + " must be one of: " + supportedOperations + ".");
        }
    }
}
