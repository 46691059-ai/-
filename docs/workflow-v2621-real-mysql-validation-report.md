# Workflow V1 RC2-S1.1 — V2.6.21 Real MySQL Validation

## 1. Final conclusion

`V2.6.21__create_workflow_version_resolver_binding.sql` passed isolated real
MySQL/Flyway validation on both Fresh and V2.6.20 Upgrade paths. The Migration
is promoted to `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`.

ROLE Runtime remained disabled, Canary remained unauthorized and disabled, and
the Kill Switch remained `STOP_NEW_AND_CLAIM`. The persistent RC1 TEST database
on port 34061 and its datadir were not connected to or modified.

## 2. Environment and isolation

- MySQL: Community Server 8.4.9
- Flyway: 13.0.0
- Fresh port: 38231
- Upgrade port: 38232
- Successful evidence root:
  `D:/codex-rc2-v2621-20260822-165045/evidence`
- Temporary Fresh and Upgrade datadirs: stopped and removed after evidence was
  copied; only logs and validation evidence remain.
- RC1 Tag verification:
  `workflow-v1.0.0-rc1` resolves to
  `bf70c588752f8b32c27f3112e2ddb5e8803b4db6`.

An initial harness run at
`D:/codex-rc2-v2621-20260822-164633/evidence` exposed two harness-only
classification defects: semantic versions were compared as strings and the
V2.6.20 source validate saw the pending V2.6.21 location. The harness was
corrected to use Flyway installation order and a dedicated V2.6.20 Migration
copy. That run showed no Migration failure, and its evidence is retained.

## 3. Migration integrity

- V2.6.21 SHA-256:
  `cb9555ba5f81c292654286fe8c7396cdead4b71c9387c4b73d09139d4147296e`
- V2.6.21 Flyway checksum: `-698735620`
- Governed Migration SHA validation: 43/43 PASS
- V2.1.0-V2.6.20 historical SHA drift: none
- V2.6.21 SQL changed during validation: no

## 4. Fresh result

- 17 authoritative Baseline assets: PASS
- Explicit Flyway baseline version: 2.0.0
- `baselineOnMigrate`: false
- Latest successful Migration: 2.6.21
- Failed Migrations: 0
- Strict validate: PASS
- Second migrate: NO_OP
- Schema fingerprint:
  `25c12ff7e7d50a4c44f7c261b92e29817914a324262b57c225294cefa62fd4ff`

## 5. Upgrade result

- Source: isolated RC1-equivalent 2.6.20 schema
- V2.6.20 source strict validate using a V2.6.20-only Migration copy: PASS
- Target: 2.6.21
- V2.6.21 execution count: exactly once
- Failed Migrations: 0
- Target strict validate: PASS
- Second migrate: NO_OP
- Schema fingerprint:
  `25c12ff7e7d50a4c44f7c261b92e29817914a324262b57c225294cefa62fd4ff`

Fresh and Upgrade fingerprints are identical.

## 6. Schema validation

Both paths confirmed:

- `workflow_version_node_resolver_binding` exists;
- `workflow_version_resolver_binding_manifest` exists;
- `workflow_version` contains the four resolver-binding snapshot columns;
- `workflow_version_release` contains the same four release snapshot columns;
- model and canonical/hash fields use the declared `ascii_bin` collation;
- lengths, nullable flags, defaults, generated attributes and comments match
  the candidate SQL;
- FK, CHECK, UNIQUE, index and Trigger metadata are present.

## 7. Negative database matrix

All 9 required negative cases passed with real MySQL rejection and no retained
test transaction:

1. cross-Definition Version ownership;
2. cross-Version Node ownership;
3. zero binding order;
4. duplicate active binding order;
5. FIXED_ORG with NULL organization;
6. INSTANCE_BUSINESS_ORG with an illegal fixed organization value;
7. Legacy Version with non-zero binding count;
8. Legacy Version with a Manifest hash;
9. second Manifest for the same Version.

## 8. Immutability and publication sequence

The 12 required mutation-path checks passed:

- DRAFT Binding INSERT, UPDATE, logical delete and restore were accepted;
- PUBLISHED Binding INSERT, UPDATE, DELETE, logical delete and restore were
  rejected;
- RETIRED Binding INSERT was rejected;
- Manifest UPDATE and DELETE were rejected.

The legal future publication sequence was executed successfully in one
transaction:

1. prepare the Version binding snapshot while DRAFT;
2. insert the immutable Manifest while DRAFT;
3. change the Version to PUBLISHED;
4. insert the immutable Version Release snapshot;
5. commit.

No Trigger was disabled or bypassed. Therefore no Manifest/Publish circular
dependency exists.

## 9. Legacy compatibility

A PUBLISHED Version and Version Release were inserted into the isolated
V2.6.20 Upgrade source before V2.6.21 ran. After migration both retained:

- `resolver_binding_model=LEGACY_USER_ONLY`;
- `resolver_binding_count=0`;
- `resolver_binding_manifest_hash=NULL`;
- `resolver_binding_canonical_version=NULL`.

No historical content hash was rewritten and no ROLE Binding was backfilled.

## 10. Asset status and remaining boundary

- V2.6.21: `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- Real MySQL validation: PASS
- ROLE Runtime: DISABLED
- Canary: NOT_AUTHORIZED_NOT_ENABLED
- Kill Switch: STOP_NEW_AND_CLAIM
- RC1 TEST database touched: NO

RC2-S2 may begin. ROLE Runtime Canary activation remains prohibited. The
combined Graph plus Manifest content-hash implementation and application-level
Binding coverage/Registry validation remain later RC2 responsibilities.

## 11. Validation asset changes

- Added `database/flyway/scripts/validate-rc2-v2621-real-mysql.ps1`.
- Added this validation report.
- Promoted V2.6.21 in `database/flyway/migration-inventory.yml`.
- Updated `database/migration/mysql/README.md` with real-validation evidence.
- Updated the existing V2.6.21 contract test to assert the promoted asset.
- `database/migration/mysql/SHA256SUMS` and V2.6.21 SQL were unchanged during
  RC2-S1.1.
