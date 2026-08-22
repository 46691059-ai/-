# V2.6.2 Workflow Task Assignment Migration真实MySQL验收报告

## 1. 验收结论

Sprint 2-3.7-WF3.4.1最终结论为 **PASS**。V2.6.2已通过隔离、一次性MySQL 8环境中的Fresh与Upgrade双路径验收，资产状态晋级为：

- `CANONICAL_IMMUTABLE`
- `EPHEMERAL_MYSQL8_VALIDATED`

本次未连接生产、托管、未知或历史验收数据库；未修改V2.5.0—V2.6.2 SQL、Investment代码或Assignment业务代码，也未进入ROLE/POSITION/ORG实现。

## 2. 环境信息

| 项目 | 值 |
| --- | --- |
| 数据库 | MySQL Community Server 8.4.9 |
| Flyway | 13.0.0 Community Edition |
| Fresh实例 | 新建临时数据目录，`127.0.0.1:34362` |
| Upgrade实例 | 新建临时数据目录，`127.0.0.1:34363` |
| 网络边界 | 仅loopback，禁用MySQL X Protocol及binlog |
| 基线版本 | Flyway baseline `2.0.0` |
| 目标版本 | `2.6.2` |

## 3. Fresh结果

- 从120张初始化基线表开始，执行正式Migration链至V2.6.2。
- Flyway history：25条成功记录（含baseline），失败记录0条。
- V2.6.2执行耗时：120 ms。
- strict `validate`：成功校验25条Migration。
- 第二次 `migrate`：`No migration necessary`。

结论：**PASS**。

## 4. Upgrade结果

- 独立实例先迁移并严格停在V2.6.1。
- 投放与仓库SHA-256一致的V2.6.2后，仅执行V2.6.2一次。
- V2.6.2执行耗时：239 ms；history中该版本数量为1。
- strict `validate`通过；第二次 `migrate`为no-op。

结论：**PASS**。

## 5. Checksum与资产完整性

- V2.6.2 SHA-256：`9750454efa98402c7d22503d80e16579e07c964a01f7b4baa5da933db01a998f`
- V2.6.2 Flyway checksum：`-1498011282`
- `SHA256SUMS`：24/24文件匹配。
- V2.5.0—V2.6.1历史Migration无摘要漂移。

## 6. Schema Fingerprint

使用 `database/mysql/verification/schema_fingerprint.sql` 生成规范化UTF-8数据流并包含最终换行计算SHA-256：

| 范围 | 规范行数 | Fresh | Upgrade | 结果 |
| --- | ---: | --- | --- | --- |
| 完整Schema | 5331 | `79c9d0712e2a7bfcf744b30ee95f4a0c6af5ca27794ad468ba0e4a397d3ae626` | 同左 | PASS |
| Workflow Schema | 582 | `9792c611a62d99159cc3affdb5bf8cf11d2a27f340fe838724e3bc17367fc717` | 同左 | PASS |

最终业务表数量为155，不计 `flyway_schema_history` 与 `database_release_audit`。

## 7. 结构验证

`workflow_task_assignment_snapshot`验证结果：

| 项目 | 数量/结果 |
| --- | ---: |
| 字段 | 22 |
| 索引 | 5 |
| 外键 | 1个复合外键、5列 |
| CHECK | 8 |

已确认存在：

- 任务、实例、版本、节点、NodeExecution归属字段；
- `strategy_type`、`target_type`、`target_snapshot`；
- `resolved_users`、`resolved_user_count`、`resolve_time`；
- `audit_info`、`trace_id`；
- `created_by/created_time/updated_by/updated_time`；
- `deleted/delete_token/version`；
- Task一对一唯一键、归属唯一键、执行及策略查询索引；
- `(task_id, instance_id, version_id, node_id, node_execution_id)` 到 `workflow_task` 的复合外键；
- `workflow_task`对应五列归属唯一键。

Fresh与Upgrade结构完全一致。

## 8. 负向测试

| 场景 | 实际结果 |
| --- | --- |
| 重复Snapshot | MySQL 1062，Task唯一键拒绝 |
| 非法Task | MySQL 1452，复合外键拒绝 |
| 非法NodeExecution | MySQL 1452，复合外键拒绝 |
| 非法strategy | MySQL 3819，策略CHECK拒绝 |
| 非法target | MySQL 3819，目标CHECK拒绝 |
| 非法delete_token | MySQL 3819，逻辑删除CHECK拒绝 |

六条失败语句均未产生残留行；唯一保留的是验收前主动创建的一条合法Snapshot夹具。

## 9. 资产状态

- 版本：V2.6.2
- 状态：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- SHA-256：`9750454efa98402c7d22503d80e16579e07c964a01f7b4baa5da933db01a998f`
- Flyway checksum：`-1498011282`
- 已从 `candidate_assets` 移入 `canonical_assets`。

## 10. 剩余风险与建议

1. 本报告仅证明一次性本地MySQL 8.4.9验收通过，不代表测试、预生产或生产已部署。
2. 应用发布必须保证目标环境先完成V2.6.2 Migration及发布后validate。
3. `resolved_users`当前仅验收显式USER快照；候选池、Claim及目录修订仍未实现。
4. ROLE/POSITION/ORG仍是扩展边界，不得因本次Migration晋级而启用。
5. 下一步应先在受控测试环境执行发布演练；任何人员解析能力必须进入独立Sprint。
