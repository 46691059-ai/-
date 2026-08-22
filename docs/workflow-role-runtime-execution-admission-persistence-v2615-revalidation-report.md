# V2.6.15 修订候选真实 MySQL/Flyway 重验报告

## 1. 最终结论

**FAIL / NOT PROMOTED**。

V2.6.15 的 MySQL 6125、Slot 父唯一键、11 条 FK、DDL 顺序和部分安装
Fail Closed 问题已经关闭；但数据库完整性验收发现两个 P0：

1. 0 条 Evidence 的 Admission 可以写入完整事件链并到达
   `APPROVED_FOR_EXECUTION`。
2. 28 条 Evidence 中存在 `capability_status=NOT_READY` 时，仍可到达
   `APPROVED_FOR_EXECUTION`。

另发现 Slot 可被直接 DELETE，未被限制为 CAS-only mutation。根据“全部通过方可
晋级”规则，V2.6.15 不得标记为 canonical。

## 2. 环境

- MySQL Community Server 8.4.9
- Flyway Community Edition 13.0.0
- Java 21.0.12
- Maven 3.9.9
- 全部数据库均为本轮新建的本机 `127.0.0.1` 一次性实例
- 未连接生产、共享、托管或未知数据库

## 3. 原失败候选

- 原 SHA-256：`405fb7fc3bc928fc84643bcbf5583cd9f9555dad8209ad6b4d962529ce969887`
- 原 Flyway checksum：`1424227542`
- 状态：`ARCHIVED_FAILED_CANDIDATE`
- 原因：Slot FK 缺少完全匹配的父 UNIQUE，MySQL 6125。

## 4. 修订候选资产

- SHA-256：`7887bfb1d5bb9aacba378bc62618ba92fdd58fb1a85007053da4899d1fe9d412`
- observed Flyway checksum：`-1956070127`
- Migration SHA：37/37 匹配
- V2.6.14 SHA：
  `a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442`
- V2.6.14 无摘要漂移，V2.6.15 SQL 在本 Sprint 未修改。

## 5. Fresh

- 完整 Migration 链执行成功。
- Flyway history 失败记录为 0。
- V2.6.15 成功记录为 1。
- strict validate：PASS。
- 第二次 migrate：`No migration necessary`。

## 6. Upgrade

- V2.6.14 基线升级至 V2.6.15 成功。
- 仅新增执行 V2.6.15 一次。
- strict validate：PASS。
- 第二次 migrate：no-op。

## 7. Schema Fingerprint

Fresh 与 Upgrade 完全一致：

| 范围 | SHA-256 |
|---|---|
| Full Schema | `4b9dd9dd19b9ba37d16bc19d74e7f75335730df0a757f3c1fffa93d2df3c8540` |
| Workflow Schema | `e651f2ee050933c8eadf97fac5511180e9671ba8b667c403a95e1530a86dc6c4` |
| Execution Admission | `94d1614c77d0c9f06874031b163f47c32db50d4fc4401bcca30a9b4273ded3d6` |

Fingerprint 覆盖表、列、索引、唯一约束、FK、CHECK 和 Trigger。

## 8. 11 条 FK

真实 `information_schema` 核查结果：11/11 创建成功。

| FK | 子表 | 父表 | 结果 |
|---|---|---|---|
| fk_role_binding_snapshot_definition_version | Candidate Snapshot | workflow_version | ENFORCED |
| fk_role_binding_snapshot_node | Candidate Snapshot | workflow_node | ENFORCED |
| fk_role_binding_snapshot_release | Candidate Snapshot | workflow_version_release | ENFORCED |
| fk_role_admission_candidate | Admission | Candidate Snapshot | ENFORCED |
| fk_role_admission_definition_version | Admission | workflow_version | ENFORCED |
| fk_role_admission_node | Admission | workflow_node | ENFORCED |
| fk_role_admission_release | Admission | workflow_version_release | ENFORCED |
| fk_role_admission_slot_candidate | Slot | Candidate Snapshot | ENFORCED |
| fk_role_admission_slot_active | Slot | Admission | ENFORCED |
| fk_role_admission_evidence_owner | Evidence | Admission | ENFORCED |
| fk_role_admission_event_owner | Event | Admission | ENFORCED |

## 9. Slot 父唯一键

`uk_role_binding_snapshot_slot_owner` 真实存在且 `non_unique=0`，列顺序为：

`id,snapshot_id,delete_token`

Slot FK 引用列完全一致，无 prefix-only、缺列、换序或 delete_token 错配。

## 10. Partial Install Matrix 与零新增

10/10 场景全部 Fail Closed，均返回 MySQL `3819 / HY000`。每个场景迁移前后
永久治理对象签名一致：

1. 仅 Candidate 新字段：PASS
2. Candidate 字段 + Admission：PASS
3. Admission + Evidence：PASS
4. Admission + Evidence + Event：PASS
5. 三表存在但缺 Slot：PASS
6. Slot 存在但缺 FK：PASS
7. FK 部分存在：PASS
8. CHECK 部分存在：PASS
9. Trigger 部分存在：PASS
10. Candidate 增强字段部分存在：PASS

Migration 未自动续装、DROP、补齐、回填或修复。

## 11. Pre-DDL Guard

- V2.6.14 基线对象检查：PASS。
- Candidate/Promotion 归属与 Hash 检查：PASS。
- Resolver Code/Version/Contract 检查：PASS。
- Definition Version、Node、Release 父键证明：PASS。
- 部分安装检查：10/10 PASS。
- 更广的 Activation 异常组合沿用已冻结 V2.6.14 Guard；本轮未改其 SQL。

## 12. Candidate Snapshot 增强

在 V2.6.14 数据库先创建真实历史 ROLE Candidate 后执行 Upgrade。新增的 10 个
Admission 未来证据字段全部保持 NULL（10/10），未回填 Definition、Version、
Node、Directory revision/hash 或 fence。

未来合法 Candidate 必须一次性提供完整证据组；实库合法样本创建成功。

## 13. 四个核心对象

- 表：4/4
- 列：116
- 独立索引：31
- 核心表 FK：8；加 Candidate 新增 FK 后合计 11
- CHECK：18
- Trigger：9
- 字符集：utf8mb4
- Hash/稳定标识字段：ASCII/ascii_bin

审计字段、delete_token 与 version 均按设计存在。

## 14. Admission 强归属

以下实库负向测试全部拒绝并返回
`ROLE_ADMISSION_FROZEN_EVIDENCE_MISMATCH`：

- Directory revision 漂移
- Directory result hash 漂移
- Resolver contract 漂移
- 跨 Definition
- 跨 Version
- 跨 Node
- 跨 Candidate

Activation → Promotion → Candidate → Admission → Evidence/Event 的 FK 和 Trigger
归属链有效。

## 15. 28 项 Evidence

合法 Admission 实际写入 28/28：

- 最小序号 1，最大序号 28
- 28 个唯一 sequence
- 28 个唯一 validatorCode
- 重复 sequence 并发写入：单赢家，另一会话返回 1062

但少 28 项 Evidence 并未阻止最终批准：0 条 Evidence 的 Admission 成功写入
`ADMISSION_CREATED → ELIGIBLE → APPROVED_FOR_EXECUTION`。该项 **FAIL/P0**。

## 16. Capability Evidence

合法链覆盖 Directory、Realtime Eligibility、DataScope、SoD、Audit、
Feature Flag、Kill Switch、Canary，并以 READY 成功持久化。

负向样本将 Directory 标记为 NOT_READY，数据库仍允许 28 条 Evidence 和最终
APPROVED 事件。DEGRADED/BLOCKED 与 NOT_READY 具有同一缺失门禁。该项
**FAIL/P0**。

## 17. Evidence Root Hash

Domain 测试通过：

- 同 Evidence 集合仅顺序变化，Root Hash 相同。
- Evidence 内容变化，Root Hash 变化。
- Root Hash 变化，Persistence Hash 变化。

数据库仅验证 Hash 格式与大小写，未重新计算 Root 或把 Event 最终批准与实际
Evidence 集合绑定；这是上述 P0 的直接边界缺口。

## 18. Directory Revision Fence

- R1/H1：PASS。
- R2/H1：拒绝。
- R1/H2：拒绝。
- 未读取最新 Directory 替换冻结值。

## 19. Definition / Version / Node

合法 Definition、published Version、Node、Release、node binding hash 与 graph
hash 匹配时通过。跨 Definition、Version、Node 或 binding evidence 均拒绝。

## 20. Append-only

下列 6 项全部由 Trigger 以 MySQL `1644 / SQLSTATE 45000` 拒绝：

- Admission UPDATE / DELETE
- Evidence UPDATE / DELETE
- Event UPDATE / DELETE

错误分别为 `ROLE_EXECUTION_ADMISSION_APPEND_ONLY`、
`ROLE_EXECUTION_ADMISSION_EVIDENCE_APPEND_ONLY` 和
`ROLE_EXECUTION_ADMISSION_EVENT_APPEND_ONLY`。

Slot 允许 CAS UPDATE，但直接 DELETE 探针在事务内影响 1 行，说明 Slot 缺少
CAS-only DELETE 防护。事务随后 ROLLBACK，测试数据未丢失。

## 21. Slot CAS 与并发

真实双 Session 同时竞争同一 Candidate：

- Session A：更新 1 行。
- Session B：更新 0 行。
- 最终仅一个 active Admission，version 从 2 增至 3。
- 未发生 1213 或 1205。

同 requestId 并发 Admission：1 成功、1 返回 1062；最终 1 行。

同 Admission/sequence Evidence 并发：1 成功、1 返回 1062；最终 1 行。

需要注意：CAS 本身正确，但它可以指向因 Evidence 门禁缺失而错误进入
APPROVED 事件链的 Admission。

## 22. 幂等与 Event

- 重复 requestId：1062 拒绝。
- 同 Candidate/idempotencyKey：唯一约束保护。
- 重复 Evidence sequence/validator：唯一约束保护。
- 非法 `APPROVED_FOR_EXECUTION → ELIGIBLE`：Trigger 拒绝。
- 合法 CREATED → ELIGIBLE → APPROVED、APPROVED → REVOKED：通过。
- REVOKED/EXPIRED 后重新批准：状态链规则拒绝。

Event 状态链不能替代 Evidence 完整性和 capability READY 校验。

## 23. Hash / Collation

大写、混合大小写、长度 63、非 Hex 的 Evidence Hash 全部被
`chk_role_admission_evidence_hash` 以 3819/HY000 拒绝。必填 Hash 的 NULL
由 NOT NULL 拒绝。ASCII/ascii_bin 大小写敏感定义符合设计。

## 24. 事务回滚与孤儿防护

- Admission 成功、Evidence Hash 失败：事务退出后 Admission/Evidence/Event
  均为 0。
- Slot 临时写入、Admission frozen evidence 失败：事务退出后原 Slot 指针恢复，
  新 Admission 为 0。
- 未产生孤儿 Slot、Admission、Evidence 或 Event。

## 25. 合法完整链

合法链成功：

Persisted Candidate → 28 PASS Evidence → 8 类 READY capability → Admission →
3 个 Event → Slot。

最终 Slot 唯一指向该 Admission。整个过程未创建 WorkflowInstance、
NodeExecution、Task、CandidatePool 或 Claim。

但负向完整性门禁失败，因此合法链成功不能支持资产晋级。

## 26. Legacy / USER

- EXPLICIT_USER_V1 保持 ACTIVE。
- ROLE_DIRECTORY_V1 保持 PREPARED/NON_EXECUTABLE。
- USER + DIRECT、SINGLE_NODE_LEGACY、历史 Multi-Node USER 不进入该持久化链。
- 历史 ROLE Candidate 未补造 Admission。
- ROLE Runtime 保持 DISABLED。

## 27. 应用回归

- Java 21 编译：PASS。
- Spring Boot Context：PASS。
- 后端全量测试：477 通过，0 失败，0 错误，0 跳过。
- V2.6.15 Migration Contract Test：PASS。
- DDL Dependency Test：PASS。
- Admission Domain/Application 专项测试：PASS。
- Domain 纯净检查：PASS。
- Production Dependency Scan：PASS。
- Migration SHA：37/37。
- `git diff --check`：PASS。
- Migration SQL 修改：0。
- Controller/API 新增：0。
- Investment 修改：0。

## 28. Migration 资产状态

```text
V2.6.15
asset_status: CANDIDATE
execution_status: EPHEMERAL_MYSQL8_VALIDATION_FAILED
sha256: 7887bfb1d5bb9aacba378bc62618ba92fdd58fb1a85007053da4899d1fe9d412
flyway_checksum: null
observed_flyway_checksum: -1956070127
```

不得晋级为 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。

## 29. 临时环境清理

验收结束后关闭本轮 36611、36612、36613 回环端口上的 MySQL 实例，并删除
`D:\codex-validation-v2615-recheck-20260817-1810` 一次性验收目录。没有保留
可继续运行的数据库实例或明文凭据。

## 30. 剩余风险

1. 最终批准事件未校验 Evidence 实际数量、序号全集、validator 集合与 root hash。
2. capability NOT_READY/DEGRADED/BLOCKED 未阻断最终批准。
3. Slot 可直接 DELETE，数据库未强制所有变更走 CAS。
4. Admission 在 Evidence 之前即冻结 decision/count 声明，数据库未验证声明与子表事实一致。
5. 当前应用层事务校验能降低风险，但不能替代数据库强制完整性。

## 31. 下一步建议

下一 Sprint 应先做 V2.6.15 候选完整性治理设计，明确：

- 最终批准 Event 的 28/28 Evidence、validator 唯一集合与 Root Hash Guard；
- capability 全 READY 的数据库可证明门禁；
- Admission decision 声明与 Event/Evidence 事实的一致性；
- Slot DELETE/非法 UPDATE 的 CAS-only Trigger；
- 失败候选归档与是否允许再次修订未晋级 V2.6.15。

在治理冻结前不要创建 V2.6.16，不要启用 ROLE Runtime，也不要进入 WF5.19。
