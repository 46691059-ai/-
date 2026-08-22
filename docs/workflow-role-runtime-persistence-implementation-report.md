# Workflow ROLE Runtime Persistence Implementation Report

## 1. 修改文件清单

### 1.1 Migration 与治理资产

- `database/migration/mysql/V2.6.9__create_role_runtime_snapshot.sql`
- `database/flyway/migration-inventory.yml`
- `database/migration/mysql/SHA256SUMS`
- `database/migration/mysql/README.md`

### 1.2 Domain

- `RoleRuntimeApproval`
- `RoleRuntimeBindingSnapshot`
- `RoleRuntimeEvidence`
- `RoleRuntimePersistencePolicy`
- `RoleRuntimeApprovalRepository`
- `RoleRuntimeBindingSnapshotRepository`

### 1.3 Infrastructure

- `RoleRuntimeBindingApprovalEntity`
- `WorkflowRoleRuntimeBindingSnapshotEntity`
- `RoleRuntimeBindingApprovalMapper`
- `WorkflowRoleRuntimeBindingSnapshotMapper`
- `RoleRuntimePersistenceEntityMapper`
- `RoleRuntimeApprovalRepositoryImpl`
- `RoleRuntimeBindingSnapshotRepositoryImpl`

### 1.4 Tests

- `WorkflowRoleRuntimePersistenceDomainTest`
- `RoleRuntimePersistenceRepositoryTest`
- `WorkflowRoleRuntimePersistenceMigrationContractTest`

未修改 Investment、Controller、Workflow Task 创建、Candidate Pool 创建、Claim 运行链路、Resolver Registry 或历史 Migration。

## 2. V2.6.9 设计实现

候选资产：

```text
V2.6.9__create_role_runtime_snapshot.sql
```

职责仅限：

1. 保存 ROLE Runtime Binding 审批证据；
2. 保存已批准 ROLE Runtime Binding 与最小 Directory 证据快照；
3. 通过现有 Workflow Instance、Node、Binding Set、Resolver Binding 和 Node Binding 外键形成所有权链；
4. 为未来 ROLE Runtime 激活提供不可变证据底座。

明确不包含：

- ROLE Resolver 激活；
- Role Directory 调用；
- ROLE Task 或 Candidate Pool 创建；
- Controller/API；
- Investment 集成；
- 历史数据回填或推断。

V2.6.9 当前仓库 SHA-256：

```text
fbe41e2a354e616ee4b369c57980009057c75ced46a2eb9e48d1eb8354beb0d2
```

## 3. 数据库变化

### 3.1 role_runtime_binding_approval

用途：保存 ROLE Runtime Binding 的治理审批事实。

核心字段：

- proposal_hash、eligibility_hash；
- resolver_code、resolver_version、contract_hash；
- status；
- approved_by、approved_at、reject_reason；
- audit_info；
- 标准 created/updated、deleted、delete_token、version 字段。

状态：

```text
PENDING / APPROVED / REJECTED / EXPIRED
```

约束包括：

- Proposal + Eligibility + delete_token 唯一键；
- 小写 SHA-256、ASCII/BINARY 比较；
- 状态与审批/驳回字段组合 CHECK；
- 审计、逻辑删除与乐观锁 CHECK。

### 3.2 workflow_role_runtime_binding_snapshot

用途：保存不可变 ROLE Runtime Binding 与最小 Directory 证据。

核心字段：

- approval_id；
- binding_set_id、resolver_binding_id、node_resolver_binding_id；
- instance_id、definition_version_id、node_id；
- resolver_code、resolver_version、contract_hash；
- role_code、organization_id、directory_revision、effective_at；
- directory_hash、role_rule_hash、candidate_rule_hash、source_evidence_hash；
- binding_hash、status、audit_info；
- 标准 created/updated、deleted、delete_token、version 字段。

状态：

```text
FROZEN / ARCHIVED / SECURITY_BLOCKED
```

约束包括：

- Approval、Instance、Node、Binding Set、Resolver Binding、Node Binding 外键；
- 同一 Approval、同一 Instance/Node 和 Binding Hash 的活动记录唯一性；
- Revision、业务键、Hash、状态、审计、删除与版本 CHECK；
- `ON DELETE RESTRICT / ON UPDATE RESTRICT`，禁止运行证据被级联修改。

### 3.3 Candidate Pool 与 Claim

未新增 ROLE 专用 Candidate Pool。继续复用：

- `workflow_task_candidate_pool`；
- `workflow_task_candidate_member`；
- `workflow_task_claim` 与 Claim Audit。

现有 Domain `CandidateSource` 已包含：

```text
EXPLICIT_USER
ROLE_DIRECTORY
```

本 Sprint 未改 Candidate Pool/Claim 表，也未让 ROLE 产生真实候选池。Claim 仍只读取冻结 Candidate Pool，不调用 Directory、不重新解析 Role。`EXPLICIT_USER_V1 + DIRECT` 行为未改变。

## 4. Domain 模型

### 4.1 RoleRuntimeApproval

不可变审批模型，提供：

- `pending(...)`；
- `approve(...)`；
- `reject(...)`；
- `expire()`；
- 非法状态转换 Fail Closed。

状态转换仅发生在 Domain 对象中。本 Sprint Repository 只提供 insert/query，不提供数据库 update API。

### 4.2 RoleRuntimeEvidence

冻结最小证据：

- Directory Revision；
- Directory Result Hash；
- Role Rule Hash；
- Candidate Rule Hash；
- Source Evidence Hash。

所有 Hash 必须是 64 位小写 SHA-256。

### 4.3 RoleRuntimeBindingSnapshot

不可变 record，构造时重新计算 Persistence Hash；数据库值发生漂移时会在进入 Domain 前被拒绝。

Snapshot 只能由 `RoleRuntimePersistencePolicy.freeze(...)` 从 APPROVED Approval 生成。PENDING、REJECTED、EXPIRED 均不能生成 Snapshot。

### 4.4 RoleRuntimePersistencePolicy

负责：

- APPROVED 门禁；
- Canonical Hash；
- Snapshot 冻结；
- Contract、Role、Organization、Directory 与 Rule 证据一致性。

Domain 包未引入 Spring、MyBatis 或 Infrastructure/Entity 依赖。

## 5. Repository 设计

新增两个 Domain Port：

```text
RoleRuntimeApprovalRepository
  - insert
  - findById
  - findByHashes

RoleRuntimeBindingSnapshotRepository
  - insert
  - findById
  - findByInstanceIdAndNodeId
```

Repository 明确没有 update、save 或 delete Runtime Binding 的入口。

Infrastructure Adapter 使用 MyBatis Plus Mapper，并执行：

- Domain/Entity 双向映射；
- UTC `Instant` 与 `LocalDateTime` 转换；
- 通用审计字段初始化；
- Duplicate Key 转换为业务异常；
- 查询结果重新通过 Domain Hash 和状态校验。

## 6. Hash 规则

冻结规范：

```text
ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1
```

Hash 输入：

- resolverCode；
- resolverVersion；
- contractHash；
- roleCode；
- organizationId；
- directoryRevision；
- effectiveAt；
- directoryHash；
- roleRuleHash；
- candidateRuleHash；
- sourceEvidenceHash。

Hash 排除：

- 数据库 ID；
- createTime/updateTime；
- operator；
- 乐观锁 version；
- remark 与非业务审计字段。

算法为 SHA-256，输出为 64 位小写十六进制。SQL 使用 `CHARACTER SET ascii COLLATE ascii_bin`，避免大小写不敏感比较。

## 7. 测试结果

### 7.1 定向测试

```text
Tests run: 11
Failures: 0
Errors: 0
Skipped: 0
```

覆盖：

- Approval 状态机；
- APPROVED 冻结门禁；
- Hash 稳定性与关键字段变化；
- 非法/漂移 Hash 拒绝；
- Snapshot 不可修改 Repository 契约；
- Repository 映射；
- Duplicate Approval/Binding 拒绝；
- Domain 纯净；
- Migration 表、索引、外键、CHECK、历史兼容和资产状态静态契约。

### 7.2 全量后端回归

运行环境：

```text
Java: 21.0.12
Maven: 3.9.9
Spring Boot: 3.5.9
```

结果：

```text
Tests run: 378
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

Spring Boot 上下文、MyBatis Plus Mapper 扫描、EXPLICIT_USER、Legacy、Candidate Pool、Claim、Security 与 Investment 既有测试全部通过。

### 7.3 Migration 验证边界

本 Sprint 只完成候选 SQL 与静态 Migration 契约检查：

- Fresh 完整链和 V2.6.8 Upgrade 被登记为后续验收路径；
- DDL SHA/Schema 资产指纹可重复；
- 未执行真实 MySQL/Flyway；
- 因此没有 Flyway checksum、真实 Schema fingerprint 或数据库 no-op 证据。

不得将静态测试解释为真实数据库验收。

## 8. Migration 状态

```text
Version: V2.6.9
Asset status: CANDIDATE
Execution status: NOT_EXECUTED
Flyway checksum: null
ROLE Runtime: DISABLED
```

历史 V2.5.0—V2.6.8 文件未修改。V2.6.9 不得在真实 MySQL/Flyway验收前晋级为 `CANONICAL_IMMUTABLE` 或 `EPHEMERAL_MYSQL8_VALIDATED`。

## 9. 风险

1. V2.6.9 尚未在真实 MySQL 8 上验证 Fresh/Upgrade、CHECK、外键和 Schema Fingerprint；
2. Approval 当前为 insert/query 基础设施，尚未提供持久化状态变更 Application Service；
3. ROLE Snapshot 尚未接入 Runtime 激活链路，这是有意的安全门禁；
4. 真实 Approval Role Directory 的 Revision、签名、超时和数据质量尚未验收；
5. ROLE Candidate Pool 与 Claim 的实时资格、DataScope、SoD 尚未进入运行验证；
6. 未来激活必须验证 Approval 状态与 Snapshot 的跨表一致性，不能只依赖外键；
7. ROLE Resolver Registry 仍为 PREPARED，任何提前改为 ACTIVE 都属于安全违规。

## 10. 下一步建议

单独执行 V2.6.9 真实 MySQL/Flyway 验收：

1. Fresh 完整 Migration；
2. V2.6.8 → V2.6.9 Upgrade；
3. strict validate 与二次 migrate no-op；
4. 两张表的字段、索引、外键、CHECK 和 Hash 大小写负向测试；
5. 重复 Approval、重复 Instance/Node Snapshot、跨实例/节点 Binding 引用负向测试；
6. Fresh/Upgrade Schema Fingerprint 一致性；
7. 通过后再更新资产状态。

在上述验收完成前，不进入真实 Role Directory、ROLE Task、Candidate Pool 或 Runtime 激活。

最终状态：

```text
ROLE_RUNTIME_PERSISTENCE_IMPLEMENTED
ROLE_RUNTIME_DISABLED
```

