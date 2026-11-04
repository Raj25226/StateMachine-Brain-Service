package com.example.statemachine.enums;

import java.util.function.Consumer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/** Shared Jackson request-binding policy. */
public enum JacksonRequestBindingConsumer implements Consumer<JsonMapper.Builder> {
    STRICT_REQUEST_BINDING;

    @Override
    public void accept(JsonMapper.Builder jsonMapperBuilder) {
        jsonMapperBuilder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .withCoercionConfig(LogicalType.Textual, JsonTextCoercionConsumer.STRICT_TEXT);
    }
}
