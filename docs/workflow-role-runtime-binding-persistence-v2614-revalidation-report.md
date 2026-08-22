# V2.6.14 Guard-Hardened Candidate Real MySQL/Flyway Revalidation Report

## 1. 最终结论

`PASS`。V2.6.14 Guard 强化候选满足本 Sprint 全部晋级条件，资产晋级为 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。ROLE Runtime 始终保持 `DISABLED`。

## 2. 环境版本

- MySQL Community Server：8.4.9
- Flyway：13.0.0
- Java：21.0.12
- Maven：3.9.9
- 数据库：四个新建、隔离、一次性、仅监听 `127.0.0.1` 的高位端口实例
- 未连接生产、未知 3306/3307 或历史验收 Schema

## 3. 修订候选 SHA

- V2.6.14 SHA-256：`a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442`
- Migration SHA 清单：36/36 匹配
- 验收前后文件摘要无变化

## 4. 原失败候选归档证据

- 原失败 SHA-256：`bcbcbb6b9f7e58b5b03297621682f88c73744e2893058d0519198203e6150b15`
- 原失败 Flyway checksum：`1106609314`
- 状态：`ARCHIVED_FAILED_CANDIDATE`
- 失败报告：`docs/workflow-role-runtime-binding-persistence-v2614-validation-report.md`
- 归档 SQL：`database/migration/archive/failed-candidates/V2.6.14__create_role_runtime_binding_persistence_foundation__failed_bcbcbb6b.sql`

失败历史未删除、未覆盖；未修改 Flyway history，未执行 repair、baseline 绕过或 out-of-order。

## 5. Fresh 结果

Fresh 空业务库从既有 V2.0.0 基线执行完整正式扫描链至 V2.6.14：

- 全部 Migration 成功；
- `flyway_schema_history` 无失败记录；
- V2.6.14 仅执行一次；
- strict validate：PASS；
- 第二次 migrate：`No migration necessary`；
- info：PASS。

## 6. Upgrade 结果

Upgrade 实例先迁移至 V2.6.13，再仅执行 V2.6.14：

- 新增成功 history 数：1；
- strict validate：PASS；
- 第二次 migrate：no-op；
- 最终 Schema 与 Fresh 完全一致。

## 7. Flyway checksum

- Version：2.6.14
- Description：`create role runtime binding persistence foundation`
- Flyway checksum：`-1734980808`
- success：1

## 8. Schema Fingerprint

Fresh 与 Upgrade 三类指纹完全一致：

- Full Schema：`927a1b9d218d22d17c4ae6218e5306f4181d70d19af00b349d0f6e8b7322ef7a`
- Workflow Schema：`ff51fa7b6fee13896f924b301b079dc72d6b77843f090c3b999a3238253b2b6d`
- ROLE Binding Persistence：`350782da49ae006a13d30a25955726ea7a821ae567d6b24f1446b93a33b477fd`

指纹覆盖 Table、Column、Index/Unique、FK、CHECK 与 Trigger。

## 9. 四阶段 Guard 执行结果

- Phase A Request 扫描：PASS
- Phase B Approval/Evidence 完整性：PASS
- Phase C 归属、Hash、Revision 一致性：PASS
- Phase D 最终放行：PASS

所有负向场景均在首个永久 DDL 前由临时 Guard CHECK 阻断；完整合法链通过四阶段后才创建治理表。

## 10. PERSISTED 语义验证

真实 MySQL 证明 `status = PERSISTED` 不能单独作为下游准入证据。只有同时具备以下事实才能放行：

- 三类互异 Approval：BUSINESS_OWNER、SECURITY_AUDIT、RELEASE_APPROVER；
- 三项 decision 均为 APPROVE；
- 五类 Evidence：ACTIVATION_APPROVAL、RESOLVER_CONTRACT、BINDING、CANDIDATE、DIRECTORY；
- Request/Approval/Evidence 所有权一致；
- Activation、Contract、Binding、Candidate、Directory Hash 一致；
- Directory Revision 合法。

## 11. Guard 失败矩阵

| 场景 | 结果 |
| --- | --- |
| PERSISTED Request 无 Approval | PASS：拒绝 |
| Approval 数量不足 | PASS：拒绝 |
| Approval 完整、Evidence 缺失 | PASS：拒绝 |
| Approval 属于其他 Activation | PASS：拒绝 |
| Evidence 属于其他 Approval/Activation | PASS：拒绝 |
| Approval 非 APPROVE | PASS：拒绝 |
| Activation Hash 漂移 | PASS：拒绝 |
| Resolver Contract Hash 漂移 | PASS：拒绝 |
| Binding Hash 漂移 | PASS：拒绝 |
| Candidate Hash 漂移 | PASS：拒绝 |
| Directory Revision 漂移 | PASS：拒绝 |
| Resolver Code/Version 非 Canonical | PASS：拒绝 |

指定 11 类与冻结设计附加场景合计 12/12 通过。

## 12. 零永久 DDL 证据

每个 Guard 场景均在执行前后采集目标对象签名，覆盖 Table、Column、Index、Unique、FK、CHECK、Trigger。12 个场景全部满足：

- 新增 Table：0
- 新增 Index：0
- 新增 FK：0
- 新增 CHECK：0
- 新增 Trigger：0
- 前后对象签名：完全一致

不是依据日志位置推定，而是通过 `information_schema` 实际元数据比对证明。

## 13. 合法 Activation 证据链

构造 `Activation Request -> 3 APPROVE -> 5 typed Evidence` 完整链后执行 V2.6.14：

- Guard：PASS
- 创建目标治理表：3/3
- 后续合法 Promotion：1
- Candidate Snapshot：1
- Lifecycle Event：3

未生成 WorkflowInstance、Task、CandidatePool 或 Claim。

## 14. 三表结构复核

| 项目 | 数量 |
| --- | ---: |
| 表 | 3 |
| 字段 | 89 |
| 索引（含 PRIMARY/UNIQUE） | 17 |
| 外键 | 4 |
| CHECK | 18 |
| Trigger | 9 |

三表为：`workflow_role_binding_promotion`、`workflow_role_binding_candidate_snapshot`、`workflow_role_binding_snapshot_event`。字段、唯一键、复合所有权外键、ASCII/ascii_bin、Hash CHECK、生命周期 CHECK 与 append-only 结构均符合冻结设计，未发现与旧候选 DDL 的无解释变化。

## 15. 强归属回归

以下数据库真实拒绝均通过：Promotion 缺失/跨 Activation 证据、重复 Promotion；Snapshot 缺失/跨 Promotion、跨 Activation、跨 Contract、重复 Snapshot；Event 缺失/跨 Snapshot、跨 Promotion、跨 Activation、非法状态链、重复 sequence。

触发器拒绝使用 MySQL `1644 / SQLSTATE 45000`；唯一键拒绝使用 `1062 / SQLSTATE 23000`。

## 16. Append-only 回归

6/6 通过：

- Promotion UPDATE/DELETE：`ROLE_BINDING_PROMOTION_APPEND_ONLY`
- Snapshot UPDATE/DELETE：`ROLE_BINDING_SNAPSHOT_IMMUTABLE`
- Event UPDATE/DELETE：`ROLE_BINDING_EVENT_APPEND_ONLY`

均由数据库 Trigger 以 `1644 / 45000` 拒绝。实际 Trigger 名称共九项：三张表各自的 `insert_guard`、`no_update`、`no_delete`。

## 17. Hash/Collation 回归

大写 Hash、混合大小写、非 Hex、长度 63/65、空值及 NULL 均被 CHECK、长度或 NOT NULL 约束拒绝。Resolver Code/Version 的小写、混合大小写、空值、非法字符和 NULL 均被拒绝。Hash 与 Resolver 继续使用大小写敏感的 ASCII/ascii_bin Canonical 语义。

## 18. 并发结果

两个独立 Session 并发执行：

- 同一 Promotion：单赢家；
- 同一 Candidate Snapshot：单赢家；
- 同一 Lifecycle Event sequence：单赢家。

失败方均由唯一约束 1062 拒绝；未出现 1213 死锁或 1205 锁等待超时。

## 19. 事务回滚

两条真实事务路径均通过：

1. Promotion 成功、Snapshot 失败：最终 `0/0/0`；
2. Promotion 与 Snapshot 成功、初始 Event 状态非法：数据库返回 `ROLE_BINDING_EVENT_INITIAL_STATE_INVALID`，最终 `0/0/0`。

不存在部分治理证据残留。

## 20. 应用全量测试

- Java 21 compile：PASS
- Spring Boot Context：PASS
- V2.6.14 契约测试：7/7
- Workflow 专项测试：PASS
- 后端全量：452 通过，0 失败，0 错误，0 跳过
- Domain 纯净检查：PASS
- Migration SHA：36/36
- `git diff --check`：PASS

EXPLICIT_USER_V1 仍为 ACTIVE；ROLE_DIRECTORY_V1 仍为 PREPARED/NON_EXECUTABLE；ROLE_RUNTIME 仍为 DISABLED。

## 21. Migration 资产状态

V2.6.14：

- `CANONICAL_IMMUTABLE`
- `EPHEMERAL_MYSQL8_VALIDATED`
- SHA-256：`a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442`
- Flyway checksum：`-1734980808`

原失败候选继续保留为 `ARCHIVED_FAILED_CANDIDATE`。

## 22. 临时环境清理

四个隔离实例已停止，高位端口 36541-36544 均无监听；验收数据目录和证据临时目录在报告固化后删除。未触碰本机已有 MySQL 服务。

## 23. 剩余风险

本次验证证明 MySQL 8.4.9/Flyway 13.0.0 下的隔离正确性，不等同于生产发布授权。数据库后台 histogram 曾记录一次与基线重放相关的锁等待警告，但 Migration、Guard 和业务验证均未失败，并发测试未出现 1205/1213。ROLE Runtime 尚未启用，真实 Directory、ROLE Task、Candidate Pool Runtime、Claim Runtime 与 Investment 集成仍不在本 Sprint 范围。

## 24. 下一步建议

保持 ROLE Runtime 禁用。下一步如获授权，应先进行发布清单与环境准入治理；不得由本 Sprint 自动进入 WF5.15、ROLE Task Runtime、Candidate Pool Runtime、Claim Runtime 或 Investment Integration。
