CREATE SCHEMA IF NOT EXISTS workflow_brain;

CREATE TABLE workflow_brain.workflow_definition (
    definition_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workflow_key VARCHAR(100) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (workflow_key, version)
);

CREATE TABLE workflow_brain.workflow_state (
    definition_id INTEGER NOT NULL REFERENCES workflow_brain.workflow_definition(definition_id),
    state_code VARCHAR(50) NOT NULL,
    initial_state BOOLEAN NOT NULL DEFAULT FALSE,
    terminal_state BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (definition_id, state_code),
    CHECK (NOT (initial_state AND terminal_state))
);

CREATE TABLE workflow_brain.workflow_action (
    action_code VARCHAR(100) PRIMARY KEY,
    description VARCHAR(500) NOT NULL
);

CREATE TABLE workflow_brain.workflow_guard (
    guard_code VARCHAR(100) PRIMARY KEY,
    description VARCHAR(500) NOT NULL
);

CREATE TABLE workflow_brain.workflow_transition (
    transition_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    definition_id INTEGER NOT NULL REFERENCES workflow_brain.workflow_definition(definition_id),
    source_state VARCHAR(50) NOT NULL,
    event_code VARCHAR(100) NOT NULL,
    target_state VARCHAR(50) NOT NULL,
    transition_kind VARCHAR(20) NOT NULL CHECK (transition_kind IN ('EXTERNAL', 'INTERNAL')),
    guard_code VARCHAR(100) REFERENCES workflow_brain.workflow_guard(guard_code),
    FOREIGN KEY (definition_id, source_state) REFERENCES workflow_brain.workflow_state(definition_id, state_code),
    FOREIGN KEY (definition_id, target_state) REFERENCES workflow_brain.workflow_state(definition_id, state_code),
    UNIQUE (definition_id, source_state, event_code),
    CHECK (transition_kind <> 'INTERNAL' OR source_state = target_state)
);

CREATE TABLE workflow_brain.workflow_state_action (
    definition_id INTEGER NOT NULL,
    state_code VARCHAR(50) NOT NULL,
    action_code VARCHAR(100) NOT NULL REFERENCES workflow_brain.workflow_action(action_code),
    phase VARCHAR(10) NOT NULL CHECK (phase IN ('ENTRY', 'EXIT', 'STATE')),
    execution_order INTEGER NOT NULL DEFAULT 0 CHECK (execution_order >= 0),
    FOREIGN KEY (definition_id, state_code) REFERENCES workflow_brain.workflow_state(definition_id, state_code),
    PRIMARY KEY (definition_id, state_code, phase, execution_order)
);

CREATE TABLE workflow_brain.workflow_transition_action (
    transition_id INTEGER NOT NULL REFERENCES workflow_brain.workflow_transition(transition_id),
    action_code VARCHAR(100) NOT NULL REFERENCES workflow_brain.workflow_action(action_code),
    execution_order INTEGER NOT NULL DEFAULT 0 CHECK (execution_order >= 0),
    PRIMARY KEY (transition_id, execution_order)
);

CREATE TABLE workflow_brain.workflow_deferred_event (
    definition_id INTEGER NOT NULL,
    state_code VARCHAR(50) NOT NULL,
    event_code VARCHAR(100) NOT NULL,
    FOREIGN KEY (definition_id, state_code) REFERENCES workflow_brain.workflow_state(definition_id, state_code),
    PRIMARY KEY (definition_id, state_code, event_code)
);

CREATE TABLE workflow_brain.workflow_instances (
    reference_id VARCHAR(100) PRIMARY KEY,
    definition_id INTEGER NOT NULL REFERENCES workflow_brain.workflow_definition(definition_id),
    current_state VARCHAR(50) NOT NULL,
    machine_context TEXT,
    context_format_version INTEGER NOT NULL DEFAULT 1 CHECK (context_format_version = 1),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (definition_id, current_state) REFERENCES workflow_brain.workflow_state(definition_id, state_code)
);

CREATE TABLE workflow_brain.workflow_request_replay (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    response_json TEXT,
    http_status INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    CHECK ((response_json IS NULL AND http_status IS NULL AND completed_at IS NULL)
        OR (response_json IS NOT NULL AND http_status BETWEEN 200 AND 299 AND completed_at IS NOT NULL))
);

CREATE TABLE workflow_brain.workflow_transition_history (
    history_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference_id VARCHAR(100) NOT NULL REFERENCES workflow_brain.workflow_instances(reference_id),
    definition_id INTEGER NOT NULL REFERENCES workflow_brain.workflow_definition(definition_id),
    source_state VARCHAR(50) NOT NULL,
    event_code VARCHAR(100) NOT NULL,
    target_state VARCHAR(50) NOT NULL,
    request_id VARCHAR(128),
    idempotency_key VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_workflow_history_reference
ON workflow_brain.workflow_transition_history (reference_id, created_at);
