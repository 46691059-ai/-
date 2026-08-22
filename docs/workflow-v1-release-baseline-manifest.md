# Workflow V1 Release Baseline Manifest

Manifest status: `CANDIDATE_INVENTORIED / FREEZE_BLOCKED`. Values are locally verified on 2026-08-21; no commit or tag was created.

| Item | Verified value |
|---|---|
| Repository | `D:\国企数字化管理系统\enterprise-platform` |
| Branch | `feature/project-module` |
| HEAD | `f2429571c5255366b26553fe29064b6052faa437` |
| Java | Eclipse Temurin 21.0.12+8 LTS |
| Maven | 3.9.9 |
| Node / npm | 24.18.0 / 11.16.0 |
| MySQL baseline | Community Server 8.4.9 |
| Flyway baseline | Community 13.0.0 |
| Latest Migration | V2.6.19 |
| Migration count | 41 versioned SQL assets |
| Migration SHA result | 41/41 MATCH |
| V2.6.19 SHA-256 | `5bcc02c3447af2e6a8af5b258d24e4a1148b333352bc39728623b3d4da4d5f6a` |
| V2.6.19 Flyway checksum | `1332327310` |
| Full Schema fingerprint | `708a0d4bb3dab6212bb5c11ad7825333432e3e1bd3d2e6f894e27991b41e916d` |
| Audit Schema fingerprint | `4a1809c4ce93f3ed6bf33ae3449b3ece4da811f698768ca2c5f3f0789ebe39ce` |
| Workflow tests | 348 |
| Backend tests | 582, 0 failures/errors/skips |
| Frontend tests | 24, 0 failures; typecheck/build PASS |
| Release asset files | 786 inventoried files before this release-document pack |
| Directory Contract | `ROLE_DIRECTORY_PORT_V1` |
| Directory Contract Hash | `5e8f9870effc7f886671f3cc5077a7bf0419cd9a6698dd6ca01e9de0c01f708d` |
| Audit Event Contract | `ROLE_CLAIM_AUDIT_EVENT_V1` |
| Audit Receipt Canonical | `EXTERNAL_AUDIT_RECEIPT_V1` |
| ROLE_DIRECTORY status | `EXECUTION_ELIGIBLE / NON_ACTIVE` technical qualification; runtime descriptor non-executable |
| ROLE_RUNTIME status | `DISABLED` |
| EXPLICIT_USER_V1 | `ACTIVE` |
| Feature Flag default | `OFF` (`claim-enabled=false`) |
| Canary default | `OFF` / exact scope fields empty |
| Kill Switch default | `STOP_NEW_AND_CLAIM` |
| Release commit | `PENDING / NOT_CREATED` |
| Proposed tag | `workflow-v1.0.0-rc1 / NOT_CREATED` |

## Frozen Migration hashes

| Version | SHA-256 |
|---|---|
| V2.6.15 | `db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44` |
| V2.6.16 | `d5d0b154eaea4c17dc8b11d7bebda0627c9f31fd96b13043545356a5a2286084` |
| V2.6.17 | `d5fdf54be071a1f4c347b61a447771bb0c849f2ff8d4113e9bedc7ae4d145b65` |
| V2.6.18 | `feca0f252d44a61938552ed37764aecc2d0cf78aa9e35d81b71d5bf73d1d3542` |
| V2.6.19 | `5bcc02c3447af2e6a8af5b258d24e4a1148b333352bc39728623b3d4da4d5f6a` |

## Canonical versions

- `ROLE_BINDING_CANONICAL_V1`
- `ROLE_CANDIDATE_CANONICAL_V1`
- `ROLE_RUNTIME_CANONICAL_V1`
- `ROLE_RUNTIME_ACTIVATION_CANONICAL_V1`
- `ROLE_RUNTIME_BINDING_PROMOTION_CANONICAL_V1`
- `ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1`
- `ROLE_RUNTIME_EXECUTION_ADMISSION_CANONICAL_V1`
- `ROLE_RUNTIME_EXECUTION_ADMISSION_PERSISTENCE_CANONICAL_V1`
- `ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1`
- `ROLE_REALTIME_ELIGIBILITY_PERSISTENCE_CANONICAL_V1`
- `ROLE_RUNTIME_CAPABILITY_EVIDENCE_ROOT_CANONICAL_V1`

## Evidence references

- MySQL/Flyway: `D:\codex-validation-wf-audit-final-20260821-112734\evidence`
- HTTPS/Audit/Concurrency: `D:\codex-validation-wf-audit-https-20260821-114535`

## Freeze blockers

The current HEAD does not contain the accumulated release assets; the working tree is not clean. More importantly, the enabled Approval Role Directory Provider has only `InMemoryApprovalRoleDirectoryProviderAuditSink` as its main-source audit sink. The manifest therefore cannot assert a reproducible or production-integrity-safe release baseline.

