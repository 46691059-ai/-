# V2.4.8 Investment Decision Approval Closure Migration 真实 MySQL 验收报告

项目：县域国企数字化运营治理平台
Sprint：2-3.4.1
验收日期：2026-08-08
结论：**PASS**

## 1. 环境信息

| 项目 | 验收环境 |
| --- | --- |
| 环境性质 | 本机临时隔离环境，非生产、非受管共享环境 |
| MySQL | MySQL Community Server 8.4.9 |
| Flyway | Community Edition 13.0.0 |
| 完整迁移实例 | `127.0.0.1:33516` |
| V2.4.7 升级实例 | `127.0.0.1:33517` |
| 基础数据库 | `01_database.sql` 至 `16_sprint_1_log_center.sql`，以及 `V1.1.0__investment_data_risk_bi.sql` |
| Flyway baseline | V2.0.0 |
| Migration 扫描目录 | `database/migration/mysql` 的字节一致临时副本 |
| Workflow 数据库 | 未创建、未连接 |
| 生产连接 | 无 |

两个 MySQL 实例使用独立数据目录，仅监听回环地址。每条路径的历史基础平台均初始化为 120 张表。

## 2. Migration 结果

执行顺序严格为：SHA-256 与禁止操作策略检查、Flyway `info`、`migrate`、迁移后严格 `validate`、第二次 `migrate` no-op。

| 检查项 | 空库完整迁移 | V2.4.7 升级至 V2.4.8 |
| --- | --- | --- |
| 受治理 SQL SHA-256 | 14/14 通过 | 14/14 通过 |
| pre `info` | V2.1.0 至 V2.4.8 Pending | 仅 V2.4.8 Pending |
| `migrate` | 成功执行 14 个版本 | 成功执行 1 个版本 |
| V2.4.8 执行时间 | 292 ms | 373 ms |
| post `validate` | 15 条记录严格校验成功 | 15 条记录严格校验成功 |
| 第二次 `migrate` | `No migration necessary` | `No migration necessary` |
| history 成功/失败 | 15 / 0 | 15 / 0 |
| 最终版本 | V2.4.8 | V2.4.8 |

15 条 history 由 1 条 V2.0.0 baseline 和 14 条版本化 Migration 组成。验收过程未执行 `clean` 或 `repair`。

### Checksum

- V2.4.8 Flyway checksum：`157769302`
- V2.4.8 SHA-256：`eb3f6fe13dff6c9cb717000293ca6aeb188fd080ac8387297f50b630be845f81`
- 两条路径的 `flyway_schema_history` 均记录 `success=1`。

## 3. Schema 变化

V2.4.8 不新增表、字段、索引或外键。它替换 `investment_decision.chk_inv_decision_approval_status` CHECK 约束，并写入三项 RBAC 元数据。

| 结构项 | 升级前 | 升级后 | 结论 |
| --- | ---: | ---: | --- |
| 表 | 145 | 145 | 无新增 |
| 字段 | 2642 | 2642 | 无新增 |
| 索引项 | 1407 | 1407 | 无新增 |
| 外键 | 251 | 251 | 无新增 |
| CHECK | 239 | 239 | 同名约束替换 |

`chk_inv_decision_approval_status` 在 MySQL 中为 `ENFORCED=YES`，允许以下规范状态：`DRAFT`、`SUBMITTED`、`IN_APPROVAL`、`APPROVED`、`REJECTED`、`WITHDRAWN`、`ARCHIVED`；同时保留 Migration 明确列出的历史兼容状态。

两条路径均产生 4,681 行结构指纹数据，最终 Schema SHA-256 完全一致：

`8eeb85652ae98cfec8214883016e3315bbc4e39ae9414c27b367715bd8104dc5`

未发现 Workflow 自有 Schema 或 `workflow_%` 自有表，跨库外键数量为 0。

## 4. 权限验证

以下权限在两个实例中均完成验证：

- `investment:decision:approve`
- `investment:decision:condition`
- `investment:decision:archive`

每项权限均满足：

- `sys_permission` 有且仅有 1 条记录；
- `sys_menu` 有且仅有 1 个 `B` 类型按钮节点；
- `SUPER_ADMIN` 的 `sys_role_permission` 有且仅有 1 条授权；
- `SUPER_ADMIN` 的 `sys_role_menu` 有且仅有 1 条授权。

未发现重复权限、重复菜单节点或缺失超级管理员授权。

## 5. 资产状态

`database/flyway/migration-inventory.yml` 已将 V2.4.8 更新为：

- 资产状态：`CANONICAL_IMMUTABLE`
- 执行状态：`EPHEMERAL_MYSQL8_VALIDATED`

该状态仅证明隔离 MySQL 8 环境验收通过，不代表生产或任何受管数据库已经执行。V2.4.8 自本次验收起不可修改，后续修正必须新增更高版本 Migration。

## 6. 剩余风险

1. 本次结构与权限验收使用空业务数据；生产发布前仍需在脱敏历史数据副本验证约束变更对现存 `approval_status` 值的兼容性。
2. CHECK 约束在达梦、人大金仓上的语法与行为尚未验收，国产数据库应使用独立适配脚本与验收链。
3. 本次未连接 Workflow 中心，未覆盖真实事件流、乱序、重复回调及故障恢复联调。
4. 本次不授权生产执行；生产仍须完成备份、变更审批、执行窗口、迁移前策略检查、迁移后严格 validate 和结构指纹比对。

## 7. 最终结论

V2.4.8 已在 MySQL 8.4.9 上通过空库完整迁移、V2.4.7 单版本升级、checksum/history 校验、严格 validate、二次 migrate no-op、CHECK 约束、RBAC 权限、双路径 Schema 指纹一致性及 Workflow 零建库检查。验收结果：**PASS**。
