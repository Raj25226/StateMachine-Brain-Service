package com.example.statemachine.constants;

/** Bounds and replacements for untrusted log metadata. */
public final class LogConstants {
    private LogConstants() {}

    public static final int MAX_VALUE_LENGTH = 1100000;
    public static final String MISSING_VALUE = "-";
    public static final String CONTROL_CHARACTER_PATTERN = "[\\p{Cntrl}]";
    public static final String CONTROL_CHARACTER_REPLACEMENT = "_";
    public static final String ACL_CALL_STARTED =
            "workflow_acl_started correlationId={} workflow={} version={} action={} requestId={}";
    public static final String ACL_CALL_COMPLETED =
            "workflow_acl_completed correlationId={} workflow={} version={} action={} durationMs={}";
    public static final String ACL_CALL_FAILED =
            "workflow_acl_failed correlationId={} workflow={} version={} action={} durationMs={} failureType={}";
    public static final String ACL_HTTP_REJECTED =
            "workflow_acl_http_rejected correlationId={} status={}";
    public static final String ACL_ACKNOWLEDGEMENT_INVALID =
            "workflow_acl_acknowledgement_invalid correlationId={}";
}
