# Workflow V1 ROLE Runtime Canary Activation Procedure

Status: `PROCEDURE_ONLY / NOT_EXECUTED`. Every phase requires evidence attachment. Stop immediately on any unknown or mismatch.

1. **PRECHECK** — verify approved commit/tag, clean tree, 41/41 SHA, Flyway validate, runtime disabled, Directory non-ACTIVE and all checklist items.
2. **FREEZE CONFIG** — freeze exact enterprise/definition/version/node, directory revision policy, DataScope/SoD/Flag/Canary/Kill Switch versions and audit contract.
3. **CAPTURE BASELINE** — capture task/pool/claim/audit counts, backlog, latency/error rates and control versions.
4. **VERIFY DIRECTORY** — verify TLS/auth/provider/contract, complete semantics, historical effectiveAt and revision/hash.
5. **VERIFY DATASCOPE** — verify current principal, organization boundary and DENY/INDETERMINATE fail-closed behavior.
6. **VERIFY PLATFORM_SOD** — verify initiator, prior actor, incompatible node, governance-admin and same-actor policies.
7. **VERIFY BUSINESS_SOD** — verify exact business rule/version and fail-closed behavior.
8. **VERIFY AUDIT SINK** — send approved probe, verify accepted immutable receipt, backlog/retry/dead/replay and alerting.
9. **VERIFY FEATURE FLAG** — confirm current version remains OFF before approval and is independently auditable.
10. **VERIFY CANARY** — verify exact four-dimensional scope; reject wildcard, range or missing value.
11. **VERIFY KILL SWITCH** — confirm `STOP_NEW_AND_CLAIM`, operator access and tested recovery procedure.
12. **APPROVAL GATE** — obtain Business Owner, Workflow Owner, Security/Audit, Operations and Release Approver approvals.
13. **ENABLE EXACT CANARY SCOPE** — documented future human-controlled change only. This baseline task does not execute it.
14. **OBSERVE** — monitor all acceptance metrics for the approved window; do not expand scope.
15. **ACCEPT / ABORT** — accept only when every threshold passes; otherwise execute rollback runbook immediately.
16. **CLOSE CHANGE** — restore Flag OFF, Canary OFF and safe Kill Switch unless a new approved change says otherwise; archive evidence and receipts.

## Mandatory stop points

Stop before phase 13 if any hash/version/owner is missing, the external audit path is unhealthy, the production dependency P0 remains, or the working tree/commit/tag is not reproducible. No API, SQL or service method may bypass this procedure.

