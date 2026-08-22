# Sprint 2-3.7-WF5.14.1 V2.6.14 真实 MySQL/Flyway 验收报告

## 1. 最终结论

**FAIL / BLOCKED / NOT PROMOTED**。

V2.6.14 在 Fresh、Upgrade、结构、严格校验、二次 no-op、强归属、append-only、
Hash/Collation、并发单赢家、事务回滚和应用回归方面均通过，但永久 DDL 前 Guard
存在 P0 缺口：数据库存在状态为 `PERSISTED`、字段格式合法、但没有任何 Approval
与 Evidence 的 Activation Request 时，Migration 被错误接受，并创建三张目标表。

因此不满足“Guard 失败且零永久 DDL”晋级条件。最终状态：

- `V2.6.14: CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`
- `ROLE_RUNTIME_DISABLED`

## 2. 环境版本

- MySQL Community Server：8.4.9
- Flyway Community Edition：13.0.0
- Java：21.0.12
- Maven：3.9.11
- 环境：新建、一次性、仅绑定 `127.0.0.1` 的隔离 MySQL 实例
- 生产、预生产、未知或既有数据库连接：0

## 3. Fresh 结果

- 基础 SQL 与完整 Flyway 链执行至 V2.6.14：PASS。
- `flyway_schema_history` 失败记录：0。
- V2.6.14 成功记录：1。
- strict validate：PASS。
- 第二次 migrate：`No migration necessary`。

## 4. Upgrade 结果

- 先建立 V2.6.13 完成库：PASS。
- 从 V2.6.13 升级 V2.6.14：仅新增 1 条成功 Migration。
- strict validate：PASS。
- 第二次 migrate：no-op。
- 最终 Schema 与 Fresh：一致。

## 5. Flyway checksum

V2.6.14 Flyway checksum：`1106609314`。

## 6. SHA-256

- V2.6.14 SHA-256：`bcbcbb6b9f7e58b5b03297621682f88c73744e2893058d0519198203e6150b15`
- 指定期望值匹配：PASS。
- Migration SHA 清单：`36/36` 匹配。
- V2.6.14 与历史 Migration 内容漂移：0。

## 7. Schema Fingerprint

Fresh 与 Upgrade 完全一致：

| 范围 | SHA-256 |
|---|---|
| Full Schema | `927a1b9d218d22d17c4ae6218e5306f4181d70d19af00b349d0f6e8b7322ef7a` |
| Workflow Schema | `ff51fa7b6fee13896f924b301b079dc72d6b77843f090c3b999a3238253b2b6d` |
| ROLE Binding Persistence | `350782da49ae006a13d30a25955726ea7a821ae567d6b24f1446b93a33b477fd` |

Fingerprint 覆盖 Table、Column、Index、Unique、FK、CHECK 与 Trigger。

## 8. 三张表结构核查

真实读取 `information_schema`、`SHOW CREATE TABLE` 与 `SHOW TRIGGERS`，结果：

- 表：3
- 字段：89
- 索引：17
- 外键：4
- CHECK：18，均由 MySQL 8.4 强制执行
- Trigger：9

逐表确认：

- `workflow_role_binding_promotion`
- `workflow_role_binding_candidate_snapshot`
- `workflow_role_binding_snapshot_event`

主键、普通/唯一/复合索引、复合外键、审计字段、`deleted`、`delete_token`、
`version`、NULL/默认值、字符集及 Collation 均按 SQL 建立。

## 9. Guard 结果

### 通过场景

通过移除隔离库的 V2.6.13 Resolver CHECK 并写入空 Resolver Code、非法小写
Resolver Version 的脏 Request，V2.6.14 在永久 DDL 前被拒绝：

- SQLSTATE：`HY000`
- MySQL Error：`3819`
- Target Table：0
- Target Index/FK/CHECK/Trigger：0
- 自动修复、删除、推断或回填：0

### P0 阻断场景

在独立隔离库写入字段、Hash、Resolver、状态均合法的 `PERSISTED` Activation Request，
但不写入 Approval 和 Evidence。V2.6.14 结果：

- Migration：错误地成功
- Flyway 退出：0
- 新建目标表：3
- 应当失败但实际接受：`FAIL_ACCEPTED`

根因：V2.6.14 Guard 只校验已有 Approval/Evidence 的归属与 Hash 漂移，没有验证每个
`PERSISTED` Request 必须具备三类 APPROVE Decision 和五类 Evidence。

## 10. Promotion 完整性

- 合法 Activation Evidence 到 Promotion：PASS。
- 缺失 Activation：拒绝，1644/45000。
- 跨 Activation Hash：拒绝，1644/45000。
- 重复 Promotion：拒绝，1062/23000。
- Activation、Resolver、Contract、Binding、Candidate、Directory Revision 与有效窗口
  一致性：PASS。

## 11. Candidate Snapshot 完整性

- 合法 Promotion 到 Snapshot：PASS。
- 不存在 Promotion、跨 Promotion、跨 Activation、跨 Resolver Contract：全部拒绝，
  1644/45000。
- 重复 Snapshot：拒绝，1062/23000。

## 12. Lifecycle Event 完整性

合法事件链 `CREATED -> VALIDATED -> ACTIVE`：PASS，数据库记录数为 3。

- 不存在 Snapshot：拒绝。
- 跨 Promotion/Activation：拒绝。
- 非法状态转换：拒绝。
- 重复序列号：拒绝，1062/23000。
- `ACTIVE` 未创建或启用任何运行资产。

## 13. Append-only 结果

以下真实 MySQL 操作均被 Trigger 以 1644/45000 拒绝：

- Promotion UPDATE/DELETE
- Candidate Snapshot UPDATE/DELETE
- Lifecycle Event UPDATE/DELETE

合法 INSERT 不受影响。

## 14. Hash / Collation 结果

64 位小写 Hex：PASS。以下全部拒绝：

- 大写、混合大小写
- 长度不足、长度超限
- 非 Hex
- 空字符串、NULL

Hash 与 Resolver 标识字段使用显式 `ASCII/ascii_bin`。Resolver Version 的小写、混合
大小写、空值、NULL 和非法字符均被拒绝。未依赖 `utf8mb4_general_ci`。

## 15. 负向测试矩阵

共执行 36 项真实数据库检查：35 项通过，1 项阻断失败。

唯一失败项：`pre_ddl_guard_missing_evidence = FAIL_ACCEPTED`。

其他强归属、唯一性、状态、Hash、Resolver、append-only 和并发检查均通过。

## 16. 并发结果

使用两个独立 `mysql.exe` Session：

- 同一 Promotion：1 成功、1 个 1062。
- 同一 Candidate Snapshot：1 成功、1 个 1062。
- 同一 Lifecycle sequence：1 成功、1 个 1062。
- MySQL 1213：0。
- MySQL 1205：0。

## 17. 事务回滚结果

事务中先写入合法 Promotion，再故意写入错误 Promotion 所有权的 Snapshot。Snapshot
失败后连接终止，最终结果：

- Promotion：0
- Snapshot：0
- Event：0

事务零残留：PASS。

## 18. 合法 Persistence 链路

真实库成功建立：

`Validated Activation Evidence -> Promotion -> Candidate Snapshot -> CREATED -> VALIDATED`

并额外验证 `ACTIVE` 事件。Activation、Promotion、Binding、Candidate、Contract Hash
及 Directory Revision 保持一致。最终合法链记录数：Promotion 1、Snapshot 1、Event 3。

未创建 WorkflowInstance、NodeExecution、Task、CandidatePool 或 Claim。

## 19. Legacy / Explicit User 兼容

- EXPLICIT_USER_V1：保持 ACTIVE，行为未变化。
- ROLE_DIRECTORY_V1：保持 PREPARED / NON_EXECUTABLE。
- USER + DIRECT、SINGLE_NODE_LEGACY：未进入新持久化链。
- 历史 Instance/Task：无回填。
- 历史 CandidatePool：无刷新。
- ROLE Runtime：DISABLED。

## 20. 应用测试结果

- Java 21 compile：PASS。
- Spring Boot Context：PASS。
- Workflow 专项、Migration Contract、Domain 纯净及后端全量测试：449 项通过。
- 失败：0；错误：0；跳过：0。
- Migration SHA：36/36。
- `git diff --check`：PASS。
- ROLE Runtime Active Bean、ROLE Task Runtime、CandidatePool Runtime、Claim Runtime：均未启用。
- Investment 修改：0。

## 21. Migration 资产状态

验收未满足全部晋级条件：

- `asset_status: CANDIDATE`
- `execution_status: EPHEMERAL_MYSQL8_VALIDATION_FAILED`
- observed Flyway checksum：`1106609314`
- SHA-256 保持：`bcbcbb6b9f7e58b5b03297621682f88c73744e2893058d0519198203e6150b15`

禁止标记为 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。

## 22. 临时环境清理

- 本次及前序脚本调试共创建的 5 组 V2.6.14 一次性目录均已删除。
- 最终与调试使用的 16 个隔离 MySQL 监听端口均已关闭。
- `D:\codex-validation-v2614-*` 剩余目录：0。
- 生产或未知数据库变化：0。

## 23. 剩余风险

1. P0：`PERSISTED` Request 缺失 Approval/Evidence 时前置 Guard 不阻断。
2. 一旦该场景进入 V2.6.14，MySQL 非事务 DDL 会创建三张永久表，无法满足零残留要求。
3. 当前 Promotion INSERT Guard 会阻断该 Request 的后续 Promotion，但这不能替代永久
   DDL 前历史完整性检查。
4. V2.6.14 未推广，任何共享或发布环境均不得执行。

## 24. 下一步建议

启动独立治理 Sprint，决定如何处理从未晋级的 V2.6.14 候选：推荐归档当前失败 SHA
作为验收证据，并在明确授权后重新设计 V2.6.14 Guard，使每个 `PERSISTED` Request
在永久 DDL 前必须具备三类 APPROVE Decision、五类 Evidence 及完整 Hash 所有权。
修复后必须从全新隔离 Schema 重跑本报告全部路径。不要进入 WF5.15，也不要启用 ROLE
Runtime。
