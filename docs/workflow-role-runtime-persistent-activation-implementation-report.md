# Workflow ROLE Runtime Persistent Activation Evidence 实施报告

## 1. 结论与边界

Sprint 2-3.7-WF5.10 已完成 Activation Approval Evidence 的追加式持久化框架。
最终状态为：

- `ROLE_RUNTIME_ACTIVATION_EVIDENCE_READY`
- `ROLE_RUNTIME_DISABLED`
- `ROLE_DIRECTORY_V1 = PREPARED / NON_EXECUTABLE`
- `EXPLICIT_USER_V1 = ACTIVE`

本次没有启用 ROLE Runtime，没有创建 ROLE Task、真实 Candidate Pool 或 Claim，
没有接入真实 Role Directory、Investment，也没有新增 Controller。V2.6.13 仅为候选，
未执行真实 MySQL/Flyway Migration。

## 2. 修改文件清单

### Domain

- `PersistentActivationStatus.java`
- `ActivationEvidenceType.java`
- `PersistentActivationCanonical.java`
- `PersistentActivationRequest.java`
- `PersistentActivationDecision.java`
- `ActivationEvidenceRecord.java`
- `ActivationAuditTrail.java`
- `ActivationPersistencePolicy.java`
- `PersistentActivationRequestRepository.java`
- `PersistentActivationDecisionRepository.java`
- `ActivationEvidenceRepository.java`

### Application / Infrastructure

- `RoleRuntimeActivationPersistenceService.java`
- `RoleRuntimeActivationRequestEntity.java`
- `RoleRuntimeActivationApprovalEntity.java`
- `RoleRuntimeActivationEvidenceEntity.java`
- `RoleRuntimeActivationRequestMapper.java`
- `RoleRuntimeActivationApprovalMapper.java`
- `RoleRuntimeActivationEvidenceMapper.java`
- `ActivationPersistenceEntityMapper.java`
- `PersistentActivationRequestRepositoryImpl.java`
- `PersistentActivationDecisionRepositoryImpl.java`
- `ActivationEvidenceRepositoryImpl.java`

### Migration / 治理

- `database/migration/mysql/V2.6.13__create_role_runtime_activation_evidence.sql`
- `database/flyway/validate-v2613-candidate.ps1`
- `database/flyway/migration-inventory.yml`
- `database/migration/mysql/SHA256SUMS`
- `database/migration/mysql/README.md`

### 测试与文档

- `WorkflowRoleRuntimePersistentActivationTest.java`
- `V2613ActivationEvidenceMigrationContractTest.java`
- 本报告

## 3. 数据库设计

V2.6.13 候选新增三张表：

| 表 | 职责 | 保存边界 |
|---|---|---|
| `role_runtime_activation_request` | 冻结 Activation 请求与最终持久化事实 | Resolver、Contract、Binding、Candidate、Directory Contract/Revision、Scope、Effective Time、Activation/Approval Hash |
| `role_runtime_activation_approval` | 冻结三方 RACI 决策 | approver type/id、decision、reason、decision/source evidence hash、decision time |
| `role_runtime_activation_evidence` | 冻结最小哈希证据 | Activation、Resolver Contract、Binding、Candidate、Directory Hash |

明确不保存用户目录明细、组织目录明细、候选成员明细、任务、Claim 和审批执行结果。

治理能力包括：

- V2.6.12 前置结构与 partial-install Guard；
- ASCII/binary 稳定键及小写 SHA-256 CHECK；
- Activation、Approver、Evidence Type 唯一约束；
- Request→Approval/Evidence 外键；
- Request、Approval、Evidence 的 UPDATE/DELETE 拒绝 Trigger；
- 完整审计字段、逻辑删除锁定规则和乐观锁字段。

## 4. Domain 模型与状态机

`PersistentActivationRequest`、`PersistentActivationDecision`、
`ActivationEvidenceRecord` 和 `ActivationAuditTrail` 均为纯 Java 不可变模型。
Domain 不依赖 Spring、MyBatis 或 Entity。

允许的主状态链为：

`DRAFT → SUBMITTED → APPROVAL_PENDING → APPROVED → PERSISTED`

异常终态为 `REJECTED / REVOKED / BLOCKED`。`PERSISTED` 仅表示批准证据已持久化，
状态模型中不存在 `ENABLED`，因此不会隐式启用 ROLE Runtime。

`ActivationPersistencePolicy` 只接受已冻结且三方批准完整的
`RoleRuntimeActivationSnapshot`，生成一个原子追加包。缺少 Business Owner、
Security/Audit 或 Release Approver，出现重复职责、Hash 漂移或证据不完整时均 fail closed。

## 5. Hash 治理

沿用并固化 `ROLE_RUNTIME_ACTIVATION_CANONICAL_V1`：

- Activation Hash 覆盖 Resolver、Contract、Binding、Candidate、Directory Contract、
  Business Scope 与 Effective Time；
- Decision Hash 覆盖 Activation ID、Approver Type/ID、Decision、Decision Time 与来源证据；
- Audit Hash 覆盖 Directory Revision、排序后的 Decision Hash 与分类后的 Evidence Hash；
- 数据库 ID、创建时间、操作人及乐观锁 version 不进入 Canonical Hash；
- 同输入保持稳定，关键字段变化强制产生新 Hash 和新 Activation。

Sandbox 只可转换为三项 hash-only 草稿证据，不会写库或产生任何生产 Runtime 对象。

## 6. Repository 与事务边界

三个 Domain Repository 仅暴露 `insert` 和 `query`。Application Service 使用单一事务：

1. 检查 Activation ID 是否已存在；
2. 生成完整不可变 Audit Trail；
3. 插入 Request；
4. 插入三方 Approval Decision；
5. 插入分类 Evidence。

任一步失败时事务整体回滚。未提供 update/delete/enable 接口，也未新增 API/Controller。

## 7. 测试结果

- Java：21.0.12
- Maven：3.9.11（本地隔离构建运行时）
- Spring Boot Context：通过
- 全量后端测试及新增定向回归：`432` 通过，`0` 失败，`0` 错误，`0` 跳过
- Domain 纯净检查：通过
- Activation 正常保存、审批缺失、Hash 漂移、状态机、Hash 稳定性：通过
- Sandbox hash-only Evidence 转换：通过
- Migration Guard、表、约束、append-only Trigger 静态契约：通过
- Migration SHA 清单：`35/35` 匹配
- `git diff --check`：通过

说明：Evidence/Approval 的 UPDATE/DELETE 拒绝已由候选 SQL 契约测试覆盖，尚未声称真实
MySQL Trigger 验收通过。

## 8. Migration 状态

- 文件：`V2.6.13__create_role_runtime_activation_evidence.sql`
- SHA-256：`7ec6ec6441006dc7f114b92adfc60efdfb2cc8f66bda3a6a7a52d9fcbf3168e6`
- 资产状态：`CANDIDATE`
- 执行状态：`NOT_EXECUTED`
- `flyway_checksum: null`
- Fresh/Upgrade 验收脚本：已准备、未执行

V2.6.9-V2.6.12 内容及摘要未变化。

## 9. 风险与下一步

- V2.6.13 尚未完成真实 MySQL 8 的 Fresh、V2.6.12 Upgrade、strict validate、no-op、
  Trigger/约束负向测试与 Schema Fingerprint 验收。
- 三方 Approval 完整性由 Domain/Application 在事务内强制；数据库本身无法在父记录插入时
  跨多条尚未插入的子记录做即时完整性检查，应在真实验收中验证事务回滚和异常中断行为。
- 当前只提供内部 Application Service，尚无治理 API；这是本 Sprint 的刻意边界。
- 下一步应单独执行 V2.6.13 真实 MySQL/Flyway 验收并据结果决定资产晋级；在此之前继续保持
  `ROLE_RUNTIME_DISABLED`，不要进入 ROLE Task/Candidate Pool/Claim Runtime 或 Investment 集成。
