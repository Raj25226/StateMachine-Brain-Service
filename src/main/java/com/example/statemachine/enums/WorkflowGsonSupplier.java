package com.example.statemachine.enums;

import com.example.statemachine.dto.api.WorkflowRequest;
import com.example.statemachine.serialization.InstantTypeAdapter;
import com.example.statemachine.serialization.WorkflowRequestTypeAdapter;
import com.example.statemachine.serialization.WorkflowResponseTypeAdapterFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import java.time.Instant;
import java.util.function.Supplier;

/** Supplies the Gson configuration used for workflow HTTP, persistence and retry serialization. */
public enum WorkflowGsonSupplier implements Supplier<Gson> {
    CONFIGURED;

    @Override
    public Gson get() {
        return new GsonBuilder()
                .serializeNulls()
                .disableHtmlEscaping()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter().nullSafe())
                .registerTypeAdapter(WorkflowRequest.class, new WorkflowRequestTypeAdapter())
                .registerTypeAdapterFactory(new WorkflowResponseTypeAdapterFactory())
                .setStrictness(Strictness.STRICT)
                .setObjectToNumberStrategy(
                        reader -> JsonNumberParserFunction.PARSE.apply(reader.nextString()))
                .create();
    }
}
