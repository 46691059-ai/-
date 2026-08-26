# MySQL Governed Migrations

Workflow V1 RC2-S11.1 adds the unexecuted V2.6.24 candidate for an exact
six-dimensional, append-only Canary governance ledger. It separates
`PROPOSED`, `APPROVED_NOT_ENABLED`, `ENABLED`, `SUSPENDED`, and `REVOKED`,
uses revision/predecessor uniqueness for CAS, and treats all legacy generic
`CANARY/ALLOW` controls as non-authorizing because runtime readers now use the
new exact scope authority. The candidate has not undergone real MySQL/Flyway
validation and must remain `CANDIDATE / NOT_EXECUTED`. ROLE Runtime and Canary
remain disabled.

Sprint 2-3.7-WF4.2.4 completed the combined V2.6.7 + V2.6.8 acceptance on
three newly initialized, loopback-only MySQL Community Server 8.4.9 instances:
Fresh, V2.6.6 Upgrade, and V2.6.7 Forward-Fix. Flyway 13.0.0 strict validate,
history, and second no-op migrate checks passed on every path. Full, Workflow,
and Claim-integrity fingerprints (including trigger definitions) matched.
All 18 database negative tests, all three historical P0 regressions, the legal
Claim chain, two-session single-winner concurrency, transaction rollback,
append-only enforcement, DIRECT/Legacy compatibility, and 336 backend tests
passed.

Governed states are now:

- `V2.6.8`: `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`, Flyway
  checksum `1316060036`;
- `V2.6.7`: `CANONICAL_IMMUTABLE /
  EPHEMERAL_MYSQL8_VALIDATED_WITH_V268`, Flyway checksum `1689998435`.

The earlier standalone V2.6.7 failure record remains preserved. V2.6.7 is not
an independently complete integrity endpoint: deployment must continue
immediately through V2.6.8 in the same governed release chain. This validation
does not assert deployment to any managed or production environment.

Sprint 2-3.7-WF4.2.3 adds the forward-only candidate
`V2.6.8__strengthen_workflow_claim_integrity.sql`. It performs dirty-data
prechecks before permanent DDL, then adds composite Task/NodeExecution/Pool
ownership constraints for Claim, a complete Audit-to-Claim ownership foreign
key, a SUCCESS Audit completeness CHECK, and append-only Audit triggers backed
by immutable Repository/Application contracts. It does not repair or infer
historical data, modify V2.6.7, enable Release/Transfer/Delegate, or touch
Investment. V2.6.8 starts as `CANDIDATE / NOT_EXECUTED`, with repository
SHA-256 `4cfd089f4aa05f6e4fb9d9dc250159c0229b3d98eb400089e5e08a758cb4a207`
and `flyway_checksum: null`. V2.6.7 retains its standalone
`EPHEMERAL_MYSQL8_VALIDATION_FAILED` history until a separate combined real
MySQL acceptance succeeds.

Automatic Flyway location: `filesystem:/flyway/sql` after this directory is mounted read-only.

Sprint 2-1.10 promoted the byte-identical, MySQL 8 validated lifecycle chain:

- `V2.1.0` lifecycle V2 structure;
- `V2.1.1` project stage snapshot links;
- `V2.1.2` LEGACY lifecycle backfill;
- `V2.1.3` standard lifecycle templates.

Sprint 2-2 added and validated the next canonical asset:

- `V2.2.0` Investment module RBAC and menu bootstrap metadata.

Sprint 2-2.1 added and validated the Investment decision-loop schema baseline:

- `V2.4.0` Investment opportunity, feasibility version, due diligence, scheme, decision node and conditional approval structures.

Sprint 2-3.2 adds, and Sprint 2-3.2.1 validates on isolated MySQL 8.4.9, the
Investment decision integration increments:

- `V2.4.4` immutable decision snapshots and decision risk/audit references;
- `V2.4.5` Investment-to-Workflow bindings and decision-node references;
- `V2.4.6` condition risk/audit references and immutable business audit events.

Sprint 2-3.3 adds, and Sprint 2-3.3.1 validates on isolated MySQL 8.4.9:

- `V2.4.7` Investment Workflow Outbox/Inbox reliability state and decision RBAC metadata.

Sprint 2-3.4 adds, and Sprint 2-3.4.1 validates on isolated MySQL 8.4.9:

- `V2.4.8` canonical decision approval states plus approval, condition, and archive RBAC metadata.

Sprint 2-3.6 adds, and Sprint 2-3.6.1 validates on isolated MySQL 8.4.9:

- `V2.4.9` Investment-side Workflow worker leases, dead-letter and Inbox replay evidence,
  reliability audit records, and controlled replay RBAC metadata.

The V2.4.9 fresh-foundation and V2.4.8-upgrade paths passed pre-policy checks,
Flyway `migrate`, strict `validate`, and a second no-op `migrate`, with the
application Outbox Worker disabled throughout acceptance.

Sprint 2-3.7-WF2.1 adds the first Workflow-owned candidate asset:

- `V2.5.0` Workflow definition, immutable version, and node-template schema.

Sprint 2-3.7-WF2.1.1 validated V2.5.0 on isolated MySQL Community Server 8.4.9
using both the fresh and V2.4.9-upgrade paths. Its governed state is now
`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`; this is acceptance evidence,
not proof of deployment to a managed or production environment. V2.4.9 and
earlier do not create or query Workflow-owned tables; V2.5.0 does not access
Investment data.

Sprint 2-3.7-WF2.3 adds the next Workflow-owned asset:

- `V2.5.1` Workflow runtime instances and materialized approval tasks.

Sprint 2-3.7-WF2.3.1 validated V2.5.1 on two newly created, loopback-only
MySQL Community Server 8.4.9 instances using the fresh-foundation and
V2.5.0-upgrade paths. Flyway 13.0.0 `migrate`, strict `validate`, history and
second no-op `migrate` checks passed; both paths produced the same schema
fingerprint. Its governed state is now
`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`. This evidence does not
assert deployment to any managed or production environment. V2.5.1 does not
implement task actions, approval logs, conditional routing, or BPM features.

Sprint 2-3.7-WF2.4 adds the candidate Workflow task-action asset:

- `V2.5.2` immutable approve, reject, and withdraw action evidence.

Sprint 2-3.7-WF2.4.1 validated V2.5.2 on two newly created, loopback-only
MySQL Community Server 8.4.9 instances using the fresh-foundation and
V2.5.1-upgrade paths. Flyway 13.0.0 `migrate`, strict `validate`, history,
second no-op `migrate`, structure constraints, negative constraints, and
dual-path schema fingerprints passed. Its governed state is now
`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`. This evidence does not
assert deployment to any managed or production environment. Task state changes
use the V2.5.1 optimistic lock; Workflow Lite still does not implement
multi-node routing, countersign, conditional gateways, delegation, or a
general BPM engine.

Sprint 2-3.7-WF2.5 adds the Workflow RBAC initialization candidate:

- `V2.5.3` Workflow permissions, M/C/B menu metadata, and SUPER_ADMIN grants.

V2.5.3 is registered as `CANDIDATE / NOT_EXECUTED`. It changes only existing
RBAC seed data and creates no business table. The migration grants the complete
Workflow matrix only to `SUPER_ADMIN`; `workflow:approve` is a feature-entry
authority and never replaces task assignee, candidate, ownership, or
organization checks. The migration has not been executed or accepted on a real
managed database and must not be treated as immutable until an isolated
acceptance Sprint passes and a later environment applies it through the
approved release process.

Sprint 2-3.7-WF2.5.1 executed V2.5.3 on two newly created, loopback-only MySQL
Community Server 8.4.9 instances. Fresh and V2.5.2-upgrade Flyway migration,
strict validation, history, checksum, second no-op migration, RBAC counts,
SUPER_ADMIN grants, permission/grant uniqueness, and foreign-key rejection all
passed. Acceptance did not pass because the current `sys_menu` model has no
`menu_code` column and no equivalent active stable-key unique constraint; a
duplicate active `/workflow` root path was accepted inside a rolled-back
negative test. V2.5.3 therefore remains `CANDIDATE / NOT_EXECUTED` and must not
be treated as `CANONICAL_IMMUTABLE` or deployed until the RBAC schema gap is
resolved by a separately approved higher Migration.

Sprint 2-3.7-WF2.5.3 adds the RBAC menu stable-key governance candidate:

- `V2.5.4` explicitly maps the 71 reviewed baseline menus, including all 10
  Workflow menus, then adds the stable `menu_code`, a format constraint, and
  `UNIQUE(menu_code, delete_token)`.

V2.5.4 performs mapping-completeness, mapping-fingerprint, menu-tree,
logical-delete-data, active-route, and active-button identity checks in
connection-local temporary tables before the first permanent DDL. It never
infers a code from a name, id, path, or parent. Unknown or changed menus cause
the Migration to fail before `ALTER TABLE`. It intentionally adds no strict
`deleted/delete_token` CHECK because the current menu deletion operation is a
two-step compatibility flow. V2.5.4 is registered as
`CANDIDATE / NOT_EXECUTED`; no real MySQL acceptance or Flyway checksum is
claimed. V2.5.3 remains unchanged and blocked until a separate combined
V2.5.3+V2.5.4 MySQL acceptance succeeds.

Sprint 2-3.7-WF2.5.4 then found a blocking MySQL 1267 error during the combined
Fresh and V2.5.2-upgrade acceptance: the temporary mapping table inherited the
schema's `utf8mb4_general_ci` default while `sys_menu` uses
`utf8mb4_0900_ai_ci`. Because V2.5.4 had not been promoted or successfully
executed, Sprint 2-3.7-WF2.5.5 revised that candidate in place. Every temporary
mapping string, both temporary tables, and the permanent `menu_code` column now
declare `CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci` explicitly. The
failed acceptance record remains in
`docs/workflow-rbac-v253-v254-combined-validation-report.md`; the revised asset
must pass a new isolated combined acceptance before promotion.

The revised V2.5.4 subsequently passed Sprint 2-3.7-WF2.5.5 on isolated MySQL
Community Server 8.4.9 using both Fresh and V2.5.2-upgrade paths. V2.5.3 and
V2.5.4 each executed once, strict validation passed, second migrate was a
no-op, both target schema fingerprints matched, all 71 menu codes were present
and unique, and the duplicate/format/logical-delete tests passed without
residual rows. V2.5.3 and V2.5.4 are therefore governed as
`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`. The earlier FAIL reports
remain part of the audit history. Per the Sprint constraint, repository
`flyway_checksum` remains `null`; observed ephemeral checksums are recorded in
the validation report and inventory attempt.

`SHA256SUMS` is the release asset manifest. Every SQL file in this directory must be listed, and all listed hashes must pass before `migrate`. Promoted files are immutable; corrections require a higher migration version.

`V2.0.0__legacy_to_v1.sql` remains excluded because it is only valid for a specific legacy `pm_*` schema.

Sprint 2-3.7-WF2.7 adds `V2.5.5__implement_workflow_version_release.sql`. It
adds the immutable Workflow version publication audit and freezes the published
definition content hash on runtime instances. It does not modify V2.5.0-V2.5.4,
Investment data, task routing, or approval semantics.

Sprint 2-3.7-WF2.7.1 validated V2.5.5 on isolated MySQL Community Server 8.4.9
with Flyway 13.0.0. Both Fresh and V2.5.4-upgrade paths migrated successfully,
passed strict validation, and produced no-op second migrations. Their complete
and Workflow-only normalized schema fingerprints matched. Historical instance
backfill, version publication/retirement, single-winner concurrent publication,
immutable audit constraints, and deterministic content hashing passed. V2.5.5
is therefore governed as `CANONICAL_IMMUTABLE /
EPHEMERAL_MYSQL8_VALIDATED`. Its repository SHA-256 remains
`50efcb230b2cba2e7a7600fdfc3e229f05a9e85877194cf9f1eed0875d52605e` and
its observed Flyway checksum is `1152171452`.

Sprint 2-3.7-WF3.1 adds
`V2.6.0__create_workflow_multi_node_foundation.sql`. It creates only `workflow_transition` and
`workflow_node_execution`, preserving all V2.5 tables and runtime behavior.
The migration does not enable automatic node advancement, countersign,
condition evaluation, automatic assignee selection, or Investment integration.
Its repository SHA-256 is
`76f2113573eac6af106fadb1681774e49a6f15713860a6af7b37f8d9ebc5c1fa`.

Sprint 2-3.7-WF3.1.1 validated V2.6.0 on isolated MySQL Community Server
8.4.9 with Flyway 13.0.0. Fresh and V2.5.5-upgrade paths both migrated,
strictly validated, and produced no-op second migrations. Their complete and
Workflow-only normalized schema fingerprints matched. All transition and node
execution structure checks and six negative constraint tests passed, with no
residual fixture rows. V2.6.0 is therefore governed as
`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`; its observed Flyway
checksum is `2093108182`.

Sprint 2-3.7-WF3.2 adds the candidate
`V2.6.1__enable_workflow_linear_runtime.sql`. It preserves all historical
Workflow and Investment migrations and adds only explicit engine/hash markers,
the authoritative instance-to-node-execution cursor, and the nullable
task-to-node-execution association required for Legacy compatibility. Its
repository SHA-256 is
`50314ff572253eaef6e0002acc2b1f9109fe7332a7360f8c8b14c4e8fde1a03e`.
V2.6.1 remains `CANDIDATE / NOT_EXECUTED`; no real MySQL or production
Migration execution is claimed by this implementation Sprint.

Sprint 2-3.7-WF3.2.1 validated V2.6.1 on two newly initialized, loopback-only
MySQL Community Server 8.4.9 instances with Flyway 13.0.0. Fresh and
V2.6.0-upgrade paths migrated successfully, passed strict validation, and
produced no-op second migrations. The complete and Workflow-only normalized
schema fingerprints matched. Structure constraints, seven negative tests, the
linear START-to-A-to-B-to-END chain, and a two-session single-winner completion
test passed. V2.6.1 is therefore governed as `CANONICAL_IMMUTABLE /
EPHEMERAL_MYSQL8_VALIDATED`; its observed Flyway checksum is `762611150`.
This is ephemeral acceptance evidence, not production deployment evidence.

Sprint 2-3.7-WF4.2 adds candidate
`V2.6.7__create_workflow_task_claim.sql`. It introduces the Candidate Pool
Claim fact and append-only audit structures, explicit Task/Pool claim-state
constraints, composite Candidate Member ownership, idempotency keys, and
single-winner uniqueness/CAS support. The runtime reuses `workflow:approve`
only as the RBAC entry gate; frozen Candidate membership, current personnel
eligibility, DataScope, and segregation-of-duties checks remain mandatory.
DIRECT and Legacy tasks remain non-claimable and are not backfilled. Release,
Transfer, Delegate, Timeout, executable ROLE/POSITION/ORG resolvers, and
Investment integration are not implemented. V2.6.7 is registered as
`CANDIDATE / NOT_EXECUTED`, with repository SHA-256
`800e1a5b1af67e1680a3f0233e9d5400c0b42f39349d181f1777c7ce04ac6aed`
and `flyway_checksum: null`. No real MySQL or production Migration execution
is claimed by this Sprint.

Sprint 2-3.7-WF4.2.1 executed V2.6.7 on two newly initialized, loopback-only
MySQL Community Server 8.4.9 instances with Flyway 13.0.0. Fresh and
V2.6.6-upgrade paths migrated successfully, passed strict validation, produced
no-op second migrations, shared Flyway checksum `1689998435`, and produced
identical complete, Workflow, and Claim schema fingerprints. The legal Claim
chain, application gates, idempotency, transaction rollback, DIRECT/Legacy
compatibility, post-Claim approval compatibility, and two-session single-winner
test passed without MySQL 1213. Promotion is blocked because MySQL accepted a
Claim whose `node_execution_id` did not exist, accepted cross-reference changes
to a SUCCESS Claim Audit row, and accepted an orphan SUCCESS Claim Audit with no
Claim. V2.6.7 therefore remains `CANDIDATE /
EPHEMERAL_MYSQL8_VALIDATION_FAILED`; repository SHA-256 remains
`800e1a5b1af67e1680a3f0233e9d5400c0b42f39349d181f1777c7ce04ac6aed`.
This failure evidence is not production deployment evidence. V2.6.7 SQL and
all historical migrations remain unchanged.

Sprint 2-3.7-WF3.9 originally added candidate
`V2.6.5__create_workflow_multi_resolver_binding.sql`. It creates the instance
resolver binding manifest, deduplicated exact resolver contracts, and immutable
node resolver rule snapshots. Legacy instances are not backfilled and retain
their explicit instance-binding compatibility path. ROLE/POSITION/ORG,
Candidate Pool persistence, Claim, and Investment integration remain disabled.
Before database acceptance, V2.6.5 was recorded as `CANDIDATE / NOT_EXECUTED`
with `flyway_checksum: null`; its repository SHA-256 was
`3d516374333f31554409d5d5c8f7ebaddf7ebd21dbc24916d3dc48c5b8337caf`.

Sprint 2-3.7-WF3.9.1 validated V2.6.5 on isolated loopback-only MySQL
Community Server 8.4.9 instances with Flyway 13.0.0. Fresh and V2.6.4
Upgrade paths applied successfully, passed strict validation, produced no-op
second migrations, and returned Flyway checksum `-588626998`. Full,
Workflow-only, and binding-only schema fingerprints matched across both paths.
All 25 CHECK constraints were ENFORCED; foreign-key, CHECK, unique-key,
Multi-Resolver A-to-B linkage, Legacy compatibility, rollback, and real
two-session single-winner tests passed. V2.6.5 is therefore governed as
`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`. This evidence is not a
production deployment record.

Sprint 2-3.7-WF4.1 adds candidate
`V2.6.6__create_workflow_candidate_pool.sql`. It adds explicit Task assignment
mode governance, immutable Candidate Pool and Candidate Member tables, and
controlled V2.6.5 CHECK replacements that preserve USER+DIRECT while reserving
ROLE/POSITION/ORG+CANDIDATE_POOL database shapes. The Static Resolver Registry
still exposes only EXPLICIT_USER_V1+USER+DIRECT, so production Candidate Pool
creation is unreachable. DIRECT and Legacy tasks are not backfilled. Claim,
Release, Transfer, Delegate, Timeout, Investment integration, and executable
ROLE/POSITION/ORG Resolvers are absent. It was initially registered as
`CANDIDATE / NOT_EXECUTED`, with repository SHA-256
`3b73653d571a5f5497d10865b3ef692e694387b8d220ec2af383bea9c82e5d4f`.

Sprint 2-3.7-WF4.1.1 validated V2.6.6 on two newly initialized, loopback-only
MySQL Community Server 8.4.9 instances with Flyway 13.0.0. Fresh and
V2.6.5-upgrade paths migrated successfully, passed strict validation, produced
no-op second migrations, and had identical full and Workflow schema
fingerprints. Candidate Pool/Member structure and data-chain checks, 22
negative constraints, DIRECT/Legacy no-backfill compatibility, atomic rollback,
and two-session single-winner concurrency all passed. V2.6.6 is therefore
`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`; its observed Flyway
checksum is `398007297`. This is ephemeral acceptance evidence, not production
deployment evidence, and does not enable Claim or ROLE/POSITION/ORG resolution.

Sprint 2-3.7-WF3.6.4.2 adds the candidate
`V2.6.4__fix_resolver_contract_hash_collation.sql`. It normalizes resolver
contract hashes accepted by V2.6.3 to canonical lowercase representation and
changes only `workflow_instance.resolver_contract_hash` to `VARCHAR(64)
CHARACTER SET ascii COLLATE ascii_bin`. V2.6.4 does not modify historical
Migration files, Resolver routing behavior, Investment, or enable
ROLE/POSITION/ORG. It starts as `CANDIDATE / NOT_EXECUTED`; its repository
SHA-256 is
`0bb017ea621980e150870bb9bac3dd48863afbc2b63b33654ab6344aced9bb55`.

Sprint 2-3.7-WF3.6.4 adds the candidate
`V2.6.3__freeze_workflow_instance_resolver_version.sql`. It adds only the
resolver code, version, and contract hash frozen on `workflow_instance`, and
backfills existing linear instances with the sole executable historical
contract `EXPLICIT_USER_V1`. It does not modify assignment snapshots, enable
ROLE/POSITION/ORG, or change Investment. V2.6.3 remains
`CANDIDATE / NOT_EXECUTED`; its repository SHA-256 is
`35c4714708d51ba00f4cdc79b04bea4d897a7e6c72624ec595c9df147f1f41e6`.
No real MySQL or production Migration execution is claimed by this Sprint.

Sprint 2-3.7-WF3.6.4.1 executed V2.6.3 on two newly initialized,
loopback-only MySQL Community Server 8.4.9 instances with Flyway 13.0.0.
Fresh and V2.6.2-upgrade paths migrated, strictly validated, produced no-op
second migrations, and had identical complete and Workflow-only schema
fingerprints. The linear-instance backfill and Legacy compatibility checks
passed. Promotion is blocked because `resolver_contract_hash` uses
`utf8mb4_general_ci`: its lowercase hexadecimal `REGEXP` accepted an uppercase
64-character hash. V2.6.3 therefore remains `CANDIDATE /
EPHEMERAL_MYSQL8_VALIDATION_FAILED`; the observed Flyway checksum is
`1481014267`. This failure is retained as acceptance history and is not
production deployment evidence.

Sprint 2-3.7-WF3.6.4.2 validated the final V2.6.4 candidate on isolated MySQL
Community Server 8.4.9 with Flyway 13.0.0. Fresh, V2.6.2-chain upgrade, and
failed-V2.6.3 repair paths all migrated successfully, passed strict validation,
produced no-op second migrations, and resulted in identical complete and
Workflow-only schema fingerprints. Lowercase SHA-256 was accepted; uppercase,
mixed-case, invalid-length, and non-hex values were rejected. V2.6.4 is
therefore `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`, with repository
SHA-256 `0bb017ea621980e150870bb9bac3dd48863afbc2b63b33654ab6344aced9bb55`
and Flyway checksum `-1847492777`. V2.6.3 is retained immutably and governed
only together with its V2.6.4 corrective successor; the earlier standalone
failure remains in the audit history.

Sprint 2-3.7-WF3.4 adds the candidate
`V2.6.2__create_workflow_task_assignment_snapshot.sql`. It creates immutable
assignment evidence for newly created linear USER tasks and adds only the task
ownership key needed by its composite foreign key. Existing task fields and
Legacy behavior remain available; historical assignments are not inferred or
backfilled. ROLE, POSITION, and ORG are domain extension types only and have no
resolver implementation. V2.6.2 is registered as `CANDIDATE / NOT_EXECUTED`;
its repository SHA-256 is
`9750454efa98402c7d22503d80e16579e07c964a01f7b4baa5da933db01a998f`.
No real MySQL or production Migration execution is claimed by this Sprint.

Sprint 2-3.7-WF3.4.1 validated V2.6.2 on two newly initialized, loopback-only
MySQL Community Server 8.4.9 instances with Flyway 13.0.0. Fresh and
V2.6.1-upgrade paths migrated successfully, passed strict validation, and
produced no-op second migrations. Their complete and Workflow-only normalized
schema fingerprints matched. Snapshot fields, indexes, the five-column task
ownership foreign key, eight CHECK constraints, and all six negative tests
passed. V2.6.2 is therefore governed as `CANONICAL_IMMUTABLE /
EPHEMERAL_MYSQL8_VALIDATED`; its observed Flyway checksum is `-1498011282`.
This is ephemeral acceptance evidence, not production deployment evidence.

Sprint 2-3.7-WF5.7 adds candidate
`V2.6.9__create_role_runtime_snapshot.sql`. It creates only the append-only
`role_runtime_binding_approval` governance fact and immutable
`workflow_role_runtime_binding_snapshot` evidence table. It reuses the V2.6.5
Resolver Binding chain and the existing Candidate Pool/Claim model; it does not
create a ROLE Task or Candidate Pool, call a Role Directory, enable
`ROLE_DIRECTORY_V1`, add a Controller, or modify Investment. The candidate is
registered as `CANDIDATE / NOT_EXECUTED`, with repository SHA-256
`fbe41e2a354e616ee4b369c57980009057c75ced46a2eb9e48d1eb8354beb0d2`
and `flyway_checksum: null`. Fresh and V2.6.8 Upgrade MySQL/Flyway execution is
reserved for a separate acceptance Sprint; repository presence is not database
execution evidence.

Sprint 2-3.7-WF5.7.1 executed the unchanged V2.6.9 candidate on two isolated,
loopback-only MySQL Community Server 8.4.9 instances with Flyway 13.0.0.
Fresh and V2.6.8 Upgrade paths migrated successfully, passed strict validate,
produced no-op second migrations, and had identical full, Workflow, and ROLE
Runtime schema fingerprints. The observed Flyway checksum is `1523438049`.
Promotion is blocked: MySQL accepted a blank Resolver Version, UPDATE and
DELETE of an immutable Runtime Snapshot, an in-place Approval UPDATE, and a
Snapshot linked to a PENDING Approval. Seven of twelve negative tests passed.
V2.6.9 therefore remains `CANDIDATE /
EPHEMERAL_MYSQL8_VALIDATION_FAILED`; ROLE Runtime remains disabled. No
production, managed, unknown, or pre-existing database was connected.

Sprint 2-3.7-WF5.7.2 adds forward-only candidate
`V2.6.10__harden_role_runtime_persistence_integrity.sql`. It leaves V2.6.9
and all historical Migration files unchanged. Before permanent DDL it uses a
temporary CHECK guard to reject blank Resolver identity, mutation evidence,
non-APPROVED Snapshot ownership, and illegal Approval state without repairing
data. It adds nonblank Resolver checks, append-only Approval/Snapshot triggers,
an APPROVED Approval ownership and exact Resolver contract INSERT trigger, and
Snapshot hash checks. It does not enable ROLE Runtime, create ROLE Tasks or
Candidate Pools, call a Role Directory, expose a Controller, or modify
Investment. It starts as `CANDIDATE / NOT_EXECUTED`, with SHA-256
`2fad3f67a4a487568f5fc29f40cec73894d89fff65564212a75be98da186f7d6`
and `flyway_checksum: null`. Real MySQL/Flyway acceptance is reserved for a
separate Sprint.

Sprint 2-3.7-WF5.7.3 executed the unchanged V2.6.9 and V2.6.10 candidates on
isolated, loopback-only MySQL Community Server 8.4.9 instances with Flyway
13.0.0. Fresh, V2.6.8 Upgrade, and V2.6.9 Forward Fix paths migrated once,
passed strict validation, produced no-op second migrations, and converged to
identical full, Workflow, and ROLE Runtime schema fingerprints. All 15
post-migration integrity tests passed. Promotion is nevertheless blocked:
V2.6.10's pre-DDL guard accepted both an existing Approval/Snapshot contract
mismatch and an invalid Snapshot binding hash, then created six permanent
constraints/triggers. V2.6.9 remains `CANDIDATE /
EPHEMERAL_MYSQL8_VALIDATION_FAILED`; V2.6.10 is `CANDIDATE /
EPHEMERAL_MYSQL8_VALIDATION_FAILED`, with observed Flyway checksum
`936550308`. ROLE Runtime remains disabled. This is ephemeral failure evidence,
not production deployment evidence.

Sprint 2-3.7-WF5.7.3 Historical Evidence Integrity Forward Fix adds candidate
`V2.6.11__repair_role_runtime_historical_integrity_guard.sql`. Before creating
any permanent object it rejects blank or malformed Resolver identity, invalid
lowercase SHA-256 evidence, missing/non-approved Approval ownership, exact
Approval/Snapshot Resolver contract drift, and a stored binding hash that does
not equal the database recomputation of
`ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1`. It never deletes, repairs, fills, or
infers historical evidence. After a clean guard it adds Approval DELETE
protection and canonical Snapshot INSERT protection; existing V2.6.10 UPDATE
and Snapshot DELETE protection remains unchanged. V2.6.11 starts as
`CANDIDATE / NOT_EXECUTED`, with SHA-256
`ee16801af2f989449b1372ff82d76bd9850950a51ea7badff0125804c0815b06`
and `flyway_checksum: null`. V2.6.9 and V2.6.10 await a new combined isolated
MySQL validation, and ROLE Runtime remains disabled.

Sprint 2-3.7-WF5.7.4 executed the unchanged V2.6.9, V2.6.10, and V2.6.11
chain on isolated MySQL Community Server 8.4.9 with Flyway 13.0.0. Fresh,
V2.6.8 Upgrade, and V2.6.10 Forward Fix paths migrated successfully, passed
strict validation and no-op reruns, and produced identical full, Workflow, and
ROLE Runtime schema fingerprints. Contract mismatch, invalid Binding Hash,
Canonical drift, append-only Trigger tests, the legal evidence chain, and a
two-session Snapshot single-winner test passed. Promotion is blocked because
the V2.6.11 Resolver Version `REGEXP` inherited `utf8mb4_general_ci` and
accepted lowercase historical evidence; V2.6.11 completed and created both
permanent triggers instead of failing before DDL. V2.6.11 therefore remains
`CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`, with Flyway checksum
`1342469954`. V2.6.9 and V2.6.10 also remain candidates, and ROLE Runtime
remains disabled.

Sprint 2-3.7-WF5.7.5 adds forward-only candidate
`V2.6.12__fix_role_runtime_resolver_version_collation.sql`. Its pre-DDL
temporary guard uses `REGEXP_LIKE(..., 'c')` and binary trim comparison to
reject null, blank, padded, lowercase, mixed-case, non-ASCII, or malformed
historical Resolver Versions without repairing data. After a clean guard, both
ROLE Runtime `resolver_version` columns are changed from inherited
`utf8mb4_general_ci` to explicit `VARCHAR(64) CHARACTER SET ascii COLLATE
ascii_bin NOT NULL`, and their existing Resolver identity CHECK names are
recreated with case-sensitive format validation. Existing append-only and
canonical-hash triggers remain intact. The candidate starts as `CANDIDATE /
NOT_EXECUTED`, with repository SHA-256
`55513b47c278d2e7d8f5d7656115b1383c3c50494bd221d95f58c66f972ec67e`
and `flyway_checksum: null`. ROLE Runtime remains disabled; real isolated
MySQL/Flyway acceptance is a separate Sprint.

Sprint 2-3.7-WF5.7.6 validated the unchanged V2.6.9 through V2.6.12 chain
on four newly initialized, loopback-only MySQL Community Server 8.4.9
instances with Flyway 13.0.0. Fresh, V2.6.8 Upgrade, and V2.6.11 Forward
Fix paths migrated successfully, passed strict validate, produced no-op second
migrations, and converged to identical full, Workflow, and ROLE Runtime schema
fingerprints. Failed Guard Recovery rejected lowercase Resolver Version plus
contract and Binding Hash drift with MySQL 3819 before any V2.6.12 target DDL
changed. Both Resolver Version columns are now explicit `ASCII/ascii_bin`;
uppercase input passed while lowercase, mixed case, blank, and NULL inputs were
rejected. Approval/Snapshot UPDATE and DELETE remained blocked with MySQL 1644,
Canonical Hash sensitivity and concurrent reads passed, and all 34 governed
Migration hashes matched. V2.6.9—V2.6.12 are therefore promoted under their
respective forward-validation statuses. ROLE Runtime remains disabled; this is
ephemeral acceptance evidence, not production deployment evidence.

Sprint 2-3.7-WF5.10 adds candidate
`V2.6.13__create_role_runtime_activation_evidence.sql`. It creates append-only
Activation Request, RACI Approval Decision, and hash-only Evidence tables with
pre-DDL prerequisite/partial-install guards, stable business keys, lower-case
SHA-256 and state CHECK constraints, foreign keys, uniqueness governance, and
UPDATE/DELETE rejection triggers. It deliberately stores no directory member,
organization member, candidate member, task, claim, or approval execution
result. The candidate is `CANDIDATE / NOT_EXECUTED`, SHA-256
`7ec6ec6441006dc7f114b92adfc60efdfb2cc8f66bda3a6a7a52d9fcbf3168e6`,
with `flyway_checksum: null`. The prepared Fresh/Upgrade script has not been
run; ROLE Runtime and `ROLE_DIRECTORY_V1` remain disabled/non-executable.

Sprint 2-3.7-WF5.10.1 attempted real isolated MySQL/Flyway acceptance of the
unchanged V2.6.13 candidate. Fresh, V2.6.12 Upgrade, and V2.6.8 Forward paths
all failed deterministically with MySQL 8.4.9 error 6125 while creating
`fk_role_activation_approval_request`: the child references `activation_id`,
but the parent only declares `(activation_id, delete_token)` as unique. MySQL
non-transactional DDL left `role_runtime_activation_request` plus a failed
Flyway history row in each disposable schema. Strict validate, no-op, final
schema fingerprints, Trigger/transaction/concurrency acceptance and promotion
therefore did not qualify. Repository SHA-256 remained
`7ec6ec6441006dc7f114b92adfc60efdfb2cc8f66bda3a6a7a52d9fcbf3168e6`;
observed Flyway checksum was `1627294290`. V2.6.13 remains `CANDIDATE /
EPHEMERAL_MYSQL8_VALIDATION_FAILED`; ROLE Runtime remains disabled.

Sprint 2-3.7-WF5.10.2 assessed a proposed V2.6.14 forward fix and blocked its
creation. Unlike earlier forward-fix cases where the lower migration completed
and only a post-migration integrity test failed, V2.6.13 itself has a failed
Flyway history row and stops the version chain before any V2.6.14 can execute.
On a clean schema it always fails at MySQL 6125; on the failed disposable
schema it also leaves a partial Request table. `repair`, history fabrication,
`ignoreMigrationPatterns`, out-of-order execution, or baselining the failed
version would hide rather than repair this state and are prohibited. The safe
resolution is to authorize correction of the never-promoted V2.6.13 candidate,
record its new SHA-256, and restart Fresh/Upgrade/Forward acceptance from clean
isolated schemas. No V2.6.14 asset was created; ROLE Runtime remains disabled.

Sprint 2-3.7-WF5.10.3 archives the byte-identical failed V2.6.13 candidate
outside the Flyway scan root as `FAILED_CANDIDATE_ARCHIVED`, SHA-256
`7ec6ec6441006dc7f114b92adfc60efdfb2cc8f66bda3a6a7a52d9fcbf3168e6`.
The active, never-promoted candidate was redesigned under explicit authority.
Approval and Evidence reference Request through `(activation_id, delete_token)`;
Evidence also references the exact Approval through
`(approval_id, activation_id, delete_token)`. Both child objects freeze
Activation, Contract, and Binding hashes, with INSERT guards enforcing the
Request-to-Approval-to-Evidence chain. Fresh, V2.6.12 Upgrade, and V2.6.8
Forward prevalidation on isolated MySQL 8.4.9 produced identical schema
fingerprints and passed strict validate/no-op. Guard failure created no new
target object; FK, hash, append-only, rollback, and corrected two-session
single-winner checks passed. New repository SHA-256 is
`da80b7d3483ba3f3ee6b4f938515e94730695e28fc9842b9e488eb8daa68fd17`,
observed Flyway checksum `1882937893`. The asset is
`CANDIDATE_READY_FOR_VALIDATION`, not canonical. ROLE Runtime remains disabled.

Sprint 2-3.7-WF5.10.4 performed final acceptance of the unchanged redesigned
V2.6.13 candidate on newly initialized, loopback-only MySQL Community Server
8.4.9 instances with Flyway 13.0.0. Fresh, V2.6.12 Upgrade, and V2.6.8 Forward
paths migrated successfully, passed strict validate, and produced no-op second
migrations. Full, Workflow, and ROLE Runtime schema fingerprints were identical.
Three tables, sixty columns, sixteen indexes, three FKs, seventeen CHECKs, and
eight triggers were verified. Guard failure added no new target table or trigger;
ownership, hash drift, invalid state/token, append-only, transaction rollback,
and two-session single-winner checks all passed. Repository SHA-256 remained
`da80b7d3483ba3f3ee6b4f938515e94730695e28fc9842b9e488eb8daa68fd17`;
Flyway checksum remained `1882937893`; all 35 governed hashes matched.
V2.6.13 is promoted to `CANONICAL_IMMUTABLE /
EPHEMERAL_MYSQL8_VALIDATED`. The archived failed candidate and its report are
retained as historical evidence. ROLE Runtime remains disabled.

Sprint 2-3.7-WF5.14 adds candidate
`V2.6.14__create_role_runtime_binding_persistence_foundation.sql`. It creates
three append-only, pre-runtime governance objects:
`workflow_role_binding_promotion`,
`workflow_role_binding_candidate_snapshot`, and
`workflow_role_binding_snapshot_event`. A pre-DDL Guard verifies the immutable
V2.6.13 parent contract and rejects partial target installations. Composite
foreign keys preserve Activation-to-Promotion-to-Snapshot-to-Event ownership;
case-sensitive Resolver identities, lower-case SHA-256 fields, CHECKs, INSERT
evidence guards, and UPDATE/DELETE rejection triggers fail closed. The asset
does not create or mutate Workflow instances, tasks, candidate pools or claims.
It starts as `CANDIDATE / NOT_EXECUTED`, SHA-256
`bcbcbb6b9f7e58b5b03297621682f88c73744e2893058d0519198203e6150b15`,
with `flyway_checksum: null`. Real isolated MySQL/Flyway acceptance is deferred
to the next Sprint; `ROLE_DIRECTORY_V1` remains PREPARED and ROLE Runtime
remains disabled.

Sprint 2-3.7-WF5.14.1 executed real isolated MySQL/Flyway acceptance of the
unchanged V2.6.14 candidate. Fresh and V2.6.13 Upgrade migrated successfully,
passed strict validate and no-op, and converged to identical Full, Workflow,
and ROLE Binding Persistence fingerprints. Metadata, append-only triggers,
ownership, Hash/Collation, transaction rollback, and three two-session
single-winner scenarios passed. A blocking Guard case nevertheless failed:
V2.6.14 accepted a syntactically valid `PERSISTED` Activation Request with no
Approval or Evidence rows, then created all three target tables. This violates
the mandatory pre-DDL evidence-completeness and zero-target-DDL requirements.
Flyway checksum is `1106609314`; all 36 governed SHA-256 entries still match.
V2.6.14 remains `CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED` and must not
be promoted. All disposable instances and data directories were removed. ROLE
Runtime remains disabled.

Sprint 2-3.7-WF5.14.2 hardens the unpromoted V2.6.14 candidate without adding
a new version. The failed source asset is retained outside the Flyway scan root
as `ARCHIVED_FAILED_CANDIDATE`; its observed SHA-256
`bcbcbb6b9f7e58b5b03297621682f88c73744e2893058d0519198203e6150b15`
and Flyway checksum `1106609314` remain historical evidence. The revised Guard
freezes `PERSISTED` to mean a complete Request, three distinct approved RACI
decisions and five distinct typed evidence records, then validates ownership
and hash consistency in four phases before any permanent DDL. The three-table
DDL suffix is unchanged. The revised candidate SHA-256 is
`a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442`;
`flyway_checksum` remains `null` pending isolated real MySQL/Flyway
revalidation. Asset state is `CANDIDATE_READY_FOR_REVALIDATION / NOT_EXECUTED`.
ROLE Runtime remains disabled.

Sprint 2-3.7-WF5.14.3 completed isolated real MySQL/Flyway revalidation of the
guard-hardened V2.6.14 asset. MySQL 8.4.9 and Flyway 13.0.0 Fresh and V2.6.13
Upgrade paths passed strict validate and second-migrate no-op, with Flyway
checksum `-1734980808`. Twelve real Guard failures (the required eleven plus
the frozen Resolver identity case) were rejected before permanent DDL; every
case retained identical target-object signatures and zero Table/Index/FK/CHECK/
Trigger counts. A complete Request + three APPROVE + five Evidence chain was
accepted. Fresh and Upgrade fingerprints converged, strong ownership,
append-only, case-sensitive Hash/Resolver checks, three two-session single-
winner cases and transaction rollback passed. SHA-256 remained
`a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442` and
all 36 governed hashes matched. V2.6.14 is promoted to
`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`. ROLE Runtime remains
disabled.

Sprint 2-3.7-WF5.18 adds candidate
`V2.6.15__create_role_runtime_execution_admission_persistence.sql`. It extends
only future ROLE binding candidates with nullable Directory fence and
Definition/Node ownership evidence; historical rows remain NULL without
inference or backfill. It creates an immutable Admission decision table,
append-only validator/capability Evidence and lifecycle Event tables, plus a
technical CAS Slot for one current approved Admission per source candidate.
Composite ownership FKs and insert guards retain the Activation -> Promotion
-> Candidate -> Admission chain. The candidate does not enable ROLE runtime or
create instances, tasks, candidate pools, claims, controllers, or Investment
data. SHA-256 is
`405fb7fc3bc928fc84643bcbf5583cd9f9555dad8209ad6b4d962529ce969887`;
status is `CANDIDATE / NOT_EXECUTED`, `flyway_checksum: null`. Real isolated
MySQL/Flyway validation is deliberately deferred.

Sprint 2-3.7-WF5.18.1 performed isolated real MySQL 8.4.9/Flyway 13.0.0
validation of the unchanged V2.6.15 candidate. Fresh and V2.6.14 Upgrade both
failed with MySQL error 6125 while creating
`fk_role_admission_slot_candidate`: the referenced candidate columns do not
have a matching unique key. Flyway recorded checksum `1424227542` and a failed
history row. Because MySQL DDL implicitly commits, both failed paths retained
the ten Candidate extension columns and the Admission, Evidence and Event
tables, while Slot and all admission triggers were absent. The partial-install
Guard case itself failed before V2.6.15 permanent DDL and left zero additional
objects. SHA-256 remained
`405fb7fc3bc928fc84643bcbf5583cd9f9555dad8209ad6b4d962529ce969887` and
all 37 governed hashes matched. V2.6.15 remains `CANDIDATE /
EPHEMERAL_MYSQL8_VALIDATION_FAILED`; no repair, baseline bypass or historical
Migration change was performed. ROLE Runtime remains disabled.

Sprint 2-3.7-WF5.18.2 archives that failed, unpromoted V2.6.15 candidate as
`database/migration/archive/failed-candidates/V2.6.15__create_role_runtime_execution_admission_persistence__failed_405fb7fc.sql`.
The archived audit record retains source SHA-256
`405fb7fc3bc928fc84643bcbf5583cd9f9555dad8209ad6b4d962529ce969887`,
observed Flyway checksum `1424227542`, the failed validation report and the
MySQL 6125 cause. The still-unpromoted V2.6.15 candidate was redesigned in
place; no V2.6.16 was created. A dedicated parent key
`uk_role_binding_snapshot_slot_owner (id,snapshot_id,delete_token)` now
exactly matches the Slot candidate FK. Slot DDL runs before Evidence/Event,
all 11 ownership FKs are added only after an executable parent PK/UNIQUE proof,
and pre-DDL partial-install detection covers candidate columns, target tables,
indexes, constraints and triggers. Admission/Evidence/Event remain
insert/append-only, Slot remains only a mutable CAS pointer, and legacy
Candidate rows are not backfilled. Revised SHA-256 is
`7887bfb1d5bb9aacba378bc62618ba92fdd58fb1a85007053da4899d1fe9d412`;
status is `CANDIDATE_READY_FOR_REVALIDATION / NOT_EXECUTED` with
`flyway_checksum: null`. Real isolated MySQL/Flyway revalidation remains a
separate Sprint. ROLE Runtime remains disabled.

Sprint 2-3.7-WF5.18.3 revalidated the repaired V2.6.15 candidate on isolated
loopback-only MySQL 8.4.9 instances with Flyway 13.0.0. Fresh and V2.6.14
Upgrade migrations, strict validate, second-migrate no-op, all 11 ownership
FKs, the dedicated Slot parent unique key, equal Fresh/Upgrade fingerprints,
the 10/10 partial-install Fail Closed matrix, nine append-only/CAS triggers,
28/28 Evidence persistence, frozen ownership checks, hash checks, concurrent
single-winner constraints and transaction rollback all passed. Observed
Flyway checksum is `-1956070127`; SHA-256 remained
`7887bfb1d5bb9aacba378bc62618ba92fdd58fb1a85007053da4899d1fe9d412`
and all 37 governed hashes matched.

The candidate was not promoted. Direct MySQL testing proved that an Admission
with zero Evidence could still append an `APPROVED_FOR_EXECUTION` event, and
that a complete Evidence set containing `NOT_READY` capability evidence could
also reach that final state. The mutable Slot also accepted direct DELETE
inside a rolled-back probe instead of enforcing CAS-only mutation. V2.6.15 is
therefore `CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`;
`flyway_checksum` remains null while the observed checksum is retained as
failure evidence. No V2.6.16, repair, baseline bypass, runtime enablement or
business-code change was performed. ROLE Runtime remains disabled.

Sprint 2-3.7-WF5.18.5 archives that second failed, unpromoted candidate byte
for byte as
`database/migration/archive/failed-candidates/V2.6.15__create_role_runtime_execution_admission_persistence__failed_7887bfb1.sql`.
The archive preserves SHA-256
`7887bfb1d5bb9aacba378bc62618ba92fdd58fb1a85007053da4899d1fe9d412`
and observed Flyway checksum `-1956070127`. The same V2.6.15 candidate version
is repaired in place because it was never promoted or released; no V2.6.16 is
created. The repaired candidate freezes the exact 28-validator contract and
eight required capabilities, rejects non-PASS/non-READY final approval,
recomputes the capability aggregate root from actual Evidence, binds Event
root and persistence reference, protects Slot mutation with token/version CAS
and a DELETE trigger, and enforces separate Decision/Closure terminal
uniqueness. The Java canonical uses the same fixed vector. Revised SHA-256 is
`db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44`;
status is `CANDIDATE_READY_FOR_REVALIDATION / NOT_EXECUTED` with
`flyway_checksum: null`. Real MySQL/Flyway revalidation is a separate Sprint;
ROLE Runtime remains disabled.

Sprint 2-3.7-WF5.18.6 completed isolated real MySQL 8.4.9/Flyway 13.0.0
revalidation of the unchanged final V2.6.15 candidate. Fresh and V2.6.14
Upgrade both migrated successfully, strict validate passed, second migrate was
no-op, and Full/Workflow/Admission fingerprints were identical. The observed
Flyway checksum is `538981271`; SHA-256 remains
`db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44`, with
37/37 governed hashes matching. Real MySQL rejected 0/28 and 27/28 Evidence,
one failed validator, every required Capability non-READY variant, wrong
Capability Root/Persistence references, Slot DELETE/non-CAS mutation and
terminal conflicts. The fixed Java/MySQL root vector matched, legal
approval/revoke/release succeeded, and double-session final approval, Slot CAS
and Evidence uniqueness each produced one winner without 1213/1205. Transaction
failure probes rolled back Admission/Evidence/Event/Slot state. V2.6.15 is
promoted to `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`; ROLE Runtime
remains disabled and no V2.6.16 was created.

Sprint 2-3.7-WF5.23 adds the unexecuted V2.6.16 candidate for immutable ROLE
realtime eligibility evidence. It freezes 27 ordered validator results and 10
deduplicated capability results, validates validator/capability/persistence
hashes, records an append-only lifecycle and binds one eligible evidence set
to at most one Claim. Legacy/USER/DIRECT rows retain nullable evidence columns.
The first candidate was archived after real MySQL exposed an unordered
`MAX(event_hash)` lifecycle lookup. The repaired candidate selects the previous
event by descending sequence number. SHA-256 is
`d5d0b154eaea4c17dc8b11d7bebda0627c9f31fd96b13043545356a5a2286084`;
The repaired asset passed clean isolated MySQL 8.4.9/Flyway 13.0.0 Fresh and
V2.6.15 Upgrade paths, strict validate, second-migrate no-op, equal Full /
Workflow / realtime-evidence fingerprints, a 15/15 negative matrix, legal
Evidence -> Claim -> Audit linkage, transaction rollback and concurrent
single-winner probes. Flyway checksum is `-1570425240`; status is
`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`. All 38 governed hashes and
530 application tests passed. ROLE Runtime remains disabled and external
Directory availability is not asserted.

Sprint ORG-DIR-1 adds V2.6.17 for the independent Organization/Governance
Approval Role Directory V1. It creates `approval_role`, effective-dated
`approval_role_assignment`, the CAS-serialized `approval_role_revision_head`,
and append-only `approval_role_revision`. Isolated MySQL 8.4.9/Flyway 13.0.0
Fresh and V2.6.16 Upgrade paths passed strict validate, second-migrate no-op,
matching Full/Organization/Directory fingerprints, negative constraints,
transaction rollback, historical half-open boundaries and two-session
revision/assignment concurrency without 1213/1205. SHA-256 is
`d5fdf54be071a1f4c347b61a447771bb0c849f2ff8d4113e9bedc7ae4d145b65` and
Flyway checksum is `-1602960257`; status is `CANONICAL_IMMUTABLE /
EPHEMERAL_MYSQL8_VALIDATED`. This does not activate Workflow ROLE runtime or
clear the external Directory connectivity blocker.

Sprint 2-3.7-WF-CAP-FINAL adds V2.6.18 for the minimum shared, versioned and
append-only ROLE runtime governance control store plus reliable external Claim
audit outbox/receipt. Isolated MySQL 8.4.9/Flyway 13.0.0 Fresh and V2.6.17
Upgrade paths passed strict validate, second-migrate no-op, identical Full /
Workflow / Capability fingerprints, append-only trigger checks, unique/hash
constraints and orphan-FK rejection. SHA-256 is
`feca0f252d44a61938552ed37764aecc2d0cf78aa9e35d81b71d5bf73d1d3542`
and Flyway checksum is `1856348077`; status is `CANONICAL_IMMUTABLE /
EPHEMERAL_MYSQL8_VALIDATED`. The migration does not activate ROLE_DIRECTORY_V1
or ROLE Runtime and does not change Investment or historical runtime assets.

Sprint WF-AUDIT-FINAL adds V2.6.19 for the independent Governance External
Audit Sink event/receipt ledger, immutable Workflow event payload envelope and
explicit ROLE Claim-to-admission reference. Isolated MySQL 8.4.9/Flyway 13.0.0
Fresh and V2.6.18 Upgrade paths passed strict validate, second-migrate no-op,
matching Full/Audit fingerprints, FK/JSON/hash/unique negative tests and six
append-only triggers. SHA-256 is
`5bcc02c3447af2e6a8af5b258d24e4a1148b333352bc39728623b3d4da4d5f6a`
and Flyway checksum is `1332327310`; status is `CANONICAL_IMMUTABLE /
EPHEMERAL_MYSQL8_VALIDATED`. A separate TLS 1.3 TEST service run validated
service authentication, contract handshake, idempotent receive, stable receipt,
outbox delivery and 10/25/50 single-winner concurrency. The same forward asset
also admits versioned Platform SoD controls without changing V2.6.18. ROLE Runtime remains
disabled and no production endpoint or credential was used.

The Workflow V1 release-freeze P0 closure adds V2.6.20 for the Approval Role
Directory Provider audit ledger. The production provider is now wired only to
a MySQL-backed adapter; each physical request writes one PII-minimized SUCCESS,
REJECTED, or FAILED event before a trusted result can return. Database triggers
reject UPDATE/DELETE. Isolated MySQL 8.4.9/Flyway 13.0.0 Fresh and V2.6.19
Upgrade paths passed strict validate, second-migrate no-op, identical schema
fingerprints, negative constraints, restart recovery, and 10/25/50 concurrent
inserts. A real TLS 1.3 TEST Provider run persisted 161 events (156 SUCCESS,
5 REJECTED, 0 FAILED), including 10/25/50 concurrent Directory requests, with
no missing or malformed hash evidence. SHA-256 is
`160f0b649ac85cc177d82c2ff0aa5070639b29c7f2dda72b02438041bd063201`
and Flyway checksum is `1721966129`; status is `CANONICAL_IMMUTABLE /
EPHEMERAL_MYSQL8_VALIDATED`. ROLE Runtime and Canary remain disabled.

Workflow V1 RC2-S1 adds V2.6.21 for Version-level
ROLE resolver configuration and its immutable publication manifest. It creates
`workflow_version_node_resolver_binding` and
`workflow_version_resolver_binding_manifest`, and adds backward-compatible
`LEGACY_USER_ONLY` binding snapshots to `workflow_version` and
`workflow_version_release`. Existing Versions and Releases are not rehashed or
backfilled with ROLE bindings. Composite Workflow-owned foreign keys prevent
cross-Definition and cross-Version node ownership; no physical Approval Role
Directory foreign key is introduced. Nine triggers make Binding rows writable
only while their Version is DRAFT and make Manifest/Release evidence
append-only. SHA-256 is
`cb9555ba5f81c292654286fe8c7396cdead4b71c9387c4b73d09139d4147296e`;
Flyway checksum is `-698735620`. Isolated MySQL 8.4.9/Flyway 13.0.0 Fresh and
V2.6.20 Upgrade paths passed strict validate, second-migrate no-op, identical
Schema fingerprints, ownership/CHECK/UNIQUE negative tests, DRAFT and
PUBLISHED/RETIRED immutability triggers, Legacy compatibility, and the legal
Manifest-before-Publish transaction sequence. Status is `CANONICAL_IMMUTABLE /
EPHEMERAL_MYSQL8_VALIDATED`. ROLE Runtime and Canary remain disabled, and the
RC1 validation assets retain their 2.6.20 baseline.

Workflow V1 RC2-S5/S5.1 adds and validates V2.6.22 for freezing a
published Version ROLE resolver contract into the existing Instance Resolver
Binding Set and Node Resolver Binding Snapshot structures. It adds stable
Version Binding provenance, supports multiple bindings per node, preserves
Legacy USER + DIRECT rows, allows only ROLE + ROLE + CANDIDATE_POOL with
FIXED_ORG for the new evidence, and makes Node snapshots append-only. SHA-256
is `658b4939a7e92295527ef3a5116d20814b0d17e4c34e46f3955e4c21c67dcc05`;
Flyway checksum is `410640379`. Isolated MySQL 8.4.9/Flyway 13.0.0 Fresh and
V2.6.21 Upgrade paths passed strict validate, second-migrate no-op, identical
Schema fingerprint
`bd4a35a7471e0e880c2c30640cdfa18014a43d48dbe1e65c266330b25da17d18`,
the complete ROLE/USER and FIXED_ORG negative matrices, multi-Binding order
uniqueness, append-only triggers, and Legacy USER compatibility. Status is
`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`. Directory revision,
candidates, tasks, claims, ROLE Runtime and Canary remain outside this
Migration; the persistent RC1 TEST database was not touched.

Workflow V1 RC2-S7 adds V2.6.23 as a narrow mapping-contract forward fix. It
adds `remark VARCHAR(500) NULL` after `delete_token` to exactly the three
Realtime Eligibility capability, event and validator evidence tables so their
schema matches the inherited `WorkflowAuditedEntity` contract. Isolated MySQL
8.4.9/Flyway 13.0.0 Fresh and V2.6.22 Upgrade paths passed strict validate,
second-migrate no-op and the identical schema fingerprint
`ffcd3b9c031c6a8521fe4cbdb1d3c248e49c50e2ccfa4860ce9aea04b5caacd0`.
Real MyBatis Mapper inserts and readback passed for both NULL and non-NULL
remarks. SHA-256 is
`874427e1df075042485c9098ad2f1b79dbc632200f01614f85529b30765c169d`
and Flyway checksum is `445023774`; status is `CANONICAL_IMMUTABLE /
EPHEMERAL_MYSQL8_VALIDATED`. No runtime logic was changed, ROLE Runtime and
Canary remain disabled, and the persistent RC1 TEST database was not touched.
