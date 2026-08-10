# Sprint 2-0.2.3 Project数据库基线收尾方案

日期：2026-08-03
状态：领域设计前数据库治理结论

## 1. `project_no`统一方案

### 1.1 当前状态

| 层级 | 当前约束 | 结论 |
|---|---|---|
| 权威DDL | `project_info.project_no VARCHAR(50) NOT NULL` | 最大50字符 |
| 唯一性 | `UNIQUE(project_no, delete_token)` | 有效记录编号唯一，逻辑删除后允许复用 |
| Entity | `String projectNo` | Entity本身没有长度校验 |
| Create DTO | `@NotBlank @Size(max = 64)` | 允许51至64字符进入Service，与DDL冲突 |
| Update DTO | 不包含 `projectNo` | 创建后不可通过现有修改接口变更编号 |
| Controller | 类级 `@Validated`，新增请求参数使用 `@Valid` | DTO约束会执行，但当前上限错误 |
| Service | 查询唯一性后执行 `trim()` 并写入 | 不会截断，超过50字符将由数据库报错 |

当前风险：长度为51至64的项目编号能够通过Controller校验，最终在05结构的MySQL写入阶段
失败，错误位置过晚且可能表现为数据库异常。

### 1.2 统一决策

统一采用：

```text
project_no 最大50字符
```

理由：

1. 遵循已确认的 `05_project.sql` 权威基线；
2. 现有项目编号通常由前缀、年份、序号构成，50字符足够承载规范编码；
3. 缩短应用输入边界可在Controller层返回明确参数错误，避免数据库异常；
4. 项目编号创建后不可编辑，统一入口和迁移边界清晰；
5. 不需要为新05数据库执行DDL。

后续代码治理项（本Sprint不执行）：

- 将Create DTO的 `@Size(max = 64)` 调整为 `@Size(max = 50)`；
- OpenAPI/API文档和前端表单同步 `maxlength=50`；
- 编号生成器如后续引入，生成后长度必须不超过50；
- 增加50字符成功、51字符被参数校验拒绝的Controller测试；
- 保持项目编号不可通过普通编辑接口修改。

### 1.3 存量数据库迁移方案

执行任何收窄操作前先只读检查：

```sql
SELECT
    MAX(CHAR_LENGTH(project_no)) AS max_length,
    SUM(CHAR_LENGTH(project_no) > 50) AS over_limit_count
FROM project_info;

SELECT id, project_no, CHAR_LENGTH(project_no) AS project_no_length
FROM project_info
WHERE CHAR_LENGTH(project_no) > 50
ORDER BY project_no_length DESC, id;
```

分类处理：

- 已是05结构且字段为 `VARCHAR(50)`：不执行DDL，只调整后续应用输入契约。
- 聚合V1或混合结构为 `VARCHAR(64)`，且不存在超长数据：在独立migration中评估收窄为
  `VARCHAR(50)`，完成索引和应用回归后发布。
- 存在超过50字符的数据：进入业务异常清单，由项目数据责任人分配新编号并维护映射；
  禁止使用 `LEFT(project_no, 50)` 等方式静默截断。
- 若历史编号受外部系统强约束无法调整：提交基线变更评审，重新评估统一为64；在评审通过
  前不得单环境自行扩列。

迁移验收必须检查：记录数、编号唯一性、逻辑删除复用、外部接口引用和项目查询结果。

## 2. `project_risk`领域边界方案

### 2.1 重复定义现状

`05_project.sql` 与 `09_risk.sql` 定义的 `project_risk` 字段、索引和外键基本相同。
09中的兼容定义额外包含：

```text
CHECK (deleted IN (0, 1))
```

并使用 `CREATE TABLE IF NOT EXISTS`。当前初始化顺序是05先创建，09中的定义随后被跳过，
因此该检查约束是否存在取决于最初由哪份脚本创建表，属于结构所有权和环境一致性风险。

### 2.2 领域归属

#### 项目风险

表名：`project_risk`
归属：Project项目生命周期域
结构所有者：`05_project.sql`

职责：

- 记录单个项目执行过程中的风险项；
- 直接关联 `project_info.id`；
- 管理项目风险类型、等级、描述、措施和处理状态；
- 受Project数据权限及 `ProjectAccessPolicy` 约束；
- 生命周期与项目阶段、任务和变更过程关联。

#### 企业风险

表名：`risk_info`
归属：Risk企业风险合规域
结构所有者：风险域基线脚本

职责：

- 建立企业级统一风险库；
- 关联风险分类、责任组织、责任人、概率和影响程度；
- 可通过 `business_type + business_id` 逻辑关联项目、合同、投资等业务对象；
- 负责企业风险评估、预警、整改和审计闭环；
- 不直接替代项目团队的项目风险台账。

### 2.3 集成边界

推荐流程：

```text
Project域发现项目风险
  -> 写入 project_risk
  -> 达到企业级上报条件
  -> 通过应用服务/API/领域事件提交Risk域
  -> Risk域创建或关联 risk_info
  -> 企业整改状态按受控接口反馈Project域
```

禁止：

- Project服务直接写 `risk_info`；
- Risk服务直接更新 `project_risk`；
- 两个域分别维护同一张 `project_risk` 表结构；
- 仅依靠前端同时写两张表；
- 使用数据库触发器跨域同步业务状态。

### 2.4 后续表命名建议

| 业务对象 | 推荐名称 | 归属 |
|---|---|---|
| 项目风险台账 | `project_risk` | Project域，保持现名 |
| 企业风险主表 | `risk_info` | Risk域，保持现名 |
| 企业风险分类 | `risk_category` | Risk域 |
| 企业风险评估 | `risk_evaluation` | Risk域 |
| 项目风险与企业风险追溯关系（确有多对多需求时） | `risk_project_link` | Risk域 |

当前 `risk_info.business_type='PROJECT' + business_id=project_info.id` 已能表达企业风险到项目的
逻辑引用。在出现多对多、上报批次或独立追溯审计要求前，不建议新增关系表。

### 2.5 后续治理建议

1. 05继续作为 `project_risk` 唯一定义；
2. 风险域下一次基线治理时停止在活动快照中重复创建 `project_risk`；
3. 现有09历史内容不原地修改，通过新的风险域基线或migration收敛；
4. 若需要 `deleted` 检查约束，应先核查历史数据，再由Project版本migration增加；
5. 建立跨域契约时使用稳定ID和幂等请求号，避免重复上报企业风险。

## 3. 空MySQL初始化验证方案

### 3.1 验证原则

- 仅使用隔离的临时MySQL 8实例和独立数据卷；
- 禁止连接生产、预生产共用库或复用已有数据卷；
- 只通过 `database/mysql/init/00_enterprise_platform.sql` 进入初始化；
- 验证结束保存日志和元数据报告后销毁隔离资源；
- migration、manual、deprecated目录不得出现在初始化日志中。

### 3.2 执行顺序

初始化清单的受控顺序为：

```text
01_database
  -> 02_sys
  -> 03_hr
  -> 04_party
  -> 05_project
  -> 06_operation
  -> 07_investment
  -> 08_data_asset
  -> 09_risk
  -> 10_init_data
  -> 11~16 Sprint 1权限/菜单/日志数据
  -> V1.1 投资、数据、风险、BI兼容结构
```

依赖说明：

- Sys先创建组织和权限基础；
- HR依赖组织，Project负责人和成员依赖HR员工；
- Party和Project在经营、投资、风险之前创建；
- Operation在Project创建后增加客户跨域外键；
- Investment引用Project；
- Risk依赖项目、投资、合同等业务对象；
- 初始化数据必须在其目标表创建后执行；
- V1.1当前只作为显式兼容尾段，后续由各业务域基线Sprint拆分治理。

### 3.3 执行步骤

1. 校验所有SOURCE目标存在并计算SHA-256清单；
2. 使用唯一Compose project名称启动全新MySQL数据卷；
3. 等待MySQL健康检查通过；
4. 导出完整初始化日志，确认无 `ERROR`、`Unknown column`、重复约束或外键失败；
5. 使用只读验收账号执行以下检查SQL；
6. 导出 `information_schema` 字段、索引和约束清单；
7. 运行后端数据库映射检查和Project Mapper最小查询；
8. 保存报告后销毁本次隔离Compose project及其专用数据卷。

销毁前必须确认Compose project名称和卷只属于本次验证，禁止对默认或生产项目执行
`down -v`。

### 3.4 Project表数量检查

```sql
SELECT COUNT(*) AS project_table_count
FROM information_schema.tables
WHERE table_schema = 'enterprise_platform'
  AND table_name IN (
      'project_stage_template', 'project_info', 'project_stage', 'project_task',
      'project_member', 'project_milestone', 'project_investment_info',
      'project_business_info', 'project_opportunity', 'project_bid',
      'project_change', 'project_risk', 'project_cost', 'project_income',
      'project_profit', 'project_acceptance', 'project_evaluation', 'project_archive'
  );
```

期望：`18`。

### 3.5 核心字段检查

```sql
SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'enterprise_platform'
  AND (
      (table_name = 'project_info' AND column_name IN (
          'project_no', 'source_type', 'customer_id', 'contract_amount',
          'actual_income', 'actual_profit', 'description', 'delete_token'
      ))
      OR
      (table_name = 'project_task' AND column_name IN (
          'task_content', 'plan_start', 'plan_end',
          'actual_start', 'actual_end', 'delete_token'
      ))
  )
ORDER BY table_name, ordinal_position;
```

必须同时确认：

- `project_no` 为 `VARCHAR(50)`；
- Project Entity补齐字段全部存在；
- 4张核心表均包含 `delete_token`。

### 3.6 `project_profit`结构检查

```sql
SELECT column_name
FROM information_schema.columns
WHERE table_schema = 'enterprise_platform'
  AND table_name = 'project_profit'
  AND column_name IN (
      'total_income', 'total_cost', 'calculate_date',
      'income', 'cost', 'statistic_date'
  )
ORDER BY column_name;
```

期望只返回：

```text
calculate_date
total_cost
total_income
```

### 3.7 索引与约束检查

```sql
SELECT table_name, index_name, non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = 'enterprise_platform'
  AND table_name LIKE 'project\_%'
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE table_schema = 'enterprise_platform'
  AND table_name LIKE 'project\_%'
ORDER BY table_name, constraint_name;
```

重点确认：

- `uk_project_info_no(project_no, delete_token)`；
- 任务到阶段、父任务的复合外键；
- Project到组织和员工的外键；
- `project_info.customer_id` 到经营客户的外键；
- 日期、金额、进度检查约束；
- `project_risk` 只形成一张实际表。

### 3.8 初始化数据检查

```sql
SELECT project_type, COUNT(*) AS stage_count,
       MIN(stage_order) AS min_order, MAX(stage_order) AS max_order
FROM project_stage_template
WHERE deleted = 0
GROUP BY project_type;

SELECT dict_type, COUNT(*) AS item_count
FROM sys_dict
WHERE dict_type IN ('project_type', 'project_status', 'project_stage')
  AND status = 1
GROUP BY dict_type
ORDER BY dict_type;
```

通用阶段模板期望：`project_type='ALL'`，阶段数8，顺序1至8。

### 3.9 验收判定

全部满足才可判定初始化通过：

- 唯一入口执行成功；
- 18张Project表齐全；
- 核心字段、索引、外键和约束与05一致；
- 聚合V1利润字段不存在；
- 阶段模板和项目字典完整；
- 后端启动及Project Mapper查询成功；
- 初始化日志、SQL摘要和元数据报告已归档。

## 4. 剩余风险

1. 当前方案尚未在真实空MySQL实例执行；
2. DTO的项目编号上限仍为64，本Sprint按要求未修改代码；
3. 生产库是否存在超过50字符的项目编号尚未核查；
4. 09仍保留 `project_risk` 历史兼容定义；
5. 05中的 `project_risk` 尚未包含 `deleted`值域检查；
6. Project风险上报企业风险的接口、事件和幂等规则尚未设计；
7. V1.1与其他业务域模块脚本仍存在重复结构，需分别治理。
