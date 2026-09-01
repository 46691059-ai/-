# Workflow V1 RC2-S11.9P-B ROLE Runtime Activation Mechanism Report

## Outcome

S11.9P-B implements the independent ROLE Runtime activation mechanism designed at commit `a9a1f3d3962b9ad4a0f4e4bb9d81b617fc820175`. It authors V2.6.25 and validates it only on isolated MySQL 8.4 instances. It does not migrate or activate the RC2 runtime.

## Why the old model is not runtime activation

`PersistentActivationStatus` describes only the request/approval/evidence persistence lifecycle. Its `PERSISTED` value proves that evidence was frozen; it does not prove that ROLE Runtime is active. `RoleRuntimeActivationPersistenceService` intentionally exposes no enable operation. Those types remain unchanged and are used only as authorization prerequisites.

## V2.6.25

`V2.6.25__create_role_runtime_activation_event.sql` creates `workflow_role_runtime_activation_event`. The table stores the exact six-dimensional scope, independent event and authorization types, sequence/revision, `DISABLED -> ACTIVATED` transition, release lineage, frozen evidence hashes, actor, occurrence time, and standard audit metadata.

The migration adds unique constraints for event identity, exact-scope/event type, exact-scope sequence, and exact-scope revision. A foreign key binds the event to a persisted activation authorization. The insert trigger requires one `PERSISTED` request, all three required `APPROVE` decisions, and evidence. UPDATE and DELETE triggers enforce append-only history.

## Authoritative model and derived state

`ROLE_RUNTIME_ACTIVATED` is the authoritative event. No event derives `DISABLED`; exactly one valid event derives `ACTIVATED`. There is no mutable current-state table and no unaudited state UPDATE.

The new `RoleRuntimeActivationState` and `RoleRuntimeActivationEvent` are independent from `PersistentActivationStatus` and Canary governance. Repository lookups use equality on enterprise, organization, definition, definition version, node, and binary role code. Null wildcard, parent inheritance, and cross-scope fallback are absent.

## Authorization and evidence

The application command requires `ACTIVATE_ROLE_RUNTIME`, never Canary `APPROVE` or `ENABLE`. It binds authorization identity, the persistence-design commit, observation evidence commit, runtime-enablement evidence commit, release commit/tag, approval evidence hash, and the five frozen business evidence hashes. The application reconstructs and compares these bindings before any append. Missing authorization, incomplete RACI approval/evidence, or any lineage/scope/evidence drift fails closed.

## Idempotency, replay, concurrency and atomicity

The first valid execution appends one event and returns `ACTIVATED`. A byte-equivalent replay returns `ALREADY_ACTIVE` without another event. A conflicting authorization or binding against an active scope fails closed.

The database exact-scope/event-type unique key is the final concurrency defense. Repository duplicate-key handling reloads the authoritative event and the application classifies only an exact replay as `ALREADY_ACTIVE`; persistence exceptions do not leak for the valid race. The complete validation and append occur in one Spring transaction. Because state is event-derived, a failed append leaves the runtime `DISABLED`, and an event cannot coexist with an independently stale state projection.

## Kill Switch and traffic isolation

The service reads and requires `STOP_NEW_AND_CLAIM`; it has no Kill Switch writer. It has no task creation, candidate population, claim, traffic release, or business-message dependency. Activation therefore does not release traffic or claims.

## Validation

Isolated MySQL verification covers fresh migration, V2.6.24-to-V2.6.25 upgrade, Flyway validation/no-op, identical schema fingerprints, table and column presence, unique constraints, legal append, duplicate exact-scope rejection, transaction rollback, and append-only rejection. V2.6.25 has SHA-256 `984b7db090ba148e3dbae7ca70988d2ff34015af51a518f33320b34371b2fbae` and Flyway checksum `1683053973`. All 46 historical migration hashes remain unchanged; the canonical inventory is now 47 SQL migrations.

Domain/application tests cover exact scope, successful activation, idempotency, replay, concurrent requests, simulated persistence failure, stale authorization, and Kill Switch isolation. Full Java 21 backend regression, Spring Context, workflow database mapping, migration integrity, S11.7/S11.8 contracts, and secret/PII scans are recorded in the gate result.

## Protected runtime status and next gate

No V2.6.25 DDL or activation DML was executed against `127.0.0.1:3306/enterprise_platform`. No access was made to 3307 or protected RC1 port 34061. ROLE Runtime remains `DISABLED`, its runtime activation event count remains zero, Canary remains `ENABLED` at revision 3, and the Kill Switch remains `STOP_NEW_AND_CLAIM`.

The only permitted next stage is S11.9P-C runtime provisioning. Neither an explicit activation decision nor actual ROLE Runtime Canary activation is authorized by this implementation gate.
