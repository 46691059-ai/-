# V2.6.14 Candidate Guard Hardening and Revalidation Preparation Report

## 1. 最终结论

Sprint 2-3.7-WF5.14.2 已完成。未晋级的 V2.6.14 候选 Migration 已在原版本号内完成 pre-DDL Guard 强化，当前状态为 `CANDIDATE_READY_FOR_REVALIDATION / NOT_EXECUTED`。本 Sprint 未连接 MySQL、未执行 Flyway、未产生新 Flyway checksum，也未启用 ROLE Runtime。

最终运行边界保持：`ROLE_RUNTIME_DISABLED`，`ROLE_DIRECTORY_V1` 仍为 `PREPARED / NON_EXECUTABLE`。

## 2. 失败候选证据治理

WF5.14.1 的失败候选证据被保留：

- 原始 SHA-256：`bcbcbb6b9f7e58b5b03297621682f88c73744e2893058d0519198203e6150b15`
- 已观察 Flyway checksum：`1106609314`
- 失败原因：仅有 `PERSISTED` Request、没有 Approval/Evidence 时，旧 Guard 仍放行并创建三个永久表。
- 历史报告：`docs/workflow-role-runtime-binding-persistence-v2614-validation-report.md`
- 归档状态：`ARCHIVED_FAILED_CANDIDATE`
- 归档文件：`database/migration/archive/failed-candidates/V2.6.14__create_role_runtime_binding_persistence_foundation__failed_bcbcbb6b.sql`

归档 SQL 因文本归档过程统一换行，归档文件本身 SHA-256 为 `27d1f6ebd330e2416d11756dd1c3e32a5676cf51eee12b00a6c0c815334cffa2`；资产台账同时保留原始失败源 SHA 和归档文本 SHA，禁止混淆二者。原失败报告未覆盖、Flyway history 未修改、未执行 repair/baseline。

## 3. 修改文件清单

- `database/migration/mysql/V2.6.14__create_role_runtime_binding_persistence_foundation.sql`
- `database/migration/archive/failed-candidates/V2.6.14__create_role_runtime_binding_persistence_foundation__failed_bcbcbb6b.sql`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/infrastructure/V2614RoleRuntimeBindingPersistenceMigrationContractTest.java`
- `database/flyway/migration-inventory.yml`
- `database/migration/mysql/SHA256SUMS`
- `database/migration/mysql/README.md`
- `docs/workflow-role-runtime-binding-persistence-v2614-guard-hardening-report.md`

业务代码修改为 0；Investment 修改为 0；历史 Migration 修改为 0；新增 Migration 为 0。

## 4. PERSISTED 语义冻结

采用冻结语义 B：`PERSISTED` 表示完整 Activation Approval Evidence Chain 已持久化，不能把状态字符串本身当作证据。

一条可放行的 `PERSISTED` Activation 必须同时具备：

1. 唯一且有效的 Activation Request；
2. `BUSINESS_OWNER`、`SECURITY_AUDIT`、`RELEASE_APPROVER` 三类互异 Approval；
3. 三条 Approval 的 decision 均为 `APPROVE`；
4. `ACTIVATION_APPROVAL`、`RESOLVER_CONTRACT`、`BINDING`、`CANDIDATE`、`DIRECTORY` 五类互异 Evidence；
5. Approval、Evidence、Request 的 activation/contract/binding Hash 和所有权一致；
6. Candidate、Directory 证据 Hash 分别与 Request 冻结值一致。

非 `PERSISTED` 的进行中 Request 不被误判为历史孤儿；它们不能作为 V2.6.14 下游持久化对象的来源。

## 5. Guard 分阶段设计

永久 DDL 之前固定执行以下阶段：

- Phase A `PHASE_A_REQUEST`：检查三个 V2.6.13 父表、两个稳定所有权索引、目标表/Trigger 零残留，以及 PERSISTED Request 的 Resolver、Hash、Directory Revision、Business Scope 基础合法性。
- Phase B `PHASE_B_COMPLETENESS`：检查三类 APPROVE Approval 与五类 Evidence 的数量、类型完备性及 Evidence Hash 映射。
- Phase C `PHASE_C_INTEGRITY`：检查 Approval/Request、Evidence/Approval/Request 的所有权、状态与 activation/contract/binding Hash 一致性。
- Phase D `PHASE_D_COMPLETE`：仅前三阶段的临时表 CHECK 全部通过后写入零违规标记。
- Phase E/F：删除临时 Guard 对象后才允许执行既有三表 DDL、约束及 Trigger。

每阶段向带有 `CHECK (violation_count = 0)` 的临时表写入结果。任何违规立即由 MySQL 拒绝，且失败点位于首个永久 `CREATE TABLE` 之前。Guard 不 UPDATE、DELETE、补齐、推断或修复历史证据。

## 6. Guard 规则覆盖

| 编号 | 场景 | 预期 |
| --- | --- | --- |
| 1 | PERSISTED Request 缺少 Approval | Guard 失败，永久 DDL 为 0 |
| 2 | Approval 存在但缺少 Evidence | Guard 失败，永久 DDL 为 0 |
| 3 | Approval 归属其他 Activation | Guard 失败，永久 DDL 为 0 |
| 4 | Evidence 关联错误 Approval | Guard 失败，永久 DDL 为 0 |
| 5 | 任一 Approval 非 APPROVE | Guard 失败，永久 DDL 为 0 |
| 6 | Activation Hash 漂移 | Guard 失败，永久 DDL 为 0 |
| 7 | Contract Hash 漂移 | Guard 失败，永久 DDL 为 0 |
| 8 | Binding Hash 漂移 | Guard 失败，永久 DDL 为 0 |
| 9 | Candidate Evidence Hash 漂移 | Guard 失败，永久 DDL 为 0 |
| 10 | Directory Revision 非法或 Directory Evidence Hash 漂移 | Guard 失败，永久 DDL 为 0 |
| 11 | 完整合法链路 | 允许进入永久 DDL |

V2.6.13 的 Approval/Evidence 没有独立 Directory Revision 字段，因此本 Guard 能校验 Request 冻结 revision 非负及 DIRECTORY evidence hash 一致，不能虚构跨表 revision 比对。该边界保留到真实 MySQL 重验报告中。

## 7. 永久 DDL 不变性

契约测试从 `CREATE TABLE workflow_role_binding_promotion` 开始，对失败归档候选与修订候选进行换行归一化后的完整后缀比较，结果一致。

因此三张表、字段、索引、四个外键、表级 CHECK 与九个 Trigger 均未重构；本次只改变首个永久 DDL 之前的 Guard。静态统计与归档候选保持一致：3 个表、14 个显式 KEY/UNIQUE KEY、4 个 FOREIGN KEY、表体 18 个 CHECK（连同临时 Guard CHECK 共 19 个）、9 个 Trigger。

## 8. Hash 与 Flyway 治理

- 失败源 SHA-256：`bcbcbb6b9f7e58b5b03297621682f88c73744e2893058d0519198203e6150b15`
- 失败时 Flyway checksum：`1106609314`
- Guard 强化后 SHA-256：`a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442`
- 当前 Flyway checksum：`null`
- SHA256SUMS：36/36 匹配

由于本 Sprint 明确不执行真实 MySQL/Flyway，新的 Flyway checksum 必须由下一次隔离重验产生，当前不得沿用旧 checksum。

## 9. 测试结果

环境：Java 21.0.12、Maven 3.9.9、Spring Boot 3.5.9。

- V2.6.14 定向 Migration 契约测试：7 项通过，0 失败；
- Guard 四阶段顺序、无历史修复、11 场景规则、DDL 后缀不变性：通过；
- 后端全量测试：452 项通过，0 失败，0 错误，0 跳过；
- Spring Boot 上下文：通过；
- Java 21 编译/测试编译：通过；
- Migration SHA 清单：36/36；
- `git diff --check`：通过。

本节结果是静态契约与应用回归，不代表真实 MySQL Guard 场景已经重验。

## 10. 资产状态

当前候选：

- Version：V2.6.14
- Asset：`CANDIDATE_READY_FOR_REVALIDATION`
- Execution：`NOT_EXECUTED`
- Result：`GUARD_HARDENED_READY_FOR_REAL_MYSQL_REVALIDATION`
- Flyway checksum：`null`

不得标记为 `CANONICAL_IMMUTABLE` 或 `EPHEMERAL_MYSQL8_VALIDATED`。

## 11. 兼容性与禁止项确认

- V2.6.13 及更早 Migration 未修改；
- EXPLICIT_USER_V1、USER + DIRECT、Legacy、历史 Candidate Pool 行为未修改；
- 没有创建 WorkflowInstance、Task、CandidatePool 或 Claim；
- 没有修改 Resolver Registry；
- 没有接入真实 Role Directory；
- 没有修改 Investment；
- ROLE Runtime 保持禁用。

## 12. 真实重验准备

下一 Sprint 应在 MySQL Community Server 8.4.9 / Flyway 13.0.0 的隔离一次性环境执行：

1. Fresh：完整链到 V2.6.14；
2. Upgrade：V2.6.13 到 V2.6.14；
3. 逐项执行本报告 11 个 Guard 场景；
4. 每个失败场景核对三表、索引、外键、Trigger 均为零；
5. strict validate 与二次 migrate no-op；
6. 对比 Fresh/Upgrade Full、Workflow、ROLE Binding Schema Fingerprint；
7. 记录新 Flyway checksum 后再决定是否晋级。

禁止复用 WF5.14.1 临时 Schema，禁止连接生产或未知数据库。

## 13. 剩余风险与下一步建议

主要剩余风险是：Guard 的 MySQL 8 执行语义、临时表 CHECK 的具体错误表现、11 类脏数据的零永久 DDL、安全失败恢复、双路径 Schema 一致性尚待真实重验。当前 V2.6.13 模型不在 Approval/Evidence 中重复保存 Directory Revision，故只能通过 Request revision 与 DIRECTORY evidence hash 证明冻结证据，不能执行不存在字段的跨表比较。

下一步仅执行 V2.6.14 隔离真实 MySQL/Flyway 重验；在通过前不得进入 WF5.15、不得启用 ROLE Runtime、不得创建 ROLE Task/Candidate Pool/Claim，也不得接入 Investment。
