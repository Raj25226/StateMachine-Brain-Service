-- Seed onboarding by its workflow key/version; database identity sequences supply entity IDs.
INSERT INTO workflow_brain.workflow_definition (workflow_key, version, status)
VALUES ('onboarding', 1, 'ACTIVE');

INSERT INTO workflow_brain.workflow_state (definition_id, state_code, initial_state, terminal_state)
SELECT definition.definition_id, seed.state_code, seed.initial_state, seed.terminal_state
FROM workflow_brain.workflow_definition definition
CROSS JOIN (VALUES
    ('OTP_VERIFICATION', TRUE, FALSE),
    ('PROFILE_DETAILS', FALSE, FALSE),
    ('ACCOUNT_STATEMENT_UPLOAD', FALSE, FALSE),
    ('UDYAM_DOCUMENT_UPLOAD', FALSE, FALSE),
    ('COMPLETED', FALSE, TRUE)
) AS seed(state_code, initial_state, terminal_state)
WHERE definition.workflow_key = 'onboarding' AND definition.version = 1;

INSERT INTO workflow_brain.workflow_action (action_code, description) VALUES
('recordStateEntry', 'Record the last entered state in extended state'),
('recordStateExit', 'Record the last exited state in extended state'),
('observeState', 'Log state activity at DEBUG level'),
('recordTransition', 'Record the last processed transition event in extended state');

INSERT INTO workflow_brain.workflow_guard (guard_code, description)
VALUES ('alwaysAllow', 'No additional business condition; the caller supplies completed business events');

INSERT INTO workflow_brain.workflow_transition
(definition_id, source_state, event_code, target_state, transition_kind, guard_code)
SELECT definition.definition_id, seed.source_state, seed.event_code, seed.target_state, 'EXTERNAL', 'alwaysAllow'
FROM workflow_brain.workflow_definition definition
CROSS JOIN (VALUES
    ('OTP_VERIFICATION', 'OTP_VERIFIED', 'PROFILE_DETAILS'),
    ('PROFILE_DETAILS', 'PROFILE_SUBMITTED', 'ACCOUNT_STATEMENT_UPLOAD'),
    ('ACCOUNT_STATEMENT_UPLOAD', 'ACCOUNT_STATEMENT_UPLOADED', 'UDYAM_DOCUMENT_UPLOAD'),
    ('UDYAM_DOCUMENT_UPLOAD', 'UDYAM_DOCUMENT_UPLOADED', 'COMPLETED')
) AS seed(source_state, event_code, target_state)
WHERE definition.workflow_key = 'onboarding' AND definition.version = 1;

INSERT INTO workflow_brain.workflow_state_action (definition_id, state_code, action_code, phase, execution_order)
SELECT definition_id, state_code, 'recordStateEntry', 'ENTRY', 0 FROM workflow_brain.workflow_state;
INSERT INTO workflow_brain.workflow_state_action (definition_id, state_code, action_code, phase, execution_order)
SELECT definition_id, state_code, 'recordStateExit', 'EXIT', 0 FROM workflow_brain.workflow_state;
INSERT INTO workflow_brain.workflow_state_action (definition_id, state_code, action_code, phase, execution_order)
SELECT definition_id, state_code, 'observeState', 'STATE', 0 FROM workflow_brain.workflow_state;
INSERT INTO workflow_brain.workflow_transition_action (transition_id, action_code, execution_order)
SELECT transition_id, 'recordTransition', 0 FROM workflow_brain.workflow_transition;
