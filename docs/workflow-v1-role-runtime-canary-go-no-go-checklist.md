# Workflow V1 ROLE Runtime Canary GO / NO-GO Checklist

Decision rule: **one veto**. Any unchecked, unknown, expired, drifted or unverifiable P0 item produces `NO-GO`. Current decision: **NO-GO** because the release commit/tag and clean tree are absent and the enabled Directory Provider uses an in-memory audit sink.

| # | P0 gate | Required evidence | Status |
|---:|---|---|---|
| 1 | Release commit frozen | immutable commit ID | ☐ |
| 2 | Release tag frozen | signed/controlled RC tag | ☐ |
| 3 | Working tree clean | `git status` empty | ☐ |
| 4 | Migration SHA stable | 41/41 and V2.6.19 frozen hash | ☑ |
| 5 | Flyway validate | strict validate and no-op evidence | ☑ |
| 6 | Directory available | approved environment health/SLA | ☐ |
| 7 | Directory Contract stable | contract/hash comparison | ☑ |
| 8 | Revision Fence healthy | revision/hash negative tests | ☑ |
| 9 | Historical query healthy | effectiveAt evidence | ☑ |
| 10 | DataScope available | versioned PASS/DENY/INDETERMINATE | ☑ |
| 11 | Platform SoD available | all required policies/version | ☑ |
| 12 | Business SoD available | governed policy/version | ☑ |
| 13 | External Audit available | HTTPS health and delivery | ☑ |
| 14 | Receipt verifiable | canonical receipt/hash | ☑ |
| 15 | Feature Flag auditable | append-only versioned control | ☑ |
| 16 | Canary Scope exact | fully completed scope form | ☐ |
| 17 | Kill Switch available | tested safe-stop operation | ☑ |
| 18 | Monitoring available | dashboards/queries verified | ☐ |
| 19 | Alert owner assigned | named accountable person | ☐ |
| 20 | Rollback owner assigned | named accountable person | ☐ |
| 21 | Business Owner approval | recorded approval | ☐ |
| 22 | Security/Audit approval | recorded approval | ☐ |
| 23 | Release Approver approval | recorded approval | ☐ |
| 24 | Canary business flow defined | exact test journey | ☐ |
| 25 | Canary node is one ROLE node | frozen definition evidence | ☐ |
| 26 | Legacy regression | automated regression | ☑ |
| 27 | USER/DIRECT regression | automated regression | ☑ |
| 28 | Evidence TTL healthy | expiry negative test | ☑ |
| 29 | Config Drift detection | commit-time drift matrix | ☑ |
| 30 | Concurrent single winner | 10/25/50 evidence | ☑ |
| 31 | Production dependency integrity | no Fake/Stub/InMemory production path | ☐ P0 |

## Current vetoes

1. Branch `feature/project-module` has no unique release commit/tag and the tree contains extensive accumulated uncommitted assets.
2. `InMemoryApprovalRoleDirectoryProviderAuditSink` is the sole main-source implementation injected when `app.approval-role-directory.provider.enabled=true`; audit facts are non-durable.
3. Canary scope and accountable approvals remain `<TBD>`.

