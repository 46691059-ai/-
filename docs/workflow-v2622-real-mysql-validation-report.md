# Workflow V1 RC2-S5.1 V2.6.22 Real MySQL Validation Report

## 1. Final conclusion

`RC2_S5_1=PASS`.

V2.6.22 passed isolated MySQL 8.4.9 and Flyway 13.0.0 validation on both a
Fresh authoritative Baseline path and an Upgrade path from V2.6.21. The
Migration is promoted to `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`.
No managed, production, unknown, or persistent RC1 TEST database was used.

ROLE Runtime remains `DISABLED`, Canary remains
`NOT_AUTHORIZED_NOT_ENABLED`, and the Kill Switch remains
`STOP_NEW_AND_CLAIM`.

## 2. Environment and isolation

| Item | Value |
| --- | --- |
| MySQL | MySQL Community Server 8.4.9 |
| Flyway | 13.0.0 |
| Baseline | 17 authoritative SQL assets, explicit Flyway baseline 2.0.0 |
| Fresh port | 38231 |
| Upgrade port | 38232 |
| Successful evidence root | `D:\codex-rc2-v2622-20260824-095737\evidence` |
| Persistent RC1 port | 34061, explicitly rejected by preflight |
| Persistent RC1 datadir | `D:\mysql-rc1\data`, not accessed |

Both temporary servers verified their own port and absolute datadir before
use. They were stopped after validation, their disposable datadirs were
removed, and logs/evidence were retained outside the repository.

An earlier run at `D:\codex-rc2-v2622-20260824-095356\evidence` completed the
database checks but reported FAIL because the new validation script counted
metadata headers and assumed a generated column would report `IS_NULLABLE=NO`.
The script assertion was corrected without changing V2.6.22 or any Migration;
the failed run remains retained as validation-tool evidence.

## 3. Migration integrity

| Asset | SHA-256 | Flyway checksum | Result |
| --- | --- | ---: | --- |
| V2.6.21 | `cb9555ba5f81c292654286fe8c7396cdead4b71c9387c4b73d09139d4147296e` | `-698735620` | unchanged |
| V2.6.22 | `658b4939a7e92295527ef3a5116d20814b0d17e4c34e46f3955e4c21c67dcc05` | `410640379` | unchanged and validated |

The governed Migration manifest matched all 44 canonical Migration files.
No historical Migration SHA changed.

## 4. Fresh validation

The Fresh path initialized a new MySQL datadir, loaded the 17 Baseline assets,
ran explicit Flyway baseline 2.0.0, migrated through V2.6.22, performed strict
validate, and executed a second migrate.

| Check | Result |
| --- | --- |
| Latest successful Migration | 2.6.22 |
| Failed Migration rows | 0 |
| V2.6.22 execution count | 1 |
| Strict validate | PASS |
| Second migrate | NO_OP |
| Successful history rows including baseline | 45 |
| Schema fingerprint | `bd4a35a7471e0e880c2c30640cdfa18014a43d48dbe1e65c266330b25da17d18` |

## 5. Upgrade validation

The Upgrade path independently loaded the same Baseline, migrated only through
V2.6.21, validated the source, created a Legacy publication fixture, then
migrated V2.6.22 exactly once.

| Check | Result |
| --- | --- |
| Source version | 2.6.21 |
| Target version | 2.6.22 |
| V2.6.22 execution count | 1 |
| Failed Migration rows | 0 |
| Strict validate | PASS |
| Second migrate | NO_OP |
| Schema fingerprint | `bd4a35a7471e0e880c2c30640cdfa18014a43d48dbe1e65c266330b25da17d18` |

Fresh and Upgrade fingerprints are byte-identical.

## 6. Schema changes verified

`ALTERED_TABLES=workflow_instance_resolver_binding_set,workflow_node_resolver_binding_snapshot`

`ADDED_COLUMNS=10`

- `version_binding_id BIGINT NULL`
- `version_binding_order INT NULL`
- `version_binding_slot INT STORED GENERATED`
- `version_binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL`
- `resolver_contract_hash_snapshot VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL`
- `role_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL`
- `organization_scope_type VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL`
- `resolved_organization_id BIGINT NULL`
- `effective_time_policy VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL`
- `binding_schema_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL`

`MODIFIED_COLUMNS=workflow_instance_resolver_binding_set.manifest_version VARCHAR(64) NOT NULL`

`ADDED_INDEXES=3`

- unique node binding order per active instance/node/order;
- unique Version Binding source per active instance;
- ROLE scope lookup index.

`REMOVED_INDEXES=uk_workflow_node_resolver_binding_node`

`ADDED_CONSTRAINTS=2`

- composite foreign key to the exact Version/Node Binding source;
- allow-listed Legacy USER, historical ROLE, and complete new ROLE evidence
  combinations.

`REPLACED_TRIGGERS=2`

- `trg_workflow_node_resolver_snapshot_no_update`;
- `trg_workflow_node_resolver_snapshot_no_delete`.

The S5 Java entity mapping names, types, nullable evidence fields, ASCII binary
hash/code collations, and generated ordering slot match the real schema.

## 7. Combination and organization matrices

| Case | Expected | Result |
| --- | --- | --- |
| USER + USER + DIRECT | accept | PASS |
| ROLE + ROLE + CANDIDATE_POOL | accept | PASS |
| ROLE + USER | reject | PASS |
| USER + ROLE | reject | PASS |
| ROLE + DIRECT | reject | PASS |
| USER + CANDIDATE_POOL | reject | PASS |
| Illegal resolver mode | reject | PASS |
| Illegal strategy/target | reject | PASS |
| ROLE + FIXED_ORG + organization ID | accept | PASS |
| FIXED_ORG + NULL organization ID | reject | PASS |
| Unsupported organization scope | reject | PASS |

Every negative case ran in a transaction, failed at the database boundary,
rolled back, and left zero rejected fixture rows.

## 8. Multi-Binding and immutability

Two active ROLE Binding snapshots for the same Node with stable orders 1 and 2
were accepted. A duplicate active `(instance, node, order)` was rejected.

For a frozen Node Resolver Binding Snapshot:

- INSERT passed;
- UPDATE failed with MySQL error 1644 and
  `WORKFLOW_NODE_RESOLVER_SNAPSHOT_IMMUTABLE`;
- logical-delete UPDATE failed with the same error;
- physical DELETE failed with the same error.

Exactly the two expected replacement triggers exist.

## 9. Legacy and mapping compatibility

Legacy `USER + USER + DIRECT` rows remain insertable and readable with all new
ROLE provenance and organization fields NULL. The V2.6.21 Upgrade publication
fixture also retained `LEGACY_USER_ONLY` defaults after V2.6.22.

`LEGACY_USER_MYSQL_COMPATIBILITY=PASS`.

No Spring application was pointed at the disposable database, so the optional
real-MySQL `DatabaseMappingChecker` path is recorded as `NOT_RUN`. Static S5
mapping contracts and the real metadata contract both report zero new V2.6.22
mapping drift. The three known remark-only drifts remain pre-existing and out
of scope.

Java 21.0.12 full backend regression and Spring Boot 3.5.9 context startup
passed: 656 tests executed, 0 failures, 0 errors, and 6 explicitly skipped live
external-audit tests. V2.6.22 contract and entity mapping tests passed within
that run.

## 10. Asset state and remaining risks

- V2.6.22: `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`.
- S4 real-MySQL publication concurrency debt remains `OPEN`; this validation
  intentionally did not mix that separate concern into S5.1.
- ROLE Directory revision, candidate resolution, Candidate Pool, Task, Claim,
  Runtime enablement, Canary authorization, and Investment integration remain
  out of scope.
- Validation used ephemeral local MySQL, not a managed environment.

## 11. Next step

RC2-S6 may start only under its own explicit task and must preserve the frozen
V2.6.22 SHA. ROLE Runtime Canary activation remains prohibited.
