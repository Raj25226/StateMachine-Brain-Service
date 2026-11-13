package com.example.statemachine.enums;

import java.math.BigInteger;
import java.util.function.Function;

/** Preserves the numeric types used by persisted workflow contexts. */
public enum JsonNumberParserFunction implements Function<String, Number> {
    PARSE;

    @Override
    public Number apply(String numberText) {
        if (numberText.indexOf('.') >= 0
                || numberText.indexOf('e') >= 0
                || numberText.indexOf('E') >= 0) {
            return Double.valueOf(numberText);
        }
        BigInteger integerValue = new BigInteger(numberText);
        if (integerValue.bitLength() < Integer.SIZE) {
            return integerValue.intValue();
        }
        if (integerValue.bitLength() < Long.SIZE) {
            return integerValue.longValue();
        }
        return integerValue;
    }
}
