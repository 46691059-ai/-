# Project数据库结构基线

版本：Sprint 2-0.2.2
确认日期：2026-08-03

## 1. 基线结论

Project模块唯一权威结构文件为：

```text
database/mysql/05_project.sql
```

该文件是Project域表名、字段、类型、默认值、索引、外键和检查约束的唯一设计来源。
新增或变更Project结构时，不得修改其他模块快照来形成第二份定义。

以下文件停止作为Project结构来源：

```text
database/mysql/deprecated/V1.0.0__enterprise_platform_v1.sql
```

该聚合V1.0文件状态为 `DEPRECATED`，仅用于历史环境识别和差异审计，不进入Docker
初始化，不用于生产升级，也不得继续追加结构变更。

## 2. 权威基线包含的18张表

| 序号 | 表名 | 职责 |
|---:|---|---|
| 1 | `project_stage_template` | 项目阶段模板 |
| 2 | `project_info` | 项目聚合根和基础信息 |
| 3 | `project_stage` | 项目阶段实例 |
| 4 | `project_task` | WBS任务 |
| 5 | `project_member` | 项目成员 |
| 6 | `project_milestone` | 项目里程碑 |
| 7 | `project_investment_info` | 投资项目扩展信息 |
| 8 | `project_business_info` | 中标经营项目扩展信息 |
| 9 | `project_opportunity` | 项目机会 |
| 10 | `project_bid` | 投标记录 |
| 11 | `project_change` | 项目变更 |
| 12 | `project_risk` | 项目风险 |
| 13 | `project_cost` | 项目成本明细 |
| 14 | `project_income` | 项目收入记录 |
| 15 | `project_profit` | 项目利润快照 |
| 16 | `project_acceptance` | 项目验收记录 |
| 17 | `project_evaluation` | 项目后评价 |
| 18 | `project_archive` | 项目档案 |

本次只确认结构所有权，不代表上述18张表均已完成业务代码开发。

## 3. SQL初始化入口治理

### 3.1 Docker唯一入口

Docker MySQL只执行：

```text
database/mysql/init/00_enterprise_platform.sql
```

Compose挂载关系：

```text
database/mysql      -> /opt/enterprise/sql              只读脚本源
database/mysql/init -> /docker-entrypoint-initdb.d       唯一自动入口
```

初始化清单显式加载受控脚本。以下目录永不自动执行：

- `database/mysql/manual`
- `database/mysql/migration`
- `database/mysql/deprecated`

### 3.2 Project结构所有权

| 文件 | 允许职责 | 禁止职责 |
|---|---|---|
| `05_project.sql` | 新库Project全量结构快照 | 存量库直接升级 |
| `06_operation.sql` | 经营域结构；受控增加客户跨域外键 | 重定义Project字段 |
| `09_risk.sql` | 风险域结构 | 新增第二份 `project_risk` 定义；现有兼容定义列入后续治理 |
| `10_init_data.sql` | 项目字典、权限和阶段模板数据 | 创建或修改Project表 |
| `migration/*` | 已发布数据库的不可变增量迁移 | 复写全量快照或修改已执行版本 |
| `deprecated/*` | 历史识别、差异审计 | 初始化、升级、继续维护 |

### 3.3 当前保留项

`V1.1.0__investment_data_risk_bi.sql` 暂时由清单显式加载，以保留现有投资、数据资产、
风险和BI兼容结构。它不作为Project核心表来源。其与模块脚本之间的重复定义应在对应
业务域基线治理Sprint中处理。

## 4. `project_profit`专项分析

### 4.1 两套历史定义

| 业务字段 | 05权威定义 | deprecated聚合V1.0 |
|---|---|---|
| 主键 | `id` | `id` |
| 项目 | `project_id` | `project_id` |
| 收入 | `total_income` | `income` |
| 成本 | `total_cost` | `cost` |
| 利润 | `profit` | `profit` |
| 利润率 | `profit_rate` | `profit_rate` |
| 业务日期 | `calculate_date` | `statistic_date` |
| 审计/逻辑删除/版本字段 | 一致 | 一致 |
| 唯一键 | `project_id, calculate_date, delete_token` | `project_id, statistic_date, delete_token` |
| 专用索引 | `profit_rate, calculate_date, deleted` | 无 |
| 金额约束 | `profit = total_income - total_cost` | 无等价约束 |

### 4.2 统一字段方案

统一采用05定义：

```text
total_income
total_cost
profit
profit_rate
calculate_date
```

理由：

1. 遵循已确认的Project结构基线；
2. `total_*` 能明确表示快照累计值，避免与收入/成本明细混淆；
3. `calculate_date` 表达利润计算时点，与BI宽表的 `statistic_date` 分域；
4. 05已有金额一致性约束和利润率查询索引。

聚合结构迁移映射：

```text
income         -> total_income
cost           -> total_cost
statistic_date -> calculate_date
```

本Sprint不修改表结构。真实数据库识别完成后，应新增版本迁移：先增加目标列、回填并
核对金额，再切换应用，最后在独立审批版本中处理旧列。禁止一次发布中直接重命名或删除
旧列。

## 5. 生产数据库结构识别方案

### 5.1 分类原则

| 识别结果 | 主要特征 |
|---|---|
| `PROJECT_05` | `project_info`拥有6个05扩展字段，`project_task`拥有5个任务扩展字段，13张扩展表齐全，利润表使用05字段 |
| `AGGREGATE_V1` | 05扩展字段和扩展表均不存在，利润表使用 `income/cost/statistic_date` |
| `MIXED` | 同时出现两套利润字段、只存在部分05字段/扩展表，或结构组合不满足以上两类 |
| `UNKNOWN` | 4张核心表不完整，无法识别为受支持基线 |

### 5.2 只读分类SQL

生产核查必须使用只读账号执行：

```sql
WITH core_tables AS (
    SELECT COUNT(DISTINCT table_name) AS core_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('project_info', 'project_stage', 'project_task', 'project_member')
),
column_signatures AS (
    SELECT
        SUM(table_name = 'project_info' AND column_name IN (
            'source_type', 'customer_id', 'contract_amount',
            'actual_income', 'actual_profit', 'description'
        )) AS info_05_count,
        SUM(table_name = 'project_task' AND column_name IN (
            'task_content', 'plan_start', 'plan_end', 'actual_start', 'actual_end'
        )) AS task_05_count,
        SUM(table_name = 'project_profit' AND column_name IN (
            'total_income', 'total_cost', 'calculate_date'
        )) AS profit_05_count,
        SUM(table_name = 'project_profit' AND column_name IN (
            'income', 'cost', 'statistic_date'
        )) AS profit_v1_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN ('project_info', 'project_task', 'project_profit')
),
extension_tables AS (
    SELECT COUNT(DISTINCT table_name) AS extension_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'project_stage_template', 'project_milestone',
          'project_investment_info', 'project_business_info',
          'project_opportunity', 'project_bid', 'project_change',
          'project_risk', 'project_cost', 'project_income',
          'project_acceptance', 'project_evaluation', 'project_archive'
      )
)
SELECT CASE
    WHEN c.core_count < 4 THEN 'UNKNOWN'
    WHEN s.info_05_count = 6
     AND s.task_05_count = 5
     AND s.profit_05_count = 3
     AND s.profit_v1_count = 0
     AND e.extension_count = 13 THEN 'PROJECT_05'
    WHEN s.info_05_count = 0
     AND s.task_05_count = 0
     AND s.profit_05_count = 0
     AND s.profit_v1_count = 3
     AND e.extension_count = 0 THEN 'AGGREGATE_V1'
    ELSE 'MIXED'
END AS project_schema_type,
c.core_count,
s.info_05_count,
s.task_05_count,
s.profit_05_count,
s.profit_v1_count,
e.extension_count
FROM core_tables c
CROSS JOIN column_signatures s
CROSS JOIN extension_tables e;
```

### 5.3 差异明细SQL

分类后还必须导出完整元数据，不能只依赖哨兵字段：

```sql
SELECT table_name, ordinal_position, column_name, column_type,
       is_nullable, column_default, extra
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name LIKE 'project\_%'
ORDER BY table_name, ordinal_position;

SELECT table_name, index_name, non_unique, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name LIKE 'project\_%'
ORDER BY table_name, index_name, seq_in_index;

SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE table_schema = DATABASE()
  AND table_name LIKE 'project\_%'
ORDER BY table_name, constraint_name;
```

核查输出应记录数据库实例、库名、MySQL版本、执行时间和结果校验和，并作为迁移审批附件。

## 6. 当前治理结果

- 已确认05为Project唯一结构基线；
- 已将聚合V1.0标记为deprecated并隔离出Docker入口；
- 已建立Docker唯一初始化清单；
- 未删除任何历史SQL；
- 未修改05及任何Project业务表定义；
- 未连接或修改生产数据库；
- 已形成Project利润表统一字段方案和生产结构识别方法。

## 7. 剩余风险

1. 尚未在空MySQL实例真实执行新的唯一初始化清单；
2. `V1.1.0` 与07、08、09仍存在其他业务域的兼容重复定义；
3. `09_risk.sql` 中仍保留 `project_risk` 兼容定义，后续应通过风险域基线治理收敛；
4. 生产数据库实际属于05、聚合V1.0还是混合结构尚未执行只读识别；
5. `project_profit` 只完成设计统一，尚未实施存量数据迁移；
6. `project_no` 的05长度为50，而当前接口校验允许64，需在后续接口契约治理中处理。
