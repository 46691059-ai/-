# Workflow V1 ROLE Runtime Canary Release Change Pack

Status: `PREPARATION_COMPLETE / NO-GO / NOT_AUTHORIZED / NOT_EXECUTED`.

## Change identity

| Item | Value |
|---|---|
| Change Request | `<TBD>` |
| Candidate commit | `f2429571c5255366b26553fe29064b6052faa437` plus uncommitted release assets; not reproducible |
| Proposed commit message | `release(workflow): freeze workflow v1 role runtime canary baseline` |
| Proposed tag | `workflow-v1.0.0-rc1` |
| Commit/Tag state | `PROPOSED / NOT_CREATED` |
| Runtime state | `DISABLED` |
| Canary state | `NOT_AUTHORIZED / NOT_ENABLED` |

## Pack contents

1. Release baseline freeze report and manifest.
2. Exact Canary scope template.
3. One-veto GO/NO-GO checklist.
4. Activation procedure.
5. Kill Switch/rollback runbook.
6. Monitoring/acceptance matrix.
7. Existing Workflow Definition of Done, frozen baseline, release checklist and operations runbook.
8. V2.6.19 External Audit final gate report and reproducible MySQL/HTTPS evidence references.

## Technical evidence summary

- Backend: 582 tests PASS; Workflow subset 348.
- Frontend: typecheck/build PASS; 24 tests PASS.
- Migration: 41/41 SHA MATCH; no duplicate/missing/unregistered scanned SQL.
- V2.6.19: canonical SHA/checksum unchanged.
- Runtime safety defaults: Claim disabled, Canary empty/OFF, Kill Switch `STOP_NEW_AND_CLAIM`.
- Domain purity, dangerous activation, PII and committed-secret scans passed.

## Blocking exception

`InMemoryApprovalRoleDirectoryProviderAuditSink` is a conditional main-source bean used by the enabled Approval Role Directory Provider. Its volatile evidence violates the release rule forbidding InMemory production-path dependencies. Resolution requires a separately authorized durable audit adapter change and regression; it cannot be waived by this pack.

## Human approvals and operations

Business Owner, Workflow Owner, Security/Audit Owner, Operations Owner, Rollback Owner and Release Approver remain `<TBD>`. No commit, tag, Feature Flag, Canary, Kill Switch release, secret, certificate, ACL, deployment or migration action was performed.

## Exit criteria

After the P0 is fixed and independently verified: form one clean release commit, create the approved RC tag, complete the exact scope template, assign owners, pass every checklist row and submit a formal Canary change. These are human-authorized future actions.

