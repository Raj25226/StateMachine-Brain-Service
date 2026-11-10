package com.example.statemachine.serialization;

import com.example.statemachine.constants.ApiPropertyConstants;
import com.example.statemachine.dto.api.ApiError;
import com.example.statemachine.dto.api.WorkflowDetails;
import com.example.statemachine.dto.api.WorkflowResponse;
import com.example.statemachine.dto.api.WorkflowResponseMetadata;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

/** Omits optional API fields while always emitting payload, including payload=null on errors. */
public class WorkflowResponseTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (type.getRawType() != WorkflowResponse.class) {
            return null;
        }
        TypeAdapter<WorkflowResponse> responseAdapter =
                gson.getDelegateAdapter(this, TypeToken.get(WorkflowResponse.class));
        TypeAdapter<WorkflowResponseMetadata> metadataAdapter =
                gson.getAdapter(WorkflowResponseMetadata.class);
        TypeAdapter<WorkflowDetails> detailsAdapter = gson.getAdapter(WorkflowDetails.class);
        TypeAdapter<ApiError> errorAdapter = gson.getAdapter(ApiError.class);
        return (TypeAdapter<T>)
                new TypeAdapter<WorkflowResponse>() {
                    @Override
                    public WorkflowResponse read(JsonReader reader) throws IOException {
                        return responseAdapter.read(reader);
                    }

                    @Override
                    public void write(JsonWriter writer, WorkflowResponse response)
                            throws IOException {
                        boolean serializeNulls = writer.getSerializeNulls();
                        writer.setSerializeNulls(false);
                        try {
                            writer.beginObject().name(ApiPropertyConstants.METADATA);
                            metadataAdapter.write(writer, response.metadata());
                            writer.name(ApiPropertyConstants.PAYLOAD);
                            if (response.payload() == null) {
                                writer.setSerializeNulls(true);
                                writer.nullValue();
                                writer.setSerializeNulls(false);
                            } else {
                                detailsAdapter.write(writer, response.payload());
                            }
                            writer.name(ApiPropertyConstants.ERROR);
                            errorAdapter.write(writer, response.error());
                            writer.endObject();
                        } finally {
                            writer.setSerializeNulls(serializeNulls);
                        }
                    }
                }.nullSafe();
    }
}
