# Workflow V1 RC2-S1 — V2.6.21 Schema Candidate

## 1. Scope

This Sprint creates the unexecuted candidate
`V2.6.21__create_workflow_version_resolver_binding.sql`. It is limited to
Version-level ROLE resolver configuration, the publication Manifest, and
Version/Release binding snapshots.

It does not change Instance binding tables, Realtime Eligibility evidence,
Candidate Pool, Claim, Admission, Workflow runtime code, Investment, RC1
validation scripts, ROLE Runtime, Canary, or the Kill Switch.

## 2. Preflight findings and design adjustments

- `workflow_version(definition_id,id)` and `workflow_node(version_id,id)` are
  existing composite unique ownership keys. V2.6.21 reuses both as composite
  foreign-key parents and does not add loose single-column ownership.
- Workflow V2.5 audit fields are retained exactly: `created_by`,
  `created_time`, `updated_by`, `updated_time`, `deleted`, `delete_token`,
  `remark`, and optimistic-lock `version`.
- Stable resolver codes, versions, role codes, canonical versions, and hashes
  use `ascii_bin`; the two new Workflow tables retain the existing
  `utf8mb4_general_ci` table default.
- The plan's optional `RESOLVER_BINDING` Node assignment type is not added.
  `workflow_version.resolver_binding_model` is the authoritative compatibility
  switch, so historical Node assignment semantics remain untouched.
- `INSTANCE_BUSINESS_ORG` is structurally reserved with a NULL
  `organization_id`; only `FIXED_ORG` can supply a complete pre-Instance Canary
  scope.
- `role_code` uses the same case-sensitive uppercase business-key expression as
  Approval Role Directory. There is deliberately no physical Directory FK.
- A Manifest is inserted while the owner Version is still DRAFT, after the
  Version snapshot fields are prepared and before the same transaction changes
  the Version to PUBLISHED. This avoids a DRAFT/PUBLISHED circular dependency.

## 3. Database changes

New tables:

1. `workflow_version_node_resolver_binding`
2. `workflow_version_resolver_binding_manifest`

Altered tables:

1. `workflow_version`
2. `workflow_version_release`

The Version and Release each receive:

- `resolver_binding_model`
- `resolver_binding_manifest_hash`
- `resolver_binding_count`
- `resolver_binding_canonical_version`

All historical rows receive the semantic default `LEGACY_USER_ONLY`, count
zero, and NULL Manifest evidence. No historical `content_hash` is changed.

The candidate defines nine triggers:

- three DRAFT-only Binding mutation guards;
- one Manifest insert integrity guard;
- two Manifest append-only guards;
- one Version Release snapshot/Manifest guard;
- two Version Release append-only guards.

## 4. Integrity boundary

The database enforces ownership, stable ordering, exact first-phase ROLE
resolver codes, hash formats, organization-scope combinations, DRAFT-only
mutation, one Manifest per Version, and append-only publication facts.

The application remains responsible in RC2-S3/S4 for Graph validation,
complete Binding coverage, Registry and Contract validation, canonical Binding
and Manifest hashes, and the combined Graph-plus-Manifest content hash.

## 5. RC2-S1.1 real MySQL negative matrix

| Case | Expected |
|---|---|
| Version belongs to another Definition | FK rejection |
| Node belongs to another Version | FK rejection |
| `binding_order=0` | CHECK rejection |
| Duplicate active `binding_order` | UNIQUE rejection |
| FIXED_ORG with NULL organization | CHECK rejection |
| INSTANCE_BUSINESS_ORG with organization value | CHECK rejection |
| Published Version Binding INSERT | Trigger rejection |
| Published Version Binding UPDATE | Trigger rejection |
| Published Version logical delete/restore | Trigger rejection |
| Manifest UPDATE | Trigger rejection |
| Manifest DELETE | Trigger rejection |
| Second Manifest for one Version | UNIQUE rejection |
| Legacy Version with non-zero count | CHECK rejection |
| Legacy Version with Manifest hash | CHECK rejection |

RC2-S1.1 must additionally run Fresh and V2.6.20 Upgrade paths, Flyway strict
validate, second-migrate no-op, and Schema fingerprint comparison. H2 or static
tests are not accepted as substitutes for the Trigger/CHECK matrix.

## 6. Asset state

- SHA-256:
  `cb9555ba5f81c292654286fe8c7396cdead4b71c9387c4b73d09139d4147296e`
- Flyway checksum: `null`
- Asset status: `CANDIDATE`
- Execution status: `NOT_EXECUTED`
- Real MySQL validation: required and not performed
- ROLE Runtime: disabled
- Canary: not authorized and not enabled
- Kill Switch: `STOP_NEW_AND_CLAIM`

## 7. Modified asset list

- `database/migration/mysql/V2.6.21__create_workflow_version_resolver_binding.sql`
- `database/flyway/migration-inventory.yml`
- `database/migration/mysql/SHA256SUMS`
- `database/migration/mysql/README.md`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/infrastructure/V2621VersionResolverBindingMigrationContractTest.java`
- `docs/workflow-v2621-version-resolver-binding-schema-candidate-report.md`

The three pre-existing untracked RC1 validation assets were not edited. No
V2.1.0-V2.6.20 Migration was edited.

## 8. Static validation result

- Java 21 compile: PASS
- Target Migration contract test: 6 passed, 0 failed, 0 errors, 0 skipped
- Governed Migration SHA verification: 43/43 matched
- SQL structural contract: 2 new tables, 2 altered tables, 9 triggers,
  27 constraints (22 named FK/CHECK constraints, 3 UNIQUE keys, 2 primary keys)
- Forbidden runtime-table alteration scan: 0
- Real MySQL/Flyway execution: not performed
