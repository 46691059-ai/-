# V2.6.15 Final Approval Integrity Governance Design

## 1. 当前失败结论

- Sprint：`2-3.7-WF5.18.4`
- 稳定基线：V2.6.14 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- V2.6.14 SHA-256：`a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442`
- 当前候选：V2.6.15 `CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`
- V2.6.15 SHA-256：`7887bfb1d5bb9aacba378bc62618ba92fdd58fb1a85007053da4899d1fe9d412`
- V2.6.15 observed Flyway checksum：`-1956070127`
- `EXPLICIT_USER_V1`：`ACTIVE`
- `ROLE_DIRECTORY_V1`：`PREPARED / NON_EXECUTABLE`
- ROLE Runtime：`DISABLED`

WF5.18.3 已证明候选的 Fresh/Upgrade、11 条 FK、Partial Install Guard、Append-only、事务和并发基础可用，但最终批准完整性不成立：零 Evidence 和含 `NOT_READY` Capability 的 Evidence 均可进入 `APPROVED_FOR_EXECUTION`；Admission Slot 也可被直接 DELETE。因此当前候选不得晋级。

本 Sprint 只完成设计冻结。未修改 SQL、业务代码、测试、Migration Inventory 或 SHA 清单，未创建 V2.6.16。

## 2. P0/P1 根因

| 编号 | 缺口 | 根因 | 治理结果 |
|---|---|---|---|
| P0-1 | 最终批准不要求 28/28 Evidence | Event Insert Guard 只检查事件序列和状态转换 | 最终事件前验证精确的 28 项合同 |
| P0-2 | `NOT_READY/DEGRADED/BLOCKED` 可批准 | Capability CHECK 只约束枚举合法，不约束批准语义 | 8 项 Required Capability 必须全部 `READY` 且 `result=PASS` |
| P0-3 | Root Hash 未绑定实际 Evidence | 数据库只检查 Hash 格式，未重算集合摘要 | 数据库重算 Capability Aggregate Digest 并校验 Event、Admission、Persistence 三方绑定 |
| P1 | Slot 可直接 DELETE | 只有 UPDATE Guard，没有 DELETE Guard | Slot 永久保留，DELETE 一律拒绝；UPDATE 仅允许受控 CAS 状态转换 |

## 3. 当前终态链路只读审计

### 3.1 当前事实

1. 最终批准通过向 `workflow_role_runtime_execution_admission_event` 插入 `event_type/to_status=APPROVED_FOR_EXECUTION` 表达；Admission 主体也保存稳定 `decision`，但不会被事件 Trigger 更新。
2. `trg_role_admission_event_insert_guard` 负责事件链推进。
3. 该 Trigger 当前只检查首事件、`sequence_no`、`previous_event_hash`、`from_status`、允许的状态转换以及部分终态限制。
4. 当前不检查 28 个 `validator_code` 的精确集合。
5. 当前不检查 `sequence_no=1..28` 的完整集合。
6. 当前不检查最终批准时所有普通 Validator 为 `PASS`、所有 Capability 为 `READY`。
7. Capability 仅通过 Evidence 的 `capability_code/capability_status` 是否为空与普通 Validator 区分；数据库没有冻结 code 到 order 的映射。
8. Admission 的 Root 字段为 `capability_evidence_root_hash`；Event 的来源 Root 字段为 `source_evidence_root_hash`。
9. Event Root 当前不从实际 Evidence 集合重算，也不与 Admission Root 强比较。
10. Slot 在批准后保存 Candidate 当前活动 Admission 指针；当前应用顺序是先写最终 Event、后 CAS 占用 Slot，无法满足“最终 Event 插入时 Slot 已归属该 Admission”。

### 3.2 当前 Trigger 不能证明的事实

`executed_check_count=28` 只是 Admission 调用方声明；Evidence 的唯一序号和唯一代码约束只能防重复，不能防缺失、错码或错误的 order/code 配对。`result` 与 `capability_status` 的 CHECK 只能证明枚举合法，不能证明批准条件成立。

## 4. 精确 28 项 Validator Contract

冻结合同：`ROLE_RUNTIME_EXECUTION_ADMISSION_VALIDATOR_CONTRACT_V1`。最终批准必须存在且仅存在以下 28 行，`sequence_no` 与 `validator_code` 必须逐项匹配：

| Order | validator_code | 类型 | 最终批准要求 |
|---:|---|---|---|
| 1 | `PERSISTED_CANDIDATE_EXISTS` | BUSINESS | `PASS`，Capability 字段均为 NULL |
| 2 | `CANDIDATE_SNAPSHOT_STATUS` | BUSINESS | 同上 |
| 3 | `PROMOTION_EVIDENCE_COMPLETE` | BUSINESS | 同上 |
| 4 | `ACTIVATION_EVIDENCE_COMPLETE` | BUSINESS | 同上 |
| 5 | `ACTIVATION_NOT_REVOKED` | BUSINESS | 同上 |
| 6 | `PROMOTION_NOT_REVOKED` | BUSINESS | 同上 |
| 7 | `RESOLVER_CODE` | BUSINESS | 同上 |
| 8 | `RESOLVER_VERSION` | BUSINESS | 同上 |
| 9 | `RESOLVER_CONTRACT_HASH` | BUSINESS | 同上 |
| 10 | `ACTIVATION_HASH` | BUSINESS | 同上 |
| 11 | `PROMOTION_HASH` | BUSINESS | 同上 |
| 12 | `BINDING_HASH` | BUSINESS | 同上 |
| 13 | `CANDIDATE_HASH` | BUSINESS | 同上 |
| 14 | `DIRECTORY_REVISION_AND_RESULT_HASH` | BUSINESS | 同上 |
| 15 | `EFFECTIVE_AT` | BUSINESS | 同上 |
| 16 | `BUSINESS_SCOPE` | BUSINESS | 同上 |
| 17 | `DEFINITION_VERSION` | BUSINESS | 同上 |
| 18 | `NODE_BINDING` | BUSINESS | 同上 |
| 19 | `RESOLVER_DESCRIPTOR` | BUSINESS | 同上 |
| 20 | `RESOLVER_ADMISSION_STATUS` | BUSINESS | 同上 |
| 21 | `DIRECTORY_READY` | CAPABILITY | `PASS` + `DIRECTORY/READY` |
| 22 | `REALTIME_ELIGIBILITY_READY` | CAPABILITY | `PASS` + `REALTIME_ELIGIBILITY/READY` |
| 23 | `DATA_SCOPE_READY` | CAPABILITY | `PASS` + `DATA_SCOPE/READY` |
| 24 | `SOD_READY` | CAPABILITY | `PASS` + `SOD/READY` |
| 25 | `AUDIT_READY` | CAPABILITY | `PASS` + `AUDIT/READY` |
| 26 | `FEATURE_FLAG_READY` | CAPABILITY | `PASS` + `FEATURE_FLAG/READY` |
| 27 | `KILL_SWITCH_READY` | CAPABILITY | `PASS` + `KILL_SWITCH/READY` |
| 28 | `CANARY_SCOPE_READY` | CAPABILITY | `PASS` + `CANARY_SCOPE/READY` |

数据库实现决策：不增加可变的 Validator 配置表；在最终批准 Trigger 中使用固定的 28 个 `SUM(CASE WHEN sequence_no=n AND validator_code=... THEN 1 END)=1` 合同。并同时要求总数恰为 28、`MIN(sequence_no)=1`、`MAX(sequence_no)=28`、`COUNT(DISTINCT sequence_no)=28`、`COUNT(DISTINCT validator_code)=28`。不得以 `COUNT(*)>=28` 替代。

## 5. PASS/READY 语义与 Required Capability Set

普通业务 Validator 仅允许批准语义 `result=PASS`，且 `capability_code`、`capability_status` 必须为 NULL。Capability Validator 使用双重语义：

- `result=PASS` 表示该检查步骤执行成功并得到可接受结果；
- `capability_status=READY` 表示对应运行能力当前具备准入资格；
- `result=FAIL` 或 `NOT_READY/DEGRADED/BLOCKED` 任一出现，都不得进入最终批准。

Required Capability 固定集合为：

1. `DIRECTORY`
2. `REALTIME_ELIGIBILITY`
3. `DATA_SCOPE`
4. `SOD`
5. `AUDIT`
6. `FEATURE_FLAG`
7. `KILL_SWITCH`
8. `CANARY_SCOPE`

最终批准前必须证明 8/8 精确存在、无额外 code、code/order/validator 三元映射正确、全部 `READY`。应用写入数量不是数据库证据。

## 6. Capability Evidence Root Hash

### 6.1 方案比较

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| A：Trigger 重算每条 Evidence Hash 及集合 Root | 数据库完全自足 | 需要在 SQL 中复制全部业务 Canonical，时间和可选字段编码易与 Java 漂移 | 不采用 |
| B：应用计算每条不可变 Evidence Hash；数据库验证精确集合并重算 Aggregate Root | 数据库能证明“哪 8 条、什么状态、什么叶 Hash”组成 Root；实现稳定 | 叶 Hash 的业务内容仍由应用 Canonical 负责 | 采用 |

### 6.2 冻结 Canonical

新增合同名：`ROLE_RUNTIME_CAPABILITY_EVIDENCE_ROOT_CANONICAL_V1`。只对 order 21—28 的 8 条 Capability Evidence 计算 Root，按 `capability_code` ASCII 升序：

`AUDIT, CANARY_SCOPE, DATA_SCOPE, DIRECTORY, FEATURE_FLAG, KILL_SWITCH, REALTIME_ELIGIBILITY, SOD`。

每项 Canonical 依次包含：`sequence_no`、`validator_code`、`capability_code`、`capability_status`、`result`、`evidence_hash`。编码使用 UTF-8、字段名和值长度前缀；所有参与字段为 ASCII。Root 为该串的 SHA-256 小写 hex。

数据库不得依赖 `GROUP_CONCAT` 或会话级 `group_concat_max_len`。Trigger 使用固定的八个 `MAX(CASE WHEN capability_code=... THEN ... END)` 槽位拼接并调用 `SHA2(...,256)`；精确集合校验先于 Hash 重算。因此成员排序变化不影响 Root，缺项、重复、状态变化或叶 Hash 变化都会改变或阻断 Root。

当前 Java `capabilityRoot()` 实际按 sequence 对全部 Evidence 计算，语义与本合同不同。下一实施 Sprint 若仅修改 SQL 而不对该最小 Hash 契约做同步修正，必须 NO-GO；本 Sprint 不修改代码。

## 7. Execution Hash、Persistence Hash 与 Capability Root

三类 Hash 职责冻结如下：

| Hash | 证明内容 | 不负责 |
|---|---|---|
| `execution_admission_hash` | 进入持久化前的 Admission 请求与执行输入 | 不证明数据库实际收到了完整 Evidence |
| `capability_evidence_root_hash` | 数据库中 8 项 Required Capability 的精确集合、状态和叶 Hash | 不代替 1—20 普通 Validator 完整性 |
| `persistence_hash` | Admission 身份、冻结业务事实、Decision、Policy 及已验证 Capability Root 的持久化封印 | 不代替 Event Hash 链 |

最终批准 Trigger 必须：

1. 重算实际 Capability Root；
2. 要求其同时等于 Admission `capability_evidence_root_hash` 和 Event `source_evidence_root_hash`；
3. 使用实际 Root 重算 `persistence_hash`，并与 Admission 保存值比较。

为避免当前 Java `Instant.toString()` 与 MySQL `DATETIME(3)` 格式差异，修订实现必须冻结数据库可重算的 `ROLE_RUNTIME_EXECUTION_ADMISSION_PERSISTENCE_CANONICAL_V2`：UTC、固定三位毫秒、长度前缀、固定字段顺序，并覆盖原 V1 的全部业务字段以及实际 Capability Root。最终 Event 的 `canonical_version` 必须明确为 `ROLE_RUNTIME_FINAL_APPROVAL_CANONICAL_V1`。候选未发布，因此允许在归档失败候选后同步最小应用 Canonical 契约；禁止继续使用“应用传入但数据库不可验证”的 Persistence Hash。

## 8. Final Event Guard

修订后的 `BEFORE INSERT` Guard 对 `APPROVED_FOR_EXECUTION` 固定按以下顺序 Fail Closed：

1. 锁定并确认 Admission 存在，`admission_id/candidate_snapshot_row_id/delete_token` 归属一致；
2. Admission `decision=APPROVED_FOR_EXECUTION`；
3. 当前事件链最新状态为 `ELIGIBLE`，sequence、previous hash 与 from status 合法；
4. Admission 的 `executed_check_count=28` 且 `last_check_sequence=28`；
5. Evidence 总数、order、code 满足精确 28 项合同；
6. 1—20 全部 `PASS` 且无 Capability 字段；
7. 21—28 映射到精确 Required Capability Set；
8. 8 项全部 `result=PASS`、`capability_status=READY`；
9. 全部 `subject_hash/evidence_hash` 为 64 位小写 hex；
10. 重算 Capability Root，并校验 Admission Root 与 Event Root；
11. 使用实际 Root 重算并校验 Persistence Hash；
12. 当前链不存在 `REVOKED/EXPIRED`，且 `NEW.occurred_at < admission_expires_at`；
13. Slot 为 `OCCUPIED` 且当前指针、Admission ID、Candidate、token 与本 Admission 一致；
14. 最终 Decision 唯一键未被占用。

任一失败返回 `SQLSTATE 45000`。稳定错误至少包括：

- `ROLE_ADMISSION_EVIDENCE_INCOMPLETE`
- `ROLE_ADMISSION_VALIDATOR_CONTRACT_MISMATCH`
- `ROLE_ADMISSION_CAPABILITY_NOT_READY`
- `ROLE_ADMISSION_CAPABILITY_ROOT_MISMATCH`
- `ROLE_ADMISSION_PERSISTENCE_HASH_MISMATCH`
- `ROLE_ADMISSION_SLOT_OWNERSHIP_MISMATCH`
- `ROLE_ADMISSION_TERMINAL_CONFLICT`
- `ROLE_ADMISSION_EXPIRED`

## 9. Evidence 不可变与错误处理

现有 Evidence `BEFORE UPDATE/DELETE` append-only Trigger 保持。Evidence 一旦写入，不得修补、覆盖或删除。若任一 Evidence 错误、缺失或 Hash 漂移：

1. 原 Admission 追加 `BLOCKED` 或 `REJECTED`；
2. 不得继续批准原 Admission；
3. 修正外部事实后使用新 `admission_id/request_id/idempotency_key` 创建新 Admission；
4. 新 Admission 重新生成全部 Evidence 和 Hash。

零 Evidence、1/28、27/28、错码、错序、重复、FAIL、非 READY 均属于不可修补的原 Admission 失败事实。

## 10. Slot CAS-only 与生命周期

### 10.1 当前缺口与字段决策

当前 Slot 没有 `active_token`，只有 `version`。为同时防止 ABA 和证明调用方持有本次占用凭据，下一修订允许在 Slot 范围内增加 64 位 ASCII/ascii_bin `active_token`；该变更属于 Slot CAS 治理，不改变四张业务事实表的职责。

### 10.2 唯一允许的更新

Slot 永不 DELETE。增加 `BEFORE DELETE` Trigger，固定返回 `ROLE_ADMISSION_SLOT_CAS_ONLY`。

`BEFORE UPDATE` 只允许两种转换，并要求：Candidate 主键、`snapshot_id`、`source_delete_token` 不变；`version=OLD.version+1`；`active_token` 必须变化；除指针、status、token、version、updated audit 外没有字段变化。

- 占用：`VACANT/NULL -> OCCUPIED/admission`，以旧 `version+active_token` 为 CAS 条件；目标 Admission 已存在且已有 `ELIGIBLE` Event。
- 释放：`OCCUPIED/admission -> VACANT/NULL`，以旧 `version+active_token+active_admission_id` 为 CAS 条件；目标 Admission 已追加 `REVOKED` 或 `EXPIRED` Event。

任何普通 UPDATE、同值 token、跳 version、换 Candidate、直接改指针或无对应 Event 均拒绝。数据库 Trigger 证明合法状态差异；应用 SQL 必须同时在 WHERE 中比较旧 version/token。生产权限应只允许 Repository 的受控 CAS 语句，不授予人工 DML 通道。

### 10.3 最终批准事务顺序

为消除当前“先批准 Event、后占用 Slot”的循环依赖，批准事务固定为：

1. INSERT Admission；
2. INSERT 28 条 Evidence；
3. INSERT `ADMISSION_CREATED`；
4. INSERT `ELIGIBLE`；
5. CAS Slot `VACANT -> OCCUPIED`，Trigger 以 `ELIGIBLE` Event 授权；
6. INSERT `APPROVED_FOR_EXECUTION`，Final Guard 此时验证 Slot 已归属；
7. COMMIT。

任一步失败整体回滚。撤销/过期则先追加对应 closure Event，再以同一事务 CAS 释放；Slot 行保留，token 变化，version 加一。

## 11. 并发批准与终态唯一性

### 11.1 固定锁顺序

所有最终批准事务采用：Candidate Snapshot → Admission Slot → Admission → Event 链。Final Guard 对 Admission/Slot 读取使用锁定读。两个 Session 同时批准时，只有一个能完成 Slot CAS；失败方不得在无 Slot 所有权时插入批准 Event。

### 11.2 两层终态

为兼容批准后撤销/过期，不能把五种事件错误地压成一个唯一值，冻结为两层：

- Decision Terminal：`APPROVED_FOR_EXECUTION / BLOCKED / REJECTED`，同一 Admission 最多一个；
- Closure Terminal：`REVOKED / EXPIRED`，只允许在已批准后出现，同一 Admission 最多一个。

Event 表候选修订可增加两个 nullable generated guard key，并分别建立 UNIQUE：Decision 类型映射为 `admission_row_id`，其他为 NULL；Closure 类型同理。结合事件序号唯一键、Hash 链和 Slot CAS，双 Session 最终批准结果必须为一成功、一幂等/唯一冲突失败，不得出现两个冲突 Decision。

状态转换冻结：`CREATED -> ELIGIBLE|BLOCKED|REJECTED`；`ELIGIBLE -> APPROVED_FOR_EXECUTION|BLOCKED|REJECTED|EXPIRED`；`APPROVED_FOR_EXECUTION -> REVOKED|EXPIRED`；其余均拒绝。`REVOKED/EXPIRED/BLOCKED/REJECTED` 不可恢复。

## 12. V2.6.15 修订 Decision

Decision：`CANDIDATE_REVISION_ALLOWED_NEXT_SPRINT`。

理由：V2.6.15 未晋级、未进入生产、未在共享环境成功发布，Flyway checksum 只来自隔离失败验收；现有治理已有失败候选归档先例。为了保持连续版本链，不创建 V2.6.16 来掩盖一个从未发布且自身终态完整性不成立的候选。

本 Decision 不授权当前 Sprint 改 SQL。下一 Sprint 必须先归档当前候选，再修订同名 V2.6.15，并重新进行 Fresh/Upgrade 全链验收；不得 repair、baseline、修改任何已成功历史库的 Flyway history。

## 13. 失败候选归档策略

实施前必须：

1. 将当前 SQL 原字节归档到 `database/migration/archive/failed-candidates/`，文件名包含 `failed_7887bfb1`；
2. 保存 SHA `7887bfb1d5bb9aacba378bc62618ba92fdd58fb1a85007053da4899d1fe9d412` 和 observed checksum `-1956070127`；
3. 保留 WF5.18.3 重验报告，不覆盖、不改写 PASS/FAIL 证据；
4. Inventory 将旧候选记为 `FAILED_CANDIDATE_ARCHIVED`，新候选仍为 `CANDIDATE / NOT_EXECUTED`；
5. 新 SHA 与新 observed checksum 只能在后续验收中记录，验收前不得晋级。

## 14. 允许的修订范围

下一 Sprint 仅允许：

- Final Approval Event Guard；
- 精确 28 项 Validator/8 项 Capability 完整性；
- Capability Root 与 Persistence Hash 绑定；
- Slot DELETE/UPDATE/CAS/token 治理及事务顺序；
- Decision/Closure 终态唯一性；
- 为上述 Canonical 对齐所必需的最小 Hash 契约修正和测试。

原则上不得改变：Candidate 增强字段、11 条 FK、Slot 父唯一键、Partial Install Guard、Admission/Evidence/Event 的聚合职责和基本归属结构。不得启用 ROLE Runtime 或创建 Runtime 对象。

## 15. 后续真实验收矩阵

| 类别 | 场景 | 期望 |
|---|---|---|
| Evidence | 0/28、1/28、27/28 | 全拒绝 |
| Evidence | 28/28 精确合同 | 通过 |
| Evidence | 重复 code、缺 order、非法 order、code/order 错配、非法 leaf Hash | 全拒绝 |
| Capability | 8/8 READY | 通过 |
| Capability | 7/8、NOT_READY、DEGRADED、BLOCKED | 全拒绝 |
| Root | 正确实际 Root | 通过 |
| Root | Event 错 Root、旧 Root、Evidence 变化但 Root 未变 | 全拒绝 |
| Persistence | Persistence Hash 覆盖并匹配实际 Root | 通过 |
| Persistence | 保存旧 Root 或不可重算 | 拒绝 |
| Slot | DELETE、非 CAS UPDATE、同 token、跳 version | 全拒绝 |
| Slot | CAS 占用、CAS 释放 | 通过 |
| 并发 | 双 Session 最终批准 | 单赢家，无双终态 |
| Terminal | APPROVED 后 REJECTED、REVOKED 后 APPROVED、EXPIRED 后 APPROVED | 全拒绝 |
| Regression | Fresh、V2.6.14 Upgrade、strict validate、二次 migrate | 全通过且 Schema Fingerprint 一致 |

## 16. NO-GO 条件与关闭情况

| NO-GO | 本设计结论 |
|---|---|
| 28 项集合不明确 | 已以 order/code 精确冻结 |
| PASS/READY 映射不明确 | 已冻结普通 PASS、Capability PASS+READY |
| Required Capability 不明确 | 已冻结 8 项集合 |
| Root 算法不明确 | 已冻结 `ROLE_RUNTIME_CAPABILITY_EVIDENCE_ROOT_CANONICAL_V1` |
| Root 无法数据库验证 | 已选固定八槽 Aggregate Digest，数据库可重算 |
| Persistence 无法绑定 Root | 已冻结 V2 Canonical 与 Final Guard 重算 |
| Slot 释放只能 DELETE | 已冻结 CAS 释放、行永久保留 |
| Terminal 唯一性未定义 | 已分 Decision/Closure 两层唯一性 |
| 并发单赢家未定义 | 已冻结锁顺序、Slot CAS、UNIQUE 三重保护 |
| 候选是否允许修订未明确 | 已决策下一 Sprint 可归档后修订 V2.6.15 |

治理契约均已关闭，设计状态可进入 READY；实现与真实验收仍是独立门禁。

## 17. 风险清单

1. 当前 Java Capability Root 覆盖全部 Evidence，与新冻结的 8 项 Capability Root 不一致；下一 Sprint 必须做最小契约同步，不能只改 Trigger。
2. Persistence Canonical V2 的 UTC 毫秒格式必须用跨 Java/MySQL 黄金向量验证，否则数据库重算会漂移。
3. MySQL Trigger 较长，必须增加静态合同测试，防止 28 个 CASE 漏项或 order/code 写错。
4. `active_token` 引入会要求 Repository CAS 参数同步；这不授权扩展任何 Runtime 行为。
5. 生成唯一键需要在 MySQL 8.4 Fresh/Upgrade 双路径验证表达式、索引和错误码。
6. Trigger 无法证明客户端 SQL 的 WHERE 文本包含 CAS 条件；必须结合旧值/新值 Guard、token/version 和生产 DML 权限治理。
7. ROLE Runtime 继续禁用；即使数据库完整性修复通过，也不能据此启用 Resolver、Task、Pool 或 Claim。

## 18. 下一步建议

下一 Sprint 仅执行 V2.6.15 候选归档与受控修订：实现 Final Guard、Hash Canonical、Slot CAS-only 和终态唯一性，增加合同/负向测试，状态保持 `CANDIDATE / NOT_EXECUTED`。之后再以独立 Sprint 执行 Fresh、V2.6.14 Upgrade、双 Session 和完整 P0 矩阵的真实 MySQL/Flyway 重验。

不得创建 V2.6.16，不得进入 WF5.19，不得启用 ROLE Runtime，不得进入 ROLE Task、Candidate Pool、Claim 或 Investment Integration。

## 19. 完成状态

- 新增设计文档：1
- 业务代码修改：0
- 测试代码修改：0
- Migration 修改：0
- Migration 新增：0
- V2.6.16：不存在
- V2.6.15：`CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`
- ROLE Runtime：`DISABLED`
- 设计状态：`V2.6.15_FINAL_APPROVAL_INTEGRITY_DESIGN_READY`

