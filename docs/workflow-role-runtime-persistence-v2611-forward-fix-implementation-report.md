# Workflow ROLE Runtime Persistence V2.6.11 Forward Fix Implementation Report

## 1. 修改文件清单

新增：

- `database/migration/mysql/V2.6.11__repair_role_runtime_historical_integrity_guard.sql`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/infrastructure/V2611HistoricalIntegrityGuardTest.java`
- `docs/workflow-role-runtime-persistence-v2611-forward-fix-implementation-report.md`

修改：

- `database/flyway/migration-inventory.yml`
- `database/migration/mysql/SHA256SUMS`
- `database/migration/mysql/README.md`

未修改 V2.6.9、V2.6.10 或任何历史 Migration；未修改 Workflow Runtime
业务代码、Investment、Controller 或 Resolver Registry。

## 2. Migration变化

新增前向候选：

`V2.6.11__repair_role_runtime_historical_integrity_guard.sql`

该 Migration 不新增业务表，不迁移业务数据，不删除、自动修复、补齐或推断任何
历史证据。执行顺序固定为：

1. 创建临时 Guard；
2. 全量扫描历史 Approval 与 Snapshot；
3. 通过临时表 CHECK 实施 fail-closed；
4. Guard 成功后删除临时表；
5. 最后创建 Approval DELETE 和 Snapshot Canonical INSERT 两个永久 Trigger。

因此 V2.6.11 自身的永久 DDL 只会在历史证据完整性全部满足时开始执行。

## 3. Guard规则

### 3.1 Approval证据

检查 `resolver_code`、`resolver_version`、`contract_hash`：

- 非 NULL、非空白；
- Resolver Code/Version 不允许首尾空格并符合领域格式；
- Contract Hash 必须为64位小写十六进制SHA-256。

### 3.2 Snapshot证据

检查 Resolver Code/Version、Contract Hash、Binding Hash，以及 Canonical 计算所依赖的
Directory、Role Rule、Candidate Rule、Source Evidence Hash：

- 全部非空且格式合法；
- Hash 必须为64位小写十六进制SHA-256。

### 3.3 Approval/Snapshot所有权

通过 `LEFT JOIN` 验证：

- `approval_id` 对应记录必须存在；
- Approval 必须活动且状态为 `APPROVED`；
- Resolver Code、Resolver Version、Contract Hash 必须按二进制语义完全一致。

### 3.4 Canonical一致性

数据库使用 `SHA2(..., 256)` 重算
`ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1`。输入覆盖：

- resolverCode、resolverVersion、contractHash；
- roleCode、organizationId；
- directoryRevision、effectiveAt；
- directoryHash、roleRuleHash、candidateRuleHash、sourceEvidenceHash。

重算结果与存储的 `binding_hash` 使用二进制比较；不一致立即失败，不执行修复。
DATETIME(3) 按领域 `Instant.toString()` 的UTC毫秒格式序列化，零毫秒时不附加小数位。

## 4. Trigger规则

- `trg_role_runtime_approval_no_delete`：拒绝 Approval DELETE，错误
  `ROLE_RUNTIME_APPROVAL_APPEND_ONLY`；
- `trg_workflow_role_runtime_snapshot_canonical_guard`：新 Snapshot INSERT 时重新计算
  Canonical V1 Hash，不一致返回
  `ROLE_RUNTIME_SNAPSHOT_CANONICAL_HASH_MISMATCH`；
- V2.6.10 已有 Approval UPDATE、Snapshot UPDATE/DELETE、Approval所有权及Resolver
  Contract INSERT Trigger保持不变。

组合后 Approval 与 Snapshot 均为 append-only，Snapshot 的 Resolver/Contract/Binding
Hash 不可原位修改，也不可删除。

## 5. 组合验收准备

下一验收Sprint必须使用三个独立、一次性、loopback-only MySQL实例：

1. Fresh：基础Schema → V2.6.9 → V2.6.10 → V2.6.11；
2. Upgrade：V2.6.8 → V2.6.9 → V2.6.10 → V2.6.11；
3. Forward Fix：已完成V2.6.9环境 → V2.6.10 → V2.6.11。

每条路径必须验证 migrate、strict validate、二次no-op、Flyway History与Full、
Workflow、ROLE Runtime Schema Fingerprint，最终要求三路径完全一致。

脏数据验收必须在隔离的V2.6.10完成库中分别注入：非法Contract、非法Snapshot
Hash、Approval/Snapshot不一致。V2.6.11必须由 Guard 拒绝，并确认 V2.6.11新增的
两个Trigger均不存在。合法且Canonical Hash正确的数据必须成功升级。

本Sprint只完成候选实现与验收设计，未执行真实MySQL/Flyway Migration。

## 6. 测试结果

- `V2611HistoricalIntegrityGuardTest`：5项通过；
- V2.6.9/V2.6.10/V2.6.11相关定向契约测试：12项通过；
- Java 21后端全量测试：390项通过，0失败，0错误，0跳过；
- Spring Boot测试上下文启动通过；
- Domain与业务运行代码未变化；
- `ROLE_DIRECTORY_V1`仍为 `PREPARED, false`。

上述结果是代码与Migration契约测试，不是V2.6.11真实MySQL执行证据。

## 7. SHA-256

V2.6.11：

`ee16801af2f989449b1372ff82d76bd9850950a51ea7badff0125804c0815b06`

`database/migration/mysql/SHA256SUMS` 已新增该记录；33项Migration摘要均匹配。
V2.6.9与V2.6.10摘要保持不变。

## 8. 资产状态

| 资产 | 状态 |
| --- | --- |
| V2.6.9 | `CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`，待组合复验 |
| V2.6.10 | `CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`，待组合复验 |
| V2.6.11 | `CANDIDATE / NOT_EXECUTED`，`flyway_checksum: null` |

保持 `ROLE_RUNTIME_DISABLED`；未创建ROLE Task、Candidate Pool或Claim，未访问真实
Role Directory。

## 9. 剩余风险

1. MySQL对Canonical JSON字符串和DATETIME(3)格式的实际计算尚需真实验收确认；
2. V2.6.10已经创建的永久Trigger早于V2.6.11运行，V2.6.11只保证其自身永久DDL前
   fail-closed，不能改写历史Migration执行顺序；
3. MySQL DDL自动提交要求每个脏数据场景使用独立实例验证无V2.6.11部分DDL；
4. 真实环境若存在不一致证据，Migration将按设计阻断，必须走独立人工数据治理，
   本Migration不会自动修复。

下一步仅应执行V2.6.9→V2.6.10→V2.6.11真实组合MySQL/Flyway验收；通过前不得
晋级资产、进入WF5.8或启用ROLE Runtime。
