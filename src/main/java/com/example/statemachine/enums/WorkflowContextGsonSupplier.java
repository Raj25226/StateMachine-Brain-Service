package com.example.statemachine.enums;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import java.util.function.Supplier;

/** Supplies the Gson configuration used for workflow persistence. */
public enum WorkflowContextGsonSupplier implements Supplier<Gson> {
    WORKFLOW_CONTEXT;

    @Override
    public Gson get() {
        return new GsonBuilder()
                .serializeNulls()
                .setStrictness(Strictness.STRICT)
                .setObjectToNumberStrategy(reader -> JsonNumberParserFunction.PARSE.apply(reader.nextString()))
                .create();
    }
}
