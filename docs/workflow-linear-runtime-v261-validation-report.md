# V2.6.1 Workflow Linear Runtime 真实 MySQL 验收报告

## 1. 验收结论

Sprint 2-3.7-WF3.2.1 最终结论为 **PASS**。V2.6.1 已通过隔离、一次性 MySQL 8 环境中的 Fresh 与 Upgrade 双路径验收，资产状态晋级为：

- `CANONICAL_IMMUTABLE`
- `EPHEMERAL_MYSQL8_VALIDATED`

本次未连接生产、托管、未知或历史验收数据库，未修改 V2.6.1 及任何历史 Migration，未修改 Workflow/Investment 业务代码，也未进入 WF3.3。

## 2. 环境信息

| 项目 | 值 |
| --- | --- |
| 数据库 | MySQL Community Server 8.4.9 |
| Flyway | 13.0.0 |
| Fresh 实例 | 新建临时数据目录，`127.0.0.1:34272` |
| Upgrade 实例 | 新建临时数据目录，`127.0.0.1:34273` |
| 网络边界 | 仅绑定 loopback，禁用 MySQL X Protocol 与 binlog |
| 基线版本 | Flyway baseline `2.0.0` |
| 目标版本 | `2.6.1` |

## 3. Fresh 验收

- 在新建空 Schema 上装载项目既有初始化基线后，执行完整正式扫描目录至 V2.6.1。
- Flyway history 共 24 条成功记录（含 baseline），失败记录 0 条。
- V2.6.1 执行耗时：1340 ms。
- strict `validate`：通过，共校验 24 条 Migration。
- 第二次 `migrate`：`No migration necessary`。

结论：**PASS**。

## 4. Upgrade 验收

- 在独立新建实例上先迁移至 V2.6.0，再投放与仓库字节一致的 V2.6.1。
- 升级阶段仅执行 V2.6.1 一次；执行耗时 1218 ms。
- V2.6.1 history 数量为 1，失败记录 0 条。
- strict `validate`：通过。
- 第二次 `migrate`：no-op。

结论：**PASS**。

## 5. Checksum 与历史资产完整性

| 版本 | SHA-256 |
| --- | --- |
| V2.5.0 | `5b41f9b787bbf4bdf0927b5f30dc487c6d88085d81aedf19e784d44474c02926` |
| V2.5.1 | `e52ca6bdd00426bc7fee1c9b92cd3dc3fecd5084e3533f955c89c4867ceb4469` |
| V2.5.2 | `00ad19ad16dec01228f5ec45eb48ad0f8a9f887b4dfe27c797c4b5e56f508302` |
| V2.5.3 | `6d6904da8028830cdc8fa7b9125994d37d2c916a09277ae9d4526f5646144e26` |
| V2.5.4 | `dea9284682001d3e3ab8a667a4ec863d5e66a33a61771628f64044ce004768a7` |
| V2.5.5 | `50efcb230b2cba2e7a7600fdfc3e229f05a9e85877194cf9f1eed0875d52605e` |
| V2.6.0 | `76f2113573eac6af106fadb1681774e49a6f15713860a6af7b37f8d9ebc5c1fa` |
| V2.6.1 | `50314ff572253eaef6e0002acc2b1f9109fe7332a7360f8c8b14c4e8fde1a03e` |

V2.6.1 Flyway checksum：`762611150`。仓库清单 23/23 文件校验通过，V2.5.0—V2.6.1 无摘要漂移。

## 6. Schema Fingerprint

规范化算法使用 `database/mysql/verification/schema_fingerprint.sql`，UTF-8 输出含最终换行后计算 SHA-256。

| 范围 | 规范行数 | Fresh | Upgrade | 结果 |
| --- | ---: | --- | --- | --- |
| 完整 Schema | 5276 | `01e33e4f017fe5477693937bdcf8467f5e88293783a0376c76d2380d64177b35` | 同左 | PASS |
| Workflow Schema | 527 | `b6d59845bbc823751dc9b9dee7d161aba63b70543e3b19555ba5052601774b96` | 同左 | PASS |

最终业务表数量为 154（不计 `flyway_schema_history` 与 `database_release_audit`）。

## 7. 结构验证

| 表 | 字段 | 索引 | 外键 | CHECK |
| --- | ---: | ---: | ---: | ---: |
| workflow_version | 22 | 6 | 2 | 10 |
| workflow_instance | 38 | 12 | 3 | 14 |
| workflow_task | 27 | 11 | 3 | 7 |
| workflow_node_execution | 26 | 10 | 4 | 8 |
| workflow_transition | 19 | 6 | 3 | 9 |

已确认：

- `workflow_version` 明确记录 `engine_mode` 与 `content_hash_algorithm`。
- `workflow_instance` 冻结引擎/哈希算法，并以 `current_node_execution_id` 维护权威执行游标。
- `workflow_task.node_execution_id` 可为空以兼容 Legacy，新线性运行任务通过四列复合外键 `(instance_id, version_id, node_id, node_execution_id)` 绑定同一执行记录。
- 新增索引、唯一约束、外键、CHECK、逻辑删除及乐观锁约束均存在。

## 8. 负向测试

| 场景 | 预期/实际结果 |
| --- | --- |
| 重复 NodeExecution | MySQL 1062，唯一键拒绝 |
| 非法 Transition 引用 | MySQL 1452，外键拒绝 |
| 非法 NodeExecution 状态 | MySQL 3819，CHECK 拒绝 |
| 跨 Version 执行 | MySQL 1452，复合外键拒绝 |
| 重复 Task 完成幂等键 | MySQL 1062，幂等唯一键拒绝 |
| 非法乐观锁版本 | 影响行数 0，无数据变化 |
| 非法 delete_token | MySQL 3819，CHECK 拒绝 |

失败语句未产生残留业务行；重复完成后仍只有 2 条动作、2 条执行和2条任务记录。

## 9. 线性执行链路

在真实 InnoDB 事务中执行 `START → NODE_A → NODE_B → END`：

- NODE_A、NODE_B 的 NodeExecution 均为 `COMPLETED / APPROVED`。
- 两个 Task 均为 `APPROVED`，且分别绑定正确 NodeExecution。
- NODE_B 记录正确保存 `previous_execution_id` 与 `source_transition_id`。
- 结束后 Instance 为 `APPROVED / APPROVED`，当前节点和当前执行游标均为空。
- `event_sequence=3`、`version=3`，状态一致。

结论：**PASS**。

## 10. 并发完成测试

两个独立 MySQL 会话同时完成同一 Task，并通过实例行锁、任务状态和乐观锁版本竞争推进：

- 会话结果：`won=1` 与 `won=0`。
- 仅一条 TaskAction 落库。
- 仅生成一条 NODE_B NodeExecution 和一条后继 Task。
- 当前 Task/NodeExecution 各只更新一次，Instance 仅指向胜出会话生成的执行记录。

结论：单赢家推进 **PASS**；失败会话无部分写入。

## 11. 资产状态

- V2.6.1：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- SHA-256：`50314ff572253eaef6e0002acc2b1f9109fe7332a7360f8c8b14c4e8fde1a03e`
- Flyway checksum：`762611150`
- `candidate_assets` 中移除并加入 `canonical_assets`。

## 12. 剩余风险与建议

1. 本次证据仅代表一次性本地 MySQL 8.4.9 验收，不代表测试、预生产或生产已部署。
2. 数据库约束能阻断重复和跨版本写入，但完整事务语义仍依赖应用服务持续使用实例锁、任务 CAS 与统一执行入口。
3. V2.6.1 只覆盖线性推进底座；条件路由、会签、自动选人和 Investment 改造不在本 Sprint 范围。
4. 下一步可进入 WF3.3 任务分配策略，但必须在独立 Sprint 明确授权后进行。
