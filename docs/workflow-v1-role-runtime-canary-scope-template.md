# Workflow V1 ROLE Runtime Canary Scope Template

Status: `TEMPLATE_ONLY / CANARY_NOT_AUTHORIZED / CANARY_NOT_ENABLED`

This form must be completed and approved manually. `<TBD>` is mandatory until an accountable owner supplies evidence; wildcard, inferred, example, production-person or broad-scope values are forbidden.

| Field | Value |
|---|---|
| Canary ID | `<TBD>` |
| Change Request ID | `<TBD>` |
| Enterprise ID | `<TBD>` |
| Workflow Definition ID | `<TBD>` |
| Workflow Definition Version | `<TBD>` |
| Node ID | `<TBD>` |
| Node Code | `<TBD>` |
| Role Code | `<TBD>` |
| Directory Provider | `<TBD>` |
| Directory Contract Version | `<TBD>` |
| Directory Revision Policy | `<TBD>` |
| Business Scope | `<TBD>` |
| DataScope Policy Version | `<TBD>` |
| Platform SoD Policy Version | `<TBD>` |
| Business SoD Policy Version | `<TBD>` |
| Feature Flag Version | `<TBD>` |
| Canary Config Version | `<TBD>` |
| Kill Switch Config Version | `<TBD>` |
| Audit Sink Contract Version | `<TBD>` |
| Start Time | `<TBD>` |
| End Time | `<TBD>` |
| Observation Window | `<TBD>` |
| Expected Volume | `<TBD>` |
| Maximum Claims | `<TBD>` |
| Maximum Concurrent Claims | `<TBD>` |
| Release Approver | `<TBD>` |
| Business Owner | `<TBD>` |
| Workflow Owner | `<TBD>` |
| Security/Audit Owner | `<TBD>` |
| Operations Owner | `<TBD>` |
| Rollback Owner | `<TBD>` |

## Scope invariants

- Exactly one enterprise, definition, version and ROLE node; no wildcard or range.
- Dedicated non-production identities and data only until a separately approved production change.
- Directory revision, all policy versions and audit contract are frozen before approval.
- `Maximum Claims` and `Maximum Concurrent Claims` are hard limits, not estimates.
- Approval does not imply `ROLE_DIRECTORY_V1=ACTIVE` or `ROLE_RUNTIME=ENABLED`.

