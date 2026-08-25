# Workflow V1 RC2-S9B Attested Fixture Execution Report

## 1. Final conclusion

`RC2_S9B=FAIL`.

The isolated Fresh database bootstrap passed, but the sole Environment Attestation decision engine rejected the collected evidence because the real structural schema fingerprint did not match the frozen RC2 eligibility fingerprint. Execution stopped at that boundary. The Fixture Seeder and read-only Candidate Discovery were not executed.

Runtime safety remained unchanged:

- `ROLE_RUNTIME=DISABLED`
- `CANARY=NOT_AUTHORIZED_NOT_ENABLED`
- `KILL_SWITCH=STOP_NEW_AND_CLAIM`
- `S4_REAL_MYSQL_CONCURRENCY_DEBT=OPEN`

## 2. Isolated environment

| Item | Result |
| --- | --- |
| MySQL | `8.4.9` |
| Host | `127.0.0.1` |
| Dynamic port | `58613` |
| Database | `enterprise_platform` |
| Evidence root | `D:\codex-rc2-s9b-20260824-165131` |
| Disposable datadir | `D:\codex-rc2-s9b-20260824-165131\mysql\data` |
| RC1 database touched | `NO` |
| Historical S9 environment reused | `NO` |

After the failed attestation, MySQL was shut down, the listener was confirmed closed, and only the disposable datadir was deleted. Evidence, logs, marker, snapshots, and the failure summary were retained.

## 3. Migration and Flyway evidence

- The frozen fixture contract precheck passed `16/16`.
- The authoritative SHA manifest enumerated exactly 45 migrations.
- Bootstrap snapshot: source `45`, destination `45`, SHA `45/45_PASS`.
- Attestation snapshot: source `45`, destination `45`, SHA `45/45_PASS`.
- The 17-file authoritative baseline was loaded.
- Flyway explicit baseline version: `2.0.0` with `baselineOnMigrate=false`.
- Latest migration: `2.6.23`.
- Successful SQL migrations: `45`; failed migrations: `0`; history rows: `46` including the baseline row.
- Strict validate passed.
- Second migrate reported `No migration necessary`.

## 4. Evidence and attestation

The Collector produced `environment-evidence.json` from the real isolated database. The evidence retained the authoritative effective time as the JSON string `2026-01-01T00:00:00Z`.

| Check | Result |
| --- | --- |
| Requested/listening port identity | `58613 = 58613` |
| Requested/actual datadir identity | `MATCH` |
| Fixture ID conflict | `0` |
| Fixture business-key conflict | `0` |
| Partial fixture state | `NONE_PRESENT` |
| Actual structural fingerprint | `73eae9b42a61f87288230d09c01c56017ef9492ebe272478c86609fd9f02daeb` |
| Frozen structural fingerprint | `154c19d739393a00b2dd84631be3753c8adfe4c9557059616331e142716f77c2` |
| Actual metadata fingerprint | `2d01817ef3853231cc32f4275577984138c67a9461664fbf28d98c8a6e5e0c03` |
| Environment Attestation | `FAIL` |
| Failed assertions | `STRUCTURAL_FINGERPRINT` |
| Warnings | `METADATA_FINGERPRINT_DRIFT` |
| Attestation ID | not generated |

The verifier consumed only the Collector evidence. The deprecated guard was not used as a second authority.

## 5. Fail-closed execution result

Because `ENVIRONMENT_ATTESTATION=FAIL`:

- final Fixture Contract verification was not entered;
- `READY_TO_EXECUTE_FIXTURE=NO`;
- the Seeder was not called;
- no organization, user, approval role, assignment, workflow definition, version, binding, manifest, or release fixture was created;
- no Workflow instance, node execution, task, Candidate Pool, Claim, Admission, realtime eligibility evidence, or governance control was created;
- read-only Candidate Discovery was not entered;
- no proposed Canary scope was emitted.

The earlier static Fixture Contract precheck is retained only as preflight evidence; it does not override the failed Environment Attestation.

## 6. Evidence inventory

Evidence is retained under `D:\codex-rc2-s9b-20260824-165131\evidence`:

- `baseline.log`
- `bootstrap-identity.txt`
- `flyway-baseline.log`
- `flyway-migrate.log`
- `flyway-validate.log`
- `flyway-noop.log`
- `flyway-info.log`
- `environment-marker.json`
- `environment-evidence.json`
- `environment-attestation-result.json`
- `summary.json`

Both 45-file migration snapshots remain under the evidence root. No evidence was rewritten to manufacture a pass.

## 7. Required follow-up

Before retrying S9B, a separate governance task must reconcile the frozen structural fingerprint contract with the structural fingerprint produced by the real Fresh `2.6.23` database. It must determine whether the frozen constant or the canonical structural serialization is stale. S9B itself must not change the Collector, Verifier, fixture contract, Seeder, migrations, or production code.

Current gates:

- `READY_FOR_CANARY_SCOPE_CHANGE_APPROVAL=NO`
- `READY_FOR_RC2_S10=NO`
- `READY_FOR_ROLE_RUNTIME_CANARY_ACTIVATION=NO`
