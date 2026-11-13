package com.example.statemachine.dto.api;

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

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getWorkflowKey() {
        return workflowKey;
    }

    public void setWorkflowKey(String workflowKey) {
        this.workflowKey = workflowKey;
    }
}
