# V2.6.0 Workflow Multi Node Foundation 真实 MySQL/Flyway 验收报告

## 1. 最终结论

- Sprint：2-3.7-WF3.1.1
- 结论：`PASS`
- Migration：`V2.6.0__create_workflow_multi_node_foundation.sql`
- 资产状态：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- 本次只执行 Migration 验收、结构验证和资产治理；未修改业务代码、V2.5.0—V2.6.0 SQL、Investment，也未进入自动推进或 WF3.2。

## 2. 环境

| 项目 | 内容 |
|---|---|
| 数据库 | MySQL Community Server 8.4.9 |
| Migration工具 | Flyway Community Edition 13.0.0 |
| Fresh实例 | 本次新初始化、仅监听 `127.0.0.1`，端口 34262 |
| Upgrade实例 | 本次新初始化、仅监听 `127.0.0.1`，端口 34263 |
| Flyway baseline | 2.0.0 |
| SQL来源 | 当前仓库 Migration 的逐字节一致副本 |

未连接生产数据库、未知数据库、托管数据库或历史临时 Schema。两个实例使用独立的新数据目录，关闭二进制日志和 MySQL X Plugin。

## 3. Fresh结果

- 基础平台 Schema 初始化：120 张基线表。
- 完整治理链执行成功，最终版本 V2.6.0。
- V2.6.0 执行一次，执行时间 221 ms。
- Flyway history：23 条成功，0 条失败。
- `validateMigrationNaming=true` 且 strict validate：通过。
- 第二次 migrate：`Schema enterprise_platform is up to date. No migration necessary.`
- 最终表数：155。

## 4. Upgrade结果

- 首先迁移至 V2.5.5，再加入与仓库逐字节一致的 V2.6.0。
- Upgrade 仅执行一次 V2.6.0，执行时间 435 ms。
- Flyway history：23 条成功，0 条失败。
- strict validate：通过。
- 第二次 migrate：no-op。
- 最终表数：155。

## 5. Checksum

| 类型 | 值 |
|---|---|
| V2.6.0 SHA-256 | `76f2113573eac6af106fadb1681774e49a6f15713860a6af7b37f8d9ebc5c1fa` |
| V2.6.0 Flyway checksum | `2093108182` |
| SHA256SUMS | 22/22 通过，0 漂移 |

## 6. Schema fingerprint

指纹覆盖列、索引、表约束、键关系和 CHECK 表达式，排除业务数据及自动增长计数器。

| 范围 | Fresh | Upgrade | 结果 |
|---|---|---|---|
| 完整Schema（6508条结构记录） | `b57b1a33fe4b611cb4f57e84e019173b0fb4b02ddd19f09325089b87a1c09448` | `b57b1a33fe4b611cb4f57e84e019173b0fb4b02ddd19f09325089b87a1c09448` | 一致 |
| Workflow Schema（667条结构记录） | `cefc503d29e4c2792ae1d573d9dcc472ae10c1fcfd77c8b13de80c9885c5c261` | `cefc503d29e4c2792ae1d573d9dcc472ae10c1fcfd77c8b13de80c9885c5c261` | 一致 |

## 7. workflow_transition结构

- 字段：19 个；
- 索引：6 个，包括主键、3 个唯一索引和2个业务查询索引；
- 外键：3 个，分别约束版本、同版本来源节点和同版本目标节点；
- CHECK：9 个，覆盖自环、触发类型、路由类型、条件配置、优先级、启用状态、逻辑删除和乐观锁；
- 唯一性：版本内 transition code 唯一，版本/来源/触发/优先级唯一路由；
- 审计字段：`created_by/created_time/updated_by/updated_time` 完整；
- 逻辑删除：`deleted/delete_token` 完整；
- 乐观锁：`version` 完整。

## 8. workflow_node_execution结构

- 字段：26 个；
- 索引：9 个，包括主键、3 个唯一索引、3 个显式查询索引和2个外键支持索引；
- 外键：4 个，约束同版本实例、同版本节点、同实例前序执行和同版本来源 Transition；
- CHECK：8 个，覆盖访问轮次、状态、时间/结果一致性、失败编码、逻辑删除和乐观锁；
- 唯一性：执行编号唯一；同实例/节点/访问轮次唯一；
- 审计、逻辑删除及乐观锁字段全部存在且类型符合设计。

## 9. 负向测试

| 测试 | 预期 | MySQL结果 | 结论 |
|---|---|---|---|
| 重复 Transition 唯一键 | 拒绝 | 1062 | PASS |
| 非法 source node | 拒绝 | 1452 | PASS |
| 非法 target node | 拒绝 | 1452 | PASS |
| 非法 NodeExecution 状态 | 拒绝 | 3819 | PASS |
| 跨 Workflow Version 引用 | 拒绝 | 1452 | PASS |
| 非法 delete_token | 拒绝 | 3819 | PASS |

失败插入均未产生残留记录；验收完成后合法 fixture 也按外键逆序清理，残留测试行数为 0。

## 10. 历史Migration哈希

| 版本 | SHA-256 |
|---|---|
| V2.5.0 | `5b41f9b787bbf4bdf0927b5f30dc487c6d88085d81aedf19e784d44474c02926` |
| V2.5.1 | `e52ca6bdd00426bc7fee1c9b92cd3dc3fecd5084e3533f955c89c4867ceb4469` |
| V2.5.2 | `00ad19ad16dec01228f5ec45eb48ad0f8a9f887b4dfe27c797c4b5e56f508302` |
| V2.5.3 | `6d6904da8028830cdc8fa7b9125994d37d2c916a09277ae9d4526f5646144e26` |
| V2.5.4 | `dea9284682001d3e3ab8a667a4ec863d5e66a33a61771628f64044ce004768a7` |
| V2.5.5 | `50efcb230b2cba2e7a7600fdfc3e229f05a9e85877194cf9f1eed0875d52605e` |
| V2.6.0 | `76f2113573eac6af106fadb1681774e49a6f15713860a6af7b37f8d9ebc5c1fa` |

所有清单资产共 22 个，全部匹配；V2.5.0—V2.6.0 无摘要漂移。

## 11. 资产状态与剩余风险

`migration-inventory.yml` 和 Migration README 已记录本次双路径验收、Flyway checksum、结构计数、指纹及负向结果。`SHA256SUMS` 保持原冻结摘要。V2.6.0 晋级为 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。

剩余风险：本次为隔离一次性 MySQL 验收，不代表生产发布授权；尚需生产变更窗口、备份、容量评估和回滚演练。V2.6.0 只提供数据底座，尚未关联 `workflow_task`、未纳入版本 Graph Hash，也未实现自动推进、会签、条件路由或自动选人。

下一步仅建议进行生产前 Migration 准入或后续独立设计评审；本 Sprint 不进入 WF3.2。
