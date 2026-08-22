# Workflow V1.0 Internal Engineering Closure Report

## 1. Executive Summary

Sprint 2-3.7-WF-FINAL 对 Workflow V1.0 完成了代码、数据库、Canonical、Runtime Safety、Legacy、测试和运维文档的内部工程收口。审计未发现 P0/P1 内部工程阻断项；发现并修复一项 P2 边界缺陷：Sandbox Fake Directory 与 Sandbox 执行模型曾位于生产源码，现已完整迁移到测试源码，并移除生产持久化策略中的 Sandbox 专用入口。

内部工程门禁通过不等于 ROLE 生产启用。真实 Directory 及外部 Capability 仍缺少当前 TEST/PREPROD 资源与验收证据。

## 2. Scope

本次仅执行 Audit、Cleanup、Consolidation、Documentation、Test Baseline Freeze、Operational Readiness、Architecture Boundary Verification 和内部无语义缺陷修复。未新增业务功能、Controller/API、Migration 或 V2.6.17；未修改 Investment；未连接 Directory、读取 Secret、启用 ROLE Runtime 或产生真实 ROLE Task/Pool/Claim。

## 3. Current Baseline

| 项目 | 仓库实测状态 |
|---|---|
| 审计 HEAD | `f2429571c5255366b26553fe29064b6052faa437` |
| Migration SHA | 38/38 PASS |
| V2.6.15 | SHA `db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44`; Flyway `538981271`; Canonical/Validated |
| V2.6.16 | SHA `d5d0b154eaea4c17dc8b11d7bebda0627c9f31fd96b13043545356a5a2286084`; Flyway `-1570425240`; Canonical/Validated |
| EXPLICIT_USER_V1 | ACTIVE |
| ROLE_DIRECTORY_V1 | PREPARED / NON_EXECUTABLE |
| ROLE Runtime | DISABLED |
| Feature Flag | 默认 OFF |
| Canary | 默认空范围 |
| Kill Switch | `STOP_NEW_AND_CLAIM` |
| 外部资源 | WF5.25 / Directory / DataScope BLOCKED |

当前工作区包含历次 Sprint 累计未提交资产；本次不执行提交操作。正式 Release 仍需形成唯一可复现 Git commit/tag。

## 4. Asset Inventory

扫描范围为 `backend/src/main`、`backend/src/test`、`database/migration/mysql`、`database/flyway` 与 `docs`。

| 分类 | 数量/结果 | 主要位置或对象 |
|---|---:|---|
| Workflow 生产 Java 文件 | 417 | `backend/src/main/java/cn/gov/enterprise/modules/workflow` |
| Domain Models/Services | 216 个源文件；其中 Service/Resolver/Validator/Policy/Canonical/Executor 31 | `domain/assignment`、`candidate`、`claim`、`model`、`role`、`service` |
| Application Services | 21 | Definition、Runtime、Linear、Assignment、Candidate、Claim、Activation、Binding、Admission、Eligibility |
| Ports | 38 个 `*Port`/`*Repository` 接口 | `domain/repository` 及各领域 Port |
| Infrastructure | 151 个源文件 | `infrastructure/directory`、`eligibility`、`persistence`、`security` |
| Repository Interfaces | 31 | `domain/repository` |
| Repository Implementations | 30 | `infrastructure/persistence/repository` |
| MyBatis Entity | 35 | `infrastructure/persistence/entity` |
| Mapper/映射辅助 | 44 | `infrastructure/persistence/mapper` 及 Entity Mapper |
| Controller/API | 6 / 23 mappings | Definition、Runtime、Task、Resolver、Transition、NodeExecution |
| Workflow/ROLE 表 | 33 | Definition/Runtime/Assignment/Claim/ROLE Evidence 链 |
| Workflow Migration | 23 | V2.5.0—V2.6.16 |
| Trigger | 49 个唯一名称 | append-only、Guard、Hash/关联保护 |
| CHECK / FK / UNIQUE 声明 | 263 / 76 / 99 | V2.5/V2.6 SQL 静态统计 |
| Canonical/Hash 实现文件 | 16 | Role、Binding、Activation、Admission、Eligibility、Evidence |
| Resolver/Registry | EXPLICIT USER + ROLE Directory | USER ACTIVE；ROLE PREPARED |
| Feature Flag/Canary/Kill Switch | 1 套运行门禁 | `ConfiguredRoleClaimRuntimeGate` |
| Audit/Activation/Binding/Admission | 均有 Domain + Persistence + Test | V2.6.9—V2.6.16 |
| Candidate Pool/Claim | Domain/Application/Persistence 完整 | V2.6.6—V2.6.8 |
| Realtime Eligibility | 27 Validator + 10 Capability Evidence | V2.6.16 |
| Fake/Test Adapter | 仅测试源码 | `backend/src/test/.../workflow/domain/sandbox` |
| Workflow tests | 103 文件 / 341 `@Test` | `backend/src/test/.../workflow` |
| Workflow docs（收口前） | 123 | `docs/workflow-*` |

核心表包括 `workflow_definition`、`workflow_version`、`workflow_node`、`workflow_instance`、`workflow_task`、`workflow_transition`、`workflow_node_execution`、Assignment Snapshot、Resolver Binding、Candidate Pool/Member、Claim/Audit、Activation、Binding、Admission、Realtime Eligibility Evidence/Event。

## 5. Architecture Audit

| 检查项 | 结果 | 证据 |
|---|---|---|
| Domain 依赖 Spring/MyBatis/Controller/Mapper/Entity/Infrastructure | PASS（无） | import 与类型名全量扫描 |
| Domain 直接调用 RepositoryImpl | PASS（无） | 实现类引用扫描 |
| ROLE 绕过 Admission/Activation/Binding/Candidate/Eligibility/Claim | PASS（无） | 应用链与危险语义扫描 |
| 生产代码依赖 Fake/Stub/Test fixture | PASS | Sandbox/Fake 已移至 test source；生产扫描为 0 |
| Workflow 依赖 Investment/userId 注入 | PASS（无） | Workflow 生产源码 Investment import 扫描为 0 |
| ROLE fallback 到 USER | PASS（无） | fallback 语义扫描 |
| Directory 异常自动选人 | PASS（无） | Resolver/Adapter 审计 |
| Pool 自动刷新/Claim 重建 Pool | PASS（无） | Candidate/Claim 链审计 |
| 历史 Instance 重新解析 ROLE | PASS（无） | Frozen binding/snapshot 链审计 |

## 6. Runtime Safety Audit

`ConfiguredRoleClaimRuntimeGate` 明确默认 `claim-enabled=false`、Kill Switch 为 `STOP_NEW_AND_CLAIM`、四级 Canary 范围为空。`RoleDirectoryResolver` 为 PREPARED/non-executable；`ExplicitUserResolver` 为 ACTIVE。扫描未发现 `autoEnableRoleRuntime`、`enableByDefault`、`fallbackEnable`、`defaultCanaryAll`、`ignoreKillSwitch`、`skipAdmission`、`skipEligibility`、`skipSoD` 或 `skipDataScope` 等实现。

生产源码不存在 Fake Adapter；测试 Sandbox 不能创建真实 Instance、Task、Pool、Claim，不能改变 Registry 状态。

## 7. Canonical Contract Matrix

| Contract | Producer | Consumer / Persistence | 算法与实现 | 测试与运行状态 |
|---|---|---|---|---|
| ROLE_DIRECTORY_PORT_V1 | RoleDirectoryPort/Adapter | RoleDirectoryResolver | 版本化 Port 契约 | Contract tests；PREPARED |
| ROLE_CANONICAL_JSON_V1 | RoleDirectoryCanonical | Directory Adapter/Resolver | UTF-8 + SHA-256 lower hex | Canonical tests；NON_EXECUTABLE |
| ROLE_CANDIDATE_CANONICAL_V1 | RoleCandidateCanonical | Candidate Draft/Pool Evidence | userId 排序去重、固定字段 | Hash stability tests；Preview |
| ROLE_BINDING_CANONICAL_V1 | RoleBindingCanonical | Proposal/Binding Set | 固定字段、顺序无关 | Binding tests；内部验证 |
| ROLE_RUNTIME_CANONICAL_V1 | RoleRuntimeCanonical | Eligibility/Candidate | SHA-256 lower hex | Runtime governance tests；禁用 |
| ROLE_RUNTIME_ACTIVATION_CANONICAL_V1 | Activation Canonical | V2.6.13 Activation Evidence | UTF-8/SHA-256、不可变证据 | Domain/DB tests；已持久化 |
| ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1 | RoleRuntimePersistencePolicy | V2.6.9—V2.6.12 | 大小写敏感 hash/version | Combined MySQL tests；可信 |
| ROLE_RUNTIME_BINDING_PROMOTION_CANONICAL_V1 | Promotion Canonical | V2.6.14 Promotion | 固定 Promotion evidence | Domain/DB tests；非执行 |
| ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1 | Persistence Canonical | V2.6.14 Snapshot/Event | append-only hash chain | MySQL tests；非执行 |
| ROLE_RUNTIME_EXECUTION_ADMISSION_CANONICAL_V1 | Admission Hash | V2.6.15 Admission | 固定 Gate 输入 | Domain/DB tests；非执行 |
| ROLE_RUNTIME_EXECUTION_ADMISSION_PERSISTENCE_CANONICAL_V1 | AdmissionPersistenceSupport | V2.6.15 Evidence/Event | append-only root | MySQL tests；非执行 |
| ROLE_RUNTIME_CAPABILITY_EVIDENCE_ROOT_CANONICAL_V1 | Capability Root Canonical | V2.6.15 Evidence | 有序 evidence root | Integrity tests；非执行 |
| ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1 | RealtimeEligibilityCanonical | Runtime eligibility | 27 checks + capability facts | Domain tests；非执行 |
| ROLE_REALTIME_ELIGIBILITY_PERSISTENCE_CANONICAL_V1 | Eligibility Persistence Canonical | V2.6.16 Evidence/Event | Validator/Capability/Event 三类 hash | MySQL tests；非执行 |

Candidate Hash、Directory Hash、Binding Hash、Activation Hash、Promotion Hash、Admission Hash、Validator Root、Capability Root、Persistence/Evidence Hash 均由上述版本化 Canonical 产生。未发现同一语义存在可进入生产的双实现、字段顺序漂移或 Java/DB 大小写语义缺口。V1 不升级 Contract。

## 8. Migration Baseline

| Version | Filename | SHA-256 | Flyway checksum | 状态 |
|---|---|---|---:|---|
| 2.5.0 | create_workflow_definition_domain | `5b41f9b787bbf4bdf0927b5f30dc487c6d88085d81aedf19e784d44474c02926` | -785031784 | Canonical/Validated |
| 2.5.1 | create_workflow_runtime | `e52ca6bdd00426bc7fee1c9b92cd3dc3fecd5084e3533f955c89c4867ceb4469` | -2076704484 | Canonical/Validated |
| 2.5.2 | create_workflow_task_action | `00ad19ad16dec01228f5ec45eb48ad0f8a9f887b4dfe27c797c4b5e56f508302` | -586677216 | Canonical/Validated |
| 2.5.3 | initialize_workflow_rbac | `6d6904da8028830cdc8fa7b9125994d37d2c916a09277ae9d4526f5646144e26` | 组合验收记录 | Canonical/Validated |
| 2.5.4 | govern_rbac_menu_unique_key | `dea9284682001d3e3ab8a667a4ec863d5e66a33a61771628f64044ce004768a7` | 组合验收记录 | Canonical/Validated |
| 2.5.5 | implement_workflow_version_release | `50efcb230b2cba2e7a7600fdfc3e229f05a9e85877194cf9f1eed0875d52605e` | 1152171452 | Canonical/Validated |
| 2.6.0 | create_workflow_multi_node_foundation | `76f2113573eac6af106fadb1681774e49a6f15713860a6af7b37f8d9ebc5c1fa` | 2093108182 | Canonical/Validated |
| 2.6.1 | enable_workflow_linear_runtime | `50314ff572253eaef6e0002acc2b1f9109fe7332a7360f8c8b14c4e8fde1a03e` | 762611150 | Canonical/Validated |
| 2.6.2 | create_workflow_task_assignment_snapshot | `9750454efa98402c7d22503d80e16579e07c964a01f7b4baa5da933db01a998f` | -1498011282 | Canonical/Validated |
| 2.6.3 | freeze_workflow_instance_resolver_version | `35c4714708d51ba00f4cdc79b04bea4d897a7e6c72624ec595c9df147f1f41e6` | 1481014267 | Canonical/with V2.6.4 |
| 2.6.4 | fix_resolver_contract_hash_collation | `0bb017ea621980e150870bb9bac3dd48863afbc2b63b33654ab6344aced9bb55` | -1847492777 | Canonical/Validated |
| 2.6.5 | create_workflow_multi_resolver_binding | `3d516374333f31554409d5d5c8f7ebaddf7ebd21dbc24916d3dc48c5b8337caf` | -588626998 | Canonical/Validated |
| 2.6.6 | create_workflow_candidate_pool | `3b73653d571a5f5497d10865b3ef692e694387b8d220ec2af383bea9c82e5d4f` | 398007297 | Canonical/Validated |
| 2.6.7 | create_workflow_task_claim | `800e1a5b1af67e1680a3f0233e9d5400c0b42f39349d181f1777c7ce04ac6aed` | 1689998435 | Canonical/with V2.6.8 |
| 2.6.8 | strengthen_workflow_claim_integrity | `4cfd089f4aa05f6e4fb9d9dc250159c0229b3d98eb400089e5e08a758cb4a207` | 1316060036 | Canonical/Validated |
| 2.6.9 | create_role_runtime_snapshot | `fbe41e2a354e616ee4b369c57980009057c75ced46a2eb9e48d1eb8354beb0d2` | 1523438049 | Canonical/with fixes |
| 2.6.10 | harden_role_runtime_persistence_integrity | `2fad3f67a4a487568f5fc29f40cec73894d89fff65564212a75be98da186f7d6` | 936550308 | Canonical/Validated |
| 2.6.11 | repair_role_runtime_historical_integrity_guard | `ee16801af2f989449b1372ff82d76bd9850950a51ea7badff0125804c0815b06` | 1342469954 | Canonical/Validated |
| 2.6.12 | fix_role_runtime_resolver_version_collation | `55513b47c278d2e7d8f5d7656115b1383c3c50494bd221d95f58c66f972ec67e` | -1172180638 | Canonical/Validated |
| 2.6.13 | create_role_runtime_activation_evidence | `da80b7d3483ba3f3ee6b4f938515e94730695e28fc9842b9e488eb8daa68fd17` | 1882937893 | Canonical/Validated |
| 2.6.14 | create_role_runtime_binding_persistence_foundation | `a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442` | -1734980808 | Canonical/Validated |
| 2.6.15 | create_role_runtime_execution_admission_persistence | `db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44` | 538981271 | Canonical/Validated |
| 2.6.16 | create_role_realtime_eligibility_evidence | `d5d0b154eaea4c17dc8b11d7bebda0627c9f31fd96b13043545356a5a2286084` | -1570425240 | Canonical/Validated |

Inventory、README 与 SHA256SUMS 一致；无重复版本、未登记正式 SQL、临时 SQL 或 V2.6.17。失败 Candidate 保留在 `database/migration/archive`，与正式扫描目录隔离。

## 9. Legacy Compatibility

Legacy single-node、multi-node、Explicit USER/DIRECT、历史 Definition/Instance/Task/CandidatePool/Claim 回归受现有测试保护。ROLE 增量能力不要求历史数据回填、重新解析、重新生成 Candidate/Evidence、重算 Hash 或重新绑定 Directory Revision。DIRECT 任务不创建 Candidate Pool，历史 Task 不执行 ROLE Resolver。

## 10. Test Baseline Matrix

测试基线为 103 个 Workflow 测试文件、341 个 `@Test` 方法。分类允许重叠，因为一项契约常由 Domain、Migration 与集成测试共同保护。

| 分类 | 代表性保护规则 |
|---|---|
| Domain Contract | 状态机、值对象、非法状态拒绝 |
| Canonical Hash | 顺序稳定、字段漂移、大小写/格式敏感 |
| Resolver/Registry | USER ACTIVE、ROLE PREPARED、无 fallback、版本/Contract 校验 |
| Binding/Activation | Proposal、Evidence、Promotion、Snapshot 不可变 |
| Persistence | insert/query only、append-only、事务回滚 |
| Admission | 固定 Gate 顺序、审批完整性、Capability root |
| Directory Adapter | Contract、Revision、Complete、错误分类；不冒充真实连通性 |
| Realtime Eligibility | 27 Validator 与 10 Capability Evidence、单次消费 |
| Candidate Pool/Claim | 冻结、实时资格、幂等、CAS、并发单赢家 |
| Migration Contract | CHECK/FK/UNIQUE/Trigger/Guard/字符集与 Hash |
| Legacy Regression | USER/DIRECT 与历史对象隔离 |
| Security/PII | RBAC≠任务资格、DataScope/SoD、无敏感目录明细固化 |
| Architecture Boundary | Domain purity、生产 Fake 依赖、模块边界 |

未删除有独立治理价值的测试；仅删除了随 Sandbox 生产入口移除而失去意义的一项重复持久化测试。

## 11. Runtime Capability Matrix

| Capability | Implementation/Persistence | DB Validated | Internal | External | Executable | Blocker |
|---|---|---|---|---|---|---|
| EXPLICIT USER | 完整；Task/Assignment | 是 | VALIDATED | 不需要 | ACTIVE | 无 |
| ROLE Directory | Port + Production Adapter 框架 | 不存目录明细 | VALIDATED | BLOCKED | NON_EXECUTABLE | endpoint/auth/TLS/contract |
| ROLE Resolver | Registry + Candidate Adapter | Binding/Evidence | 是 | BLOCKED | PREPARED | Directory |
| Binding | Proposal/Promotion/Snapshot | V2.6.14 | VALIDATED | N/A | NON_EXECUTABLE | Runtime disabled |
| Activation | Request/Approval/Evidence | V2.6.13 | VALIDATED | N/A | NON_EXECUTABLE | Runtime disabled |
| Admission | Gate/Slot/Evidence/Event | V2.6.15 | VALIDATED | BLOCKED | NON_EXECUTABLE | 外部 capability |
| Candidate Pool | Pool/Member | V2.6.6 | VALIDATED | ROLE blocked | USER 路径可用 | ROLE Directory |
| Realtime Eligibility | 27+10 Evidence | V2.6.16 | VALIDATED | BLOCKED | NON_EXECUTABLE for ROLE | Directory/DataScope/SoD |
| Claim | Claim/Audit/CAS/锁 | V2.6.7/8 | VALIDATED | ROLE blocked | USER 兼容 | ROLE gate |
| Audit | append-only evidence chain | 是 | VALIDATED | External Audit blocked | 内部可用 | 外部审计 |
| DataScope | Port/Gate | 证据结构已验证 | 内部契约通过 | BLOCKED | ROLE 不可执行 | 外部能力 |
| Platform/Business SoD | Port/Gate | 证据结构已验证 | 内部契约通过 | BLOCKED | ROLE 不可执行 | 外部规则 |
| Feature Flag | 配置门禁 | N/A | VALIDATED | 真实源 BLOCKED | 默认 OFF | 配置源 |
| Canary | 精确范围模型 | N/A | VALIDATED | 真实源 BLOCKED | 无默认范围 | 配置源 |
| Kill Switch | STOP_NEW_AND_CLAIM | N/A | VALIDATED | 生产控制 BLOCKED | 生效于新行为 | 控制源/RACI |

## 12. External Dependency Register

以下全部标记 `EXTERNAL_DEPENDENCY`：TEST/PREPROD Directory Endpoint、Environment Identity、TLS CA、Client Certificate（如需）、Authentication Mode、Credential Reference、Provider Contract Handshake、Canonical Test Vector、Revision Fence、Historical `effectiveAt`、Complete/Pagination 语义、Directory 测试数据与 SLA、Realtime Eligibility Directory、DataScope、Platform SoD、Business SoD、External Audit、Feature Flag/Canary/Kill Switch 真实配置源、Security Approval 与 RACI Owner。它们不再拆为内部工程待办。

## 13. Operations Readiness

已新增运行治理手册，覆盖组件、默认状态、Directory/Admission/Eligibility/Claim/并发/Hash/Revision/数据库异常、append-only、证据保全和恢复流程。运维原则为 Fail Closed、No Fallback、先保全证据、只对新行为使用 Kill Switch、不删除历史事实。

## 14. Release Readiness

五级门禁结果：A Internal Engineering `PASS`；B External Capability `BLOCKED`；C TEST/PREPROD `NOT_EXECUTED`；D Canary `NOT_EXECUTED`；E Production Enablement `NOT_EXECUTED`。因此不得进入生产启用。

## 15. Findings

- P2：Sandbox Fake Adapter 与执行模型位于 main source，存在生产类路径直接包含测试辅助实现的边界风险。
- P2：当前 Git 工作区包含历次 Sprint 累计未提交资产，尚无唯一 release commit/tag；不影响内容门禁，但阻断正式制品发布。
- External：真实 Directory 与其他 Capability 资源未交付，不能执行外部验收。
- 未发现 P0/P1 内部工程缺陷。

## 16. Fixes Performed

将以下 10 个 `domain/sandbox` 类型从 main source 迁移到 test source：`FakeRoleDirectoryAdapter`、`RoleRuntimeSandboxExecutor`、`SandboxAuditEvidence`、`SandboxClaimSimulation`、`SandboxExecutionResult`、`SandboxExecutionStatus`、`SandboxExecutionTrace`、`SandboxFault`、`SandboxHash`、`SandboxRoleRuntimeContext`。同时从 `ActivationPersistencePolicy` 移除 Sandbox 专用 Evidence 构建方法，并删除对应重复测试。该修复不改变 Domain 业务语义、Canonical、数据库、API、Investment 或 Runtime 状态。

## 17. Remaining Internal Risks

- 正式 Release 前必须将累计工作区资产形成唯一可复现 commit/tag，并在干净工作区复跑门禁。
- MyBatis/Mockito 存在未来版本兼容 warning（动态 Agent 与部分现有映射检查日志），当前不影响 Java 21 测试。
- 49 个 Trigger 与高密度数据库约束增加后续变更复杂度；必须继续前向 Migration 与三路径验收。

## 18. External Blockers

WF5.25、Directory Connectivity 与 DataScope Capability 继续 `BLOCKED`。Fake/Sandbox 只能证明内部 Contract，不证明外部 endpoint、身份、TLS、凭据、数据完整性、SLA 或治理 RACI。

## 19. Definition of Done

Architecture、Compile、Spring Context、Full Tests、Migration SHA、Canonical Inventory、Legacy、Runtime Safety、Production Dependency、PII、diff integrity、Migration governance 与安全状态门禁全部通过。外部门禁独立，不计为内部工程失败。

## 20. Final Gate Result

Internal Engineering Gate：`PASS`。External Capability：`BLOCKED`。TEST/PREPROD、Canary、Production Enablement：`NOT_EXECUTED`。

## 21. Modified Files

- 10 个 Sandbox/Fake Java 类型：main source → test source（同包名，仅测试可见）
- `backend/src/main/java/.../domain/role/ActivationPersistencePolicy.java`
- `backend/src/test/java/.../domain/WorkflowRoleRuntimePersistentActivationTest.java`
- `docs/workflow-v1-runtime-operations-runbook.md`
- `docs/workflow-v1-release-readiness-checklist.md`
- `docs/workflow-v1-frozen-baseline.md`
- `docs/workflow-v1-definition-of-done.md`
- `docs/workflow-v1-internal-engineering-closure-report.md`

数据库变化：0。API/Controller 变化：0。Investment 变化：0。Migration 变化：0。

## 22. Test Results

- Sandbox/Persistence/Spring Context 专项：PASS。
- Java 21 compile：PASS。
- Workflow 专项：341/341 PASS；后端全量：535/535 PASS，0 failure、0 error、0 skipped。
- Domain purity、Production Fake dependency、Investment dependency、PII/Secret、危险 enable/fallback/skip 语义扫描：PASS。
- Migration SHA：38/38 PASS；V2.6.15/V2.6.16 immutable PASS；V2.6.17 absent。
- `git diff --check`：PASS。

## 23. Final Status

```
WORKFLOW_V1_INTERNAL_ENGINEERING_COMPLETE
ROLE_EXTERNAL_VALIDATION_PENDING
ROLE_RUNTIME_DISABLED

EXPLICIT_USER_V1 = ACTIVE
ROLE_DIRECTORY_V1 = PREPARED / NON_EXECUTABLE
WF5.25 = EXTERNAL_RESOURCE_BLOCKED
```

不声明 `ROLE_RUNTIME_READY_FOR_PRODUCTION`、`ROLE_RUNTIME_ENABLED`、`ROLE_DIRECTORY_ACTIVE` 或 `PRODUCTION_READY`。
