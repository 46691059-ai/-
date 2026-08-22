# V2.6.9 ROLE Runtime Persistence MySQL/Flyway 验收报告

## 1. 最终结论

Sprint 2-3.7-WF5.7.1 结论为：

```text
FAIL / BLOCKED / NOT_PROMOTED
ROLE_RUNTIME_DISABLED
```

V2.6.9 的 Fresh、Upgrade、Flyway history、strict validate、二次 migrate no-op 与双路径 Schema 收敛均通过，但数据库完整性没有满足 ROLE Runtime 的 Insert Only 和 Approval 门禁要求。12 项负向测试仅 7 项通过，存在 5 项生产阻断问题。

因此 V2.6.9 不得晋级为 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`，资产状态保持：

```text
CANDIDATE
EPHEMERAL_MYSQL8_VALIDATION_FAILED
```

本次验收没有修改 V2.6.9 或任何历史 Migration，没有启用 ROLE Resolver，没有创建真实 ROLE Task/Candidate Pool，也没有调用 Role Directory。

## 2. 环境信息

- MySQL Community Server 8.4.9；
- Flyway Community Edition 13.0.0；
- 两个全新一次性实例，仅监听 `127.0.0.1`；
- Fresh 端口：35201；Upgrade 端口：35202；
- 每个实例使用独立 data、日志和配置目录；
- 验收脚本：`database/flyway/scripts/validate-v269.ps1`；
- 证据目录：`D:\codex-validation-v269-20260813-110418\evidence`；
- 未连接生产、托管、未知或既有 MySQL 实例。

## 3. Fresh 结果

基线初始化后，从 Flyway baseline V2.0.0 执行完整治理链至 V2.6.9：

- Flyway history：32 条成功记录（含 baseline），0 失败；
- V2.6.9 出现且仅出现 1 次；
- strict validate：通过；
- 第二次 migrate：`No migration necessary`；
- 完整迁移共应用 31 个版本化 Migration；
- V2.6.9 所属完整链执行时间约 15.719 秒。

## 4. Upgrade 结果

独立实例先迁移至 V2.6.8，再以完整扫描目录升级到 V2.6.9：

- 升级阶段仅新增 1 条 Migration；
- V2.6.9 执行且仅执行 1 次；
- Flyway history：32 条成功记录（含 baseline），0 失败；
- strict validate：通过；
- 第二次 migrate：no-op；
- V2.6.9 增量执行时间约 0.326 秒。

## 5. Flyway checksum 与 SHA-256

- V2.6.9 Flyway checksum：`1523438049`；
- V2.6.9 SHA-256：`fbe41e2a354e616ee4b369c57980009057c75ced46a2eb9e48d1eb8354beb0d2`；
- V2.6.9 SQL 与验收前冻结内容一致；
- V2.6.8 及历史 Migration 未修改。

## 6. Schema Fingerprint

指纹基于实际 `information_schema`、索引、外键、CHECK 与 Trigger 输出。Fresh 与 Upgrade 完全一致：

| 范围 | Fresh = Upgrade SHA-256 |
| --- | --- |
| Full Schema | `156dec0801b0bd0db613832360b5da06b51f7d2d0e53fdad340b53e2a62dfe1d` |
| Workflow Schema | `f08561138e1dc07dc6450783b00b74c6daeb0c2f692465318ed63d18eac38d61` |
| ROLE Runtime Schema | `db15d1042f81079bfc80ae18ce10eba792ac707004c2681f5f444d2bda132f2b` |

Schema 收敛通过不代表完整性验收通过。

## 7. 结构验证

### 7.1 role_runtime_binding_approval

- 字段：19；
- 索引统计行：11；
- CHECK：9；
- 审计字段、`deleted`、`delete_token`、`version` 均存在；
- Proposal/Eligibility 唯一键存在；
- Hash 使用 ASCII/BINARY 字符集排序规则。

### 7.2 workflow_role_runtime_binding_snapshot

- 字段：30；
- 索引统计行：33；
- 外键：6；
- CHECK：8；
- Approval、Instance、Node、Binding Set、Resolver Binding、Node Binding 外键存在；
- Hash、审计、逻辑删除和乐观锁字段均存在。

## 8. 负向测试

| # | 场景 | 结果 | MySQL/SQLSTATE | 说明 |
| ---: | --- | --- | --- | --- |
| 1 | 非法 Approval 状态 | PASS | 3819/HY000 | CHECK 拒绝 |
| 2 | 非法 Hash 格式 | PASS | 3819/HY000 | Hash CHECK 拒绝 |
| 3 | 重复活动 Proposal/Eligibility Hash | PASS | 1062/23000 | 唯一键拒绝 |
| 4 | 重复 Binding Hash | PASS | 1062/23000 | 唯一键拒绝 |
| 5 | 空 Resolver Version | **FAIL** | 无错误 | 数据库接受空字符串 |
| 6 | 非法 Contract Hash | PASS | 3819/HY000 | 小写 SHA-256 CHECK 拒绝 |
| 7 | 空 Organization | PASS | 3819/HY000 | 业务键 CHECK 拒绝 |
| 8 | 修改不可变 Snapshot | **FAIL** | 无错误 | UPDATE 被接受 |
| 9 | 删除不可变 Snapshot | **FAIL** | 无错误 | DELETE 被接受 |
| 10 | 非法 delete_token | PASS | 3819/HY000 | delete_token CHECK 拒绝 |
| 11 | Approval 原位 UPDATE | **FAIL** | 无错误 | Insert Only 未由数据库强制 |
| 12 | PENDING Approval 生成 Snapshot | **FAIL** | 无错误 | 外键只验证 ID，未验证 APPROVED 状态 |

通过：7；失败：5。

所有负向测试均在事务中执行并回滚，没有将测试违规数据留在验收实例。

## 9. Insert Only 验证

### 9.1 Approval

- 合法 INSERT：通过；
- 非法状态/Hash UPDATE：可被现有 CHECK 部分阻止；
- 但字段组合仍合法的原位 UPDATE 可执行；
- 结论：数据库没有实现真正的 append-only/insert-only 治理。

### 9.2 Runtime Binding Snapshot

- 合法 INSERT：通过；
- UPDATE：被数据库接受；
- DELETE：被数据库接受；
- 结论：`workflow_role_runtime_binding_snapshot` 与“不可修改、不可删除”的冻结要求不一致。

### 9.3 Approval 所有权

Snapshot 外键只要求 `approval_id` 存在，无法保证对应 Approval 状态为 APPROVED。真实测试成功创建了指向 PENDING Approval 的 Snapshot，因此 Approval 晋级门禁未在数据库层闭合。

## 10. 兼容验证

- `EXPLICIT_USER_V1` 历史实例保持正常，测试库计数为 2；
- V2.6.9 没有回填 Legacy Task；
- 真实 ROLE Runtime Snapshot 仅来自验收 Fixture，没有接入运行链；
- Candidate Pool 与 ROLE_DIRECTORY 来源污染：0；
- Claim 关联 ROLE Snapshot：0；
- 未创建真实 ROLE Task 或 Candidate Pool；
- 未调用真实 Role Directory；
- Investment 未修改。

## 11. 阻断问题

### P0-1 Snapshot 不可变性未由数据库强制

数据库允许 UPDATE 和 DELETE `workflow_role_runtime_binding_snapshot`。Repository 的 insert/query 限制不足以防止运维 SQL、其他 Mapper 或未来代码绕过。

### P0-2 Approval 与 Snapshot 状态链不完整

数据库允许 PENDING Approval 生成 Runtime Snapshot，不能证明 Runtime Binding 来源于有效批准。

### P0-3 Approval Insert Only 未闭合

数据库允许对 Approval 做满足 CHECK 的原位 UPDATE，审批历史可能被覆盖。

### P0-4 Resolver Version 业务键约束不足

`resolver_version NOT NULL` 不能拒绝空字符串，可能形成不可解析的冻结证据。

## 12. 资产状态

```text
Version: V2.6.9
Asset status: CANDIDATE
Execution status: EPHEMERAL_MYSQL8_VALIDATION_FAILED
Flyway checksum: 1523438049
SHA-256: fbe41e2a354e616ee4b369c57980009057c75ced46a2eb9e48d1eb8354beb0d2
ROLE Runtime: DISABLED
```

失败历史必须保留，不得通过 `flyway repair`、删除 history 或修改 V2.6.9 掩盖。

## 13. 风险与后续建议

建议下一 Sprint 只设计并实现前向修复 Migration，不修改 V2.6.9：

1. 为 Approval 和 Snapshot 增加 BEFORE UPDATE/DELETE 拒绝 Trigger，明确 append-only 边界；
2. 增加 resolver_code/resolver_version 非空白 CHECK；
3. 为 Approval 的 APPROVED 状态建立可被 Snapshot 强外键引用的稳定所有权键，或采用只写批准事实扩展表；
4. 增加迁移前脏数据 Guard，失败必须发生在永久 DDL 前；
5. 单独执行 Fresh、V2.6.8 Upgrade、V2.6.9 Forward-Fix、负向约束和 Schema Fingerprint 组合验收；
6. 修复验收通过前，继续保持 `ROLE_DIRECTORY_V1=PREPARED` 和 `ROLE_RUNTIME_DISABLED`。

不要进入 WF5.8 业务运行，不要创建 ROLE Task/Candidate Pool。

