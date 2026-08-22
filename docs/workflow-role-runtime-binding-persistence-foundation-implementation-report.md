# Sprint 2-3.7-WF5.14 ROLE Runtime Binding Persistence Foundation 实施报告

## 1. 最终结论

本阶段完成 `RuntimeBindingCandidate -> RuntimeBindingSnapshot` 的持久化基础设施，状态为：

- `ROLE_RUNTIME_BINDING_PERSISTENCE_FOUNDATION_IMPLEMENTED`
- `ROLE_RUNTIME_DISABLED`
- `V2.6.14: CANDIDATE / NOT_EXECUTED`

本实现只冻结 Promotion、Candidate Snapshot 与 Lifecycle Evidence，不产生可执行
Runtime Binding，不创建 WorkflowInstance、NodeExecution、Task、CandidatePool 或 Claim，
也未修改 Investment、Resolver Registry 状态或接入真实 Role Directory。

## 2. 修改文件清单

### Domain

- `RoleRuntimeBindingPromotion`
- `RoleRuntimeBindingPromotionReference`
- `RoleRuntimeBindingEvidence`
- `RoleRuntimeBindingCandidateSnapshot`
- `RoleRuntimeBindingLifecycleStatus`
- `RoleRuntimeBindingLifecycleEvent`
- `RoleRuntimeBindingPersistenceCanonical`
- `RoleRuntimeBindingPersistenceBundle`
- `RoleRuntimeBindingPersistencePolicy`

### Repository / Infrastructure

- `RoleRuntimeBindingPromotionRepository`
- `RoleRuntimeBindingCandidateSnapshotRepository`
- `RoleRuntimeBindingLifecycleRepository`
- `WorkflowRoleBindingPromotionEntity` / `Mapper`
- `WorkflowRoleBindingCandidateSnapshotEntity` / `Mapper`
- `WorkflowRoleBindingSnapshotEventEntity` / `Mapper`
- `RoleRuntimeBindingPromotionRepositoryImpl`
- `RoleRuntimeBindingCandidateSnapshotRepositoryImpl`
- `RoleRuntimeBindingLifecycleRepositoryImpl`
- `RoleRuntimeBindingFoundationEntityMapper`

### Application / Test / Governance

- `RoleRuntimeBindingPersistenceApplicationService`
- `WorkflowRoleRuntimeBindingPersistenceFoundationTest`
- `V2614RoleRuntimeBindingPersistenceMigrationContractTest`
- `database/migration/mysql/V2.6.14__create_role_runtime_binding_persistence_foundation.sql`
- `database/flyway/migration-inventory.yml`
- `database/migration/mysql/SHA256SUMS`
- `database/migration/mysql/README.md`
- 本报告

## 3. V2.6.14 设计实现

候选 Migration 新增三张表：

| 表 | 定位 | 写模型 |
|---|---|---|
| `workflow_role_binding_promotion` | 已批准 Promotion 的持久化事实 | insert only |
| `workflow_role_binding_candidate_snapshot` | Instance 创建前的不可变 Binding Candidate 快照 | insert only |
| `workflow_role_binding_snapshot_event` | Snapshot 生命周期 Hash 链 | append only |

Migration 在任何永久 DDL 之前执行临时 Guard，检查 V2.6.13 父契约、父数据
Hash/所有权以及目标表和 Trigger 不存在。发现异常即失败，不修复、不删除、不推断、
不回填。

## 4. 数据库变化

三张表均包含主键、创建/更新时间、创建/更新人、逻辑删除字段、`delete_token`、
`version`、状态/Hash CHECK、稳定业务唯一键及查询索引。Resolver Code/Version 和
Hash 字段显式使用 `ASCII/ascii_bin`，Hash 仅接受 64 位小写十六进制值。

强所有权链为：

`Activation Request -> Promotion -> Candidate Snapshot -> Lifecycle Event`

- Promotion 通过 `(activation_id, activation_delete_token)` 引用 V2.6.13 Request。
- Snapshot 通过 `(promotion_row_id, promotion_id, activation_id, delete_token)` 引用
  Promotion，并再次引用 Activation Request。
- Event 通过 `(snapshot_row_id, snapshot_id, promotion_id, activation_id, delete_token)`
  引用 Snapshot。
- 三张表均配置 `BEFORE UPDATE`、`BEFORE DELETE` 拒绝 Trigger。
- 三条 `BEFORE INSERT` Guard 分别验证 Activation Evidence 完整性、Promotion/Snapshot
  字段一致性和 Lifecycle 前序 Hash/状态连续性。

未修改任何历史 Migration，也未修改 Workflow Runtime 核心表。

## 5. Domain 模型与生命周期

`RoleRuntimeBindingPersistencePolicy` 固定执行以下校验：Activation 必须为
`PERSISTED`、Promotion 必须为 `PROMOTED`、Candidate 必须不可执行，并验证
Activation、Resolver Contract、Binding、Candidate、Directory Revision、业务范围和
有效时间一致。

冻结成功生成：

1. Promotion 持久化事实；
2. Activation/Promotion 强引用；
3. Hash-only Evidence Set；
4. Candidate Snapshot；
5. `CREATED -> VALIDATED` 初始生命周期事件链。

后续生命周期仅允许：`VALIDATED -> ACTIVE/BLOCKED`、`ACTIVE -> REVOKED`。
其中 `ACTIVE` 仅表示持久化治理对象生效，不代表 ROLE Runtime Enabled。

## 6. Hash 治理

新增 Canonical：`ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1`。

Hash 覆盖 Promotion Reference、Activation/Approval/Promotion/Permission/Directory
证据、Resolver Code/Version/Contract、Binding、Candidate、Directory Revision、
业务范围、有效窗口和生命周期前序事件。数据库 ID、审计时间、操作人和乐观锁
版本不进入 Snapshot Hash；事件操作人与发生时间只进入事件留痕 Hash。

任何输入漂移均生成不同 Hash 或在冻结前被拒绝。历史 Snapshot 不会因目录、角色或
人员变化而刷新。

## 7. Repository 与事务边界

三个 Domain Repository 只暴露 `insert/append/query`，无 update、delete、
saveOrUpdate。Infrastructure Adapter 使用 MyBatis Mapper，并将重复键映射为明确业务
异常。

内部 `RoleRuntimeBindingPersistenceApplicationService.persist` 使用单一
`@Transactional` 边界，按 Promotion、Snapshot、Lifecycle Event 顺序写入；任何一步
失败均回滚。本阶段无 Controller、无公开 API。

## 8. API 情况

新增 API：`0`。

没有开放 Promotion、Snapshot 或 Lifecycle 的写接口，也没有开放 Runtime Enable、
Resolver 切换、Task、CandidatePool 或 Claim 接口。

## 9. 测试结果

- Java 21 编译：PASS。
- 新增 Domain/契约测试：PASS。
- Spring Boot Context：PASS。
- 后端全量测试：PASS，`449` 项，`0` 失败，`0` 错误，`0` 跳过。
- Domain 纯净检查：PASS，无 Spring/MyBatis/Entity 依赖。
- Repository insert/query-only 契约：PASS。
- 内部 Application Service 事务注解契约：PASS。
- V2.6.14 Guard、三表、复合 FK、CHECK、INSERT Guard、六个不可变 Trigger 静态契约：PASS。
- EXPLICIT_USER_V1：保持 `ACTIVE`。
- ROLE_DIRECTORY_V1：保持 `PREPARED / NON_EXECUTABLE`。
- Migration SHA 清单：`36/36` 匹配。
- `git diff --check`：PASS。

未执行真实 MySQL/Flyway Migration，因此没有 Flyway checksum、Schema Fingerprint、
Fresh/Upgrade/no-op 或数据库 Trigger 动态验收结论。

## 10. Migration 资产状态

- 文件：`V2.6.14__create_role_runtime_binding_persistence_foundation.sql`
- SHA-256：`bcbcbb6b9f7e58b5b03297621682f88c73744e2893058d0519198203e6150b15`
- `asset_status: CANDIDATE`
- `execution_status: NOT_EXECUTED`
- `flyway_checksum: null`
- 依赖：V2.6.13

V2.5.0-V2.6.13 内容及既有 SHA 未发生漂移。V2.6.14 未提前标记为
`CANONICAL_IMMUTABLE` 或 `EPHEMERAL_MYSQL8_VALIDATED`。

## 11. 禁止项验证

- 未启用 ROLE Runtime。
- 未将 ROLE_DIRECTORY_V1 切换为 ACTIVE。
- 未创建真实 ROLE Task、CandidatePool 或 Claim。
- 未创建 WorkflowInstance 或 NodeExecution。
- 未连接真实 Role Directory。
- 未修改 Investment。
- 未新增 Controller/API。
- 未执行真实数据库 Migration。

## 12. 剩余风险

1. V2.6.14 尚未在真实 MySQL 8.4/Flyway 13 上验证 SQL 语法、FK 建立顺序和 Trigger 行为。
2. Fresh 与 V2.6.13 Upgrade 的最终 Schema Fingerprint 尚未比较。
3. MySQL 非事务 DDL 下 Guard 失败与中途 DDL 失败的残留对象边界尚需隔离实例验证。
4. 并发重复 Promotion/Snapshot/Event 的唯一键冲突和死锁表现尚未进行双会话验收。
5. Snapshot `ACTIVE` 目前只有治理语义；后续仍需独立人工门禁，禁止将其误读为 Runtime Enabled。

## 13. 下一步建议

仅进入独立的 V2.6.14 真实 MySQL/Flyway 验收 Sprint：执行 Fresh、V2.6.13 Upgrade、
strict validate、二次 migrate no-op、Schema Fingerprint、Guard/Trigger/复合 FK 负向测试、
事务与双会话并发测试。验收通过前继续保持 `ROLE_RUNTIME_DISABLED`。

## 14. 完成状态

`ROLE_RUNTIME_BINDING_PERSISTENCE_FOUNDATION_IMPLEMENTED / ROLE_RUNTIME_DISABLED`
