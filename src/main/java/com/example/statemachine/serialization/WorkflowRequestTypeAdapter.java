package com.example.statemachine.serialization;

import com.example.statemachine.constants.ApiPropertyConstants;
import com.example.statemachine.dto.api.EmptyWorkflowPayload;
import com.example.statemachine.dto.api.WorkflowRequest;
import com.example.statemachine.dto.api.WorkflowRequestMetadata;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Strict binding of the command envelope; operation-specific validation remains in the validator.
 */
public class WorkflowRequestTypeAdapter extends TypeAdapter<WorkflowRequest> {
    @Override
    public WorkflowRequest read(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        reader.beginObject();
        WorkflowRequestMetadata metadata = null;
        EmptyWorkflowPayload payload = null;
        Set<String> properties = new HashSet<>();
        while (reader.hasNext()) {
            String property = reader.nextName();
            if (!properties.add(property)) {
                throw new JsonSyntaxException("Duplicate request property: " + property);
            }
            switch (property) {
                case ApiPropertyConstants.METADATA -> metadata = readMetadata(reader);
                case ApiPropertyConstants.PAYLOAD -> payload = readPayload(reader);
                default -> throw new JsonSyntaxException("Unknown request property: " + property);
            }
        }
        reader.endObject();
        return new WorkflowRequest(metadata, payload);
    }

    private WorkflowRequestMetadata readMetadata(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        reader.beginObject();
        WorkflowRequestMetadata metadata = new WorkflowRequestMetadata();
        Set<String> properties = new HashSet<>();
        while (reader.hasNext()) {
            String property = reader.nextName();
            if (!properties.add(property)) {
                throw new JsonSyntaxException("Duplicate metadata property: " + property);
            }
            if (reader.peek() != JsonToken.STRING) {
                throw new JsonSyntaxException(
                        "Metadata values must be non-null strings: " + property);
            }
            String value = reader.nextString();
            switch (property) {
                case ApiPropertyConstants.OPERATION -> metadata.setOperation(value);
                case ApiPropertyConstants.REQUEST_ID -> metadata.setRequestId(value);
                case ApiPropertyConstants.REFERENCE_ID -> metadata.setReferenceId(value);
                case ApiPropertyConstants.EVENT -> metadata.setEvent(value);
                case ApiPropertyConstants.IDEMPOTENCY_KEY -> metadata.setIdempotencyKey(value);
                case ApiPropertyConstants.WORKFLOW_KEY -> metadata.setWorkflowKey(value);
                default -> throw new JsonSyntaxException("Unknown metadata property: " + property);
            }
        }
        reader.endObject();
        return metadata;
    }

    private EmptyWorkflowPayload readPayload(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        reader.beginObject();
        if (reader.hasNext()) {
            throw new JsonSyntaxException("Payload must be an empty JSON object.");
        }
        reader.endObject();
        return new EmptyWorkflowPayload();
    }

    @Override
    public void write(JsonWriter writer, WorkflowRequest request) throws IOException {
        if (request == null) {
            writer.nullValue();
            return;
        }
        boolean serializeNulls = writer.getSerializeNulls();
        writer.setSerializeNulls(false);
        try {
            writer.beginObject().name(ApiPropertyConstants.METADATA);
            if (request.metadata() == null) {
                writer.nullValue();
            } else {
                WorkflowRequestMetadata metadata = request.metadata();
                writer.beginObject()
                        .name(ApiPropertyConstants.OPERATION)
                        .value(metadata.getOperation())
                        .name(ApiPropertyConstants.REQUEST_ID)
                        .value(metadata.getRequestId())
                        .name(ApiPropertyConstants.REFERENCE_ID)
                        .value(metadata.getReferenceId())
                        .name(ApiPropertyConstants.EVENT)
                        .value(metadata.getEvent())
                        .name(ApiPropertyConstants.IDEMPOTENCY_KEY)
                        .value(metadata.getIdempotencyKey())
                        .name(ApiPropertyConstants.WORKFLOW_KEY)
                        .value(metadata.getWorkflowKey())
                        .endObject();
            }
            writer.name(ApiPropertyConstants.PAYLOAD);
            if (request.payload() == null) {
                writer.nullValue();
            } else {
                writer.beginObject().endObject();
            }
            writer.endObject();
        } finally {
            writer.setSerializeNulls(serializeNulls);
        }
    }
}
