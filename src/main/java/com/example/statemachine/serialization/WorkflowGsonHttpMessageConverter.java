package com.example.statemachine.serialization;

import com.example.statemachine.dto.api.WorkflowRequest;
import com.example.statemachine.dto.api.WorkflowResponse;
import com.google.gson.Gson;
import org.springframework.http.converter.json.GsonHttpMessageConverter;

/**
 * Uses Gson for workflow envelopes without intercepting Swagger's own documents or static content.
 */
public class WorkflowGsonHttpMessageConverter extends GsonHttpMessageConverter {
    public WorkflowGsonHttpMessageConverter(Gson gson) {
        super(gson);
    }

    @Override
    protected boolean supports(Class<?> type) {
        return type == WorkflowRequest.class || type == WorkflowResponse.class;
    }
}
