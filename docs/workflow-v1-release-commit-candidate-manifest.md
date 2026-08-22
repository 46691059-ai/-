# Workflow V1 Release Commit Candidate Manifest

审计时间：2026-08-22（Asia/Shanghai）

基线分支：`feature/project-module`

基线 HEAD：`f2429571c5255366b26553fe29064b6052faa437`
审计性质：只读分类与 Release Commit Candidate 固化；未执行 `git add/commit/tag/push/reset/clean/checkout/restore`。

## 1. 结论与计数口径

初始真实 Working Tree 共 **813** 项：18 tracked modified、795 untracked、0 staged、0 conflict。813 项均已归入可审计分组，`UNKNOWN=0`、`SECRET_SUSPECT=0`、`OUT_OF_SCOPE=0`。

本次任务按要求新增本 Manifest 与最终审计报告两项正式 Release 治理文档。因此，人工提交时的候选集合为 **815** 项；这两个输出不改变源码、Migration 或运行状态。

| 口径 | 数量 |
|---|---:|
| 初始 dirty entries | 813 |
| 本任务新增治理文档 | 2 |
| 最终 Release Commit Candidate | 815 |
| Include | 815 |
| Exclude（Git 状态项） | 0 |
| Unknown | 0 |
| Secret suspect | 0 |
| Out of scope | 0 |
| Conflict / staged | 0 / 0 |

## 2. 可审计资产分组

下表分组对初始 813 项实现无重叠、无遗漏覆盖；`gitStatus` 为该组实际状态集合。

| path / group | gitStatus | classification | count | includeInReleaseCommit | reason | risk / notes |
|---|---|---|---:|---|---|---|
| `backend/src/main/java/cn/gov/enterprise/modules/workflow/**` | `??` | RELEASE_CORE | 434 | YES | Workflow V1 定义、运行、任务、Assignment、Resolver、Candidate、Claim、ROLE 治理与外部能力完整实现链 | ROLE 仍被 Gate 禁用；不得以类存在推断已启用 |
| `backend/src/main/java/cn/gov/enterprise/modules/organization/approvalrole/**` | `??` | RELEASE_CORE | 50 | YES | Approval Role Directory、Provider 与持久审计是 ROLE Runtime 的正式外部能力依赖 | Provider 默认 OFF；真实资源启用仍需发布授权 |
| `backend/src/main/java/cn/gov/enterprise/modules/governance/audit/**` | `??` | RELEASE_CORE | 7 | YES | 外部审计 Sink 与 fail-closed 能力属于 Release 安全闭环 | Sink 默认 OFF |
| `backend/src/main/java/cn/gov/enterprise/modules/system/**` | ` M` | RELEASE_CORE | 6 | YES | Workflow RBAC 菜单稳定业务键与权限接入的不可分割共享底座 | 仅 Workflow 所需 RBAC 兼容改动 |
| `backend/src/main/java/cn/gov/enterprise/security/**` | ` M` | RELEASE_CORE | 2 | YES | Provider/internal endpoint 的鉴权与安全链路 | 未放宽默认权限 |
| `backend/src/main/java/cn/gov/enterprise/EnterprisePlatformApplication.java` | ` M` | RELEASE_CORE | 1 | YES | Workflow/Organization/Governance 模块扫描入口 | 无独立业务功能扩张 |
| `backend/src/main/resources/application.yml` | ` M` | RELEASE_CORE | 1 | YES | Directory、Audit、Workflow 外部能力与安全默认配置 | Secret 均环境注入；默认 OFF |
| `frontend/src/api/menu.test.ts`, `frontend/src/types/menu.ts`, `frontend/src/views/system/menu/index.vue` | ` M` | RELEASE_CORE | 3 | YES | Workflow RBAC 菜单稳定业务键的前端契约与展示适配 | 无独立前端功能包 |
| `backend/src/test/java/**` | ` M`, `??` | RELEASE_TEST | 129 | YES | Domain、Repository、权限、事务、Migration 契约、Directory/Audit/Capability、安全和回归测试 | 含测试专用 InMemory/Fake；不进入生产装配 |
| `docs/**`（审计前已有） | `??` | RELEASE_DOC | 123 | YES | 冻结设计、实施、真实验收、运行、Release、Canary 与历史 Sprint 证据 | 历史失败结论保留，不重写 |
| `database/migration/mysql/V2.5.0__*.sql`—`V2.6.20__*.sql` | `??` | RELEASE_MIGRATION | 27 | YES | Workflow V1 canonical 增量 Migration 链 | 全部进入正式扫描目录；禁止再修改 |
| `database/flyway/migration-inventory.yml`, `database/migration/mysql/SHA256SUMS`, `database/migration/mysql/README.md` | ` M` | RELEASE_MIGRATION | 3 | YES | Migration 状态、SHA 与执行治理权威资产 | 42/42 校验通过 |
| `database/flyway/scripts/*.ps1`, `database/flyway/validate-v2613-candidate.ps1`, `database/mysql/manual/wf-audit-final-outbox-fixture.sql` | `??` | RELEASE_SCRIPT | 22 | YES | 隔离 MySQL/Flyway、HTTPS、审计与治理验收脚本/fixture | 运行时生成 token/TLS；未提交生成物或真实凭据 |
| `database/migration/archive/failed-candidates/*.sql` | `??` | FAILED_CANDIDATE_ARCHIVE | 5 | YES | 保留失败候选 SHA 与缺陷演进的审计证据 | 位于正式 Flyway `database/migration/mysql` 扫描根之外；不进入 canonical SHA256SUMS |
| `docs/workflow-v1-release-commit-candidate-manifest.md` | `??` | RELEASE_DOC | 1 | YES | 当前候选集合的唯一审计清单 | 本任务新增 |
| `docs/workflow-v1-release-commit-candidate-audit-report.md` | `??` | RELEASE_DOC | 1 | YES | Release Gate 最终证据与人工动作边界 | 本任务新增 |

分类汇总（最终 815 项）：`RELEASE_CORE=504`、`RELEASE_TEST=129`、`RELEASE_DOC=125`、`RELEASE_MIGRATION=30`、`RELEASE_SCRIPT=22`、`FAILED_CANDIDATE_ARCHIVE=5`。

## 3. 排除与敏感资产审计

Git 状态中没有 `target/`、`dist/`、`build/`、`coverage/`、日志、MySQL datadir、binlog、socket、pid、临时 Schema、`.env`、IDE 文件或 TLS/证书文件，因此 dirty-entry 排除数为 0。仓库 `.gitignore` 已覆盖 `backend/target/`、`frontend/node_modules/`、`frontend/dist/`、`*.log`、`.env`、`logs/`、`deploy/data/`、`deploy/secrets/`、`deploy/backups/` 和生产环境文件。

候选级 untracked whitespace 扫描曾发现 37 份历史 Markdown 的 96 处旧式行尾空格；本任务仅机械移除这些空格，没有修改历史文字或结论。复扫结果为 0。

证书扩展名与 PEM private-key marker 均为 0。命中的 token/password 字面量仅为测试断言、明确的 `test-only` JWT 值、空数据库密码或运行时随机 GUID；没有真实 service token、credential、private key。故 `SECRET_SUSPECT=0`。不自动修改 `.gitignore`；建议仅在未来出现仓库内本地验证目录时，再评估增加精确目录规则，避免泛化忽略正式公开测试 fixture。

## 4. Out-of-Scope 与共享依赖

Investment、Project、HR、党建、财务等业务模块没有 dirty entry。System/Menu、Security 和三项 Frontend 变更虽位于共享模块，但分别承载 Workflow RBAC `menu_code`、内部 Provider 鉴权和菜单契约，无法从 Workflow V1 基线剥离，故归入 `RELEASE_CORE` 而非 `OUT_OF_SCOPE`。

## 5. 文档入口层级

- Frozen Baseline：`workflow-v1-frozen-baseline.md`、`workflow-v1-definition-of-done.md`、`workflow-v1-release-baseline-manifest.md`。
- Final Architecture：Workflow Center、multi-node、assignment/resolver、ROLE runtime 各冻结设计。
- Operations：`workflow-v1-runtime-operations-runbook.md` 与 Directory/Audit 运维约束。
- Release：本 Manifest、最终审计报告、release readiness/checklist/change pack。
- Canary：activation procedure、scope template、monitoring matrix、rollback runbook、go/no-go checklist。
- Historical Sprint Evidence：WF2—WF5 实施/验收报告及 `failed-candidates`。

## 6. Commit Cohesion

建议 **ONE COHERENT RELEASE COMMIT**。理由：V2.5.0—V2.6.20、Workflow/Directory/Audit 源码、共享 RBAC/Security、测试、脚本和证据尚未形成中间提交，彼此构成一条可编译、可迁移、可验证的完整依赖链。拆分会产生不可构建或不可审计的中间状态；无需为美化历史人工重写链路。

建议 Commit：`release(workflow): freeze workflow v1 canary-ready baseline`

建议 Tag：`workflow-v1.0.0-rc1`
状态：`PROPOSED / NOT_CREATED`。
