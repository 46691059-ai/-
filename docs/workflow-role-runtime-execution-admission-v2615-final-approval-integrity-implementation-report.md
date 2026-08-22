# V2.6.15 Final Approval Integrity Candidate Repair Implementation Report

## 1. 最终结论

Sprint `2-3.7-WF5.18.5` 已完成候选修复：

- `V2.6.15_FINAL_APPROVAL_INTEGRITY_IMPLEMENTED`
- V2.6.15：`CANDIDATE_READY_FOR_REVALIDATION / NOT_EXECUTED`
- ROLE Runtime：`DISABLED`
- `EXPLICIT_USER_V1`：`ACTIVE`
- `ROLE_DIRECTORY_V1`：`PREPARED / NON_EXECUTABLE`

本 Sprint 未执行真实 MySQL/Flyway Migration，未创建 V2.6.16，未启用 ROLE Runtime，也未创建 WorkflowInstance、ROLE Task、Candidate Pool 或 Claim。

## 2. 当前失败资产与归档

被替换候选：

- SHA-256：`7887bfb1d5bb9aacba378bc62618ba92fdd58fb1a85007053da4899d1fe9d412`
- observed Flyway checksum：`-1956070127`
- 状态：`EPHEMERAL_MYSQL8_VALIDATION_FAILED`
- 失败证据：零 Evidence 和含 `NOT_READY` 的完整 Evidence 均可最终批准；Slot 可直接 DELETE。

该候选已按原字节归档为：

`database/migration/archive/failed-candidates/V2.6.15__create_role_runtime_execution_admission_persistence__failed_7887bfb1.sql`

归档 SHA 与源 SHA 完全一致。原 WF5.18.3 重验报告保留，未覆盖。更早的 `405fb7fc...` 失败归档也保持不变。

## 3. 修改文件清单

### Migration 与资产治理

- `database/migration/mysql/V2.6.15__create_role_runtime_execution_admission_persistence.sql`
- `database/migration/archive/failed-candidates/V2.6.15__create_role_runtime_execution_admission_persistence__failed_7887bfb1.sql`
- `database/flyway/migration-inventory.yml`
- `database/migration/mysql/SHA256SUMS`
- `database/migration/mysql/README.md`
- `database/flyway/scripts/validate-v2615.ps1`

### Domain/Application

- `RoleRuntimeExecutionAdmissionValidatorContract.java`
- `RoleRuntimeCapabilityEvidenceRootCanonical.java`
- `RoleRuntimeExecutionAdmissionPersistenceHash.java`
- `RoleRuntimeExecutionAdmissionPersistencePolicy.java`
- `RoleRuntimeExecutionAdmissionEvent.java`
- `RoleRuntimeExecutionAdmissionSlot.java`
- `RoleRuntimeExecutionAdmissionPersistenceService.java`
- `RoleRuntimeExecutionAdmissionSlotRepository.java`

### Infrastructure

- `WorkflowRoleRuntimeExecutionAdmissionEventEntity.java`
- `WorkflowRoleRuntimeExecutionAdmissionSlotEntity.java`
- `RoleRuntimeExecutionAdmissionEntityMapper.java`
- `WorkflowRoleRuntimeExecutionAdmissionSlotMapper.java`
- `RoleRuntimeExecutionAdmissionSlotRepositoryImpl.java`

### Tests/Documents

- `RoleRuntimeCapabilityEvidenceRootCanonicalTest.java`
- `WorkflowRoleRuntimeExecutionAdmissionPersistenceDomainTest.java`
- `WorkflowRoleRuntimeExecutionAdmissionPersistenceServiceTest.java`
- `V2615ExecutionAdmissionPersistenceMigrationContractTest.java`
- 本报告

## 4. P0 根因关闭映射

| 缺口 | 修复 |
|---|---|
| 0/28、1/28、27/28 可批准 | Final Event Guard 要求 count/distinct/min/max 精确 28/28 |
| code/order 未冻结 | Evidence CHECK 固定 1—28 的 order/code 映射 |
| Capability 非 READY 可批准 | 最终批准要求 8 项全部 `result=PASS` 且 `capability_status=READY` |
| Root 未绑定实际集合 | Trigger 从数据库实际 8 项 Evidence 重算 Aggregate Root |
| Persistence 未引用实际 Root | 实际 Root 同时匹配 Admission/Event；Event `source_persistence_hash` 必须匹配 Admission `persistence_hash` |
| Slot 可 DELETE/非 CAS 修改 | 增加 DELETE Trigger、token/version CAS UPDATE Guard |
| 终态冲突 | Decision/Closure generated token 分别建立唯一键 |
| 并发批准证明不足 | ELIGIBLE 后先 CAS Slot，再写 Final Event；CAS、Slot ownership 与终态唯一键共同保证单赢家 |
| Java Root 语义不一致 | 新增与 SQL 完全一致的八项 Capability Canonical 和固定向量 |

## 5. 28 项 Validator Contract

合同版本：`ROLE_RUNTIME_EXECUTION_ADMISSION_VALIDATOR_CONTRACT_V1`。

顺序与代码直接复用 WF5.18.4 冻结清单：1—20 为普通 Validator，21—28 分别为 `DIRECTORY_READY`、`REALTIME_ELIGIBILITY_READY`、`DATA_SCOPE_READY`、`SOD_READY`、`AUDIT_READY`、`FEATURE_FLAG_READY`、`KILL_SWITCH_READY`、`CANARY_SCOPE_READY`。

数据库同时使用：

- `sequence_no` 范围 CHECK；
- 每个 order/code 的固定 CHECK；
- admission 内 sequence 唯一；
- admission 内 validator code 唯一；
- 最终 Event 前 count/distinct/min/max 聚合门禁。

因此重复 code、重复 order、缺号、错号和额外 code 均不能形成合法 28/28 集合。

## 6. Required Capability 与 PASS/READY

Required Capability 固定为：`DIRECTORY`、`REALTIME_ELIGIBILITY`、`DATA_SCOPE`、`SOD`、`AUDIT`、`FEATURE_FLAG`、`KILL_SWITCH`、`CANARY_SCOPE`。

- 普通 Validator：必须 `PASS`，Capability 字段必须为 NULL。
- Capability Validator：必须 `PASS + READY`，且 provider version 非空。
- `FAIL/NOT_READY/DEGRADED/BLOCKED` 均被 Final Event Guard 拒绝。

Domain Persistence Policy 同步执行相同的 28 项合同，数据库仍是最终不可绕过门禁。

## 7. Root Canonical 与 Java/DB 同步

Canonical：`ROLE_RUNTIME_CAPABILITY_EVIDENCE_ROOT_CANONICAL_V1`。

仅包含八项 Required Capability，按 `capability_code` ASCII 升序，逐项使用长度前缀编码：

- sequence
- validatorCode
- capabilityCode
- capabilityStatus
- result
- evidenceHash
- providerVersion
- policyVersion

数据库使用八个固定 `MAX(CASE...)` 槽位，不依赖自然顺序或 `GROUP_CONCAT`。Java 使用同样字段、顺序和编码。

固定测试向量：

`9f73f6861c4969751365f82d540715ec272f336b193d0f95a41de903b9f1e5cb`

逆序输入得到相同 Root；status、providerVersion、policyVersion 或 evidenceHash 任一变化都会改变 Root。

## 8. Persistence Hash 绑定

采用冻结设计允许的 Aggregate Digest 回退方案，而不是声称 MySQL 完整复制 Java Persistence Canonical：

1. Trigger 从实际 Evidence 重算 Root；
2. 实际 Root 必须等于 Admission `capability_evidence_root_hash`；
3. Final Event `source_evidence_root_hash` 必须等于实际 Root；
4. Event `source_persistence_hash` 必须等于 Admission `persistence_hash`；
5. Java Persistence Hash 继续覆盖 `capabilityEvidenceRootHash`，Policy 在持久化前重算验证。

Execution Hash、Capability Root 和 Persistence Hash 继续使用独立字段，未发生语义复用。

## 9. Final Event Guard

`trg_role_admission_event_insert_guard` 现在在 `APPROVED_FOR_EXECUTION` 前验证：Admission 强归属和稳定 Decision、事件链、未过期、精确 28 项合同、普通 PASS、8/8 PASS+READY、实际 Root、Persistence 引用、Slot 当前归属以及终态唯一性。

稳定错误包括：

- `ROLE_ADMISSION_EVIDENCE_INCOMPLETE`
- `ROLE_ADMISSION_VALIDATOR_CONTRACT_MISMATCH`
- `ROLE_ADMISSION_CAPABILITY_NOT_READY`
- `ROLE_ADMISSION_CAPABILITY_ROOT_MISMATCH`
- `ROLE_ADMISSION_PERSISTENCE_HASH_MISMATCH`
- `ROLE_ADMISSION_SLOT_OWNERSHIP_MISMATCH`
- `ROLE_ADMISSION_TERMINAL_EVENT`
- `ROLE_ADMISSION_EXPIRED`

Evidence、Admission 和 Event 原有 UPDATE/DELETE append-only Trigger 保持。

## 10. Slot CAS-only 与释放

Slot 新增 64 位小写 SHA-256 `active_token`。初始 token 使用安全随机值；每次占用/释放由数据库基于旧 token、动作、Admission 和新 version 生成新 token。

Repository CAS WHERE 同时比较 Candidate、旧 version、旧 token 和当前指针。Trigger 进一步要求：

- Candidate、snapshot 和 source ownership 不变；
- version 必须严格 `+1`；
- token 必须变化；
- 占用只允许 `VACANT -> OCCUPIED` 且已有 ELIGIBLE Event；
- 释放只允许 `OCCUPIED -> VACANT` 且已有 REVOKED/EXPIRED Event；
- Slot DELETE 固定返回 `ROLE_RUNTIME_ADMISSION_SLOT_DELETE_FORBIDDEN`。

释放不删除 Slot，历史协调行永久保留。

## 11. 事务与并发单赢家

最终批准事务顺序调整为：Admission → 28 Evidence → CREATED/ELIGIBLE Event → Slot token/version CAS → APPROVED Event。

Final Event 若失败，Spring 事务回滚 Slot CAS 和此前写入。双 Session 同时批准时：

- Slot CAS 仅一方成功；
- Final Event 还要求 Slot 当前归属；
- Event sequence 唯一；
- Decision terminal token 唯一。

本 Sprint 只完成静态、Domain 和事务调用顺序测试；真实双 Session MySQL 证明留待下一重验 Sprint。

## 12. Terminal 模型

- Decision Terminal：`APPROVED_FOR_EXECUTION/BLOCKED/REJECTED`，同一 Admission 最多一条。
- Closure Terminal：`REVOKED/EXPIRED`，同一 Admission 最多一条。

Event 表新增两个 generated token 及 UNIQUE。状态链允许 `CREATED -> ELIGIBLE|BLOCKED|REJECTED`、`ELIGIBLE -> APPROVED|BLOCKED|REJECTED|EXPIRED`、`APPROVED -> REVOKED|EXPIRED`；终态不能恢复。

## 13. 已通过结构保护

以下既有治理未删除或降级：

- 11 条强归属 FK；
- `uk_role_binding_snapshot_slot_owner`；
- Candidate future-only nullable 扩展，历史数据不回填；
- Partial Install Guard 位于第一条永久 DDL 之前；
- Definition/Node/Release FK；
- Directory Revision Fence；
- Hash `ascii/ascii_bin`；
- request/idempotency/persistence hash 唯一键；
- Admission/Evidence/Event append-only。

## 14. 测试结果

- Java：21.0.12
- Maven：3.9.9
- Java compile：PASS
- Spring Boot Context：PASS
- Migration Contract Test：PASS
- DDL Dependency Test：PASS，11/11 FK 合同保持
- Capability Root Canonical fixed vector：PASS
- Admission Domain/Policy：PASS
- Service transaction order/CAS：PASS
- Domain framework dependency scan：0 violation
- 后端全量测试：481 tests，0 failures，0 errors，0 skipped

未执行真实 MySQL/Flyway Migration，符合本 Sprint 禁止项。

## 15. Migration Guard 与资产

失败候选 SHA/checksum：

- SHA：`7887bfb1d5bb9aacba378bc62618ba92fdd58fb1a85007053da4899d1fe9d412`
- observed checksum：`-1956070127`

修订候选 SHA：

- `db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44`
- Flyway checksum：`null`（未执行）
- 状态：`CANDIDATE_READY_FOR_REVALIDATION / NOT_EXECUTED`

Migration SHA 清单保持 37 项；V2.6.14 SHA 保持 `a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442`。

## 16. 剩余风险

1. V2.6.15 修订 SQL 尚未在真实 MySQL 8.4.9/Flyway 13.0.0 上执行，Trigger 语法、generated unique key 和真实错误码仍需验证。
2. Java/SQL fixed vector 已静态对齐，但需要真实数据库 SELECT/Trigger 结果再次交叉确认。
3. Slot 的真正双 Session 单赢家、锁等待和死锁行为尚未重验。
4. Persistence 采用 Event 引用绑定回退方案；数据库未完整重算 Java 的全部 Persistence Canonical，后续若升级为全量重算必须另行冻结 Canonical。
5. ROLE Runtime 仍被禁用，本次修复不构成启用授权。

## 17. 下一步建议

单独执行 V2.6.15 真实 MySQL/Flyway 重验：Fresh、V2.6.14 Upgrade、strict validate、二次 migrate no-op、28 项负向矩阵、Java/DB Root 向量、Slot DELETE/非 CAS、双 Session 最终批准、Decision/Closure 冲突和 Schema Fingerprint。

在该验收 PASS 前不得晋级 V2.6.15，不得进入 WF5.19，也不得启用 ROLE Runtime。
