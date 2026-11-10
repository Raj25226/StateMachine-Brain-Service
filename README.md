# State Machine Brain

A database-configured workflow service for Finaag and Lyntra. Built with Java 25,
Spring Boot 4.1.1, Spring StateMachine 4.0.2, PostgreSQL, Redis, Flyway, JDBC and Gson.
Persistence uses `NamedParameterJdbcTemplate`; there is no JPA.

The brain owns workflow definitions, state transitions, persistence and request
idempotency. Business steps execute synchronously over HTTP in the separately
runnable [anti-corruption-layer](../anti-corruption-layer/README.md) service.
Only an explicit successful ACL response allows progress to be persisted.

The ACL provides default local implementations for all business steps. They log
completion and succeed without external APIs. Replace each implementation body
with its downstream call when ready; current success does not confirm a real
OTP check, profile update, identity check, plan selection or document upload.

```text
OTP_VERIFICATION --VERIFY_OTP--> PROFILE_DETAILS
PROFILE_DETAILS --UPDATE_PROFILE--> ACCOUNT_STATEMENT_UPLOAD
ACCOUNT_STATEMENT_UPLOAD --UPLOAD_ACCOUNT_STATEMENT--> UDYAM_DOCUMENT_UPLOAD
UDYAM_DOCUMENT_UPLOAD --UPLOAD_UDYAM_DOCUMENT--> COMPLETED
```

| Command event | Intended task | State after success |
| --- | --- | --- |
| `VERIFY_OTP` | Ask the OTP service to verify the supplied OTP | `PROFILE_DETAILS` |
| `UPDATE_PROFILE` | Ask the profile service to save/update profile details | `ACCOUNT_STATEMENT_UPLOAD` |
| `UPLOAD_ACCOUNT_STATEMENT` | Ask the document service to store the account statement | `UDYAM_DOCUMENT_UPLOAD` |
| `UPLOAD_UDYAM_DOCUMENT` | Ask the document service to store the Udyam registration document | `COMPLETED` |

These names describe requests to perform work. `CREATE` starts at
`OTP_VERIFICATION`; `GET_STATE` reads progress without executing a step.

## Run locally

Install Java 25+, Maven 3.9+, PostgreSQL and Redis. Create a database named `workflow`
and give the database account permission to create the `workflow_brain` schema
and its tables. Flyway creates the schema, application tables and seed data when
the application starts.

For macOS/Linux:

```sh
export DB_URL='jdbc:postgresql://localhost:5432/workflow'
export DB_USERNAME='admin'
export DB_PASSWORD='admin'
export SERVER_PORT='8081'
export REDIS_HOST='localhost'
export REDIS_PORT='6379'
mvn spring-boot:run
```

The values above match the local defaults in
[application.yml](src/main/resources/application.yml). Change them for your
PostgreSQL installation. `workflow` is the database name; `workflow_brain` is the
schema inside that database.

| Environment variable | Default |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/workflow` |
| `DB_USERNAME` | `admin` |
| `DB_PASSWORD` | `admin` |
| `SERVER_PORT` | `8081` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |
| `REDIS_PASSWORD` | empty |
| `ACL_BASE_URL` | `http://localhost:8082` |
| `ACL_TIMEOUT` | `10s` |

Start Redis separately with persistence and eviction settings appropriate for
idempotency storage, for example:

```sh
redis-server --appendonly yes --appendfsync always --maxmemory-policy noeviction
```

The ACL is a separate sibling project at `../anti-corruption-layer`.
Start it in a separate terminal with `mvn -f ../anti-corruption-layer/pom.xml spring-boot:run`.
The repository does not include a Docker Compose file or Maven wrapper.
Check `java -version` and `mvn -version` if Maven uses a different JDK.

With the default port:

- [Swagger UI](http://localhost:8081/swagger-ui.html)
- [OpenAPI JSON](http://localhost:8081/v3/api-docs)
- [OpenAPI YAML](http://localhost:8081/v3/api-docs.yaml)

## Build and test

```sh
mvn clean package
java -jar target/state-machine-brain-0.0.1-SNAPSHOT.jar
```

Configure the database environment variables before running the JAR. To run only
the automated tests:

```sh
mvn test
```

Tests cover request binding and validation, Swagger examples, exception routing,
Gson serialization, saved-response replay, fingerprint compatibility, SQL catalog
lookups and the integer definition-ID boundary. Full workflow transaction and
concurrency tests against PostgreSQL are not part of the automated suite.

## Formatting

Spotless formats Java with Google Java Format in AOSP style (four-space indentation).
Apply formatting in both services:

```sh
mvn spotless:apply
mvn -f ../anti-corruption-layer/pom.xml spotless:apply
```

Use `spotless:check` to check formatting without changing files. Both services also
run this check during `mvn verify`.


## Finaag and Lyntra

`finaag` is the default workflow; `lyntra` must be selected on CREATE. Workflow
keys and event codes are case sensitive.

| Workflow | Command sequence | States |
| --- | --- | --- |
| `finaag` | `VERIFY_OTP` → `UPDATE_PROFILE` → `UPLOAD_ACCOUNT_STATEMENT` → `UPLOAD_UDYAM_DOCUMENT` | 5 |
| `lyntra` | `VERIFY_OTP` → `VERIFY_IDENTITY` → `SELECT_PLAN` | 4 |

Finaag retains the original sequence. Lyntra follows:

```text
OTP_VERIFICATION --VERIFY_OTP--> IDENTITY_REVIEW
IDENTITY_REVIEW --VERIFY_IDENTITY--> PLAN_SELECTION
PLAN_SELECTION --SELECT_PLAN--> COMPLETED
```

The brain owns these sequences and sends the workflow key/version and action name
to the ACL. Individual `@Component` implementations in `strategy/step` execute the
action; there are no ACL workflow lists or grouped registration classes.
Both workflows use `VerifyOtpStep`. The other actions have implementations scoped
to their workflow key. All six implementations log local completion and succeed by
default. Replace each `execute` body with its downstream API call later; no demo flag
is required. These local defaults do not perform real business operations yet.

Create a Lyntra instance with:

```json
{
  "metadata": {
    "operation": "CREATE",
    "requestId": "req-lyntra",
    "referenceId": "LYN-101",
    "workflowKey": "lyntra",
    "idempotencyKey": "LYN-101-create"
  },
  "payload": {}
}
```

Submit the commands in the table in order, each with its own idempotency key.
SUBMIT_EVENT uses the instance reference to find the workflow; omit `workflowKey`
on that operation. Event names determine business actions, not state ordering.

## API contract

All business operations use **POST `/api/workflows`** with
`Content-Type: application/json`.

Each request contains exactly two objects: `metadata` and `payload`. The current
payload must be `{}`. Unknown fields, duplicate JSON properties, trailing JSON,
non-string metadata, explicit null metadata fields and fields forbidden for the
selected operation return `400 INVALID_REQUEST`.

| Operation | Required metadata | Optional metadata | Forbidden metadata | Success |
| --- | --- | --- | --- | --- |
| `CREATE` | operation, requestId, referenceId, idempotencyKey | workflowKey; defaults to `finaag` | event | 201 |
| `GET_STATE` | operation, requestId, referenceId | None | event, workflowKey, idempotencyKey | 200 |
| `SUBMIT_EVENT` | operation, requestId, referenceId, event, idempotencyKey | None | workflowKey | 200 |

Operation names and workflow/event codes are case sensitive. Required strings
must be nonblank. `requestId` is a caller-supplied correlation token;
`idempotencyKey` identifies one logical mutation. Neither requires a UUID format.

`referenceId` must be globally unique across workflow instances, including different
workflow definitions and versions. The database enforces this through
`workflow_instances.reference_id VARCHAR(100) NOT NULL UNIQUE`. A new CREATE using
an existing reference returns `409 WORKFLOW_ALREADY_EXISTS`; an identical retry
with the original idempotency key replays its saved result.

PAN, mobile and email format validation is intentionally pending; currently only
a nonblank value is required by request validation.
Workflow keys and event codes are validated against database definitions. Event
execution also checks the current state.

Request validation does not impose string-length limits. Database columns still
limit references, workflow keys and event codes to 100 characters, and persisted
request IDs and idempotency keys to 128. Oversized stored values currently cause
a database error. Revisit these constraints when reference formats are defined.

### Create a workflow

```json
{
  "metadata": {
    "operation": "CREATE",
    "requestId": "req-001",
    "referenceId": "APP-101",
    "workflowKey": "finaag",
    "idempotencyKey": "APP-101-create"
  },
  "payload": {}
}
```

Creates an instance using the highest `ACTIVE` version of the workflow. Seeded
finaag starts at `OTP_VERIFICATION`. A reference can have only one instance.

### Request a workflow step

```json
{
  "metadata": {
    "operation": "SUBMIT_EVENT",
    "requestId": "req-002",
    "referenceId": "APP-101",
    "event": "VERIFY_OTP",
    "idempotencyKey": "APP-101-otp"
  },
  "payload": {}
}
```

Example response:

```json
{
  "metadata": {
    "operation": "SUBMIT_EVENT",
    "requestId": "req-002",
    "referenceId": "APP-101",
    "event": "VERIFY_OTP",
    "idempotencyKey": "APP-101-otp",
    "workflowKey": "finaag",
    "status": "SUCCESS",
    "processedAt": "2026-11-04T06:00:00Z",
    "idempotencyReplayed": false
  },
  "payload": {
    "referenceId": "APP-101",
    "workflowKey": "finaag",
    "definitionVersion": 1,
    "currentState": "PROFILE_DETAILS",
    "nextEvent": "UPDATE_PROFILE",
    "nextState": "ACCOUNT_STATEMENT_UPLOAD",
    "completed": false,
    "availableTransitions": [
      {"event": "UPDATE_PROFILE", "nextState": "ACCOUNT_STATEMENT_UPLOAD"}
    ],
    "eventOutcome": "APPLIED"
  }
}
```

Continue with `UPDATE_PROFILE`, `UPLOAD_ACCOUNT_STATEMENT` and
`UPLOAD_UDYAM_DOCUMENT`, using a separate idempotency key for each event.
There is no execute-all operation.

### Read current progress

```json
{
  "metadata": {
    "operation": "GET_STATE",
    "requestId": "req-003",
    "referenceId": "APP-101"
  },
  "payload": {}
}
```

`GET_STATE` reads the saved snapshot without locking the instance or starting a
state machine. It does not execute actions or add transition history.

`nextEvent` and `nextState` appear only when one outgoing transition exists.
Branching workflows expose all configured possibilities in `availableTransitions`;
Terminal states have `completed=true` and no
available transitions.

Custom definitions can configure internal transitions, which apply without changing
state. Each submitted event is processed immediately; unsupported events return
`EVENT_NOT_ALLOWED`.

## Gson serialization

[GsonConfiguration](src/main/java/com/example/statemachine/config/GsonConfiguration.java)
provides the shared Gson bean used for workflow HTTP envelopes, saved Redis
responses and fingerprint generation. PostgreSQL persistence does not serialize JSON.

- `WorkflowRequestTypeAdapter` enforces the strict request structure.
- `WorkflowResponseTypeAdapterFactory` omits optional null fields and always
  includes `payload`, including `payload: null` on errors.
- `InstantTypeAdapter` reads and writes ISO-8601 timestamps.
- `JsonNumberParserFunction` preserves numeric types when binding general JSON values.
- `WorkflowGsonHttpMessageConverter` handles workflow envelopes; Swagger documents
  use their own framework serialization.

Fingerprint serialization preserves the existing property order and escaping so
changing JSON libraries does not invalidate saved idempotency reservations.

## Idempotency and transactions

`CREATE` and `SUBMIT_EVENT` require an idempotency key; `GET_STATE` forbids one.
Keys are global within the application and have no automatic expiration.

A SHA-256 fingerprint binds each key to the operation, reference, event and
normalized workflow key. `requestId` is excluded. Omitting `workflowKey` on
`CREATE` is equivalent to specifying `finaag`.

An identical successful retry returns the original HTTP status, payload and
`processedAt`, echoes the retry's `requestId`, and sets `idempotencyReplayed=true`.
The saved result can be older than current progress; use `GET_STATE` to read the
latest state. Different inputs with the same key return `IDEMPOTENCY_CONFLICT`.
Failed attempts keep the key bound to their inputs without saving a success.

Redis stores fingerprints, claim status and successful response JSON under
`workflow:idempotency:<idempotencyKey>`. Lua scripts atomically claim, complete and
release a confirmed rollback. No idempotency table is used in PostgreSQL.

A claim has one owner and no automatic takeover or expiration. Concurrent attempts
with the same inputs return `409 IDEMPOTENCY_IN_PROGRESS`; different inputs return
`409 IDEMPOTENCY_CONFLICT`. A confirmed JDBC rollback marks the claim retryable
without removing its fingerprint. State updates and transition history commit in
one PostgreSQL transaction. After that commit, the success response is saved in
Redis. Redis errors return `503 IDEMPOTENCY_UNAVAILABLE`.

PostgreSQL and Redis do not share a transaction. A crash or Redis failure after the
database commit can leave a claim PROCESSING without a saved result. Retries stay
blocked rather than execute the mutation again. Such claims require an operator
to inspect workflow state/history and restore the result or establish that retry
is safe. Do not delete an in-progress key, automatically expire it, or use a new
key to bypass recovery.

Redis is durable application storage here, not an evictable cache. Run a dedicated
instance with AOF persistence (`appendonly yes`, preferably `appendfsync always`)
and `maxmemory-policy noeviction`. Losing or evicting Redis records loses retry
protection. The application does not change server persistence settings. External
business calls still need their own idempotency or an outbox.

Run the optional real Redis integration tests against a disposable instance:

```sh
REDIS_INTEGRATION_PORT=6379 mvn test
```

The tests create UUID-suffixed keys and delete only those keys. They cover atomic
fingerprint checks, owner tokens, rollback retry and concurrent requests. Without
the environment variable, these tests are skipped; unit tests still run.

## Database and Flyway

All application tables and `flyway_schema_history` use the `workflow_brain` schema.

| Migration | Responsibility |
| --- | --- |
| [V1__create_workflow_schema.sql](src/main/resources/db/migration/V1__create_workflow_schema.sql) | Tables, constraints, identity columns and indexes |
| [V2__insert_workflow_seed_data.sql](src/main/resources/db/migration/V2__insert_workflow_seed_data.sql) | Finaag and Lyntra definitions, states, transitions, actions and bindings |

Every application table has an `id INTEGER` identity primary key, represented by
Integer in Java. All foreign-key columns use `fk_id_<full_entity_name>`, for example
`fk_id_workflow_definition`, `fk_id_workflow_state`, `fk_id_workflow_action`, `fk_id_workflow_transition`
and `fk_id_workflow_instance`. Source/target relationships use `fk_id_source_workflow_state` and
`fk_id_target_workflow_state`. Foreign-key columns appear after all business
columns and timestamps in each table, followed by table-level constraints.

Business references, request IDs, idempotency keys and workflow/state/event codes
remain strings. Actions store their Java registry names in `action_name`; related rows store their integer IDs. Runtime joins resolve
these names and the readable state codes. Definition IDs become strings only at
the Spring StateMachine factory boundary, where the framework requires them.

The schema has 8 application tables: workflow definitions, states, transitions,
actions, state-action bindings, transition-action bindings, instances and transition history. See
[docs/DATABASE.md](docs/DATABASE.md) for columns, relationships and inspection SQL.

### Reset the testing database

The current V1/V2 files replace the earlier testing migration chain. Stop the app
and reset the application tables **and Flyway history** before using this baseline.
Recreating `workflow_brain` also resets its tables, sequences and history. Remove
an old `public.flyway_schema_history` as part of that reset if it exists.

Deleting only application tables leaves stale migration records. These migrations
expect an empty schema and a fresh history; they do not upgrade the old baseline.

### Runtime SQL catalog

All runtime JDBC queries are in
[sql-queries.yml](src/main/resources/sql-queries.yml). Repositories supply a parent
group and a query suffix through constants:

```java
final String sql = SqlQueryFunction.GET_QUERY.apply(
        SqlQueryConstants.WORKFLOW_DEFINITION_PARENT,
        SqlQueryConstants.FIND_LATEST_ACTIVE_DEFINITION_ID);
```

The parent is `workflow-definition`; the suffix is `.find-latest-active-id`.
`SqlQueryFunction` implements `BiFunction<String, String, String>`, loads the
catalog once from the classpath and rejects missing or blank lookups. Values are
bound through JDBC named parameters.

## Workflow configuration and execution

```text
WorkflowController -> WorkflowRequestValidator -> WorkflowCommandGateway
  -> WorkflowCommandService -> WorkflowExecutionService
       -> StateMachineFactory -> JdbcWorkflowModelFactory
                                -> JdbcWorkflowDefinitionRepository
       -> DefaultStateMachinePersister -> JdbcWorkflowStateMachinePersistence
                                        -> JdbcWorkflowInstanceRepository
```

Each instance stays pinned to its definition version. New instances select the
highest active version; retiring it does not change existing instances. Publish
configuration changes through a new Flyway migration with a new version, using
generated IDs and their `RETURNING` values for related rows.

Definitions require one initial state, at least one terminal state, no outgoing
transitions from terminal states and no unreachable states. Action names resolve through `WorkflowBehaviorRegistry`; unknown names fail closed.
Business action names are forwarded unchanged to the ACL with the pinned workflow
key/version, reference, event, request ID and idempotency key. The brain has no
OTP, profile or document implementation. Bookkeeping actions remain local.
New business action names need only a database binding and an ACL handler.
Unknown implementations fail in the ACL without persisting progress.

The ACL supports workflow-specific and shared implementations; see
[Configurable workflow steps](docs/WORKFLOW_STEPS.md). The public brain request
contract remains unchanged: step inputs are currently looked up by reference ID,
and `payload` remains `{}` until downstream input contracts are defined.

Machine restoration uses the instance's `fk_id_workflow_state` and pinned definition.
PostgreSQL stores no machine-context JSON or context format version. The current
state is resolved through the state FK; processed events and source/target states
are already recorded in transition history. The bookkeeping actions only log.
Restored machines have empty extended variables. Attempts to persist nonempty
extended variables are rejected; durable business values require explicit relational
columns and queries. Hierarchical/parallel persistence is not supported.

Applied events are recorded in `workflow_transition_history`. Internal transitions
also create history. Creates, reads, rejected events and
idempotent replays do not create transition history. No history HTTP endpoint is
exposed.

## Errors and logging

Errors use the same envelope with `payload: null`, `metadata.status=FAILED` and
`error.code`/`error.description`. Caller metadata is echoed when recoverable.
JSON binding failures can omit caller fields, but the correlation header remains.

| HTTP | Error code | Meaning |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | Invalid request structure, operation, fields or unknown event |
| 404 | `WORKFLOW_NOT_FOUND` | Reference does not exist |
| 404 | `WORKFLOW_DEFINITION_NOT_FOUND` | No active definition for the workflow key |
| 404 | `ENDPOINT_NOT_FOUND` | Unknown route |
| 409 | `WORKFLOW_ALREADY_EXISTS` | Reference already has a workflow |
| 409 | `EVENT_NOT_ALLOWED` | Event cannot run in the current state |
| 409 | `IDEMPOTENCY_CONFLICT` | Retry key is bound to different inputs |
| 409 | `IDEMPOTENCY_IN_PROGRESS` | Processing or uncertain result requires recovery |
| 503 | `IDEMPOTENCY_UNAVAILABLE` | Redis unavailable or claim ownership lost |
| 405 / 415 | `INVALID_REQUEST` | Unsupported method or content type |
| 500 | `INTERNAL_ERROR` | Unexpected failure; details remain in server logs |

Classes use Lombok `@Slf4j`. INFO logs capture startup, request receipt and successful
completion. WARN logs identify rejected requests; ERROR logs retain unexpected
failure details. State processing and retry details are logged at DEBUG. Enable
these with `LOGGING_LEVEL_COM_EXAMPLE_STATEMACHINE=DEBUG` or the corresponding
`logging.level.com.example.statemachine` property.

Every `/api/` request gets an `X-Correlation-Id` response header. It identifies the
HTTP attempt separately from `requestId` and `idempotencyKey`. Request attributes
share recovered metadata and response details with exception handling and the
logging filter. MDC is restored after each request. Log metadata is bounded and
control characters are sanitized; raw bodies and business document contents are
not logged. DEBUG processing logs can precede a rollback; the HTTP completion log
records the final request outcome.

## Swagger and source layout

Swagger annotations, request examples and response examples are declared directly
in `WorkflowController`. They cover all three operations, each finaag event,
completion, retries, illustrative custom workflows and handled errors. Swagger
Try it out executes real requests against the configured database.

```text
src/main/java/com/example/statemachine/
  config/          Gson bean and HTTP converter configuration
  constants/       API properties, error codes, SQL keys and workflow constants
  controller/      Business endpoint and Swagger annotations
  dto/api/         HTTP envelopes and response details
  dto/persistence/ Relational database records
  dto/workflow/    Workflow definitions and commands
  enums/           Domain enums and functional utility implementations
  exception/       Per-exception handlers and error envelope construction
  logging/         HTTP correlation, timing and outcome logs
  repository/      JDBC workflow repositories, Redis idempotency and state-machine persistence
  serialization/   Gson request, response and timestamp adapters
  service/         Dispatch, idempotency and workflow transactions
  statemachine/    Model factory, action registry and event processor
  validation/      Operation-specific request validation
```

Reusable utilities use enum implementations of `Predicate`, `Function`,
`BiFunction`, `Supplier`, `Consumer` or `BiConsumer`, called directly at usage sites.

Brain-to-ACL calls log their workflow, version, action, request ID and duration.
The brain forwards `X-Correlation-Id` to the ACL; both services log that ID so a
single execution can be traced across their logs. Non-success HTTP responses and
invalid ACL acknowledgements are logged without response bodies.
