# Workflow V1 RC2.1 Canary Scope Approval Package

## 1. Decision and non-authorization statement

This package is an audited draft for a future governance-hardened release. It is not an approval or activation instruction.

- `APPROVAL_DECISION=PENDING_HUMAN_APPROVAL`
- `BUSINESS_APPROVAL_EVIDENCE_COMPLETE=YES`
- `RELEASE_IDENTITY_ATTESTATION=AWAITING_RELEASE_IDENTITY`
- `CANARY_AUTHORIZED=NO`
- `CANARY_ENABLED=NO`
- `ROLE_RUNTIME_ENABLED=NO`
- `ROLE_RUNTIME=DISABLED`
- `KILL_SWITCH=STOP_NEW_AND_CLAIM`

No `PROPOSED`, `APPROVED_NOT_ENABLED`, or `ENABLED` governance row may be created from this draft. No Instance, Task, Candidate Pool, Claim, Admission, or Realtime Eligibility evidence may be created.

## 2. Release identities

The two release identities are deliberately distinct:

| Identity | Tag / commit | Meaning |
| --- | --- | --- |
| Base RC2 | `workflow-v1.0.0-rc2` / `740bee63e79a2744eb06ff693b17e7ed3fdf9375` | Frozen historical RC2; does not contain Canary Governance hardening |
| Governance Hardened candidate | recommended tag `workflow-v1.0.0-rc2.1` / final commit not yet created | Must contain S11.1 code, V2.6.24, S11.2 evidence/baseline, this final package, and the S11.3 report |
| S11 checkpoint | no release tag / `7240683772ee65d4ee4a57d935e7dd0011907144` | Contains S11.1 and S11.2 assets, but is not the final tagged release identity |

Business approval evidence is frozen before the release commit. The release commit is then created externally and does not need to contain its own SHA. S11.4 must bind the final annotated RC2.1 tag, peeled commit, remote verification and frozen `contentHash` in a post-tag Release Identity Attestation. The Base RC2 tag/commit must never be used as the complete hardened runtime identity.

## 3. V2.6.24 identity and structural attestation

| Evidence | Frozen value |
| --- | --- |
| Migration | `V2.6.24__create_exact_canary_scope_governance.sql` |
| Migration SHA-256 | `e549147abcf77168a1b1ef465c76ed642f7ce02f250117d0019bbeeb8d524822` |
| Flyway checksum | `857951823` |
| Latest migration | `2.6.24` |
| Migration SHA validation | `46/46 PASS` |
| Structural canonical | `RC2_CANARY_GOVERNANCE_STRUCTURAL_CANONICAL_V1` |
| Structural fingerprint | `20253809be2aeb7c76fe36a7d37293b6ca8b77741998aead726893056044a5b9` |
| Fresh A / Fresh B / Upgrade | identical |

The Base RC2 V2.6.23 attestation remains separately frozen in `rc2-schema-fingerprint-baseline.json`; it was not overwritten.

## 4. Exact six-dimensional scope

Only this exact scope may be considered by a future human approval:

| Dimension | Frozen value |
| --- | --- |
| enterpriseId | `990001` |
| organizationId | `990101` |
| definitionId | `990401` |
| definitionVersionId | `990402` |
| nodeId | `990404` |
| roleCode | `RC1_TEST_CANARY_APPROVER` |

The scope model rejects null, wildcard, omitted role/organization, definition-level fallback, enterprise-level fallback, and any one-dimension mismatch.

## 5. Directory and approval evidence inventory

| Field | Value/status | Authoritative source |
| --- | --- | --- |
| directoryRevision | `1` | controlled fixture manifest and Directory revision contract |
| directoryCandidateCount | `2` | controlled fixture contract |
| directoryResultHash | `2e1736fae83be972259ccee92b8d448d463e7c8a5483d49afcda7225a3279234` | `ApprovalRoleCanonical.resultHash` over the frozen fixture Directory result |
| versionBindingHash | `5b473845390a2a4c69f28a7b9d63d538a0a371cea447e97ecd00707ddbb87c1c` | `VersionNodeResolverBindingCanonical` for binding `990405` |
| manifestHash | `e76bc7f8ee3cca977d4363b6073106d66deffe2491d0290a569d46fa3bac57ed` | `RC2_CANARY_GOVERNANCE_RELEASE_MANIFEST_V1` compact canonical JSON |
| contentHash | `b2bc64b3c6cebbd1713b92074f5fdcf6862d94fb70338a6b2d07c76c61ef8ade` | `RC2_CANARY_APPROVAL_CONTENT_V1` compact canonical JSON |
| releaseTag | `AWAITING_RELEASE_IDENTITY` | S11.4 post-tag attestation; not an input to `contentHash` |
| releaseCommit | `AWAITING_RELEASE_IDENTITY` | S11.4 post-tag attestation; not an input to `contentHash` |
| structuralFingerprint | `20253809be2aeb7c76fe36a7d37293b6ca8b77741998aead726893056044a5b9` | V2.6.24 structural baseline |

The four business hashes and structural fingerprint are frozen in `rc2-canary-approval-evidence-v1.json`; their sources and algorithms are specified in `workflow-rc2-canary-approval-evidence-hash-spec.md`. `AWAITING_RELEASE_IDENTITY` is a release-stage state, not a placeholder hash. No governance row may be written because the post-tag identity attestation has not happened.

## 6. State machine and separation

The append-only state model is:

```text
PROPOSED -> APPROVED_NOT_ENABLED -> ENABLED -> SUSPENDED -> ENABLED
     |                |                |            |
     +----------------+----------------+------------+-> REVOKED
```

Approval appends `APPROVED_NOT_ENABLED`; it does not enable Canary, enable ROLE Runtime, change the Kill Switch, or create runtime products. `PROPOSED -> ENABLED` and `REVOKED -> ENABLED` are rejected. `APPROVED_NOT_ENABLED` remains a runtime deny decision.

Runtime allow requires all of the following independently:

1. the exact six-dimensional Canary scope is `ENABLED`;
2. ROLE Runtime is explicitly enabled by its separate governance action;
3. Feature Flag and runtime safety evidence pass;
4. every Kill Switch layer explicitly allows execution.

## 7. Negative matrix

The S11.2 real MySQL/application matrix proved denial for each individual mismatch in enterprise, organization, definition, definition version, node, and role. It also proved null/wildcard rejection or isolation, legacy fail-closed behavior, approval-without-enablement denial, enable-without-approval rejection, runtime-disabled denial, and Kill Switch denial.

No `WARN_AND_CONTINUE`, `NULL=ALL`, wildcard, broad scope, or legacy governance fallback is permitted.

## 8. Append-only, concurrency, and legacy behavior

Historical ledger rows reject `UPDATE` and `DELETE`. State changes insert a new revision. Scope/revision and predecessor uniqueness plus expected-revision CAS prevent two current successors, duplicate revisions, silent overwrite, and last-write-wins. Legacy V2.6.23 governance rows are never translated into a new six-dimensional Canary state and remain fail closed.

## 9. Kill Switch and rollback

The authoritative state remains `STOP_NEW_AND_CLAIM`. Approval, Canary enablement, and ROLE Runtime enablement cannot modify it implicitly.

Rollback order after any future separately authorized activation is: keep or restore `STOP_NEW_AND_CLAIM`; disable ROLE Runtime; append `SUSPENDED` or `REVOKED`; block new runtime/claim/admission; retain all ledger and audit evidence; investigate without deleting history.

## 10. Human approval section

| Field | Value |
| --- | --- |
| Decision | `PENDING_HUMAN_APPROVAL` |
| Human approver | intentionally blank; no decision requested in S11.3 |
| Decision time | intentionally blank |
| Approval reference | intentionally blank |

Human scope approval remains prohibited until S11.4 creates and remotely verifies the final hardened commit, annotated tag and post-tag Release Identity Attestation. Even a later human scope approval would produce only `APPROVED_NOT_ENABLED`; Canary enablement and ROLE Runtime activation still require separate approvals.

## 11. Current blocker summary

1. Business approval evidence is complete and reproducible before the release commit.
2. The final governance-hardened commit and annotated `workflow-v1.0.0-rc2.1` tag do not yet exist.
3. S11.4 must create the external post-tag attestation before any human approval request.

This package must remain `PENDING_HUMAN_APPROVAL / NOT AUTHORIZED / NOT ENABLED`.
