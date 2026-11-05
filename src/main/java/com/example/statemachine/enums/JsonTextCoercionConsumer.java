package com.example.statemachine.enums;

import java.util.function.Consumer;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.MutableCoercionConfig;

/** Rejects implicit conversions from JSON numbers and booleans to metadata strings. */
public enum JsonTextCoercionConsumer implements Consumer<MutableCoercionConfig> {
    STRICT_TEXT;

    @Override
    public void accept(MutableCoercionConfig coercionConfiguration) {
        coercionConfiguration.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
        coercionConfiguration.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        coercionConfiguration.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }
}
