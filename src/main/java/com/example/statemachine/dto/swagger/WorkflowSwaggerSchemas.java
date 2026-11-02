package com.example.statemachine.dto.swagger;

/**
 * Documentation-only models for springdoc / Swagger.
 *
 * <p>The real HTTP controller still receives JsonNode; WorkflowRequestValidator remains the runtime
 * authority on permitted fields and normalized defaults.
 */
public final class WorkflowSwaggerSchemas {
    private WorkflowSwaggerSchemas() {}

    // Java text blocks are compile-time constants and can be used in annotations.
    public static final String OPERATION_DESCRIPTION =
            """
            Persisted workflow brain: create a workflow instance, inspect its current state,
            or record a business event ALREADY completed by the consuming service.
            The tool does NOT verify OTPs, collect or update profiles, upload documents,
            modify Udyam registration information, or perform any business operation.

            AI TOOL SELECTION GUIDE:
            - "What's my onboarding status?", "What comes next?", "Is Udyam pending?",
              "Can I upload my Udyam?" -> GET_STATE using the real referenceId.
            - "Start onboarding" for a genuinely NEW reference -> CREATE.
            - "Verify OTP" -> consuming OTP service must verify successfully; ONLY AFTER
              success, SUBMIT_EVENT(event=OTP_VERIFIED).
            - "Submit my profile" -> consuming service must save the profile; ONLY AFTER
              success, SUBMIT_EVENT(event=PROFILE_SUBMITTED).
            - "Upload my bank statement" -> document service must store statement;
              ONLY AFTER success, SUBMIT_EVENT(event=ACCOUNT_STATEMENT_UPLOADED).
            - "Update my Udyam", "Upload Udyam document", "Attach Udyam certificate" ->
              the consuming Udyam/document service must finish the upload/update FIRST.
              ONLY AFTER confirmed success, SUBMIT_EVENT(event=UDYAM_DOCUMENT_UPLOADED).
              This endpoint CANNOT itself perform the requested Udyam update. If external
              success is unknown, GET_STATE may help assess readiness; never mark complete.
            - Never invent referenceId, state, or completed business actions.
            - If uncertain which transition is valid, call GET_STATE and inspect
              payload.availableTransitions. A guard may still reject a listed transition.

            SEEDED DATABASE WORKFLOW (workflowKey=onboarding, definition=onboarding-v1):
              OTP_VERIFICATION --OTP_VERIFIED--> PROFILE_DETAILS
              PROFILE_DETAILS --PROFILE_SUBMITTED--> ACCOUNT_STATEMENT_UPLOAD
              ACCOUNT_STATEMENT_UPLOAD --ACCOUNT_STATEMENT_UPLOADED--> UDYAM_DOCUMENT_UPLOAD
              UDYAM_DOCUMENT_UPLOAD --UDYAM_DOCUMENT_UPLOADED--> COMPLETED
            COMPLETED is terminal. Current seeded guard alwaysAllow does NOT verify the
            underlying business work. Future database definitions may have different events or deferred events.
            For a configured deferred event, eventOutcome=DEFERRED and pendingEvents
            holds it until a suitable later state; seeded onboarding-v1 has none.

            REQUEST FORMAT:
            Exactly two top-level JSON objects: metadata and payload. payload MUST be {}.
            CREATE requires operation, requestId, referenceId, idempotencyKey; optional workflowKey
            (defaults to onboarding); NO event.
            GET_STATE requires operation, requestId, referenceId; NO event, workflowKey
            or idempotencyKey.
            SUBMIT_EVENT requires operation, requestId, referenceId, exact event and
            idempotencyKey; NO workflowKey.
            Strings are case-sensitive. requestId/idempotencyKey max 128 characters;
            referenceId/workflowKey/event max 100 characters, all nonblank.

            READS, AUDIT AND CORRELATION:
            GET_STATE reads the persisted state/context snapshot without a mutation row
            lock or executing state-machine actions. It does not create history records.
            Successfully applied events are stored in workflow_transition_history in the
            same transaction as workflow progress. Internal transitions and applied deferred
            events are recorded; rejected events and idempotency replays add no history.
            No history-reading API is exposed. Historical transitions from before deployment
            are not reconstructed. X-Correlation-Id is a server-generated response header
            for tracing this HTTP attempt; it is distinct from requestId and idempotencyKey.

            RETRIES AND RESPONSE:
            requestId is correlation only, and can change on retry. idempotencyKey is
            required for CREATE and SUBMIT_EVENT. It is globally scoped and
            must be reused ONLY for the same operation/referenceId/event/workflowKey
            inputs. On a successful replay, the original status/payload/processedAt are
            returned, with current requestId and idempotencyReplayed=true. The original
            saved snapshot may be stale; use GET_STATE for up-to-date progress. Failed
            requests do not store a successful response; keys do not automatically expire.
            CREATE -> 201; GET_STATE and SUBMIT_EVENT -> 200.
            400 INVALID_REQUEST, 404 WORKFLOW_NOT_FOUND/WORKFLOW_DEFINITION_NOT_FOUND,
            409 WORKFLOW_ALREADY_EXISTS/EVENT_NOT_ALLOWED/GUARD_REJECTED/
            IDEMPOTENCY_CONFLICT, 500 INTERNAL_ERROR. Unsupported methods and content
            types return 405 and 415, respectively.
            """;

}
