# V2.6.15 候选完整性修复治理报告

## 1. 原失败候选资产

- 文件：`V2.6.15__create_role_runtime_execution_admission_persistence.sql`
- 原 SHA-256：`405fb7fc3bc928fc84643bcbf5583cd9f9555dad8209ad6b4d962529ce969887`
- 原 Flyway checksum：`1424227542`
- 原状态：`CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`
- 失败证据报告：`docs/workflow-role-runtime-execution-admission-persistence-v2615-validation-report.md`
- 归档文件：`database/migration/archive/failed-candidates/V2.6.15__create_role_runtime_execution_admission_persistence__failed_405fb7fc.sql`
- 归档文本 SHA-256：`246c6a8d42e1ea36538213d6421882fc1d2b9acc1f478f4ca843f027bbe8ea56`

归档文件保留原候选 SQL 文本；`source_sha256` 保留验收时按原文件字节计算的正式摘要，`archived_text_sha256` 记录仓库换行规范化后的归档文本摘要。失败验收报告未覆盖、Flyway history 未修改，也未执行 repair、baseline 或 out-of-order。

## 2. MySQL 6125 根因

`fk_role_admission_slot_candidate` 的子列为
`(candidate_snapshot_row_id,snapshot_id,source_delete_token)`，引用父表
`workflow_role_binding_candidate_snapshot(id,snapshot_id,delete_token)`。
父表当时只有主键 `(id)` 和更长的
`uk_role_binding_snapshot_owner(id,snapshot_id,promotion_id,activation_id,delete_token)`，
不存在与被引用三列完全匹配的 PK/UNIQUE，因此 MySQL 8.4.9 返回 6125。

## 3. DDL 执行依赖图

```text
Pre-DDL Guard
  -> Candidate future-only nullable columns
  -> Candidate Slot parent UNIQUE
  -> Admission table and owner UNIQUE keys
  -> Slot table (CAS only)
  -> Evidence table
  -> Event table
  -> Parent PK/UNIQUE executable proof
  -> 11 ownership foreign keys
  -> FK structure assertion
  -> 9 append-only/ownership/CAS triggers
  -> final structure assertion
```

失败候选的真实执行顺序证据为：Candidate ALTER 已提交；Admission、Evidence、
Event 已创建；Slot 在父唯一键不完整处失败；已成功创建 6 个 FK 和 16 个
CHECK；9 个 Trigger 尚未创建。MySQL DDL 隐式提交导致这些对象未自动回滚，
该部分状态不是合法升级基线。

## 4. Slot 父唯一键修复

新增专用父业务键：

```sql
UNIQUE KEY uk_role_binding_snapshot_slot_owner (id,snapshot_id,delete_token)
```

Slot FK 保持原三列归属语义并精确引用该键。没有为修复 FK 向 Slot 填入
Activation、Promotion、Evidence 或 Decision 字段。Slot 仍只负责单 Candidate
活动 Admission 指针与 CAS/version 并发控制，历史 Admission 仍由不可变事实表保留。

## 5. FK Parent Unique 矩阵

| FK | 子列 | 父表 | 父列 | 匹配键 |
|---|---|---|---|---|
| fk_role_binding_snapshot_definition_version | definition_id, definition_version_id | workflow_version | definition_id, id | uk_workflow_version_owner |
| fk_role_binding_snapshot_node | definition_version_id, node_id | workflow_node | version_id, id | uk_workflow_node_owner |
| fk_role_binding_snapshot_release | definition_release_id | workflow_version_release | id | PRIMARY |
| fk_role_admission_candidate | candidate_snapshot_row_id, snapshot_id, promotion_id, activation_id, source_delete_token | workflow_role_binding_candidate_snapshot | id, snapshot_id, promotion_id, activation_id, delete_token | uk_role_binding_snapshot_owner |
| fk_role_admission_definition_version | definition_id, definition_version_id | workflow_version | definition_id, id | uk_workflow_version_owner |
| fk_role_admission_node | definition_version_id, node_id | workflow_node | version_id, id | uk_workflow_node_owner |
| fk_role_admission_release | definition_release_id | workflow_version_release | id | PRIMARY |
| fk_role_admission_slot_candidate | candidate_snapshot_row_id, snapshot_id, source_delete_token | workflow_role_binding_candidate_snapshot | id, snapshot_id, delete_token | uk_role_binding_snapshot_slot_owner |
| fk_role_admission_slot_active | active_admission_row_id, active_admission_id, candidate_snapshot_row_id, source_delete_token | workflow_role_runtime_execution_admission | id, admission_id, candidate_snapshot_row_id, delete_token | uk_role_admission_owner |
| fk_role_admission_evidence_owner | admission_row_id, admission_id, delete_token | workflow_role_runtime_execution_admission | id, admission_id, delete_token | uk_role_admission_evidence_owner |
| fk_role_admission_event_owner | admission_row_id, admission_id, candidate_snapshot_row_id, delete_token | workflow_role_runtime_execution_admission | id, admission_id, candidate_snapshot_row_id, delete_token | uk_role_admission_owner |

Migration 在添加任何上述 FK 前，通过 `information_schema.statistics`
按顺序比较完整父列组；任一父 PK/UNIQUE 不匹配即 Fail Closed。

## 6. DDL 顺序与部分安装治理

修订后先创建 Slot，再创建 Evidence/Event，并把所有新增 FK 统一延迟到父键证明
之后。首条永久 DDL 前的 Guard 同时检查：

- 10 个 Candidate 增强字段是否已部分存在；
- 4 张目标表是否任一存在；
- 候选专用父唯一键及 Admission 相关索引是否存在；
- Admission 相关 constraint/FK/CHECK 是否存在；
- Admission 相关 Trigger 是否存在。

发现“仅 Candidate 字段”“仅 Admission”“Admission+Evidence”
“Admission+Evidence+Event”“缺 Slot”“缺 Trigger”或“缺 FK”等任何非空部分状态，
均在继续升级前失败；Migration 不自动 DROP、补齐、回填或修复。由于 MySQL DDL
非事务性，治理目标是通过前置证明和依赖安全顺序避免可预见的中途失败，而不是宣称
失败后能自动回滚全部 DDL。

## 7. 历史 Candidate 策略

V2.6.15 新增的 Directory fence、Definition/Version/Node 与图 Hash 字段继续允许
历史行全 NULL。禁止基于当前 Definition、Node 或最新 Directory revision/hash
进行推断、回填或覆盖。

## 8. Admission / Evidence / Event 与 Slot

- Admission：结构与业务语义不降级，insert-only，UPDATE/DELETE Trigger 拒绝。
- Evidence：继续使用 28 项 validator 明细子表，insert-only，不改为单 JSON。
- Event：继续保存 append-only 生命周期 Hash 链，UPDATE/DELETE Trigger 拒绝。
- Slot：字段模型未扩张，仅新增父表精确唯一键契约；通过 UNIQUE + CAS/version
  保证同一 Candidate 最多一个活动指针。

最终仍要求完整创建 9 个 Trigger。修复没有启用 ROLE Runtime、没有创建
WorkflowInstance、ROLE Task、真实 CandidatePool 或 Claim。

## 9. Guard 保持情况

原有 V2.6.14 结构、Activation/Promotion/Candidate 归属、Resolver Contract、
Hash 格式、Definition Version、Node、Release 与重复活动 Admission 语义均保留。
新增父键证明和 FK 完整性断言只增强，不弱化既有 Guard。

## 10. 测试结果

- `V2615ExecutionAdmissionPersistenceMigrationContractTest`：通过。
- `V2615ExecutionAdmissionPersistenceDdlDependencyTest`：通过；解析实际 DDL，
  建立 11 条 FK 到父 PK/UNIQUE 的契约映射，不以简单字符串存在代替证明。
- Java 21 compile：通过。
- Spring Boot Context：通过。
- 后端全量测试：477 项通过，0 失败，0 错误，0 跳过。
- Domain 纯净检查：2 项通过。
- Production Dependency Scan：2 项通过。
- Migration SHA：37/37 匹配。
- `git diff --check`：通过。
- V2.6.14 SHA-256 保持
  `a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442`。

## 11. 摘要与资产状态

- 原失败 SHA-256：`405fb7fc3bc928fc84643bcbf5583cd9f9555dad8209ad6b4d962529ce969887`
- 原失败 Flyway checksum：`1424227542`
- 修订 SHA-256：`7887bfb1d5bb9aacba378bc62618ba92fdd58fb1a85007053da4899d1fe9d412`
- 修订 Flyway checksum：`null`（未执行）
- 当前状态：`CANDIDATE_READY_FOR_REVALIDATION / NOT_EXECUTED`
- ROLE Runtime：`DISABLED`

不得在真实 MySQL 重验通过前写入
`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。

## 12. 剩余风险与下一步重验

剩余风险集中于真实 MySQL 8.4.9 对重排 DDL、精确复合 FK、Guard 与 Trigger 的
实际执行行为，以及 Fresh/Upgrade 双路径 Schema 指纹一致性。下一 Sprint 应仅执行
隔离重验：Fresh 至 V2.6.15、V2.6.14 Upgrade、strict validate、二次 migrate no-op、
11 条 FK/父唯一键核查、部分安装矩阵、append-only/Slot CAS 负向测试和双路径
Schema fingerprint。不得复用失败的部分安装 Schema，不得创建 V2.6.16。
