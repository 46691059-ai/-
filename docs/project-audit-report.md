# Sprint 2-0.1 项目生命周期模块代码审计报告

## 1. 审计说明

- 审计日期：2026-08-03
- 审计范围：`backend/src/main/java/cn/gov/enterprise/modules/project`
- 数据库比对基线：`database/mysql/05_project.sql`
- 审计方式：静态只读检查，不启动应用、不连接数据库、不修改业务代码和数据库脚本
- PRD依据：仓库内未发现 `03_项目全生命周期中心PRD.md` 原文件；A类、B类能力依据现有 API 文档、项目 DDL、项目类型定义和需求上下文进行差距分析

## 2. 当前结构

```text
modules/project
├── controller
│   ├── ProjectController.java
│   ├── ProjectMemberController.java
│   ├── ProjectStageController.java
│   └── ProjectTaskController.java
├── dto
│   └── ProjectDtos.java
├── entity
│   ├── ProjectEntity.java
│   ├── ProjectMemberEntity.java
│   ├── ProjectStageEntity.java
│   └── ProjectTaskEntity.java
├── mapper
│   ├── ProjectMapper.java
│   ├── ProjectMemberMapper.java
│   ├── ProjectReferenceMapper.java
│   ├── ProjectStageMapper.java
│   └── ProjectTaskMapper.java
├── security
│   └── ProjectAccessPolicy.java
└── service
    ├── impl
    │   └── ProjectLifecycleServiceImpl.java
    ├── ProjectLifecycleAssembler.java
    ├── ProjectLifecycleQueryService.java
    ├── ProjectLifecycleService.java
    ├── ProjectMemberCommandService.java
    ├── ProjectReferenceValidator.java
    ├── ProjectStageTransitionPolicy.java
    └── ProjectTaskCommandService.java
```

### 2.1 包职责

| 包 | 当前职责 | 审计结论 |
|---|---|---|
| `controller` | 暴露项目、阶段、任务、成员 REST API，并执行功能权限校验 | 职责清晰，未发现 Controller 直接访问 Mapper |
| `dto` | 集中定义请求、分页、详情及子资源响应记录 | 可运行，但请求 DTO 与返回模型集中在单文件，未形成独立 VO 层 |
| `entity` | MyBatis Plus 数据表映射 | 4 张核心表已有实体，但与完整 DDL 存在字段缺口 |
| `mapper` | 核心表 CRUD、逻辑删除以及组织/员工引用检查 | 边界基本清晰；`ProjectReferenceMapper`承载跨主数据引用查询 |
| `security` | 统一执行项目级访问策略和组织范围校验 | 是项目资源访问的关键安全边界 |
| `service` | 查询编排、命令处理、DTO组装、引用校验、阶段流转规则 | 已做部分查询/命令拆分，但主实现仍承担项目创建、修改、阶段初始化与进度回算等多项职责 |
| `service.impl` | 生命周期事务编排 | 单一实现类集中处理项目主流程 |
| `config` | 当前不存在 | 模块暂无专属配置需求，不构成缺陷 |
| `vo` | 当前不存在 | 返回对象位于 `ProjectDtos.java`，与既定 DTO/VO 分层规范有偏差 |

## 3. 已实现功能

### 3.1 项目基础信息

- 项目分页查询：支持关键字、状态、阶段、责任组织条件，限制最大分页大小。
- 项目详情：聚合项目、阶段、任务、成员及数量统计。
- 项目新增：校验项目编号唯一性、组织、负责人、日期范围，创建项目后初始化阶段及项目经理成员。
- 项目编辑：使用版本号进行乐观锁更新，负责人变化时同步项目经理成员。
- 项目删除：项目及阶段、任务、成员执行逻辑删除。
- 主数据引用：校验责任组织、负责人、任务负责人和项目成员的有效性及组织关系。

### 3.2 阶段管理

- 新项目固定初始化 8 个阶段：项目储备、项目论证、立项审批、建设实施、项目验收、运营管理、项目后评价、项目归档。
- 支持阶段列表及阶段更新。
- 支持阶段状态流转、前置阶段检查、未完成任务检查。
- `INITIATION`、`ACCEPTANCE` 阶段要求审批通过。
- 根据阶段完成情况回算项目当前阶段、状态、实际开始日期和进度。

### 3.3 任务管理

- 任务分页、创建、编辑、删除。
- 支持阶段归属、父子任务、任务编号、负责人、计划/实际日期、优先级、状态、进度和排序。
- 支持父任务有效性、循环引用与最大层级保护。
- 支持项目内任务编号唯一性和负责人有效性校验。

### 3.4 成员管理

- 成员分页、添加、编辑、移除。
- 支持 `MANAGER`、`CORE`、`PARTICIPANT`、`EXPERT` 成员角色。
- 防止同一员工重复绑定同一成员角色。
- 项目负责人对应的经理成员受保护，并随负责人变更同步。

### 3.5 其他已有能力

- Spring Security `@PreAuthorize` 功能权限校验。
- `@DataScope` 组织/本人数据范围过滤。
- `ProjectAccessPolicy` 项目资源访问校验。
- 逻辑删除、审计字段自动填充、乐观锁。

### 3.6 DDL已有但代码未实现的能力

`05_project.sql` 还定义了阶段模板、里程碑、投资项目扩展、中标项目扩展、机会、投标、变更、风险、成本、收入、利润、验收、后评价和归档等表；当前模块没有对应 Entity、Mapper、Service 或 Controller。

## 4. 数据库映射检查

4 张实体均继承 `BaseEntity`，公共映射字段为：`create_time`、`update_time`、`create_by`、`update_by`、`deleted`、`version`、`remark`。各实体自行声明 `id`。`BaseEntity` 未包含 `delete_token`。

### 4.1 `project_info`

| 分类 | 字段 |
|---|---|
| Entity已映射 | `id`, `project_no`, `project_name`, `project_type`, `project_mode`, `leader_id`, `department_id`, `status`, `start_date`, `end_date`, `actual_start_date`, `actual_end_date`, `budget_amount`, `expected_income`, `expected_profit`, `current_stage_code`, `risk_level`, `progress`, 7个公共字段 |
| 数据库额外字段 | `source_type`, `customer_id`, `contract_amount`, `actual_income`, `actual_profit`, `description`, `delete_token` |
| Entity额外字段 | 无 |

影响：来源、客户、合同金额、实际收入、实际利润和项目说明无法由当前实体读写；`delete_token` 唯一性/软删除令牌机制未进入实体模型。

### 4.2 `project_stage`

| 分类 | 字段 |
|---|---|
| Entity已映射 | 除 `delete_token` 外的全部字段 |
| 数据库额外字段 | `delete_token` |
| Entity额外字段 | 无 |

影响：软删除令牌未进入实体模型。

### 4.3 `project_task`

| 分类 | 字段 |
|---|---|
| Entity已映射 | `id`, `project_id`, `stage_id`, `parent_task_id`, `task_no`, `task_name`, `responsible_person`, `plan_date`, `actual_date`, `progress`, `priority`, `status`, `sort_no`, 7个公共字段 |
| 数据库额外字段 | `task_content`, `plan_start`, `plan_end`, `actual_start`, `actual_end`, `delete_token` |
| Entity额外字段 | 无 |

影响：任务内容及计划/实际起止区间无法由当前实体读写；当前模型仅保留单个计划日期和实际日期。

### 4.4 `project_member`

| 分类 | 字段 |
|---|---|
| Entity已映射 | 除 `delete_token` 外的全部字段 |
| 数据库额外字段 | `delete_token` |
| Entity额外字段 | 无 |

影响：软删除令牌未进入实体模型。

### 4.5 映射结论

- 差异与 `docs/database-mapping-audit.md` 记录的历史问题一致。
- 本次审计未修改实体或数据库。
- 历史聚合脚本 `database/mysql/deprecated/V1.0.0__enterprise_platform_v1.sql` 与模块完整脚本 `05_project.sql` 的项目字段集合并非完全一致；Sprint 2-0.2.2已确认05为唯一结构基线，但生产数据库仍需先执行结构识别再迁移。

## 5. 当前 API 列表

应用统一上下文为 `/api`，下表列出完整访问路径。

| 请求方式 | URL | 功能 | 当前权限 |
|---|---|---|---|
| GET | `/api/projects` | 项目分页查询 | `project:lifecycle:list` |
| GET | `/api/projects/{projectId}` | 项目生命周期详情 | `project:lifecycle:list` |
| POST | `/api/projects` | 新增项目 | `project:add` |
| PUT | `/api/projects/{projectId}` | 修改项目 | `project:edit` |
| DELETE | `/api/projects/{projectId}` | 删除项目 | `project:lifecycle:delete` |
| GET | `/api/projects/{projectId}/stages` | 查询项目阶段 | `project:lifecycle:list` |
| PUT | `/api/projects/{projectId}/stages/{stageId}` | 修改/流转阶段 | `project:edit` |
| GET | `/api/projects/{projectId}/tasks` | 分页查询项目任务 | `project:lifecycle:list` |
| POST | `/api/projects/{projectId}/tasks` | 新增任务 | `project:edit` |
| PUT | `/api/projects/{projectId}/tasks/{taskId}` | 修改任务 | `project:edit` |
| DELETE | `/api/projects/{projectId}/tasks/{taskId}` | 删除任务 | `project:edit` |
| GET | `/api/projects/{projectId}/members` | 分页查询项目成员 | `project:lifecycle:list` |
| POST | `/api/projects/{projectId}/members` | 添加成员 | `project:edit` |
| PUT | `/api/projects/{projectId}/members/{memberId}` | 修改成员 | `project:edit` |
| DELETE | `/api/projects/{projectId}/members/{memberId}` | 移除成员 | `project:edit` |

## 6. 权限审计

### 6.1 查询入口检查

| 查询入口 | `@DataScope` | `ProjectAccessPolicy` | 结论 |
|---|---|---|---|
| 项目分页 | `ProjectLifecycleQueryService.page()`：`orgField=project_info.department_id`，`userField=project_info.create_by` | 同时执行 `applyScope()` | 已覆盖，双重约束 |
| 项目详情 | 访问策略方法本身带 `@DataScope` | `requireAccessible(projectId)` | 已覆盖 |
| 阶段列表 | 由项目访问策略承接 | 查询子表前执行 `requireAccessible(projectId)` | 已覆盖 |
| 任务列表 | 由项目访问策略承接 | 查询子表前执行 `requireAccessible(projectId)` | 已覆盖 |
| 成员列表 | 由项目访问策略承接 | 查询子表前执行 `requireAccessible(projectId)` | 已覆盖 |

阶段、任务、成员不独立按自身组织字段过滤，而是先按 `project_id` 校验所属项目，再查询子资源。这与当前子表没有组织字段的设计一致。

### 6.2 数据范围字段

| 资源 | 组织范围字段 | SELF字段 | 说明 |
|---|---|---|---|
| `project_info` | `department_id` | `create_by` | SELF固定解释为项目创建人，不等同于项目成员 |
| `project_stage` | 继承所属项目 | 继承所属项目 | 通过 `project_id` 调用访问策略 |
| `project_task` | 继承所属项目 | 继承所属项目 | 通过 `project_id` 调用访问策略 |
| `project_member` | 继承所属项目 | 继承所属项目 | 通过 `project_id` 调用访问策略 |

### 6.3 风险接口列表

未发现已暴露且可绕过 `ProjectAccessPolicy` 的查询接口，风险列表为空。

仍需关注以下安全一致性问题：

1. **中风险：权限标识并行。** 初始化基线和 API 文档使用 `project:lifecycle:create`、`project:lifecycle:update`，当前 Controller 使用 `project:add`、`project:edit`。不同环境可能出现“菜单可见但接口403”或兼容权限被过度复用的问题。
2. **中风险：写权限粒度偏粗。** 阶段流转、任务增删改、成员增删改全部复用 `project:edit`，无法落实最小权限原则和高风险操作独立授权。
3. **低风险：安全前置条件依赖调用约定。** 部分内部组件和 Mapper 查询本身不携带项目策略，当前由公开 Service 在调用前保证。若未来被其他 Service 直接复用，可能形成新绕过路径，应由架构测试持续阻断。

## 7. A类、B类业务模型差距

### 7.1 A类：投资项目

当前支持程度：**仅支持项目类型标识和通用生命周期，不支持完整投资项目业务模型。**

已有基础：

- `project_type=01` 可标识投资项目。
- 通用项目、阶段、任务、成员模型可承载部分执行过程。
- DDL已预留 `project_investment_info`，包含投资金额、资金来源、投资比例、合作方、SPV、ROI、IRR、回收期。

主要缺口：

- `project_investment_info` 无实体、Mapper、Service、接口及返回模型。
- 创建项目始终初始化同一套8阶段，不能按投资项目配置储备、论证、决策、实施、投后监管、退出等专属门禁。
- 未接入投资决策、经济测算、被投企业、股权台账、投后指标、收益、风险、退出等投资管理主数据。
- 未实现投资金额、比例、收益率、回收期等领域规则与审批联动。
- 缺少项目主表与投资中心的明确聚合边界和跨模块契约。

### 7.2 B类：中标项目

当前支持程度：**仅支持项目类型标识和通用执行管理，不支持机会—投标—中标—交付—验收—经营闭环。**

已有基础：

- `project_type=02` 可标识中标项目。
- DDL已预留 `project_business_info`、`project_opportunity`、`project_bid`。
- 通用任务、成员、阶段能够承载中标后的部分交付执行。

主要缺口：

- 中标扩展、机会、投标表无对应 Java 代码和 API。
- `project_info` 的 `source_type`、`customer_id`、`contract_amount`、`actual_income`、`actual_profit` 未映射，经营视角关键字段无法进入现有模型。
- 缺少机会转项目、投标结果转项目、客户与合同关联、交付周期、付款方式等流程。
- 未接入合同、收入、成本和利润的跨模块数据，无法形成项目经营分析闭环。
- 固定8阶段无法体现投标前、合同签订、交付、客户验收、回款等B类项目专属门禁。

### 7.3 共性模型问题

- 项目类型目前只是字符串字段，没有类型策略、阶段模板策略或扩展聚合装配机制。
- DDL已有 `project_stage_template`，代码却把阶段模板硬编码在 `ProjectLifecycleServiceImpl`，无法按A/B类型配置生命周期。
- DDL扩展能力与代码实现范围差距较大，不能仅通过给现有 Service 增加条件分支完成，需明确聚合边界和分阶段实现顺序。

## 8. 架构问题

| 级别 | 问题 | 影响 |
|---|---|---|
| 高 | 4张核心实体与完整DDL不一致 | 关键字段静默缺失，后续功能可能误判数据库无此能力 |
| 高 | A/B类型仅有分类字段，无类型化生命周期策略 | 无法满足投资项目和中标项目不同的阶段、审批及经营规则 |
| 中 | 阶段定义硬编码，未使用 `project_stage_template` | 配置能力缺失，新增项目类型需要修改代码 |
| 中 | 权限命名存在 `project:add/edit` 与 `project:lifecycle:create/update` 两套体系 | 角色授权、菜单与接口可能不一致 |
| 中 | `ProjectLifecycleServiceImpl` 同时承担事务编排、阶段初始化、项目修改和进度回算 | 继续扩展后类职责会快速膨胀，测试隔离困难 |
| 中 | 数据库扩展表大量未实现 | DDL能力与可用产品能力不一致 |
| 低 | DTO、VO集中在 `ProjectDtos.java`，无独立VO包 | 模型边界和版本演进不够直观 |
| 低 | 状态、角色、阶段代码主要以字符串和正则维护 | 跨代码、字典和数据库的值域容易漂移 |

## 9. 改造建议

建议在后续专项 Sprint 按以下顺序推进，且每一步先确认生产数据库实际版本：

1. **冻结权威数据库基线。** 核对真实 MySQL 的4张核心表，以迁移脚本统一 `05_project.sql` 与聚合版本脚本；本步骤不得由应用启动时自动改表。
2. **补齐核心实体映射。** 按既有字段补全 Entity、DTO、VO 与映射集成测试，不新增或修改数据库字段；明确 `delete_token` 由统一持久化基类还是模块实体承载。
3. **统一权限命名。** 选定一组标准权限并提供受控兼容迁移，确保菜单、角色初始化、Controller 和文档一致；阶段、任务、成员写操作按风险拆分权限。
4. **配置化阶段模板。** 将硬编码阶段迁移为 `project_stage_template` 驱动，按项目类型选择模板，同时保留已创建项目的阶段快照，避免模板变化影响历史项目。
5. **建立类型扩展策略。** 以通用 `project_info` 为项目聚合根，A类接入 `project_investment_info`，B类接入 `project_business_info`；通过策略/领域服务处理类型差异，避免在单一实现类堆叠条件分支。
6. **按业务闭环逐步交付。** A类优先实现投资扩展与投资中心契约；B类优先实现机会、投标、中标扩展及合同交接。里程碑、变更、风险、成本、收入、验收、评价、归档按独立子域迭代。
7. **固化安全门禁。** 保持所有项目及子资源入口先调用 `ProjectAccessPolicy`；增加 Controller—Service—Policy 架构测试和真实数据库数据范围集成测试，禁止未来直接暴露 Mapper。
8. **拆分事务编排。** 在不改变外部 API 的前提下，将阶段初始化、项目状态回算和项目命令编排拆成高内聚服务；返回模型迁入独立 VO 包。

## 10. 审计结论

现有模块已具备项目、阶段、任务、成员的基础CRUD和通用生命周期流转能力，当前公开查询入口的数据权限与项目访问策略覆盖完整，未发现直接的跨组织查询绕过接口。

进入A类投资项目、B类中标项目开发前，必须先解决数据库权威基线、核心字段映射和权限命名一致性问题。当前实现不能视为已经支持两类项目的完整业务闭环，只能视为可复用的通用生命周期骨架。
