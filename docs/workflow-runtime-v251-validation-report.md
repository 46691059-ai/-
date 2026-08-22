# Workflow Runtime V2.5.1 真实 MySQL 验收报告

项目：《县域国企数字化运营治理平台》
Sprint：2-3.7-WF2.3.1
验收日期：2026-08-10
结论：**PASS**

## 1. 验收边界

本次只验收 `V2.5.1__create_workflow_runtime.sql`。没有修改V2.5.1、V2.5.0或V2.4.0—V2.4.9，没有修改Workflow和Investment业务代码，没有连接未知、受管或生产数据库。

V2.5.1验收前后SHA-256保持：

```text
e52ca6bdd00426bc7fee1c9b92cd3dc3fecd5084e3533f955c89c4867ceb4469
```

V2.5.0 SHA-256保持：

```text
5b41f9b787bbf4bdf0927b5f30dc487c6d88085d81aedf19e784d44474c02926
```

## 2. 环境信息

| 项目 | Fresh | Upgrade |
|---|---|---|
| MySQL | Community Server 8.4.9 | Community Server 8.4.9 |
| Flyway | Community 13.0.0 | Community 13.0.0 |
| 地址 | `127.0.0.1:33760` | `127.0.0.1:33761` |
| 数据目录 | 新建独立临时目录 | 新建独立临时目录 |
| 起点 | 平台基础Schema + V2.0.0 baseline | 平台基础Schema + V2.0.0 baseline |
| 网络 | 仅绑定回环地址 | 仅绑定回环地址 |
| 生产连接 | 无 | 无 |

两个MySQL实例均为本次验收新建，未复用V2.5.0历史验收数据目录。Migration使用正式目录的字节一致只读副本。

## 3. Fresh完整迁移

完整链路：

```text
V2.1.0 -> V2.1.1 -> V2.1.2 -> V2.1.3
-> V2.2.0
-> V2.4.0 -> V2.4.9
-> V2.5.0 -> V2.5.1
```

| 检查项 | 结果 |
|---|---|
| 初始Pending | V2.1.0—V2.5.1 |
| migrate | 成功执行17个版本化Migration |
| 最终版本 | V2.5.1 |
| V2.5.1执行时间 | 205 ms |
| history成功/失败 | 18 / 0，包含1条baseline |
| strict validate | 18条记录验证通过 |
| 第二次migrate | `No migration necessary` |
| 最终表数量 | 149 |

## 4. V2.5.0升级迁移

Upgrade路径先使用Flyway `target=2.5.0`停在冻结基线，确认V2.5.1为唯一Pending版本，再执行单版本升级。

| 检查项 | 结果 |
|---|---|
| 升级前版本 | V2.5.0 |
| Pending版本 | 仅V2.5.1 |
| 单版本migrate | 成功 |
| V2.5.1执行时间 | 287 ms |
| 最终版本 | V2.5.1 |
| history成功/失败 | 18 / 0 |
| strict validate | 18条记录验证通过 |
| 第二次migrate | `No migration necessary` |

两条路径均未执行`clean`或`repair`。

## 5. Checksum与History

| 字段 | Fresh | Upgrade |
|---|---:|---:|
| version | 2.5.1 | 2.5.1 |
| description | create workflow runtime | create workflow runtime |
| Flyway checksum | -2076704484 | -2076704484 |
| success | 1 | 1 |
| 文件SHA-256 | e52ca6...b4469 | 相同 |

## 6. 结构核查

| 表 | 字段 | 独立索引（含PK及FK自动索引） | 外键 | CHECK/ENFORCED |
|---|---:|---:|---:|---:|
| `workflow_instance` | 34 | 10 | 2 | 10/10 |
| `workflow_task` | 26 | 8 | 2 | 7/7 |
| 合计 | 60 | 18 | 4 | 17/17 |

两张表的8个治理字段全部存在：`created_by`、`created_time`、`updated_by`、`updated_time`、`deleted`、`delete_token`、`remark`、`version`。

### 6.1 唯一约束与索引

- Instance：实例号、企业幂等键、企业业务尝试、版本归属唯一约束；状态、业务键、发起人和定义版本索引。
- Task：任务号、实例节点参与者唯一约束；办理人、实例和节点状态索引。
- 所有可逻辑删除业务唯一约束均包含`delete_token`。

### 6.2 外键

| 外键 | 关系 | 规则 |
|---|---|---|
| `fk_workflow_instance_version` | 实例版本必须属于指定定义 | RESTRICT/RESTRICT |
| `fk_workflow_instance_current_node` | 当前节点必须属于实例冻结版本 | RESTRICT/RESTRICT |
| `fk_workflow_task_instance` | 任务必须属于指定版本实例 | RESTRICT/RESTRICT |
| `fk_workflow_task_node` | 任务节点必须属于实例冻结版本 | RESTRICT/RESTRICT |

### 6.3 CHECK

17项CHECK全部为`ENFORCED=YES`，覆盖实例和任务状态、版本号、尝试次数、事件序号、启动/完成/撤回证据、任务签收证据、逻辑删除、`delete_token`及乐观锁。

## 7. Schema一致性

使用 `database/mysql/verification/schema_fingerprint.sql` 生成只读规范化结构流。

| 指纹 | Fresh | Upgrade |
|---|---|---|
| 完整Schema行数 | 4,971 | 4,971 |
| 完整Schema SHA-256 | `b2b400768b6ae40b26f264cff2261b2650e73cf7527c6ed0fe82f9cdabc2eed5` | 相同 |
| Workflow SHA-256 | `8e890ec4a08e06dd56d5f0750fc27b40413d50ef16b309450f568e4dbcb07516` | 相同 |
| Runtime SHA-256 | `c6e53473a17a608c9d69122f40d57ae71aee138a7660a8b77a257dd282775b32` | 相同 |

结论：Fresh与V2.5.0 Upgrade最终Schema完全一致。

## 8. 负向测试

以下测试分别在Fresh和Upgrade实例执行，均得到预期MySQL错误，随后测试数据清理为0条：

| 测试 | Fresh | Upgrade | MySQL错误 |
|---|---|---|---|
| Instance实例号重复 | PASS | PASS | 1062 |
| Instance版本外键非法 | PASS | PASS | 1452 |
| Instance状态非法 | PASS | PASS | 3819 |
| Task任务号重复 | PASS | PASS | 1062 |
| Task节点外键非法 | PASS | PASS | 1452 |
| Task状态非法 | PASS | PASS | 3819 |

## 9. 资产治理结果

`migration-inventory.yml`中的V2.5.1已更新为：

```text
CANONICAL_IMMUTABLE
EPHEMERAL_MYSQL8_VALIDATED
Flyway checksum: -2076704484
```

`SHA256SUMS`中已登记的V2.5.1哈希经过重新计算并保持一致，因此未改写冻结哈希。README同步记录真实验收结论。

该状态只表示一次性隔离MySQL 8验收通过，不表示开发、测试、预生产或生产环境已执行V2.5.1。

## 10. 剩余风险

1. 尚未完成达梦、人大金仓方言对CHECK、复合外键、LONGTEXT和毫秒时间精度的专项验收。
2. 本次为空业务数据结构验收，未覆盖大量实例和任务下的索引选择性与性能。
3. V2.5.1只提供运行实例和任务结构，不包含审批动作日志、节点推进或任务办理能力。
4. 生产执行仍需备份、审批窗口、迁移后strict validate及Schema指纹比对。

## 11. 最终结论

V2.5.1在MySQL Community Server 8.4.9上通过Fresh全链迁移、V2.5.0单版本升级、Flyway history/checksum、strict validate、二次migrate no-op、字段/索引/外键/CHECK核查、双路径Schema指纹一致性及实例与任务负向约束测试。

验收结果：**PASS**。
