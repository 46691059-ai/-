# Workflow V1 RC2-S11.2 Canary Governance Real MySQL Validation

## 1. Result and safety boundary

`RC2_S11_2=PASS`. V2.6.24 passed isolated MySQL 8.4.9 Fresh, double-Fresh, V2.6.23 upgrade, strict Flyway validation, second-migrate no-op, schema equivalence, governance persistence, concurrency, application gate, mapping, and full backend regression checks.

No RC1 TEST, persistent RC2, or production database was used. The ephemeral servers used ports `39471`, `39472`, and `39473`; their three datadirs were removed after shutdown. No Canary approval or persistent authorization was created, ROLE Runtime remained disabled, and the kill switch remained `STOP_NEW_AND_CLAIM`. No commit, tag, or push was performed.

## 2. Frozen migration evidence

- Migration: `V2.6.24__create_exact_canary_scope_governance.sql`
- SHA-256: `e549147abcf77168a1b1ef465c76ed642f7ce02f250117d0019bbeeb8d524822`
- Flyway checksum: `857951823`
- Migration manifest: `46/46 PASS`; no historical migration SHA changed

## 3. Fresh and upgrade evidence

| Check | Fresh A | Fresh B | Upgrade |
| --- | --- | --- | --- |
| MySQL | 8.4.9 | 8.4.9 | 8.4.9 |
| Final migration | 2.6.24 | 2.6.24 | 2.6.24 |
| Failed migrations | 0 | 0 | 0 |
| Strict validate | PASS | PASS | PASS |
| Second migrate | NO_OP | NO_OP | NO_OP |
| Structural fingerprint | `20253809be2aeb7c76fe36a7d37293b6ca8b77741998aead726893056044a5b9` | same | same |

The Upgrade source was exactly V2.6.23. Fresh A, Fresh B, and Upgrade produced byte-identical structural canonical evidence. The old V2.6.23 RC2 fingerprint was not modified. V2.6.24 is recorded separately in `rc2-canary-governance-schema-fingerprint-baseline.json` under `RC2_CANARY_GOVERNANCE_STRUCTURAL_CANONICAL_V1`.

## 4. Governance database gates

- Exact six-dimensional non-null schema: PASS
- Duplicate current/root scope rejection and revision/predecessor uniqueness: PASS
- UPDATE and DELETE history rejection: PASS
- Legal append-only transitions, including suspend/resume: PASS
- `PROPOSED -> ENABLED` and `REVOKED -> ENABLED` rejection: PASS
- Approve produces `APPROVED_NOT_ENABLED`, not runtime enablement: PASS
- Enable without approval: rejected
- Six one-dimension scope mismatches: `6/6 DENY`
- NULL organization, NULL role, and wildcard expansion: rejected or isolated and denied
- Legacy governance record auto-authorization: NO; fail closed: YES
- ROLE Runtime disabled while Canary scope is enabled: runtime DENY
- Kill switch `STOP_NEW_AND_CLAIM`: runtime/claim/admission DENY
- Four dual-session races: exactly one legal successor; no duplicate revision/current state
- Audit actor/reference, approval/enable/suspend/revoke timestamps, and revision fields: present and persisted

## 5. Mapping, fixture, and regression evidence

The real MySQL `DatabaseMappingCheckerRealMysqlTest` and `V2624CanaryGovernanceRealMysqlTest` passed against the Fresh V2.6.24 database. The new S11 entity true-drift count was zero; existing generated-column DB supersets remained allowed.

The S9 fixture contract regression passed without executing the fixture or Canary runtime: `ROLE_BOUND_NODE_COUNT=1`, `DIRECTORY_CANDIDATE_COUNT=2`, `RECOMMENDED_CANDIDATE_COUNT=1`. Governance remains pending and unauthorized.

Java 21 compile passed. The full backend suite passed with `712` run, `702` passed, `0` failures, `0` errors, and `10` skipped. Spring Context passed. Frontend files were unchanged, so the full frontend suite was not rerun.

## 6. Promotion decision

All V2.6.24 database promotion gates passed, so the migration and its versioned structural baseline are `CANONICAL_IMMUTABLE`. This is a technical validation result only. Human approval remains pending; Canary authorization, Canary enablement, and ROLE Runtime activation remain prohibited.

The annotated `workflow-v1.0.0-rc2` tag was only read and its peeled commit was checked against `740bee63e79a2744eb06ff693b17e7ed3fdf9375`; it was not moved or rewritten.
