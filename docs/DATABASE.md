# Database design and queries

All 11 application tables and `flyway_schema_history` use the `workflow_brain`
schema. Flyway creates the schema before initializing its history.

- `V1__create_workflow_schema.sql`: tables, constraints, identity columns and indexes.
- `V2__insert_workflow_seed_data.sql`: onboarding definitions and behavior bindings.

The current files are a fresh testing baseline. Reset application tables and Flyway
history together before using them. Recreating `workflow_brain` also resets its
identity sequences. Remove an old `public.flyway_schema_history` during the same
testing reset if one remains; do not reuse history from an earlier baseline.

## Column conventions

Every application table has an `id INTEGER GENERATED ALWAYS AS IDENTITY` primary
key. Foreign-key columns are INTEGER and use `fk_id_<full_entity_name>`, including
`fk_id_workflow_definition`, `fk_id_workflow_state`, `fk_id_workflow_action`, `fk_id_workflow_guard`,
`fk_id_workflow_transition` and `fk_id_workflow_instance`. State roles use `fk_id_source_workflow_state` and
`fk_id_target_workflow_state`. Foreign-key columns appear after all business
columns and timestamps in each table, followed by table-level constraints.

Names and business identifiers are separate from database IDs:

- States retain `state_code`; transitions and deferred events retain `event_code`.
- Actions retain `action_name`, such as `recordStateEntry`.
- Guards retain `guard_name`, such as `alwaysAllow`.
- Instances retain a unique `reference_id` for API lookups.
- Retry reservations retain a unique `idempotency_key`; `request_id` is correlation.

Java entity identifiers use Integer. Spring StateMachine's factory accepts a
String machine identifier, so definition IDs are converted only at that boundary.
State and event codes remain strings when building the machine.

## Tables and relationships

Every table below has its own `id` primary key in addition to the listed columns.

| Table | Important columns and constraints |
| --- | --- |
| workflow_definition | workflow_key, version, status, created_at; unique (workflow_key, version) |
| workflow_state | state_code, initial_state, terminal_state; fk_id_workflow_definition; unique (fk_id_workflow_definition, state_code) |
| workflow_action | action_name unique, description |
| workflow_guard | guard_name unique, description |
| workflow_transition | event_code, transition_kind; fk_id_workflow_definition, fk_id_source_workflow_state, fk_id_target_workflow_state, optional fk_id_workflow_guard; unique (fk_id_workflow_definition, fk_id_source_workflow_state, event_code) |
| workflow_state_action | phase, execution_order; fk_id_workflow_state, fk_id_workflow_action; unique (fk_id_workflow_state, phase, execution_order) |
| workflow_transition_action | execution_order; fk_id_workflow_transition, fk_id_workflow_action; unique (fk_id_workflow_transition, execution_order) |
| workflow_deferred_event | event_code; fk_id_workflow_state; unique (fk_id_workflow_state, event_code) |
| workflow_instances | reference_id unique, machine_context, context_format_version, created_at, updated_at; fk_id_workflow_definition, fk_id_workflow_state |
| workflow_request_replay | idempotency_key unique, request_fingerprint, response_json, http_status, created_at, completed_at |
| workflow_transition_history | event_code, request_id, idempotency_key, created_at; fk_id_workflow_instance, fk_id_workflow_definition, fk_id_source_workflow_state, fk_id_target_workflow_state |

Composite foreign keys ensure source, target and current states belong to the
correct definition. History also checks that its instance belongs to that
definition. Internal transitions require matching source and target state IDs.

The replay table is independent of instance foreign keys because a failed command
can reserve a retry key before any instance exists.

## Seeded onboarding

V2 creates one active definition (`workflow_key=onboarding`, `version=1`), five
states, four external transitions, four actions, one guard, fifteen state-action
bindings and four transition-action bindings. There are no deferred-event rows.

Seed statements resolve generated IDs through workflow keys, state codes, action
names and guard names. They do not depend on specific identity sequence values.
Runtime queries join action/guard tables to resolve the registered Java names.

## Inspect workflow rules

```sql
SELECT d.id AS definition_id, d.workflow_key, d.version, t.id AS transition_id,
       source.state_code AS source_state, t.event_code,
       target.state_code AS target_state, t.transition_kind, g.guard_name
FROM workflow_brain.workflow_transition t
JOIN workflow_brain.workflow_definition d ON d.id = t.fk_id_workflow_definition
JOIN workflow_brain.workflow_state source ON source.id = t.fk_id_source_workflow_state
JOIN workflow_brain.workflow_state target ON target.id = t.fk_id_target_workflow_state
LEFT JOIN workflow_brain.workflow_guard g ON g.id = t.fk_id_workflow_guard
ORDER BY d.workflow_key, d.version, t.id;
```

## Inspect progress and available transitions

```sql
SELECT i.id, i.reference_id, current.state_code AS current_state,
       t.event_code AS next_event, target.state_code AS next_state
FROM workflow_brain.workflow_instances i
JOIN workflow_brain.workflow_state current ON current.id = i.fk_id_workflow_state
LEFT JOIN workflow_brain.workflow_transition t
  ON t.fk_id_workflow_definition = i.fk_id_workflow_definition AND t.fk_id_source_workflow_state = i.fk_id_workflow_state
LEFT JOIN workflow_brain.workflow_state target ON target.id = t.fk_id_target_workflow_state
WHERE i.reference_id = 'APP-101';
```

Branching definitions can return multiple rows. The instance stores its current
state ID; runtime/API queries resolve it to a state code. There is no duplicated
physical current_state or next_state column to drift from the configured states.

## Inspect context and retry responses

```sql
SELECT id, reference_id, fk_id_workflow_definition, fk_id_workflow_state, machine_context
FROM workflow_brain.workflow_instances WHERE reference_id = 'APP-101';

SELECT id, idempotency_key, http_status, created_at, completed_at, response_json
FROM workflow_brain.workflow_request_replay
WHERE idempotency_key = 'APP-101-otp';
```

`machine_context` is versioned JSON containing formatVersion, state and variables.
The state code must match the code resolved from the instance's current state FK.
Contexts without JSON restore from the current state with empty extended variables.

A mutation locks the reservation by its unique idempotency key. Event submissions
also lock the instance row with `FOR UPDATE OF instance`, avoiding locks on shared
state-definition rows. State changes, history and successful retry responses
participate in the same transaction.

## Inspect applied transition history

```sql
SELECT h.id, source.state_code AS source_state, h.event_code,
       target.state_code AS target_state, h.request_id, h.idempotency_key, h.created_at
FROM workflow_brain.workflow_transition_history h
JOIN workflow_brain.workflow_instances i ON i.id = h.fk_id_workflow_instance
JOIN workflow_brain.workflow_state source ON source.id = h.fk_id_source_workflow_state
JOIN workflow_brain.workflow_state target ON target.id = h.fk_id_target_workflow_state
WHERE i.reference_id = 'APP-101'
ORDER BY h.created_at, h.id;
```

The index `(fk_id_workflow_instance, created_at)` supports history lookups. `id` breaks
transaction timestamp ties; sequence gaps after rollback are normal.

Only applied events produce history. Internal transitions are recorded even when
the state stays the same. Deferred events are recorded when applied, using the
request that triggers them. Creates, reads, rejected events and retry replays add
no history. History stores numeric relationships and resolves display values by
joining the instance and state tables.

## Publish a new definition version

Use a new Flyway migration. Insert a definition with its workflow_key and new
version, using `RETURNING id` for its generated ID. Insert its states with
fk_id_workflow_definition and capture their IDs for transitions. Resolve action and guard
IDs by action_name and guard_name. Use transition IDs for action bindings.

Existing instances retain their fk_id_workflow_definition. New creates select the highest
ACTIVE version. Review graph structure and registered names before activation;
published definitions should be treated as immutable.
