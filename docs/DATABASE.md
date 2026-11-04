# Database design and queries

All application tables and `flyway_schema_history` use the `workflow_brain`
schema. Flyway creates the schema before initializing its migration history.
`V1__create_workflow_schema.sql` creates tables, constraints and indexes.
`V2__insert_workflow_seed_data.sql` inserts the onboarding seed data afterward.

This testing baseline replaces the previous migration chain. Reset the application
tables and Flyway history together before starting it. Recreating `workflow_brain`
resets its tables, identity sequences and migration history. Remove any old
`public.flyway_schema_history` as part of the same testing reset if it exists.
An existing migration history must not be reused with this rewritten V1.

## Definition tables: shared rules

| Table | Key columns and relationships |
| --- | --- |
| workflow_definition | definition_id INTEGER identity PK; workflow_key + version unique; status DRAFT/ACTIVE/RETIRED; created_at |
| workflow_state | (definition_id, state_code) PK; initial_state; terminal_state; definition FK |
| workflow_transition | transition_id INTEGER identity PK; definition_id; source_state; event_code; target_state; transition_kind; optional guard_code FK; source/target composite FKs to states; (definition_id, source_state, event_code) unique |
| workflow_action | action_code PK; description |
| workflow_guard | guard_code PK; description |
| workflow_state_action | (definition_id, state_code, phase, execution_order) PK; action_code FK; phase ENTRY/EXIT/STATE; composite state FK |
| workflow_transition_action | (transition_id, execution_order) PK; action_code FK |
| workflow_deferred_event | (definition_id, state_code, event_code) PK; composite state FK |

`definition_id`, `transition_id` and `history_id` are PostgreSQL INTEGER identity
columns from initial creation. Definition and transition IDs use Java Integer.
All foreign-key columns referencing these IDs are INTEGER too. Database-generated
IDs are used by seed queries without hardcoded ID values or legacy text ID columns.

`reference_id`, `request_id`, idempotency keys and workflow/state/event/action/guard
codes remain business strings. Reference format validation is still pending.
Spring StateMachine requires a String machine identifier; numeric definition IDs
are converted only at that framework boundary.

## Execution and retry tables

| Table | Columns |
| --- | --- |
| workflow_instances | reference_id PK; definition_id FK; current_state; machine_context TEXT; context_format_version; created_at; updated_at; composite (definition_id, current_state) FK |
| workflow_request_replay | idempotency_key PK; request_fingerprint; response_json TEXT; http_status; created_at; completed_at |
| workflow_transition_history | history_id INTEGER identity PK; reference_id FK; definition_id FK; source_state; event_code; target_state; request_id; idempotency_key; created_at |

machine_context is versioned JSON, not a Java object serialized into unreadable
bytes. It contains formatVersion, state and variables. current_state remains
separate for ordinary SQL queries; reads reject a mismatching snapshot.

```mermaid
erDiagram
    workflow_definition ||--|{ workflow_state : defines
    workflow_definition ||--o{ workflow_transition : defines
    workflow_definition ||--o{ workflow_instances : used_by
    workflow_state ||--o{ workflow_state_action : runs
    workflow_state ||--o{ workflow_deferred_event : postpones
    workflow_action ||--o{ workflow_state_action : implements
    workflow_action ||--o{ workflow_transition_action : implements
    workflow_transition ||--o{ workflow_transition_action : runs
    workflow_guard o|--o{ workflow_transition : checks
```

workflow_request_replay is independent: GET_STATE and error reservations need not
have a successful workflow row. Its fingerprint binds a key to the request.

## See the rules (source state -> event -> next state)

```sql
SELECT d.workflow_key, d.version, t.source_state, t.event_code, t.target_state,
       t.transition_kind, t.guard_code
FROM workflow_brain.workflow_transition t
JOIN workflow_brain.workflow_definition d USING (definition_id)
ORDER BY d.workflow_key, d.version, t.transition_id;
```

## See a reference's current state and possible next steps

```sql
SELECT i.reference_id, i.current_state, t.event_code AS next_event,
       t.target_state AS next_state
FROM workflow_brain.workflow_instances i
LEFT JOIN workflow_brain.workflow_transition t
  ON t.definition_id = i.definition_id AND t.source_state = i.current_state
WHERE i.reference_id = 'APP-101';
```

This can return multiple rows for branching definitions. There is no duplicated
next_state column in workflow_instances that could drift from the rules.

## See saved context and retry results

```sql
SELECT reference_id, current_state, definition_id, machine_context
FROM workflow_brain.workflow_instances WHERE reference_id = 'APP-101';

SELECT idempotency_key, http_status, created_at, completed_at, response_json
FROM workflow_brain.workflow_request_replay
WHERE idempotency_key = 'APP-101-otp';
```

After CREATE, one instance row is inserted. After OTP_VERIFIED that row changes
from OTP_VERIFICATION to PROFILE_DETAILS, along with its context and updated_at.
Shared definition rows are reused. With a retry key, one replay row is reserved
and later completed with the successful response. Without a key, no replay row
is added. Applied-transition history is stored independently of retry responses.

## Publishing a new version

Use a new Flyway migration. Insert workflow_definition with a workflow_key and
incremented version. Use `RETURNING definition_id` to obtain its generated Integer
ID, then insert its states, transitions and bindings. Use `RETURNING transition_id`
when inserting transitions to obtain their generated IDs for action bindings. Review its
graph and registered behavior names before activation. Existing references retain
their definition_id; only new CREATE requests select the latest ACTIVE version.
Do not edit/delete rules referenced by existing instances.

The seeded definition has five states, four external transitions, four actions,
one guard, fifteen state action bindings and four transition action bindings.
It has no deferred events or internal transitions because onboarding currently
does not require them. The tables and engine also support configuring internal
transitions and deferred events for other workflow definitions.

## See applied transition history

```sql
SELECT history_id, source_state, event_code, target_state,
       request_id, idempotency_key, created_at
FROM workflow_brain.workflow_transition_history
WHERE reference_id = 'APP-101'
ORDER BY created_at, history_id;
```

The index (reference_id, created_at) supports lookups by reference and time.
history_id breaks timestamp ties: PostgreSQL CURRENT_TIMESTAMP can be identical
for multiple transitions in the same transaction. Sequence gaps after rollback
are normal and do not indicate missing committed history.

Only applied events insert rows. A same-state internal transition also inserts a
row. A deferred event is recorded when applied, with the request_id and
idempotency_key of the request that triggered its replay. Idempotency retries,
rejections, GET_STATE and CREATE add no history. Prior transitions are not backfilled.
History insertion, context persistence and successful response replay storage
share the workflow transaction. The initial migration creates the history table and its index.
