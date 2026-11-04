CREATE SCHEMA IF NOT EXISTS workflow_brain;

CREATE TABLE workflow_brain.workflow_definition (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workflow_key VARCHAR(100) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (workflow_key, version)
);

CREATE TABLE workflow_brain.workflow_state (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    state_code VARCHAR(50) NOT NULL,
    initial_state BOOLEAN NOT NULL DEFAULT FALSE,
    terminal_state BOOLEAN NOT NULL DEFAULT FALSE,
    fk_id_workflow_definition INTEGER NOT NULL REFERENCES workflow_brain.workflow_definition(id),
    UNIQUE (fk_id_workflow_definition, state_code),
    UNIQUE (fk_id_workflow_definition, id),
    CHECK (NOT (initial_state AND terminal_state))
);

CREATE TABLE workflow_brain.workflow_action (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    action_name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500) NOT NULL
);

CREATE TABLE workflow_brain.workflow_transition (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_code VARCHAR(100) NOT NULL,
    transition_kind VARCHAR(20) NOT NULL CHECK (transition_kind IN ('EXTERNAL', 'INTERNAL')),
    fk_id_workflow_definition INTEGER NOT NULL REFERENCES workflow_brain.workflow_definition(id),
    fk_id_source_workflow_state INTEGER NOT NULL,
    fk_id_target_workflow_state INTEGER NOT NULL,
    FOREIGN KEY (fk_id_workflow_definition, fk_id_source_workflow_state) REFERENCES workflow_brain.workflow_state(fk_id_workflow_definition, id),
    FOREIGN KEY (fk_id_workflow_definition, fk_id_target_workflow_state) REFERENCES workflow_brain.workflow_state(fk_id_workflow_definition, id),
    UNIQUE (fk_id_workflow_definition, fk_id_source_workflow_state, event_code),
    CHECK (transition_kind <> 'INTERNAL' OR fk_id_source_workflow_state = fk_id_target_workflow_state)
);

CREATE TABLE workflow_brain.workflow_state_action (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    phase VARCHAR(10) NOT NULL CHECK (phase IN ('ENTRY', 'EXIT', 'STATE')),
    execution_order INTEGER NOT NULL DEFAULT 0 CHECK (execution_order >= 0),
    fk_id_workflow_state INTEGER NOT NULL REFERENCES workflow_brain.workflow_state(id),
    fk_id_workflow_action INTEGER NOT NULL REFERENCES workflow_brain.workflow_action(id),
    UNIQUE (fk_id_workflow_state, phase, execution_order)
);

CREATE TABLE workflow_brain.workflow_transition_action (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    execution_order INTEGER NOT NULL DEFAULT 0 CHECK (execution_order >= 0),
    fk_id_workflow_transition INTEGER NOT NULL REFERENCES workflow_brain.workflow_transition(id),
    fk_id_workflow_action INTEGER NOT NULL REFERENCES workflow_brain.workflow_action(id),
    UNIQUE (fk_id_workflow_transition, execution_order)
);

CREATE TABLE workflow_brain.workflow_instances (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference_id VARCHAR(100) NOT NULL UNIQUE,
    machine_context TEXT,
    context_format_version INTEGER NOT NULL DEFAULT 1 CHECK (context_format_version = 1),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fk_id_workflow_definition INTEGER NOT NULL REFERENCES workflow_brain.workflow_definition(id),
    fk_id_workflow_state INTEGER NOT NULL,
    UNIQUE (id, fk_id_workflow_definition),
    FOREIGN KEY (fk_id_workflow_definition, fk_id_workflow_state) REFERENCES workflow_brain.workflow_state(fk_id_workflow_definition, id)
);

CREATE TABLE workflow_brain.workflow_transition_history (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_code VARCHAR(100) NOT NULL,
    request_id VARCHAR(128),
    idempotency_key VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fk_id_workflow_instance INTEGER NOT NULL,
    fk_id_workflow_definition INTEGER NOT NULL REFERENCES workflow_brain.workflow_definition(id),
    fk_id_source_workflow_state INTEGER NOT NULL,
    fk_id_target_workflow_state INTEGER NOT NULL,
    FOREIGN KEY (fk_id_workflow_instance, fk_id_workflow_definition) REFERENCES workflow_brain.workflow_instances(id, fk_id_workflow_definition),
    FOREIGN KEY (fk_id_workflow_definition, fk_id_source_workflow_state) REFERENCES workflow_brain.workflow_state(fk_id_workflow_definition, id),
    FOREIGN KEY (fk_id_workflow_definition, fk_id_target_workflow_state) REFERENCES workflow_brain.workflow_state(fk_id_workflow_definition, id)
);

CREATE INDEX idx_workflow_history_instance
ON workflow_brain.workflow_transition_history (fk_id_workflow_instance, created_at);
