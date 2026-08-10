# V2.4.7 Workflow 可靠性 Migration 真实 MySQL 验收报告

项目：县域国企数字化运营治理平台
Sprint：2-3.3.1
验收日期：2026-08-08
结论：**PASS**

## 1. 环境信息

| 项目 | 验收环境 |
| --- | --- |
| 环境性质 | 本机临时隔离环境，非开发共享库、测试共享库、预生产或生产 |
| MySQL | MySQL Community Server 8.4.9，Win64 x86_64 |
| Flyway | Redgate Flyway Community Edition 13.0.0 |
| 绑定地址 | `127.0.0.1` |
| 完整迁移端口 | `33416` |
| V2.4.6 升级端口 | `33417` |
| 基础数据库 | `01_database.sql` 至 `16_sprint_1_log_center.sql`，以及 `V1.1.0__investment_data_risk_bi.sql` |
| Flyway baseline | V2.0.0 |
| Migration 扫描副本 | `database/migration/mysql` 的字节一致临时副本 |
| Workflow 数据库 | 未创建、未连接 |
| 生产连接 | 无 |

两个实例使用独立临时数据目录、独立端口，且只监听环回地址。基础平台初始化后，两条路径均包含 120 张业务表。

## 2. 验收路径

### 2.1 空库完整迁移

```text
初始化基础平台 120 张表
  -> Flyway baseline V2.0.0
  -> pre 策略检查及 info
  -> migrate V2.1.0 至 V2.4.7
  -> post 严格 validate
  -> 第二次 migrate no-op
  -> information_schema 与 Schema 指纹核查
```

结果：成功执行 13 个版本化 Migration，最终版本 V2.4.7。

### 2.2 V2.4.6 升级迁移

```text
初始化基础平台 120 张表
  -> Flyway baseline V2.0.0
  -> target=V2.4.6 migrate
  -> pre 策略检查及 info（V2.4.7 为唯一 Pending）
  -> migrate V2.4.7
  -> post 严格 validate
  -> 第二次 migrate no-op
  -> information_schema 与 Schema 指纹核查
```

V2.4.7 使用 `CREATE TABLE` 成功执行，因此升级前目标表不存在；本路径只执行一个新增版本，没有重放或修改 V2.4.0 至 V2.4.6。

## 3. Migration 结果

| 检查项 | 空库完整迁移 | V2.4.6 升级 |
| --- | --- | --- |
| pre SHA-256 策略检查 | 13/13 通过 | 13/13 通过 |
| pre Flyway info | 13 个 Pending | 仅 V2.4.7 Pending |
| migrate | 13 个版本成功 | 1 个版本成功 |
| V2.4.7 执行时间 | 225 ms | 210 ms |
| post 严格 validate | 14 条记录验证成功 | 14 条记录验证成功 |
| 第二次 migrate | `No migration necessary` | `No migration necessary` |
| history 成功记录 | 14 | 14 |
| history 失败记录 | 0 | 0 |
| 最终版本 | V2.4.7 | V2.4.7 |
| 最终表数 | 145 | 145 |

14 条 history 由 1 条 V2.0.0 baseline 和 13 条版本化 Migration 组成。未执行 `clean` 或 `repair`。

## 4. Checksum 与 Flyway History

| 校验类型 | 两条路径结果 |
| --- | --- |
| Flyway checksum | `1082051033` |
| SHA-256 | `fb1df126c9d03a67460b741f7be7aa14943f64aa66affdaed8febbb0142e0d55` |
| version | `2.4.7` |
| description | `add investment decision workflow reliability` |
| type | `SQL` |
| success | `1` |

两条路径的 Flyway checksum 完全一致。V2.4.0 至 V2.4.6 的历史 checksum 与已冻结 Inventory 一致，未发生漂移。

## 5. Schema 变化

### 5.1 `investment_workflow_outbox`

- 字段：21 个；
- 索引：5 个，包括主键、幂等唯一索引、调度索引、决策索引和外键支撑索引；
- 外键：2 个，分别关联 `investment_decision` 与 `investment_workflow_binding`；
- CHECK：4 个，覆盖事件类型、投递状态、重试次数和逻辑删除标志；
- 4 个 CHECK 在 MySQL 中均为 `ENFORCED=YES`。

关键字段包含：`idempotency_key`、`payload_hash`、`status`、`retry_count`、`next_retry_time`、`published_time` 和 `trace_id`。

### 5.2 `investment_workflow_inbox`

- 字段：23 个；
- 索引：7 个，包括事件 ID 唯一索引、实例序号唯一索引、缓冲查询索引和外键支撑索引；
- 外键：3 个，分别关联决策、Workflow 本地绑定和决策快照；
- CHECK：4 个，覆盖尝试号、事件序号、处理状态和逻辑删除标志；
- 4 个 CHECK 在 MySQL 中均为 `ENFORCED=YES`。

关键字段包含：`event_id`、`workflow_instance_id`、`snapshot_id`、`attempt_no`、`event_sequence`、`payload_hash` 和 `process_status`。

### 5.3 双路径一致性

| 路径 | 指纹行数 | Schema SHA-256 |
| --- | ---: | --- |
| 空库完整迁移 | 4,681 | `04f3687d379373b098367c8150e52f17ed0972a1d8c88a87d5e2846fc985d77d` |
| V2.4.6 升级 | 4,681 | `04f3687d379373b098367c8150e52f17ed0972a1d8c88a87d5e2846fc985d77d` |

两条路径得到完全一致的最终 Schema。

### 5.4 Workflow 边界

`workflow_%` 自有表数量在两个实例中均为 0。新增外键只引用 Investment 本地域表，不引用 Workflow 数据库或 Workflow 自有表。

## 6. 权限验证

以下权限在两个实例中均各存在 1 条有效记录：

- `investment:decision:view`
- `investment:decision:create`
- `investment:decision:submit`
- `investment:decision:withdraw`

每项权限均验证：

- `sys_permission` 有效记录 1 条；
- 按钮类型 `sys_menu` 节点 1 条；
- `SUPER_ADMIN` 的 `sys_role_permission` 授权 1 条；
- `SUPER_ADMIN` 的 `sys_role_menu` 授权 1 条。

未发现重复权限、重复按钮节点或缺失超级管理员授权。

## 7. 资产状态

`database/flyway/migration-inventory.yml` 已更新：

| 版本 | 资产状态 | 执行状态 |
| --- | --- | --- |
| V2.4.7 | `CANONICAL_IMMUTABLE` | `EPHEMERAL_MYSQL8_VALIDATED` |

该状态仅表示在隔离 MySQL 8 环境完成验收，不表示任何受管环境或生产数据库已应用此版本。V2.4.7 自本次验收起不可修改，修正必须新增更高版本 Migration。

## 8. 剩余风险

1. 本次使用空业务数据验证结构和权限；生产发布前仍需在脱敏历史数据副本验证 DDL 锁等待、磁盘空间和执行窗口。
2. 国产数据库的 CHECK、索引和文本字段行为尚未验收，达梦与人大金仓需要独立适配 Migration。
3. Outbox/Inbox 表结构已验证，但消息积压、失败重试、乱序重放和归档容量需在应用联调与压力测试中验证。
4. `payload_json` 为完整集成载荷，应用和运维层必须继续执行敏感字段最小化、访问控制及日志脱敏。
5. 本报告不授权生产执行。生产仍需备份、变更审批、pre 策略检查、migrate、post validate 和 Schema 指纹比对。

## 9. 最终结论

V2.4.7 在 MySQL Community Server 8.4.9 上通过：

- 空库完整迁移；
- V2.4.6 单版本升级；
- Flyway pre/info、migrate、post 严格 validate；
- 二次 migrate no-op；
- checksum 与 history 一致性检查；
- Outbox/Inbox 字段、索引、外键和 CHECK 约束检查；
- 四项 RBAC 权限及超级管理员授权检查；
- 双路径 Schema 指纹一致性检查；
- Workflow 零建库、零自有表及零跨库外键检查。

验收结果：**PASS**。
