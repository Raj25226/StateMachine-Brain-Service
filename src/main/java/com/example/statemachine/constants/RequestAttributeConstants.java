package com.example.statemachine.constants;

/** Servlet request attribute names shared by the controller, advice and HTTP logging filter. */
public final class RequestAttributeConstants {
    private RequestAttributeConstants() { }

    public static final String METADATA_ATTRIBUTE = "com.example.statemachine.exception.WorkflowExceptionHandler.metadata";
    public static final String RESPONSE_ATTRIBUTE = "com.example.statemachine.logging.WorkflowRequestLoggingFilter.response";
}
