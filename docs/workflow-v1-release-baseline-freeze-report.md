# Workflow V1 Release Baseline Freeze & ROLE Runtime Canary Release Preparation

## 1. Final conclusion

`RELEASE_BASELINE_FREEZE_BLOCKED / ROLE_RUNTIME_CANARY_RELEASE_NOT_READY / ROLE_RUNTIME_DISABLED`.

Workflow V1 remains `INTERNAL_ENGINEERING_COMPLETE`; the completed engineering assets are not downgraded. Release preparation documents and inventories are complete, Migration and clean-build gates pass, and runtime remains safely disabled. Freeze cannot be declared because the tree is not reproducible and a production dependency P0 exists: the Approval Role Directory Provider's only main-source audit implementation is in-memory.

## 2. Repository and working-tree inventory

- Repository: `D:\国企数字化管理系统\enterprise-platform`
- Branch: `feature/project-module`
- HEAD: `f2429571c5255366b26553fe29064b6052faa437`
- Staged: 0.
- Tracked modified: 18 before this documentation pack.
- Untracked: 778 files before this documentation pack (`--untracked-files=all`); accumulated backend, frontend, database, scripts and 114 documents. After adding the eight required release documents, the final count is 786 untracked files.
- Ignored/generated: `backend/target`, `backend/logs`, `frontend/node_modules`, `frontend/dist`, `frontend/tsconfig.tsbuildinfo`, `logs`.
- No automatic add, commit, tag, push, clean, reset, checkout or deletion occurred.

### Classification

| Class | Inventory decision |
|---|---|
| TRACKED_COMMITTED | Current HEAD and tracked unchanged sources/configuration |
| TRACKED_MODIFIED | 18 files across application bootstrap, RBAC menu, security/config, Flyway governance and frontend menu |
| STAGED | none |
| UNTRACKED | accumulated Workflow/Organization/Governance source/tests, 33 database assets, 21 Flyway assets, 114 docs and related files |
| GENERATED | Maven target, frontend dist/tsbuildinfo and logs; release-excluded |
| LOCAL_ONLY | disposable validation roots under `D:\codex-validation-*`; release-excluded evidence locations |
| SECRET_SUSPECT | none found; examples contain placeholders only |
| RELEASE_REQUIRED | 786 Workflow V1 source/test/migration/script/document assets inventoried before this pack |
| RELEASE_EXCLUDED | generated outputs, local logs, node_modules, disposable MySQL/TLS material and archived failed candidates from Flyway scanning |

The complete machine-readable source of this inventory is `git status --porcelain=v1 --untracked-files=all`; because it is non-empty, no tag can currently reproduce the release assets.

## 3. Release asset inventory

| Area | Files | Result |
|---|---:|---|
| Workflow main | 434 | inventoried |
| Organization Approval Role Directory main | 48 | inventoried |
| Governance Audit main | 7 | inventoried |
| Workflow tests | 110 files / 348 tests | PASS |
| Organization tests | 8 files / 31 tests | PASS |
| Governance Audit tests | 3 files / 9 tests | PASS |
| Workflow V2.5/V2.6 migrations | 26 | inventoried |
| Workflow validation scripts | 19 | inventoried |
| Workflow/Approval Role docs | 131 | inventoried |
| Total release asset files | 786 | inventory complete |

Domain/Application/Infrastructure/Repository/Mapper/Entity/API, definition/runtime/claim/pool/eligibility/directory/DataScope/SoD/audit/controls, Flyway assets, tests, scripts, runbooks, checklist, frozen baseline, DoD and external capability reports were covered. No orphan scanned Migration exists. Main-source non-wired InMemory circuit-breaker and promotion repository are release-excluded framework/test artifacts; the conditional Directory Provider audit sink is wired and is the P0 below.

## 4. Migration canonical audit

- 41 versioned SQL files, 41 SHA entries, 41/41 MATCH.
- Duplicate versions: 0; missing/unregistered scanned SQL: 0; SHA orphans: 0.
- Five archived failed-candidate SQL files are outside the canonical Flyway scan directory.
- V2.6.15–V2.6.19 hashes match the manifest; V2.6.19 SHA is `5bcc02c3447af2e6a8af5b258d24e4a1148b333352bc39728623b3d4da4d5f6a`, checksum `1332327310`.
- No Migration was created or modified in this release-preparation task.

## 5. Clean-build gate

| Gate | Result |
|---|---|
| Java 21 compile / Maven test | PASS |
| Spring Boot Context | PASS |
| Workflow tests | 348 PASS |
| Organization/Directory | 31 PASS |
| Audit | 9 PASS |
| Backend total | 582 PASS, 0 failure/error/skip |
| Frontend typecheck/build | PASS |
| Frontend tests | 24 PASS |
| `git diff --check` | PASS |
| Domain forbidden imports | 0 |
| Secret/PII scan | PASS; no committed secret or prohibited PII payload |
| Dangerous activation scan | PASS |
| Migration SHA | 41/41 PASS |
| Production Fake/Stub/InMemory | **FAIL / P0** |

## 6. Production dependency P0

`backend/src/main/java/.../InMemoryApprovalRoleDirectoryProviderAuditSink.java` is annotated `@Component` and activated with `app.approval-role-directory.provider.enabled=true`. It is the sole implementation supplied to `ApprovalRoleDirectoryProviderFacade`. Its bounded deque is volatile and does not provide durable governance audit evidence. This is an explicit P0 under the release rules; no fake result was used to pass the gate.

Other main-source InMemory classes found are not Spring-wired and are referenced only by tests, so they are not the blocking production path. The P0 cannot be resolved through configuration or documentation without disabling the required Directory Provider capability.

## 7. Runtime activation safety

- `workflow.role-runtime.claim-enabled` defaults false.
- Canary identifiers default empty; there is no wildcard.
- Kill Switch defaults `STOP_NEW_AND_CLAIM`.
- Approval Role Directory Provider, Governance Audit Sink and Workflow external audit worker default disabled.
- `ROLE_DIRECTORY_V1` remains non-ACTIVE and its resolver descriptor is non-executable.
- Technical status `EXECUTION_ELIGIBLE / NON_ACTIVE` is not a runtime switch.
- Missing dynamic controls fail closed; no force Claim, fallback allow or skip-SoD/DataScope/Audit/Eligibility path was found.
- Development-only DataScope test endpoint is isolated to `application-dev.yml` and is release-excluded.

## 8. Release commit and tag proposal

- Proposed commit: `release(workflow): freeze workflow v1 role runtime canary baseline`
- Proposed tag: `workflow-v1.0.0-rc1`
- State: `PROPOSED / NOT_CREATED`.
- Current HEAD cannot represent the release because required assets remain uncommitted.

## 9. Files created by this task

1. `docs/workflow-v1-release-baseline-freeze-report.md`
2. `docs/workflow-v1-release-baseline-manifest.md`
3. `docs/workflow-v1-role-runtime-canary-scope-template.md`
4. `docs/workflow-v1-role-runtime-canary-go-no-go-checklist.md`
5. `docs/workflow-v1-role-runtime-canary-activation-procedure.md`
6. `docs/workflow-v1-role-runtime-canary-rollback-runbook.md`
7. `docs/workflow-v1-role-runtime-canary-monitoring-acceptance-matrix.md`
8. `docs/workflow-v1-role-runtime-canary-release-change-pack.md`

No business code, Investment code, Migration, Runtime object, Feature Flag, Canary or registry state was changed.

## 10. Final states and next action

- `WORKFLOW_V1_INTERNAL_ENGINEERING_COMPLETE`
- `WORKFLOW_V1_RELEASE_BASELINE_FREEZE_BLOCKED`
- `ROLE_RUNTIME_CANARY_RELEASE_NOT_READY`
- `RELEASE_BASELINE_COMMIT_PENDING`
- `ROLE_DIRECTORY_V1_EXECUTION_ELIGIBLE_NON_ACTIVE`
- `ROLE_RUNTIME_DISABLED`
- `CANARY_NOT_AUTHORIZED / CANARY_NOT_ENABLED`

Only next recommendation: obtain authorization for a durable, append-only Approval Role Directory Provider audit adapter, implement and validate it without activating Runtime; then rerun this freeze gate before any commit/tag or Canary approval.
