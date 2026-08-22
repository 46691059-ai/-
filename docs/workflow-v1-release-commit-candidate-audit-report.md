# Workflow V1 Release Commit Candidate Audit & Baseline Freeze Revalidation

审计日期：2026-08-22（Asia/Shanghai）

## 1. Final Conclusion

**WORKFLOW_V1_RELEASE_COMMIT_CANDIDATE_READY**

**WORKFLOW_V1_RELEASE_BASELINE_READY_TO_COMMIT**

**ROLE_RUNTIME_DISABLED**

**CANARY_NOT_AUTHORIZED**

当前 HEAD 本身不包含未提交资产，故 `NOT REPRODUCIBLE AS WORKFLOW V1 RELEASE`；经完整分类与当前工作树 Gate 验证，当前工作树为 `READY_TO_FORM_REPRODUCIBLE_RELEASE_COMMIT`。本任务没有创建 Commit 或 Tag。

## 2. Branch

`feature/project-module`

## 3. HEAD

`f2429571c5255366b26553fe29064b6052faa437`

## 4. Dirty Entry Count

审计输入 813 项；加入本 Manifest 与本报告后最终候选 815 项。

## 5. Tracked Modified

18 项，均已分类并纳入候选。

## 6. Untracked

审计输入 795 项；加入两份治理文档后为 797 项。

## 7. Staged

0。

## 8. Conflicts

0；未发现 `UU/AA/DD` 等冲突状态。

## 9. Release Core Count

504：后端主源码/配置 501，前端 RBAC 菜单契约 3。

## 10. Release Test Count

129 个 dirty test source 文件，全部纳入。

## 11. Release Docs Count

初始 123；加入本任务两个正式治理输出后为 125。

## 12. Migration Count

30 个 dirty 正式治理资产：27 个 V2.5.0—V2.6.20 SQL，加 inventory、SHA256SUMS、README 三项。

## 13. Scripts

22：20 个 `database/flyway/scripts` 新增验证脚本、一个候选验证脚本、一个人工审计 fixture。

## 14. Failed Candidate Archive

5 项，纳入 Release 审计证据。它们位于 `database/migration/archive/failed-candidates`，不在 canonical `database/migration/mysql` 扫描根，也不进入正式 SHA256SUMS。

## 15. Generated Artifacts

Git 状态项 0。测试产生的 `backend/target` 与 `frontend/dist` 已由 `.gitignore` 排除，不属于候选。

## 16. Ephemeral Resources

Git 状态项 0。没有临时 MySQL datadir、TLS keystore/truststore、证书、token、端口文件或运行日志进入候选。

## 17. Local Environment

Git 状态项 0。IDE、`.env`、logs、deploy data/secrets/backups 已被忽略；仓库外 JDK/Maven 与隔离验收证据不进入候选。

## 18. Secret Suspects

0。证书/私钥文件和 PEM marker 均为 0；脚本中的 token 为运行时 GUID，JWT 为明确 test-only 字面量，数据库验收为空密码隔离实例。配置仅保存空默认值/环境变量引用。

## 19. Out of Scope

0。Investment、Project、HR、党建、财务无 dirty entry；System/Security/Frontend 的 12 项共享改动为 Workflow RBAC、内部 Provider 鉴权与菜单契约的不可分割依赖。

## 20. Unknown

0。完整分组见 `workflow-v1-release-commit-candidate-manifest.md`。

## 21. Migration Integrity

- Canonical SQL：42。
- SHA256SUMS：42 条，`42/42 PASS`，无缺失或漂移。
- V2.6.15—V2.6.20 均匹配治理清单。
- V2.6.20 SHA-256：`160f0b649ac85cc177d82c2ff0aa5070639b29c7f2dda72b02438041bd063201`。
- V2.6.20 Flyway checksum：`1721966129`（已验证资产记录）。
- V2.6.20：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。
- 本次未修改或执行任何 Migration。

## 22. Production Fake Scan

PASS。Spring-wired、可执行生产路径中的 Fake/InMemory/NoOp 为 0；`InMemoryApprovalRoleDirectoryProviderAuditSink` 在 `src/main` 为 0。两项名称含 InMemory 的普通 final 类无 Spring 注解且只被测试引用，不构成生产 fallback；Provider 审计由持久 Adapter 且 fail-closed。

## 23. Secret Scan

PASS。未发现 private key、真实 token/credential、硬编码生产 secret 或证书私钥。配置 secret 使用环境注入。

## 24. PII Scan

PASS。Provider audit schema 仅保存业务键、版本/Hash、revision、candidate count、结果与时间；不保存姓名、电话、证件、地址、薪资、完整成员、token、secret、credential 或 authorization。

## 25. Backend Tests

Java `21.0.12`、Maven `3.9.9`，当前真实工作树执行 `mvn test`：**587 tests / 0 failures / 0 errors / 6 conditional skips，BUILD SUCCESS**。其中 Workflow 348、Organization/Directory 36、Governance/Audit 9（6 个外部环境条件性 skip）；Spring Boot Context 通过。

## 26. Frontend Tests

Node `24.18.0`、npm `11.16.0`：Vitest **9 files / 24 tests PASS**；`npm run type-check` PASS。

## 27. Build

后端 Java 21 compile/test compile PASS；前端 `npm run build` PASS（1716 modules transformed）。`git diff --check` PASS；候选级 untracked 扫描发现并机械清理 37 份历史 Markdown 的 96 处行尾空格，复扫为 0，未改变历史文字或结论。

## 28. Runtime Safety

- `ROLE_DIRECTORY_V1 = EXECUTION_ELIGIBLE / NON_ACTIVE`；Registry descriptor 保持 `PREPARED / enabled=false`。
- `EXPLICIT_USER_V1 = ACTIVE`，既有 USER + DIRECT 行为未改变。
- `ROLE_RUNTIME = DISABLED`；`workflow.role-runtime.claim-enabled` 默认 `false`。
- Feature/Provider/Audit/Worker 默认 OFF。
- Canary scope 未授权，不能使用空值/通配范围启用。
- Kill Switch 默认 `STOP_NEW_AND_CLAIM`。
- 未发现 fallback allow、DataScope/SoD/Audit/Eligibility bypass、skip audit 或 skip eligibility。
- `CANARY = NOT_AUTHORIZED / NOT_ENABLED`。

## 29. Release Cohesion

`ONE COHERENT RELEASE COMMIT`。所有资产来自未提交的 Workflow V1 连续依赖链；Migration、代码、测试、脚本和证据共同构成首个可复现基线。拆成多个提交会制造不可编译或缺少治理证据的中间状态，因此不建议 split。

## 30. Proposed Commit Scope

最终 815 项：Workflow/Organization Approval Role/Governance Audit 正式实现，共享 RBAC/Security/Frontend 适配，V2.5.0—V2.6.20 Migration 及治理资产，完整测试与验证脚本，冻结架构、运行、Release/Canary 和历史验收证据。

## 31. Proposed Commit Message

`release(workflow): freeze workflow v1 canary-ready baseline`

建议 description 覆盖：Workflow V1、Approval Role Directory、Realtime Eligibility、Claim Runtime、DataScope、SoD、External Audit、Canary controls、Migrations through V2.6.20、Release governance。

## 32. Proposed Tag

`workflow-v1.0.0-rc1`，状态 `PROPOSED / NOT_CREATED`。

## 33. Remaining Risks

1. HEAD 在人工提交前仍不可复现当前 Workflow V1；必须按 Manifest 精确暂存并人工复核 staged diff。
2. ROLE Directory 真实生产资源、Canary 业务授权与生产发布审批仍不在本任务授权范围。
3. 六个 Governance/Audit 测试因需要外部真实环境而条件性跳过；相关能力已有隔离 MySQL/HTTPS 历史验收证据，本次没有伪造或替代真实联调。
4. 两项非 Spring-wired InMemory framework/test helper 会编译入主 jar，但不可执行且无生产注入路径；未来可单独治理源码布局，不阻断当前 Gate。
5. Working Tree 规模大；人工 `git add` 前后必须复核文件数、SHA、staged secret scan 和 `git diff --cached --check`。

## 34. Manual Actions Required

本报告仅授权进入人工 Git Commit Freeze，不代替审批。人工操作者应：

1. 按 Manifest 暂存 815 项，确认没有额外生成物、环境文件或未知项。
2. 重新执行 `git status --short`、`git diff --cached --name-status`、`git diff --cached --check`、42/42 SHA 与 staged secret scan。
3. 人工创建建议 Commit；核对 Commit tree 后再经发布负责人批准创建 RC Tag。
4. 不得因 Commit/Tag 自动开启 ROLE Runtime 或 Canary。

本任务未执行任何 Git 写操作。

## 35. Final Gate

| Gate | Result |
|---|---|
| Unknown / Secret / Conflict | 0 / 0 / 0 |
| Out-of-scope | 0 |
| Production Fake | PASS（wired/executable = 0） |
| Migration SHA | 42/42 PASS |
| Backend / Frontend / Build | PASS |
| Domain purity / dependency scan | PASS |
| Secret / PII | PASS |
| Runtime safety | PASS |
| Release cohesion | PASS — single baseline commit |

最终判定：**允许进入人工 Git Commit Freeze**。

Release Commit：`NOT_CREATED`。

RC Tag：`NOT_CREATED`。

ROLE Runtime：`DISABLED`。

Canary：`NOT_AUTHORIZED / NOT_ENABLED`。
