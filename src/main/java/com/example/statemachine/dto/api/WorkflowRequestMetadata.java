package com.example.statemachine.dto.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/** Mutable transport POJO; validated commands use immutable RequestMetadata. */
public class WorkflowRequestMetadata {
    private String operation;
    private String requestId;
    private String referenceId;
    private String event;
    private String idempotencyKey;
    private String workflowKey;

    public String getOperation() {
        return operation;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getRequestId() {
        return requestId;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getReferenceId() {
        return referenceId;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getEvent() {
        return event;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setEvent(String event) {
        this.event = event;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getWorkflowKey() {
        return workflowKey;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setWorkflowKey(String workflowKey) {
        this.workflowKey = workflowKey;
    }
}
