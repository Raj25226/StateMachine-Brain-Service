# Database design and queries

All 8 application tables and `flyway_schema_history` use the `workflow_brain`
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
`fk_id_workflow_definition`, `fk_id_workflow_state`, `fk_id_workflow_action`,
`fk_id_workflow_transition` and `fk_id_workflow_instance`. State roles use `fk_id_source_workflow_state` and
`fk_id_target_workflow_state`. Foreign-key columns appear after all business
columns and timestamps in each table, followed by table-level constraints.

Names and business identifiers are separate from database IDs:

- States retain `state_code`; transitions retain `event_code`.
- Actions retain `action_name`, such as `recordStateEntry`.
- Instances retain a unique `reference_id` for API lookups.
- Redis reservations use `idempotency_key`; transition history retains it only for audit correlation.

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
| workflow_transition | event_code, transition_kind; fk_id_workflow_definition, fk_id_source_workflow_state, fk_id_target_workflow_state; unique (fk_id_workflow_definition, fk_id_source_workflow_state, event_code) |
| workflow_state_action | phase, execution_order; fk_id_workflow_state, fk_id_workflow_action; unique (fk_id_workflow_state, phase, execution_order) |
| workflow_transition_action | execution_order; fk_id_workflow_transition, fk_id_workflow_action; unique (fk_id_workflow_transition, execution_order) |
| workflow_instances | reference_id unique, created_at, updated_at; fk_id_workflow_definition, fk_id_workflow_state |
| workflow_transition_history | event_code, request_id, idempotency_key, created_at; fk_id_workflow_instance, fk_id_workflow_definition, fk_id_source_workflow_state, fk_id_target_workflow_state |

Composite foreign keys ensure source, target and current states belong to the
correct definition. History also checks that its instance belongs to that
definition. Internal transitions require matching source and target state IDs.

Redis handles idempotency reservations and saved responses. PostgreSQL contains
no replay table. History stores the caller's request ID and idempotency key as
audit metadata, without a foreign key to Redis.

## Seeded onboarding

V2 creates one active definition (`workflow_key=onboarding`, `version=1`), five
states, four external transitions, eight actions, fifteen state-action
bindings and eight transition-action bindings. Each transition runs its TODO step
strategy at execution order 0, followed by recordTransition at order 1. See
[Configurable workflow steps](WORKFLOW_STEPS.md) for Java strategy registration
and database bindings.

Seed statements resolve generated IDs through workflow keys, state codes, action
names. They do not depend on specific identity sequence values.
Runtime queries join the action table to resolve the registered Java names.

## Inspect workflow rules

```sql
SELECT d.id AS definition_id, d.workflow_key, d.version, t.id AS transition_id,
       source.state_code AS source_state, t.event_code,
       target.state_code AS target_state, t.transition_kind
FROM workflow_brain.workflow_transition t
JOIN workflow_brain.workflow_definition d ON d.id = t.fk_id_workflow_definition
JOIN workflow_brain.workflow_state source ON source.id = t.fk_id_source_workflow_state
JOIN workflow_brain.workflow_state target ON target.id = t.fk_id_target_workflow_state
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

## Inspect saved progress

```sql
SELECT instance.id, instance.reference_id, instance.fk_id_workflow_definition,
       instance.fk_id_workflow_state, state.state_code AS current_state,
       instance.created_at, instance.updated_at
FROM workflow_brain.workflow_instances instance
JOIN workflow_brain.workflow_state state ON state.id = instance.fk_id_workflow_state
WHERE instance.reference_id = 'APP-101';
```

The machine restores directly from the state FK with empty extended variables.
There is no serialized context column or format version. No additional columns
are required for the removed bookkeeping values:

| Former JSON value | Relational source |
| --- | --- |
| state / lastEnteredState | Instance's state FK joined to workflow_state |
| lastProcessedEvent | Applied events in workflow_transition_history |
| lastExitedState | Source states in workflow_transition_history |

History includes internal transitions too, so distinguish actual state exits from
same-state processing when reviewing that audit trail. Arbitrary extended variables
are not durable; persistence rejects them rather than silently dropping values.
Add explicit columns for future durable business values.

### Apply the schema change

The two testing-baseline migrations now omit both context columns. Reset the
testing schema and Flyway history together before reseeding this baseline.
Changing a migration file does not change an already-running database.

For a database whose data must be retained, keep its previously applied V1/V2
files unchanged and put this DDL in a new versioned migration:

```sql
ALTER TABLE workflow_brain.workflow_instances
    DROP COLUMN IF EXISTS machine_context,
    DROP COLUMN IF EXISTS context_format_version;
```

The state FK, pinned definition and transition-history rows remain intact. The
removed JSON values in the current implementation are bookkeeping duplicates;
any custom extended variables introduced outside this repository need explicit
relational columns before their old JSON storage is removed. Deploy the revised
code and schema together. No existing user database is altered by editing these files.

An atomic Redis Lua script claims each mutation key. Event submissions also lock
the PostgreSQL instance row with `FOR UPDATE OF instance`. State updates and
history commit together in PostgreSQL; response storage follows in Redis.
Confirmed rollbacks make the Redis claim retryable. Uncertain outcomes remain
PROCESSING until recovery. See the README's idempotency section for persistence
requirements and the cross-store commit limitation.

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
the state stays the same. Creates, reads, rejected events and retry replays add
no history. History stores numeric relationships and resolves display values by
joining the instance and state tables.

## Publish a new definition version

Use a new Flyway migration. Insert a definition with its workflow_key and new
version, using `RETURNING id` for its generated ID. Insert its states with
fk_id_workflow_definition and capture their IDs for transitions. Resolve action
IDs by action_name. Use transition IDs for action bindings.

Existing instances retain their fk_id_workflow_definition. New creates select the highest
ACTIVE version. Review graph structure and registered names before activation;
published definitions should be treated as immutable.
