# Investment V2.4.0 Migration 实施报告

## 1. 执行摘要

Sprint 2-2.1 已将投资决策闭环第一批结构作为单一 Flyway 资产正式放入 `database/migration/mysql`。本次未修改 `database/mysql/05_project.sql`、历史 Migration 或业务代码。

| 项目 | 结果 |
|---|---|
| Migration | `V2.4.0__create_investment_decision_baseline.sql` |
| 正式扫描目录 | `database/migration/mysql` |
| 目标表 | 14 |
| 新建表 | 11 |
| 兼容扩展旧表 | 3 |
| MySQL 验收版本 | MySQL Community Server 8.4.10 |
| Flyway 版本 | 13.0.0 |
| Flyway checksum | `-717284868` |
| 文件 SHA-256 | `6a498a2065a4139a9423d0d23bb616fe66a7c91860ff6cd4c2642d629ec3ae49` |
| Schema 指纹 SHA-256 | `3868573fd5237dadaea74e3d858e4b9fd8e836753f3ca31ecf92bc124b9f1e21` |
| 结论 | PASS |

## 2. 新增与演进表

### 2.1 兼容扩展现有表

| 表 | 处理 | 关键变化 |
|---|---|---|
| `investment_project` | ALTER | 增加出资方式、合作模式、合作方摘要、被投企业引用及状态索引 |
| `investment_feasibility` | ALTER | 演进为可研档案头，增加当前版本、冻结版本和档案状态 |
| `investment_decision` | ALTER | 演进为决策事项头，增加业务编号、冻结资料引用、路线快照、三重一大标识、审批状态和替代关系 |

历史数据不会被删除或根据名称推断业务事实：

- 无法确定的历史出资方式标记为 `LEGACY_UNKNOWN`；
- 无法确定权威版本的旧可研档案标记为 `LEGACY`，版本指针保持空；
- 旧决策编号使用 `LEGACY-DEC-{id}`，仅在已有决策结果时标记为 `COMPLETED`。

### 2.2 新建表

| 表 | 职责 |
|---|---|
| `investment_opportunity` | 机会来源、提出组织、初步测算和转投资事项 |
| `investment_feasibility_version` | 可研报告多版本、测算指标、冻结哈希和附件引用 |
| `investment_due_diligence_package` | 尽调轮次、必需类型规则快照和整体结论 |
| `investment_due_diligence` | 财务、法务、业务和技术尽调报告版本 |
| `investment_due_diligence_item` | 尽调问题、阻断级别、整改、复核和证据 |
| `investment_scheme` | 项目投资方案档案头和当前版本指针 |
| `investment_scheme_version` | 投资金额、出资、股权、治理、合作与退出方案快照 |
| `investment_scheme_funding` | 方案版本的多来源资金明细 |
| `investment_decision_node` | 业务审核、党委前置、董事会/经理层等决策路线快照 |
| `investment_decision_condition` | 附条件批准任务、整改、复核与豁免 |
| `investment_decision_condition_action` | 条件任务不可变操作历史和幂等键 |

## 3. 字段、约束与索引

14 张目标表均通过字段完整性 SQL 核查，无缺失的通用字段：

`id`、`create_time`、`create_by`、`update_time`、`update_by`、`deleted`、`delete_token`、`remark`、`version`。

约束原则：

- 主键使用 `BIGINT`，由应用雪花算法生成，不依赖 MySQL 自增；
- 逻辑删除使用 `deleted + delete_token`，在保留历史的同时实现有效业务键唯一；
- `version` 作为乐观锁字段；
- 从属记录使用物理外键固定投资事项、版本、组织和文件关系，无级联删除；
- 金额、比例、版本号、布尔标识和日期区间增加 `CHECK` 约束；
- 人员引用暂不增加外键，避免在 `sys_user.id` 与 `hr_employee.id` 口径未冻结前产生错误绑定。

本次目标表共核验 88 个命名索引、45 个外键和 38 个检查约束。索引覆盖 `project_id`、`investment_id`、业务编号、状态、版本号、审批引用、责任人和期限等主要查询路径。

## 4. Flyway 执行结果

### 4.1 空业务库验收

验收使用全新 MySQL 8.4.10 临时数据目录，先执行 `01_database.sql`—`16_sprint_1_log_center.sql` 及 `V1.1.0__investment_data_risk_bi.sql`，再在 V2.0.0 建立 Flyway baseline。这里“空库”指已有基础平台结构、无投资业务数据的新环境。

| 检查 | 结果 |
|---|---|
| Baseline | V2.0.0 成功 |
| Migrate | V2.1.0 → V2.1.3 → V2.2.0 → V2.4.0 成功 |
| 整体 migrate 墙钟时间 | 13,319 ms |
| V2.4.0 SQL 执行时间 | 6,465 ms |
| Validate | 成功 |
| 历史失败记录 | 0 |
| 14 张目标表 | 全部存在 |
| Schema 指纹 | `3868573fd5237dadaea74e3d858e4b9fd8e836753f3ca31ecf92bc124b9f1e21` |

### 4.2 已有 V2.1.3 环境升级

第二次验收重建同一隔离实例，先将 Flyway 停在 V2.1.3，写入旧版 `investment_project`、`investment_feasibility` 和 `investment_decision` 关联数据，再执行升级。

| 检查 | 结果 |
|---|---|
| 升级路径 | V2.1.3 → V2.2.0 → V2.4.0 成功 |
| 升级墙钟时间 | 10,164 ms |
| V2.4.0 SQL 执行时间 | 7,614 ms |
| Flyway validate | 成功 |
| 旧投资事项 | 保留，未知出资方式为 `LEGACY_UNKNOWN` |
| 旧可研 | 保留，状态为 `LEGACY`，未伪造版本指针 |
| 旧决策 | 保留，编号、主题、结果和内容成功兼容回填 |
| Schema 指纹 | 与空业务库一致 |

`flyway_schema_history` 最终为 7 条成功记录：V2.0.0 baseline，V2.1.0—V2.1.3，V2.2.0，V2.4.0；安装顺序连续，无 out-of-order、clean 或 repair。

## 5. 兼容性

- 对旧结构采用扩展式 DDL，未删除旧列，旧读路径可继续工作；
- `project_info` 仍是通用项目根，`investment_project.project_id` 仍是 Investment 访问 Project 权限策略的关联入口；
- 新结构只使用关系列、精确数值、短编码和文件ID，未使用 JSON 保存需要统计或门禁的业务事实；
- MySQL 扫描资产已冻结 SHA-256 和 Flyway checksum；达梦、人大金仓应在各自 vendor 目录生成等价 DDL，不直接执行本 MySQL 文件。

## 6. 风险清单

1. MySQL DDL 存在隐式提交；若中途失败，不能依赖单一事务回滚，必须按现场对象前向修复。
2. 可研/方案的当前版本指针是循环关联；“指针必须属于同一档案”需应用事务和一致性验收 SQL 共同强制。
3. `package_id + investment_id`、决策引用的可研/尽调/方案版本必须属于同一投资事项，这类跨表规则无法仅靠普通 CHECK 完成。
4. `LEGACY_UNKNOWN` 和 `LEGACY` 是过渡性哨兵值；后续数据治理必须由业务确认真实出资方式和可研状态，禁止系统自动猜测。
5. 人员ID口径仍需冻结；在此之前只存稳定ID，不增加可能错绑的物理外键。
6. 本次未执行任何受管、预生产或生产数据库；生产发布前仍需全量备份、恢复演练、业务冲突 SQL 与国产数据库等价脚本验收。

## 7. 资产登记

- `database/migration/mysql/SHA256SUMS` 已登记 V2.4.0 SHA-256；
- `database/flyway/migration-inventory.yml` 已登记资产状态、执行状态、checksum、指纹和两条验收路径；
- V2.4.0 验收后视为不可变资产，任何修正必须使用更高 Migration 版本，禁止 repair 或直接改写已执行文件。
