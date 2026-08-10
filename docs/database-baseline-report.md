# Sprint 2-0.2.1 Project数据库脚本基线审计报告

## 审计说明

- 审计日期：2026-08-03
- 审计范围：`database/mysql` 下所有包含Project表结构、外键、初始化数据、权限数据或历史迁移说明的SQL
- 审计方式：静态只读比对，未连接或修改任何数据库
- 核心结论：**审计时Project模块存在模块化脚本与聚合脚本两套不等价来源。Sprint 2-0.2.2已确认以 `05_project.sql` 作为Project表结构的唯一权威基线，聚合V1.0已迁入deprecated目录。**

## 1. SQL文件清单

### 1.1 直接定义或修改Project表结构

| 文件 | Project相关作用 | 类型 | 审计结论 |
|---|---|---|---|
| `05_project.sql` | 创建18张Project域表，完整定义核心表、扩展表、索引、外键和检查约束 | 模块化全量结构 | 内容最完整，且与当前项目类型字典、Entity治理方向一致 |
| `deprecated/V1.0.0__enterprise_platform_v1.sql` | 创建 `project_info`、`project_stage`、`project_task`、`project_member`、`project_profit` | 已停用聚合兼容结构 | 与 `05_project.sql` 不等价，不可作为并列权威来源 |
| `06_operation.sql` | 给 `project_info.customer_id` 增加索引及到 `operation_customer` 的外键 | 跨域增量 | 依赖 `05_project.sql` 中的 `customer_id`；聚合脚本单独使用时无法执行此变更 |
| `09_risk.sql` | 使用 `CREATE TABLE IF NOT EXISTS` 再次定义 `project_risk` | 兼容重复定义 | 与 `05_project.sql` 形成双重所有权，结果受执行顺序影响 |
| `V1.1.0__investment_data_risk_bi.sql` | 投资事项引用 `project_info`，创建 `bi_project_analysis`，补充党建表到Project/Investment的外键 | 聚合后续版本 | 依赖已有Project基表，但不补齐聚合V1.0缺失的Project字段和扩展表 |
| `migration/V2.0.0__legacy_to_v1.sql` | 将4张旧 `pm_project*` 表重命名为 `*_legacy`，以注释描述字段映射 | 人工历史迁移说明 | 不是可闭环的数据库迁移，不创建新表、不迁移数据、不校验结果 |

### 1.2 Project相关依赖和初始化数据

| 文件 | 相关内容 | 是否定义Project结构 |
|---|---|---|
| `02_sys.sql` | `PROJECT_TEAM` 组织类型等基础主数据能力 | 否 |
| `04_party.sql` | `party_project`、`party_major_decision` 等党建融合数据包含项目逻辑引用 | 否，存在跨域依赖 |
| `07_investment.sql` | `investment_project.project_id` 外键引用 `project_info` | 否，存在跨域依赖 |
| `10_init_data.sql` | 项目角色、权限、菜单、项目类型/状态/阶段字典，以及 `project_stage_template` 种子数据 | 否，但要求 `project_stage_template` 已由 `05_project.sql` 创建 |
| `13_sprint_1_role_permissions.sql` | 初始化 `PROJECT_MANAGER` 角色 | 否 |
| `14_sprint_1_rbac_acceptance.sql` | 增加 `project:add`、`project:edit` 验收权限和项目经理授权 | 否 |
| `manual/15_sprint_1_3_1_acceptance_fixture.sql` | Project RBAC验收账号、角色、组织数据 | 否 |
| `manual/17_sprint_1_6_datascope_acceptance_fixture.sql` | Project Manager CUSTOM数据范围验收数据 | 否 |

### 1.3 审计时自动初始化路径

> 治理状态：Sprint 2-0.2.2已改为 `database/mysql/init/00_enterprise_platform.sql`
> 唯一入口。以下内容保留为问题发现时的历史记录。

`deploy/docker-compose.yml` 将整个 `database/mysql` 目录挂载到：

```text
/docker-entrypoint-initdb.d
```

MySQL首次创建数据卷时会处理根目录SQL，当前文档又要求依次执行模块化脚本、`V1.0.0` 和 `V1.1.0`。这意味着两套结构文件会进入同一初始化流程：

```text
01_database.sql
  -> 02_sys.sql ... 05_project.sql ... 10_init_data.sql ... 16_sprint_1_log_center.sql
  -> V1.0.0__enterprise_platform_v1.sql
  -> V1.1.0__investment_data_risk_bi.sql
```

虽然聚合脚本大量使用 `IF NOT EXISTS`，但这只会跳过已存在表，不会验证两份定义是否一致，也不能消除聚合脚本中的重复 `ALTER TABLE` 风险。因此当前初始化不是单一、确定性的版本链。

## 2. 字段差异

以下以 `05_project.sql` 与 `V1.0.0__enterprise_platform_v1.sql` 对比。

### 2.1 `project_info`

#### 聚合V1.0缺失字段

| `05_project.sql`字段 | 用途 |
|---|---|
| `source_type` | 项目来源 |
| `customer_id` | 客户单位引用 |
| `contract_amount` | 合同金额 |
| `actual_income` | 实际收入 |
| `actual_profit` | 实际利润 |
| `description` | 项目说明 |

`delete_token` 两份脚本均存在。

#### 同名字段定义不同

| 字段 | `05_project.sql` | 聚合V1.0 | 风险 |
|---|---|---|---|
| `project_no` | `VARCHAR(50)` | `VARCHAR(64)` | 当前请求模型允许64字符，以05为基线时仍存在应用校验与数据库长度不一致风险 |
| `project_type` | `VARCHAR(50)`，语义为 `01`至`06` | `VARCHAR(32)`，注释语义为英文枚举 | 数据值域冲突；当前字典和API使用数字编码 |
| `project_mode` | `VARCHAR(50)` | `VARCHAR(32)` | 长度不一致 |
| `status` | `VARCHAR(30)` | `VARCHAR(32)` | 长度不一致 |
| `risk_level` | `VARCHAR(20)` | `VARCHAR(16)` | 长度不一致 |

#### 约束差异

- `05_project.sql` 分别校验计划日期、实际日期，并覆盖合同、预期和实际金额规则。
- 聚合V1.0只校验计划日期及部分金额字段。
- `05_project.sql` 比聚合V1.0多项目状态索引和项目类型索引。
- `06_operation.sql` 依赖 `customer_id` 增加客户索引和外键；聚合V1.0没有该字段。

### 2.2 `project_stage`

| 字段 | `05_project.sql` | 聚合V1.0 |
|---|---|---|
| `stage_code` | `VARCHAR(50)` | `VARCHAR(32)` |
| `stage_name` | `VARCHAR(100)` | `VARCHAR(64)` |
| `status` | `VARCHAR(20)` | `VARCHAR(32)` |

`05_project.sql` 额外包含：

- 计划开始/结束日期约束；
- 实际开始/结束日期约束；
- `status, end_time, deleted` 状态到期索引。

### 2.3 `project_task`

#### 聚合V1.0缺失字段

| 字段 | 用途 |
|---|---|
| `task_content` | 任务内容 |
| `plan_start` | 计划开始日期 |
| `plan_end` | 计划结束日期 |
| `actual_start` | 实际开始日期 |
| `actual_end` | 实际结束日期 |

#### 其他差异

- `status`：05为 `VARCHAR(20)`，聚合V1.0为 `VARCHAR(32)`。
- 05额外提供项目维度索引和父任务索引。
- 05额外校验计划起止日期和实际起止日期。
- 两份脚本均包含任务到项目、阶段、父任务和负责人的外键约束。

### 2.4 `project_member`

| 字段 | `05_project.sql` | 聚合V1.0 |
|---|---|---|
| `role` | `VARCHAR(50)` | `VARCHAR(32)` |
| `status` | `VARCHAR(20)` | `VARCHAR(16)` |

其余核心字段、唯一键、项目/员工索引和外键基本一致。

### 2.5 `project_profit`

两份脚本定义的是同名但不兼容的表：

| 业务含义 | `05_project.sql` | 聚合V1.0 |
|---|---|---|
| 收入 | `total_income` | `income` |
| 成本 | `total_cost` | `cost` |
| 统计日期 | `calculate_date` | `statistic_date` |

05还提供利润率索引和金额一致性检查。由于聚合脚本使用 `IF NOT EXISTS`，实际列名取决于哪份脚本先创建该表，这是明确的环境漂移风险。

## 3. 表结构差异

### 3.1 表集合

`05_project.sql` 定义18张表：

```text
project_stage_template
project_info
project_stage
project_task
project_member
project_milestone
project_investment_info
project_business_info
project_opportunity
project_bid
project_change
project_risk
project_cost
project_income
project_profit
project_acceptance
project_evaluation
project_archive
```

聚合V1.0仅定义其中5张：

```text
project_info
project_stage
project_task
project_member
project_profit
```

因此聚合V1.0缺少13张Project表，不能表达当前完整项目数据库设计。

### 3.2 重复所有权

| 对象 | 重复来源 | 问题 |
|---|---|---|
| 4张核心表 | 05与聚合V1.0 | 字段长度、值域、字段集合及约束不同 |
| `project_profit` | 05与聚合V1.0 | 同名字段体系不兼容 |
| `project_risk` | 05与09 | 09额外增加 `deleted` 检查；`IF NOT EXISTS` 使结果依赖顺序 |
| `project_info.customer_id`约束 | 05与06共同完成 | 所有权跨文件，聚合V1.0缺字段导致06无法应用 |

### 3.3 初始化依赖差异

- `10_init_data.sql` 会写入 `project_stage_template`；聚合V1.0没有创建该表。
- 仅执行聚合V1.0后再执行初始化数据，会在阶段模板数据处失败。
- 仅执行聚合V1.0后执行 `06_operation.sql`，会因 `project_info.customer_id` 不存在而失败。
- 仅执行聚合V1.0时，Sprint 2-0.2补齐后的Project Entity默认查询会引用缺失列，产生未知列错误。
- 先执行聚合V1.0再执行05，05使用非幂等 `CREATE TABLE`，会因表已存在失败。
- 先执行05再执行聚合V1.0时，聚合结构被静默跳过，无法发挥版本校验作用。

## 4. Migration脚本审计

当前仅发现：

```text
database/mysql/migration/V2.0.0__legacy_to_v1.sql
```

该脚本当前只执行：

- 关闭外键检查；
- 将 `pm_project`、`pm_project_stage`、`pm_project_task`、`pm_project_member` 重命名为 `*_legacy`；
- 通过注释说明部分字段映射；
- 恢复外键检查。

缺失能力：

1. 没有创建目标Project表；
2. 没有执行 `INSERT ... SELECT` 数据转换；
3. 没有处理项目类型、状态、阶段、用户到员工等值域转换；
4. 没有迁移异常表或错误清单；
5. 没有迁移前置检查和幂等保护；
6. 没有记录数、金额、空值、外键和哈希核验；
7. 没有可执行回滚步骤；
8. 没有数据库版本历史表或Flyway/Liquibase执行约束；
9. 文件名为V2.0.0，但目标描述为“legacy to v1”，版本语义不清晰。

结论：该文件只能作为人工迁移说明的一部分，不能作为Project结构权威来源，也不能直接宣称完成旧库升级。

## 5. 推荐权威版本

### 5.1 当前阶段

推荐确定：

```text
database/mysql/05_project.sql
```

为Project模块唯一表结构权威基线，理由如下：

1. 完整包含18张Project域表；
2. 4张核心表字段与Sprint 2-0.2 Entity治理目标一致；
3. `project_type` 使用 `01`至`06`，与 `10_init_data.sql` 字典和现有API校验一致；
4. 包含更完整的日期、金额、进度约束和业务索引；
5. `README.md` 与数据库设计文档已经把05描述为项目全生命周期完整脚本；
6. 06、07、09、10等后续模块脚本均以05所创建的Project对象为实际依赖。

权威范围应明确为：

- Project域表的字段、类型、索引、外键和检查约束，以05为准；
- `06_operation.sql` 只负责经营域对象及受控的客户跨域外键；
- `09_risk.sql` 不应再次拥有 `project_risk` 的结构定义；
- `10_init_data.sql` 只负责字典、权限和阶段模板种子数据；
- 聚合V1.0只可作为历史兼容快照，在完成对齐前不得作为新环境Project建库来源。

### 5.2 长期版本治理

`05_project.sql` 适合作为当前设计快照，但长期生产升级不应持续修改全量快照。后续应使用单向、不可变的版本迁移链作为部署权威，例如：

```text
Project基线快照
  -> V2.x.1 对齐核心字段
  -> V2.x.2 对齐索引和约束
  -> V2.x.3 数据回填及校验
  -> 后续每个Sprint新增独立迁移
```

全量快照用于新库初始化，版本迁移用于存量库升级，两者必须由自动化测试验证最终结构一致。

## 6. 后续迁移建议

### 6.1 先治理脚本入口

1. 新库初始化只能选择一条路径，禁止同时执行模块化快照和聚合快照。
2. 将聚合V1.0移出Docker自动初始化根目录，或在完成自动生成和一致性校验后再恢复使用。
3. 为Docker初始化提供显式清单/专用目录，而不是自动执行根目录全部SQL。
4. 明确 `05 -> 06 -> 07 -> 08 -> 09 -> 10` 的模块依赖顺序。

### 6.2 存量数据库迁移流程

1. 采集生产库 `information_schema`，识别实际来源是05结构、聚合V1.0结构还是混合结构。
2. 对4张核心表和 `project_profit` 生成环境差异清单，不根据文件名推测线上结构。
3. 先新增缺失字段，保持字段可空或使用安全默认值；完成数据回填后再增加严格约束。
4. 对项目类型执行值域盘点，明确英文枚举到 `01`至`06` 的映射，未知值进入异常清单。
5. 对 `project_profit` 制定明确列迁移：`income/cost/statistic_date` 与 `total_income/total_cost/calculate_date` 不得靠重建空表解决。
6. 补齐索引和检查约束前，先清理不符合约束的历史数据。
7. 每步记录变更前后行数、孤儿外键、金额汇总、空值和重复业务编码。
8. 迁移通过预发布演练、备份恢复演练和应用回归后再进入生产。
9. 旧表只读保留一个明确观察期，确认后再由独立变更审批处理，不在应用发布中直接删除。

### 6.3 自动化阻断项

后续CI建议增加：

- 从空库执行唯一初始化链；
- 从聚合V1.0模拟库执行升级链；
- 从旧 `pm_project*` 模拟库执行历史迁移链；
- 对比最终 `information_schema.columns/statistics/table_constraints`；
- 启动应用并执行Project Mapper最小查询；
- 发现同一表由两个快照重复定义时阻断合并；
- 发现Entity字段不在权威DDL或DDL字段未纳入映射豁免清单时阻断合并。

## 7. 最终结论

Project模块当前唯一推荐数据库来源为 `database/mysql/05_project.sql`。聚合V1.0属于不完整且已发生结构漂移的兼容快照；09中的 `project_risk` 是重复定义；V2.0.0 legacy脚本只是迁移骨架。下一阶段应先收敛初始化入口并建立可执行版本迁移，再对任何真实数据库实施结构变更。
