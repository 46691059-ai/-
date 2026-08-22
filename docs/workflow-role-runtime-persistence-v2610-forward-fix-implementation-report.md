# V2.6.10 ROLE Runtime Persistence Integrity Forward Fix 实施报告

## 1. 失败问题映射

V2.6.9 真实 MySQL 验收的五项 P0 与 V2.6.10 修复映射如下：

| P0 | V2.6.9 实际缺口 | V2.6.10 前向修复 |
| --- | --- | --- |
| P0-1 | 空 `resolver_version` 被接受 | Approval 与 Snapshot 增加 Resolver Code/Version 非空白 CHECK |
| P0-2 | Snapshot UPDATE 被接受 | `BEFORE UPDATE` Trigger，返回 1644/45000 |
| P0-3 | Snapshot DELETE 被接受 | `BEFORE DELETE` Trigger，返回 1644/45000 |
| P0-4 | Approval 原位 UPDATE 被接受 | Approval `BEFORE UPDATE` Trigger，强制 append-only |
| P0-5 | PENDING Approval 可生成 Snapshot | Snapshot `BEFORE INSERT` Trigger 校验 Approval=APPROVED 及 Resolver/Contract 一致 |

V2.6.9 与历史 Migration 均未修改，失败验收记录继续保留。

## 2. V2.6.10 设计

新增候选 Migration：

```text
V2.6.10__harden_role_runtime_persistence_integrity.sql
```

执行顺序：

```text
临时脏数据Guard
→ Resolver身份CHECK
→ Approval append-only Trigger
→ Snapshot INSERT治理Trigger
→ Snapshot UPDATE/DELETE不可变Trigger
```

V2.6.10 仅强化 V2.6.9 持久化完整性，不创建 ROLE Runtime、Task 或 Candidate Pool，不调用 Role Directory，不修改 Investment。

## 3. 数据库变化

### 3.1 Resolver Identity

`role_runtime_binding_approval` 和 `workflow_role_runtime_binding_snapshot` 新增：

```sql
CHAR_LENGTH(TRIM(resolver_code)) > 0
AND CHAR_LENGTH(TRIM(resolver_version)) > 0
```

原字段已经是 `NOT NULL`，新增 CHECK 负责拒绝空字符串和纯空白字符串。

### 3.2 Approval 强关联

V2.6.9 已包含 `approval_id` 及外键，V2.6.10 不重复增加列或外键。新增 Snapshot INSERT Trigger：

- Approval 必须存在且未逻辑删除；
- Approval 状态必须为 APPROVED；
- Approval 与 Snapshot 的 resolver_code、resolver_version、contract_hash 必须一致；
- 不满足时使用 1644/45000 Fail Closed。

### 3.3 Hash 保护

保留 V2.6.9 小写 SHA-256 CHECK，并在 Snapshot INSERT Trigger 再校验：

- binding_hash；
- contract_hash。

Canonical 版本保持：

```text
ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1
```

V2.6.10 不改变 Hash 算法或业务字段集合。

## 4. Trigger 治理

### 4.1 Approval

```text
trg_role_runtime_approval_no_update
```

任何 UPDATE 均拒绝：

```text
1644 / 45000
ROLE_RUNTIME_APPROVAL_APPEND_ONLY
```

状态变化必须追加新的审批事实，禁止覆盖已有记录。

### 4.2 Runtime Snapshot

```text
trg_workflow_role_runtime_snapshot_insert_guard
trg_workflow_role_runtime_snapshot_no_update
trg_workflow_role_runtime_snapshot_no_delete
```

错误语义：

- `ROLE_RUNTIME_APPROVAL_NOT_APPROVED`；
- `ROLE_RUNTIME_APPROVAL_CONTRACT_MISMATCH`；
- `ROLE_RUNTIME_SNAPSHOT_HASH_INVALID`；
- `ROLE_RUNTIME_SNAPSHOT_IMMUTABLE`。

Snapshot 只允许合法 INSERT；所有 UPDATE 和 DELETE 均拒绝。

## 5. Migration 预检 Guard

永久 DDL 前创建临时 CHECK Guard，统计：

1. Approval 或 Snapshot Resolver Code/Version 为空白；
2. Snapshot `version <> 0` 或 `updated_time <> created_time` 的可观察修改证据；
3. Snapshot 引用非 APPROVED 或已删除 Approval；
4. 非法 Approval 状态。

违规计数非零时由临时 CHECK 立即终止 Migration。Guard 不修复、不删除、不推断或回填数据，并在通过后删除临时表。

说明：V2.6.9 没有独立不可变审计事件表，因此 Guard 只能使用现有持久化字段识别可观察的修改证据；无法取证未留下字段变化的历史尝试。这一边界已列入风险。

## 6. 测试结果

### 6.1 定向契约测试

```text
Tests run: 12
Failures: 0
Errors: 0
Skipped: 0
```

覆盖：

- Guard 先于永久 DDL；
- Guard 不自动修改数据；
- Resolver 非空白约束；
- Approval/Snapshot Trigger 名称、错误码和错误消息；
- APPROVED Approval 及精确 Resolver Contract 门禁；
- Hash INSERT 门禁；
- V2.6.10 Candidate/Not Executed 状态；
- ROLE Registry 继续 PREPARED；
- 无 Task、Candidate Pool、Investment 或运行启用内容；
- V2.6.9 Domain 与纯净检查回归。

### 6.2 后端全量回归

环境：Java 21.0.12、Maven 3.9.9、Spring Boot 3.5.9。

```text
Tests run: 382
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

Spring Boot 上下文、EXPLICIT_USER_V1、Legacy、Candidate Pool、Claim、Workflow Security 与 Investment 既有测试全部通过。

### 6.3 真实数据库边界

本 Sprint 未执行 V2.6.10 真实 MySQL/Flyway Migration。Fresh、Upgrade、Forward-Fix、strict validate、no-op、Trigger 错误码与 Schema Fingerprint 必须在独立验收 Sprint 完成。

## 7. Migration 状态

V2.6.10 SHA-256：

```text
2fad3f67a4a487568f5fc29f40cec73894d89fff65564212a75be98da186f7d6
```

资产状态：

```text
Version: V2.6.10
Asset status: CANDIDATE
Execution status: NOT_EXECUTED
Flyway checksum: null
```

V2.6.9 继续保持：

```text
CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED
```

V2.6.10 通过真实组合验收前，不得晋级 V2.6.9/V2.6.10。

## 8. 风险

1. V2.6.10 尚未经过 MySQL 8.4.9/Flyway 13.0.0 真实执行；
2. Approval append-only 后，应用未来必须使用新记录表达状态变化，不能调用现有 UPDATE；
3. Snapshot INSERT Trigger 使用跨表读取，需要验证并发 Approval 状态与隔离级别行为；
4. V2.6.9 未保存独立修改事件，Guard 对历史修改证据的识别受现有字段限制；
5. Trigger 需要纳入 Schema Fingerprint、备份恢复和数据库兼容性评估；
6. ROLE Runtime 仍未接入真实 Role Directory、Task、Candidate Pool 或 Claim 运行链。

## 9. 下一步建议

单独执行 V2.6.9 + V2.6.10 真实组合验收：

1. Fresh 完整链至 V2.6.10；
2. V2.6.9 → V2.6.10 Upgrade；
3. V2.6.9 失败环境 Forward-Fix；
4. Guard 失败必须发生在永久 DDL 前；
5. NULL/空白 Resolver、Snapshot UPDATE/DELETE、Approval UPDATE、PENDING Snapshot 全部返回预期错误；
6. APPROVED 且合同一致的合法 INSERT 通过；
7. strict validate、二次 migrate no-op、Fresh/Upgrade/Forward-Fix Schema Fingerprint 一致；
8. 通过后再治理 V2.6.9/V2.6.10 资产状态。

最终代码状态：

```text
ROLE_RUNTIME_PERSISTENCE_INTEGRITY_HARDENED
ROLE_RUNTIME_DISABLED
```

