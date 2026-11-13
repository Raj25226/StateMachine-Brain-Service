-- Identity columns generate IDs; seed relationships are resolved by business names and codes.
INSERT INTO workflow_brain.workflow_definition (workflow_key, version, status)
VALUES ('finaag', 1, 'ACTIVE'), ('lyntra', 1, 'ACTIVE');

INSERT INTO workflow_brain.workflow_state (state_code, initial_state, terminal_state, fk_id_workflow_definition)
SELECT seed.state_code, seed.initial_state, seed.terminal_state, definition.id
FROM workflow_brain.workflow_definition definition
JOIN (VALUES
    ('finaag', 'OTP_VERIFICATION', TRUE, FALSE),
    ('finaag', 'PROFILE_DETAILS', FALSE, FALSE),
    ('finaag', 'ACCOUNT_STATEMENT_UPLOAD', FALSE, FALSE),
    ('finaag', 'UDYAM_DOCUMENT_UPLOAD', FALSE, FALSE),
    ('finaag', 'COMPLETED', FALSE, TRUE),
    ('lyntra', 'OTP_VERIFICATION', TRUE, FALSE),
    ('lyntra', 'IDENTITY_REVIEW', FALSE, FALSE),
    ('lyntra', 'PLAN_SELECTION', FALSE, FALSE),
    ('lyntra', 'COMPLETED', FALSE, TRUE)
) AS seed(workflow_key, state_code, initial_state, terminal_state)
    ON seed.workflow_key = definition.workflow_key
WHERE definition.version = 1;

INSERT INTO workflow_brain.workflow_action (action_name, description) VALUES
('recordStateEntry', 'Log the entered state; current state is stored by its FK'),
('recordStateExit', 'Log the exited state; transition history stores source states'),
('observeState', 'Log state activity at DEBUG level'),
('recordTransition', 'Log the processed event; transition history stores applied events'),
('verifyOtp', 'Verify OTP through the shared ACL handler'),
('updateProfile', 'Update profile through the ACL'),
('uploadAccountStatement', 'Upload account statement through the ACL'),
('uploadUdyamDocument', 'Upload Udyam document through the ACL'),
('verifyIdentity', 'Verify identity through the ACL'),
('selectPlan', 'Select a plan through the ACL');

INSERT INTO workflow_brain.workflow_transition
(event_code, transition_kind, fk_id_workflow_definition, fk_id_source_workflow_state, fk_id_target_workflow_state)
SELECT seed.event_code, 'EXTERNAL', definition.id, source_state.id, target_state.id
FROM workflow_brain.workflow_definition definition
JOIN (VALUES
    ('finaag', 'OTP_VERIFICATION', 'VERIFY_OTP', 'PROFILE_DETAILS'),
    ('finaag', 'PROFILE_DETAILS', 'UPDATE_PROFILE', 'ACCOUNT_STATEMENT_UPLOAD'),
    ('finaag', 'ACCOUNT_STATEMENT_UPLOAD', 'UPLOAD_ACCOUNT_STATEMENT', 'UDYAM_DOCUMENT_UPLOAD'),
    ('finaag', 'UDYAM_DOCUMENT_UPLOAD', 'UPLOAD_UDYAM_DOCUMENT', 'COMPLETED'),
    ('lyntra', 'OTP_VERIFICATION', 'VERIFY_OTP', 'IDENTITY_REVIEW'),
    ('lyntra', 'IDENTITY_REVIEW', 'VERIFY_IDENTITY', 'PLAN_SELECTION'),
    ('lyntra', 'PLAN_SELECTION', 'SELECT_PLAN', 'COMPLETED')
) AS seed(workflow_key, source_state, event_code, target_state)
    ON seed.workflow_key = definition.workflow_key
JOIN workflow_brain.workflow_state source_state
    ON source_state.fk_id_workflow_definition = definition.id AND source_state.state_code = seed.source_state
JOIN workflow_brain.workflow_state target_state
    ON target_state.fk_id_workflow_definition = definition.id AND target_state.state_code = seed.target_state
WHERE definition.workflow_key IN ('finaag', 'lyntra') AND definition.version = 1;

INSERT INTO workflow_brain.workflow_state_action (phase, execution_order, fk_id_workflow_state, fk_id_workflow_action)
SELECT binding.phase, 0, state.id, action.id
FROM workflow_brain.workflow_state state
CROSS JOIN (VALUES
    ('recordStateEntry', 'ENTRY'),
    ('recordStateExit', 'EXIT'),
    ('observeState', 'STATE')
) AS binding(action_name, phase)
JOIN workflow_brain.workflow_action action ON action.action_name = binding.action_name;

-- The brain forwards each business action to the ACL; handlers never decide the next state.
INSERT INTO workflow_brain.workflow_transition_action (execution_order, fk_id_workflow_transition, fk_id_workflow_action)
SELECT 0, transition.id, action.id
FROM workflow_brain.workflow_transition transition
JOIN workflow_brain.workflow_definition definition ON definition.id = transition.fk_id_workflow_definition
JOIN (VALUES
    ('VERIFY_OTP', 'verifyOtp'),
    ('UPDATE_PROFILE', 'updateProfile'),
    ('UPLOAD_ACCOUNT_STATEMENT', 'uploadAccountStatement'),
    ('UPLOAD_UDYAM_DOCUMENT', 'uploadUdyamDocument'),
    ('VERIFY_IDENTITY', 'verifyIdentity'),
    ('SELECT_PLAN', 'selectPlan')
) AS binding(event_code, action_name) ON binding.event_code = transition.event_code
JOIN workflow_brain.workflow_action action ON action.action_name = binding.action_name
WHERE definition.workflow_key IN ('finaag', 'lyntra') AND definition.version = 1;

INSERT INTO workflow_brain.workflow_transition_action (execution_order, fk_id_workflow_transition, fk_id_workflow_action)
SELECT 1, transition.id, action.id
FROM workflow_brain.workflow_transition transition
JOIN workflow_brain.workflow_definition definition ON definition.id = transition.fk_id_workflow_definition
JOIN workflow_brain.workflow_action action ON action.action_name = 'recordTransition'
WHERE definition.workflow_key IN ('finaag', 'lyntra') AND definition.version = 1;
