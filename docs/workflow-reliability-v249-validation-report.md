# V2.4.9 Workflow Reliability Migration 真实 MySQL 验收报告

项目：县域国企数字化运营治理平台
Sprint：2-3.6.1
验收日期：2026-08-08
结论：**PASS**

## 1. 环境信息

| 项目 | 验收环境 |
| --- | --- |
| 环境性质 | 本机临时隔离环境，非生产、非受管共享环境 |
| MySQL | MySQL Community Server 8.4.9 |
| Flyway | Community Edition 13.0.0 |
| 完整迁移实例 | `127.0.0.1:33616` |
| V2.4.8 升级实例 | `127.0.0.1:33617` |
| 基础数据库 | `01_database.sql` 至 `16_sprint_1_log_center.sql`，以及 `V1.1.0__investment_data_risk_bi.sql` |
| Flyway baseline | V2.0.0 |
| Migration 扫描目录 | `database/migration/mysql` 的字节一致临时副本 |
| Worker 状态 | `WORKFLOW_OUTBOX_WORKER_ENABLED=false` |
| Workflow 数据库 | 未创建、未连接 |
| 生产连接 | 无 |

两个 MySQL 实例使用独立临时数据目录，仅监听回环地址。两条路径的历史基础平台均初始化为 120 张表。

## 2. Migration 结果

执行顺序为：SHA-256 资产检查、禁止操作策略检查、Flyway `info`、`migrate`、迁移后严格 `validate`、第二次 `migrate` no-op。

| 检查项 | 空库完整迁移 | V2.4.8 升级至 V2.4.9 |
| --- | --- | --- |
| 受治理 SQL SHA-256 | 15/15 通过 | 15/15 通过 |
| pre 策略检查 | 通过 | 通过 |
| pre `info` | V2.1.0 至 V2.4.9 Pending | 仅 V2.4.9 Pending |
| `migrate` | 成功执行 15 个版本 | 成功执行 1 个版本 |
| V2.4.9 执行时间 | 537 ms | 470 ms |
| post 严格 `validate` | 16 条记录校验成功 | 16 条记录校验成功 |
| 第二次 `migrate` | `No migration necessary` | `No migration necessary` |
| history 成功/失败 | 16 / 0 | 16 / 0 |
| 最终版本 | V2.4.9 | V2.4.9 |
| 最终表数 | 146 | 146 |

16 条 Flyway history 由 1 条 V2.0.0 baseline 和 15 条版本化 Migration 组成。验收过程未执行 `clean` 或 `repair`。

### 2.1 Checksum

- Flyway checksum：`-48227397`
- SHA-256：`96436a5f7b845e69a0c9a9ea55761898aeb5701b9df0eee3af132b02f968f0b8`
- version：`2.4.9`
- description：`enhance workflow reliability`
- type：`SQL`
- 两条路径均为 `success=1`。

## 3. Schema 变化

升级路径的整体结构计数：

| 结构项 | V2.4.8 | V2.4.9 | 变化 |
| --- | ---: | ---: | ---: |
| 表 | 145 | 146 | +1 |
| 字段 | 2642 | 2674 | +32 |
| `information_schema.statistics` 项 | 1407 | 1428 | +21 |
| 外键 | 251 | 255 | +4 |
| CHECK | 239 | 245 | +6 |

### 3.1 Outbox 增强字段

`investment_workflow_outbox` 新增且类型符合设计：

- `worker_id varchar(100)`；
- `locked_at datetime(3)`；
- `lock_until datetime(3)`；
- `dead_time datetime(3)`；
- `dead_reason varchar(200)`。

租约索引 `idx_inv_workflow_outbox_lease(status, lock_until, deleted)` 存在。

### 3.2 Inbox 增强字段

`investment_workflow_inbox` 新增：

- `payload_json text`；
- `replay_count int NOT NULL DEFAULT 0`；
- `last_replay_time datetime(3)`；
- `last_replay_by bigint`；
- `manual_review_required smallint NOT NULL DEFAULT 0`。

缺口索引 `idx_inv_workflow_inbox_gap(binding_id, process_status, event_sequence, deleted)` 存在。

以下 CHECK 均为 `ENFORCED=YES`：

- `chk_inv_workflow_inbox_replay`：重放次数不得小于 0；
- `chk_inv_workflow_inbox_review`：人工核查标识仅允许 0 或 1。

### 3.3 可靠性审计表

`investment_workflow_reliability_audit` 成功创建，共 22 个字段，包含主键、业务关联、审计类型、来源、结果、原因、操作人、复核人、工单、TraceId 和统一审计字段。

验证结果：

- 6 个索引，包括主键、4 个业务查询索引及 1 个外键支撑索引；
- 4 个外键，均只关联 Investment 本地表；
- 4 个 CHECK，覆盖审计类型、操作来源、结果状态和逻辑删除标识；
- 所有 CHECK 均为 `ENFORCED=YES`。

### 3.4 双路径一致性

两条路径均产生 4,745 行结构指纹数据，最终 Schema SHA-256 完全一致：

`7a12e14388e78ad0ff7e8e59b3c3b1860a6f46f7294fae005f9ba0a847fb7be3`

未创建 Workflow Schema，也未产生跨 Workflow 数据库外键。

## 4. 权限验证

权限 `investment:workflow:replay` 在两个实例中均验证通过：

- `sys_permission` 有且仅有 1 条记录；
- `sys_menu` 有且仅有 1 个 `B` 类型按钮节点；
- `SUPER_ADMIN` 的 `sys_role_permission` 有且仅有 1 条授权；
- `SUPER_ADMIN` 的 `sys_role_menu` 有且仅有 1 条授权。

未发现重复权限、重复按钮节点或缺失超级管理员授权。

## 5. 资产状态

`database/flyway/migration-inventory.yml` 已将 V2.4.9 更新为：

- `CANONICAL_IMMUTABLE`
- `EPHEMERAL_MYSQL8_VALIDATED`
- Flyway checksum：`-48227397`

该状态只表示隔离 MySQL 8 验收通过，不表示任何生产或受管数据库已应用 V2.4.9。自本次验收后禁止修改 V2.4.9；修正必须创建更高版本 Migration。

## 6. 剩余风险

1. 本次为结构和权限验收，未使用历史业务积压数据；生产前需在脱敏副本评估 ALTER TABLE 锁等待、执行窗口和存储空间。
2. Worker 在验收期间保持关闭；并发抢占、租约超时、消息积压和死信容量仍需连接真实 Workflow 后进行故障演练。
3. 真实 Workflow 的事件补拉、错误码、限流和幂等行为尚未联调。
4. Redis 高可用、Nonce 防重放、时钟漂移及密钥轮换仍需真实环境验收。
5. 达梦、人大金仓对 `SKIP LOCKED`、CHECK 和 ALTER TABLE 的兼容性尚未验收，需要独立方言 Migration。
6. 本报告不授权生产执行；生产仍需备份、变更审批、发布窗口、迁移后 validate 和 Schema 指纹比对。

## 7. 最终结论

V2.4.9 已在 MySQL 8.4.9 上通过空库完整迁移、V2.4.8 单版本升级、pre 策略检查、Flyway migrate、严格 validate、二次 no-op、checksum/history、结构、索引、外键、CHECK、权限及双路径 Schema 指纹一致性验证。验收结果：**PASS**。
