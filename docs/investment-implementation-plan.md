# 投资模块数据库Migration与开发实施规划

> 版本：V1.0
> 对应阶段：Sprint 2-1.6
> 文档性质：实施规划，不修改业务代码、不创建或执行Migration
> 技术基线：Java 21、Spring Boot 3、MyBatis Plus、Spring Security、JWT、Redis、MySQL 8
> 领域基线：投资机会、投资项目、投资论证决策、投资实施投后设计

## 1. 规划目标与落地原则

目标是在不破坏Project底座和历史投资数据的前提下，将Investment模块分批落地为可迁移、可灰度、可回滚、可验收的企业级业务模块。

原则：

1. `project_info` 是通用项目聚合根，`investment_project` 是投资事项权威数据。
2. 所有Investment从属资源先通过 `investment_id → project_id → ProjectAccessPolicy` 校验。
3. 数据库采用“扩展—回填—双读/双写验证—收紧—收敛”，不修改历史SQL。
4. Migration、业务代码和生命周期模板分开发布，避免一个失败点回滚全部能力。
5. 每个开发批次必须形成端到端闭环，不交付只能写入、不能查询或无法审计的半成品。
6. 决策、支付、股权、收益和退出采用不可变事实或追加更正，不允许无审计覆盖。
7. MySQL 8是首发运行库，DDL和SQL同时评审达梦、人大金仓兼容性。
8. 本文只规划V2.4候选链和开发路线，不生成SQL、不执行数据库变更、不修改业务代码。

### 1.1 版本规划收敛

前序设计文档曾预留V2.2、V2.3编号，但仓库中真实Migration最高仅为V2.1.3，且V2.2/V2.3脚本从未创建或执行。本Sprint按用户要求将所有未落地设计收敛为唯一正式候选链V2.4：

- V2.2/V2.3仅作为历史设计草案，不再生成同名脚本；
- V2.4成为Investment模块首次可执行Migration候选链；
- 文档评审通过后再创建脚本，创建前必须重新检查仓库与目标数据库版本占用；
- 若其他分支已占用V2.4版本，必须整体顺延，禁止重命名已执行脚本。

## 2. V2.4 Migration版本链

### 2.1 总体执行链

```mermaid
flowchart LR
    A["V2.4.0 基线门禁"] --> B["V2.4.1 机会与投资事项扩展"]
    B --> C["V2.4.2 可研版本"]
    C --> D["V2.4.3 尽调模型"]
    D --> E["V2.4.4 投资方案"]
    E --> F["V2.4.5 决策闭环"]
    F --> G["V2.4.6 投资实施"]
    G --> H["V2.4.7 投后与现金流"]
    H --> I["V2.4.8 股权变化与退出"]
    I --> J["V2.4.9 历史回填"]
    J --> K["V2.4.10 约束收紧"]
    K --> L["V2.4.11 字典菜单权限"]
    L --> M["V2.4.12 生命周期模板新版本"]
```

### 2.2 版本明细

| 版本 | 建议文件 | 主要内容 | 前置依赖 | 发布性质 |
|---|---|---|---|---|
| V2.4.0 | `V2.4.0__verify_investment_baseline.sql` | 校验05/07结构、版本、必需表、重复业务键；不修改数据 | V2.1.3 | 阻断式前置检查 |
| V2.4.1 | `V2.4.1__create_investment_opportunity_and_extend_project.sql` | 机会、责任人、转化引用；扩展投资事项方式/合作模式等 | V2.4.0 | 扩展DDL |
| V2.4.2 | `V2.4.2__create_investment_feasibility_version.sql` | 可研档案头演进、版本、核心指标和附件引用 | V2.4.1 | 扩展DDL |
| V2.4.3 | `V2.4.3__create_investment_due_diligence.sql` | 尽调包、四类报告版本、问题整改 | V2.4.2 | 扩展DDL |
| V2.4.4 | `V2.4.4__create_investment_scheme.sql` | `investment_scheme`、版本、资金来源 | V2.4.2、V2.4.3 | 扩展DDL |
| V2.4.5 | `V2.4.5__upgrade_investment_decision.sql` | 决策事项、节点、三重一大、党委前置研究、条件任务和动作历史 | V2.4.4 | 扩展DDL |
| V2.4.6 | `V2.4.6__create_investment_implementation.sql` | 实施头、合同关系、出资计划、实缴扩展、实施节点 | V2.4.5 | 扩展DDL |
| V2.4.7 | `V2.4.7__create_investment_post_management.sql` | 周期报告、指标版本、收益扩展、现金流、回报评价 | V2.4.6 | 扩展DDL |
| V2.4.8 | `V2.4.8__create_equity_change_and_exit.sql` | 被投企业/股权兼容扩展、股权变化、退出方案版本、执行与清算 | V2.4.7 | 扩展DDL |
| V2.4.9 | `V2.4.9__backfill_investment_legacy_data.sql` | 回填历史可研、决策、支付、股权、收益和退出映射 | V2.4.1—8 | 数据迁移 |
| V2.4.10 | `V2.4.10__enforce_investment_constraints.sql` | 非空、唯一、外键、索引与指针一致性收紧 | V2.4.9验收通过 | 约束收紧 |
| V2.4.11 | `V2.4.11__seed_investment_rbac_and_dictionary.sql` | 字典、菜单、按钮权限；幂等补充，不覆盖角色自定义授权 | V2.4.10 | 初始化DML |
| V2.4.12 | `V2.4.12__publish_investment_lifecycle_template.sql` | 发布新投资模板版本及条件，不修改既有ACTIVE版本和历史快照 | V2.4.10、11 | 模板DML |

### 2.3 V2.4.0基线门禁

V2.4.0必须在任何DDL前失败关闭，检查：

- 数据库为 `enterprise_platform` 且字符集、时区符合基线；
- 真实版本链包含V2.0.0及V2.1.0—V2.1.3；
- `project_info`、`investment_project`、既有投资表结构可识别；
- 不存在同名V2.4目标表、半创建对象或冲突列；
- `investment_plan` 仍为年度投资计划，不被误用为项目投资方案；
- 投资编号重复、Project一对多投资事项、孤儿外键、异常逻辑删除记录形成报告；
- 历史脚本校验和未被修改。

当前仓库未发现Flyway/Liquibase依赖。实施前必须确定受控执行器和版本元数据表；无论采用流水线、DBA工具或后续引入Flyway，均应保证脚本只执行一次、保存校验和和审计记录。

### 2.4 依赖分层

```text
系统基础层：sys_user / sys_org / sys_menu / sys_file
    ↓
Project层：project_info + Lifecycle V2
    ↓
Investment核心：investment_project + opportunity
    ↓
论证层：feasibility → due diligence → scheme
    ↓
决策层：decision case → nodes → conditions
    ↓
实施层：contracts → contribution plan → payment → milestone
    ↓
投后层：company/equity → reports/indicators → income/cash flow/risk
    ↓
退出层：exit plan → approval → execution → settlement
```

跨模块外键必须由双方评审；Operation、财务、审批、文件中心尚未形成稳定物理模型时，先保存经过校验的稳定引用，不创建指向不存在或不稳定对象的外键。

### 2.5 单版本发布门禁

每个Migration必须附带：

1. 目标、前置版本、受影响表和数据量估算；
2. MySQL 8执行计划及锁表风险；
3. 达梦、人大金仓语法差异说明；
4. 空库、标准存量库、异常存量库测试结果；
5. 升级前后记录数、金额汇总、业务唯一键和孤儿检查；
6. 回滚触发条件、最晚回滚点和操作步骤；
7. 负责人、审批记录、执行时间和校验和。

### 2.6 回滚策略

| 阶段 | 可回滚方式 | 禁止事项 |
|---|---|---|
| V2.4.0 | 只读检查，无回滚 | 不允许忽略失败继续执行 |
| V2.4.1—8未启用写入 | 受控删除新增对象或反向RENAME | 不删除既有历史表 |
| V2.4.1—8已启用写入 | 关闭功能开关，保留新表，前向修复 | 不直接DROP已写业务数据 |
| V2.4.9回填 | 依据备份和映射表逆向恢复 | 不按名称/日期猜测历史映射 |
| V2.4.10收紧 | 删除新增约束或索引，保留数据 | 不回滚有效回填数据 |
| V2.4.11初始化 | 删除本版本新增且未被使用的数据，或停用菜单 | 不覆盖用户已有角色授权 |
| V2.4.12模板 | 停用新模板版本；新建项目切回指定ACTIVE版本 | 不修改已生成项目的条件快照 |

生产升级前必须全量备份并完成恢复演练。扩展阶段至少保留旧读路径一个观察期；清理旧列、旧表和兼容视图不纳入V2.4首发链，另开后续Major/Minor版本。

### 2.7 发布批次与Migration对应

Migration可以先完成扩展DDL，但业务功能按批次启用：

| 开发批次 | 必需Migration | 生命周期模板启用 |
|---|---|---|
| 第一批：投资决策闭环 | V2.4.0—5、9—11相关回填/权限 | 仅启用机会、可研、尽调、决策条件的新模板版本 |
| 第二批：投资实施 | V2.4.6、9—11相关部分 | 启用IMPLEMENTATION门禁 |
| 第三批：投后管理 | V2.4.7、9—11相关部分 | 启用POST_INVESTMENT持续条件 |
| 第四批：股权与退出 | V2.4.8、9—12完整链 | 启用终局EXIT门禁 |

若V2.4.12一次发布全部阶段条件，则未上线能力会阻断业务。因此建议模板版本按功能批次分版本发布，V2.4.12只建立首批可用版本，后续条件用更高Migration版本发布。

## 3. 开发批次划分

### 3.1 第一批：投资决策闭环

范围：

- 机会登记、初筛、分析、评估、审批和原子化转Project；
- 投资事项查询和基础信息；
- 可研档案及不可变版本；
- 四类尽调、问题整改、复核和阻断；
- 投资方案版本、资金来源和冻结；
- 决策材料、路线、三重一大、党委前置研究、董事会/经理层节点；
- 附条件批准、整改、复核及决策完成；
- Lifecycle V2进入和完成FEASIBILITY、DUE_DILIGENCE、DECISION。

明确不含：支付、实缴、投后指标、收益、股权变化和退出执行。

入口条件：V2.4.0—5结构评审通过，审批/文件/Project端口契约稳定。
完成标准：机会可安全转Project，冻结方案可完成完整决策，附加条件关闭后可进入IMPLEMENTATION；权限、数据范围、审计和历史兼容全部通过。

### 3.2 第二批：投资实施

范围：

- 投资合同稳定引用与状态核验；
- 出资计划和支付先决条件；
- 实缴申请、审批、执行、财务凭证核验和冲正；
- SPV/被投企业设立信息；
- 股权首次登记、治理席位和实施节点；
- Lifecycle V2 IMPLEMENTATION进入/完成条件。

入口条件：第一批生产稳定，V2.4.6验证通过，财务/资金及Operation接口明确。
完成标准：从决策完成到实施验收全链闭环，累计实缴不超额度，重复回调幂等，账户敏感数据受控，具备进入POST_INVESTMENT的权威事实。

### 3.3 第三批：投后管理

范围：

- 周期报告和数据截止版本；
- 经营、财务、投资、治理和风险指标；
- 投资收益、分红、应收/到账和财务凭证核验；
- 现金流台账与ROI、IRR、MOIC、DPI评价；
- 投资风险、升级、整改、接受和关闭；
- 董监高、治理会议和股权一致性监控；
- Lifecycle V2 POST_INVESTMENT持续合规条件。

入口条件：第二批实施事实稳定，V2.4.7验证通过，财务指标口径和报告模板获确认。
完成标准：投后报告可复算、收益与现金流不混淆、风险可闭环、逾期可预警、数据权限和敏感字段验收通过。

### 3.4 第四批：股权变化与退出

范围：

- 增资、减资、转入、部分转让和稀释事件；
- 有效期股权台账和历史快照；
- 退出触发、方案版本、审批路线、实施节点和清算；
- 退出价款、收益确认、剩余义务、档案和终局关闭；
- Lifecycle V2终局EXIT阶段。

入口条件：V2.4.8及历史股权回填验收通过，产权交易/监管流程明确。
完成标准：部分退出继续投后，终局退出才关闭项目；资金、权属、收益、风险和档案全部一致。

### 3.5 每批交付顺序

每个批次严格按照：

```text
数据库详细设计与Migration评审
  → API契约与错误码
  → Domain + Application
  → Infrastructure
  → Controller
  → 前端页面与权限
  → 自动化测试
  → 真实MySQL验收
  → 灰度与监控
```

禁止先开发Controller/页面再补领域状态机和数据库约束。

## 4. 后端模块结构

### 4.1 目标目录

```text
backend/src/main/java/cn/gov/enterprise/modules/investment
├─ domain
│  ├─ opportunity
│  │  ├─ model
│  │  ├─ repository
│  │  ├─ policy
│  │  └─ event
│  ├─ feasibility
│  ├─ duediligence
│  ├─ scheme
│  ├─ decision
│  ├─ implementation
│  ├─ postmanagement
│  ├─ income
│  ├─ equity
│  └─ exit
├─ application
│  ├─ command
│  ├─ query
│  ├─ service
│  ├─ dto
│  ├─ assembler
│  └─ port
├─ infrastructure
│  ├─ persistence
│  │  ├─ entity
│  │  ├─ mapper
│  │  ├─ repository
│  │  └─ converter
│  ├─ integration
│  │  ├─ project
│  │  ├─ workflow
│  │  ├─ operation
│  │  ├─ finance
│  │  ├─ file
│  │  └─ risk
│  ├─ event
│  ├─ cache
│  └─ config
├─ controller
│  ├─ opportunity
│  ├─ argumentation
│  ├─ decision
│  ├─ implementation
│  ├─ postmanagement
│  └─ exit
└─ support
   ├─ security
   ├─ lifecycle
   └─ idempotency
```

该结构沿用现有Project的渐进式分层，但Investment从一开始禁止把新逻辑放回根级 `service/mapper/entity`。MyBatis实体只能位于Infrastructure，Domain对象不得依赖MyBatis Plus注解。

### 4.2 分层职责

| 层 | 职责 | 禁止 |
|---|---|---|
| Domain | 聚合、值对象、状态机、不变量、Repository端口、领域事件 | 依赖Controller、Mapper、Spring Web、MyBatis实体 |
| Application | 用例编排、事务边界、权限入口、幂等、DTO/VO组装、跨域端口调用 | 写原生SQL、绕过聚合改状态 |
| Infrastructure | MyBatis实体/Mapper/Repository实现、外部系统适配、Outbox、缓存 | 向Controller暴露Mapper、实现业务审批规则 |
| Controller | 参数校验、鉴权声明、调用Application、返回ApiResponse | 业务逻辑、直接访问Repository/Mapper、拼接数据权限SQL |
| Support | Investment统一安全、生命周期事实、编号、幂等支持 | 形成隐藏业务旁路 |

### 4.3 聚合与Repository

建议Repository端口：

```text
InvestmentOpportunityRepository
InvestmentProjectRepository
FeasibilityRepository
DueDiligenceRepository
InvestmentSchemeRepository
InvestmentDecisionRepository
InvestmentImplementationRepository
PostInvestmentReportRepository
InvestmentIncomeRepository
InvestmentCashFlowRepository
InvestmentRiskRepository
InvesteeCompanyRepository
EquityHoldingRepository
InvestmentExitRepository
```

Repository按聚合读写，不为每张表机械暴露通用CRUD。分页查询使用专门QueryService/ReadMapper，命令侧仍通过聚合Repository。

### 4.4 Application服务

| 服务 | 核心用例 |
|---|---|
| `OpportunityApplicationService` | 登记、研判、审批、转Project |
| `ArgumentationApplicationService` | 可研、尽调、方案版本与冻结 |
| `DecisionApplicationService` | 组卷、路线、节点、附条件和完成 |
| `ImplementationApplicationService` | 合同、出资计划、实缴、SPV和节点 |
| `PostManagementApplicationService` | 报告、指标、风险和治理监控 |
| `InvestmentIncomeApplicationService` | 收益、分红、现金流和回报评价 |
| `EquityApplicationService` | 股权变化与有效期台账 |
| `ExitApplicationService` | 退出方案、审批、执行、清算和关闭 |

跨聚合长流程不使用一个超大本地事务。单库内保持聚合一致性；跨审批、财务、Operation、文件系统通过Outbox、幂等回调和补偿任务实现最终一致。

### 4.5 安全入口

统一 `InvestmentAccessPolicy`：

```text
requireInvestmentAccessible(investmentId)
  → load investment_project.project_id
  → ProjectAccessPolicy.requireAccessible(projectId)
  → check feature authority
  → check sensitive-data authority when required
```

机会尚未创建Project，按 `proposing_org_id` 和参与人执行统一数据权限。转化后必须切换到Project访问策略，机会参与关系不自动授予项目访问权。

## 5. 接口规划

### 5.1 API原则

- 沿用现有项目无全局版本前缀的约定；如未来统一 `/api/v1`，应全平台一次治理，不只修改Investment。
- 资源集合使用复数名词；状态动作采用明确子资源或命令端点，不提供通用 `PUT status`。
- 所有写接口支持乐观锁 `version`；关键命令支持 `Idempotency-Key`。
- 列表统一分页、排序白名单和数据权限；详情统一执行Investment/Project访问策略。
- 响应继续使用现有 `ApiResponse`，保留 `traceId` 和 `timestamp`。

### 5.2 投资机会接口

| 方法 | 路径 | 用途 | 权限 |
|---|---|---|---|
| GET | `/investment-opportunities` | 分页查询机会 | `investment:opportunity:view` |
| GET | `/investment-opportunities/{id}` | 机会详情与当前研判 | `investment:opportunity:view` |
| POST | `/investment-opportunities` | 新建草稿 | `investment:opportunity:create` |
| PUT | `/investment-opportunities/{id}` | 修改草稿/退回记录 | `investment:opportunity:update` |
| POST | `/investment-opportunities/{id}/submissions` | 提交登记 | `investment:opportunity:submit` |
| POST | `/investment-opportunities/{id}/screenings` | 完成初筛 | `investment:opportunity:screen` |
| POST | `/investment-opportunities/{id}/analyses` | 提交业务分析 | `investment:opportunity:analyze` |
| POST | `/investment-opportunities/{id}/evaluations` | 完成综合评估 | `investment:opportunity:evaluate` |
| POST | `/investment-opportunities/{id}/conversion-approvals` | 提交/处理转化审批 | `investment:opportunity:approve` |
| POST | `/investment-opportunities/{id}/conversions` | 原子化转Project | `investment:opportunity:convert` |
| POST | `/investment-opportunities/{id}/closures` | 关闭机会 | `investment:opportunity:close` |
| GET | `/investment-opportunities/{id}/timeline` | 研判审计时间线 | `investment:opportunity:audit:view` |

### 5.3 投资事项接口

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/investments` | 投资事项分页 |
| GET | `/investments/{investmentId}` | 投资事项详情 |
| GET | `/investments/{investmentId}/summary` | 生命周期与各域摘要 |
| GET | `/investments/{investmentId}/project` | 关联Project摘要 |

投资事项原则上由机会转化创建，不提供绕过机会和审批的通用新增接口；确需历史补录时使用专门的受控迁入用例和权限。

### 5.4 可研接口

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/investments/{id}/feasibility` | 档案与当前冻结版本 |
| GET | `/investments/{id}/feasibility/versions` | 版本列表 |
| POST | `/investments/{id}/feasibility/versions` | 创建草稿版本 |
| PUT | `/investments/{id}/feasibility/versions/{versionId}` | 修改草稿 |
| POST | `/investments/{id}/feasibility/versions/{versionId}/submissions` | 提交审核 |
| POST | `/investments/{id}/feasibility/versions/{versionId}/reviews` | 审核/退回 |
| POST | `/investments/{id}/feasibility/versions/{versionId}/freezes` | 批准并冻结 |

### 5.5 尽调接口

| 方法 | 路径 | 用途 |
|---|---|---|
| GET/POST | `/investments/{id}/due-diligence-packages` | 包列表/创建尽调包 |
| GET | `/investments/{id}/due-diligence-packages/{packageId}` | 包详情 |
| POST | `/investments/{id}/due-diligence-packages/{packageId}/reports` | 创建专业报告版本 |
| PUT | `/investments/{id}/due-diligence/reports/{reportId}` | 修改草稿报告 |
| POST | `/investments/{id}/due-diligence/reports/{reportId}/submissions` | 提交报告 |
| POST | `/investments/{id}/due-diligence/reports/{reportId}/freezes` | 审核并冻结报告 |
| POST | `/investments/{id}/due-diligence/reports/{reportId}/findings` | 新增问题 |
| PUT | `/investments/{id}/due-diligence/findings/{findingId}` | 提交整改 |
| POST | `/investments/{id}/due-diligence/findings/{findingId}/reviews` | 复核关闭/退回 |
| POST | `/investments/{id}/due-diligence-packages/{packageId}/reviews` | 形成尽调包结论 |

### 5.6 投资方案接口

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/investments/{id}/schemes` | 方案版本列表 |
| POST | `/investments/{id}/schemes` | 创建方案版本 |
| GET/PUT | `/investments/{id}/schemes/{schemeVersionId}` | 查看/修改草稿 |
| POST | `/investments/{id}/schemes/{schemeVersionId}/reviews` | 提交/处理内部评审 |
| POST | `/investments/{id}/schemes/{schemeVersionId}/freezes` | 冻结方案 |
| POST | `/investments/{id}/schemes/{schemeVersionId}/material-change-assessments` | 实质变更判断 |

### 5.7 投资决策接口

| 方法 | 路径 | 用途 |
|---|---|---|
| GET/POST | `/investments/{id}/decisions` | 决策事项列表/组卷创建 |
| GET | `/investments/{id}/decisions/{decisionId}` | 决策包、路线和结果 |
| POST | `/investments/{id}/decisions/{decisionId}/route-calculations` | 计算并冻结路线 |
| POST | `/investments/{id}/decisions/{decisionId}/submissions` | 提交决策流程 |
| POST | `/investments/{id}/decisions/{decisionId}/nodes/{nodeId}/results` | 记录受权节点结果 |
| POST | `/investments/{id}/decisions/{decisionId}/conditions` | 创建附加条件 |
| PUT | `/investments/{id}/decisions/{decisionId}/conditions/{conditionId}` | 提交整改 |
| POST | `/investments/{id}/decisions/{decisionId}/conditions/{conditionId}/reviews` | 复核/退回/关闭 |
| POST | `/investments/{id}/decisions/{decisionId}/finalizations` | 计算整体结果 |

审批系统回调使用内部受信路径，与用户接口分离，必须验签、校验业务绑定和幂等键。

### 5.8 投资实施接口

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/investments/{id}/implementation` | 实施总览 |
| GET/POST | `/investments/{id}/contracts` | 合同引用列表/关联 |
| GET/POST | `/investments/{id}/contribution-plans` | 出资计划列表/新增期次 |
| PUT | `/investments/{id}/contribution-plans/{planId}` | 修改未执行计划 |
| POST | `/investments/{id}/contribution-plans/{planId}/approvals` | 提交/处理计划审批 |
| GET/POST | `/investments/{id}/contributions` | 实缴列表/发起实缴 |
| POST | `/investments/{id}/contributions/{paymentId}/verifications` | 财务核验 |
| POST | `/investments/{id}/contributions/{paymentId}/reversals` | 冲正 |
| GET/POST | `/investments/{id}/milestones` | 实施节点列表/记录事实 |
| POST | `/investments/{id}/implementation/acceptances` | 实施验收 |

### 5.9 投后接口

| 方法 | 路径 | 用途 |
|---|---|---|
| GET/POST | `/investments/{id}/post-reports` | 周期报告列表/创建 |
| GET/PUT | `/investments/{id}/post-reports/{reportId}` | 查看/维护报告 |
| POST | `/investments/{id}/post-reports/{reportId}/confirmations` | 确认报告 |
| GET/POST | `/investments/{id}/monitor-indicators` | 指标查询/配置 |
| POST | `/investments/{id}/monitor-values` | 采集指标值 |
| GET/POST | `/investments/{id}/risks` | 风险列表/登记 |
| POST | `/investments/{id}/risks/{riskId}/actions` | 整改、升级、关闭 |
| GET/POST | `/investments/{id}/incomes` | 收益列表/登记 |
| POST | `/investments/{id}/incomes/{incomeId}/verifications` | 到账与凭证核验 |
| GET | `/investments/{id}/cash-flows` | 现金流查询 |
| POST | `/investments/{id}/return-evaluations` | 重算并保存回报评价 |

### 5.10 股权与退出接口

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/investee-companies/{companyId}` | 被投企业详情 |
| GET | `/investee-companies/{companyId}/equity-holdings` | 当前和历史持股 |
| GET/POST | `/investee-companies/{companyId}/equity-changes` | 变化列表/发起变化 |
| POST | `/investee-companies/{companyId}/equity-changes/{changeId}/registrations` | 登记并切换有效期台账 |
| GET/POST | `/investments/{id}/exit-cases` | 退出列表/创建退出事项 |
| GET | `/investments/{id}/exit-cases/{exitId}` | 退出详情 |
| POST | `/investments/{id}/exit-cases/{exitId}/plans` | 创建退出方案版本 |
| POST | `/investments/{id}/exit-cases/{exitId}/approvals` | 提交/处理退出审批 |
| POST | `/investments/{id}/exit-cases/{exitId}/milestones` | 记录退出执行事实 |
| POST | `/investments/{id}/exit-cases/{exitId}/settlements` | 退出清算与收益核对 |
| POST | `/investments/{id}/exit-cases/{exitId}/completions` | 完成部分/终局退出 |

## 6. 测试策略

### 6.1 测试金字塔与门禁

| 层级 | 目标 | 执行时机 |
|---|---|---|
| Domain单元测试 | 状态机、不变量、策略、计算器 | 每次提交 |
| Application单元测试 | 编排、权限入口、幂等和事务行为 | 每次提交 |
| Repository集成测试 | MyBatis映射、SQL、乐观锁和逻辑删除 | PR流水线 |
| API集成测试 | 校验、鉴权、错误码和ApiResponse | PR流水线 |
| Migration测试 | 空库、存量库、回填、约束和回滚 | Migration变更时 |
| 端到端测试 | 四批核心业务闭环 | 合并develop及发布候选 |
| 国产库兼容测试 | 达梦/人大金仓DDL、分页、类型和索引 | 版本候选 |
| 安全/性能测试 | 越权、注入、敏感数据、并发和容量 | 版本候选 |

### 6.2 单元测试

必须覆盖：

- Opportunity全部合法/非法状态流转和转化前置条件；
- 可研、尽调、方案冻结后的不可变性；
- 四类尽调完整性、BLOCKING问题和风险接受；
- 决策路线、党委前置研究不替代法定决策、附条件关闭；
- 出资额度、分期、非现金出资和冲正；
- 收益与现金流分类、ROI/IRR/MOIC/DPI计算；
- 股权有效期、比例合计、增资/减资/转让；
- 部分退出与终局退出差异；
- Lifecycle事实端口的允许、阻断和超时关闭行为。

Domain测试不启动Spring、不连接数据库，保证快速和确定性。

### 6.3 集成测试

计划引入MySQL 8 Testcontainers或等价隔离数据库；当前POM未包含Testcontainers，增加依赖需在实际开发批次单独评审。

覆盖：

- Entity/TableField与真实字段一致；
- Mapper分页、排序白名单、唯一键、逻辑删除和乐观锁；
- Repository聚合装载与保存；
- 外键、金额精度、时间精度和内容哈希；
- Outbox写入与业务事务原子性；
- Redis幂等键、缓存失效和不可用降级；
- 审批、文件、Operation、财务和Risk适配器契约测试。

禁止使用H2替代MySQL验证关键SQL，因为MySQL、达梦和人大金仓在类型、索引和语法上存在差异。

### 6.4 Migration测试

每个V2.4脚本至少验证：

1. 从05/07标准基线空业务库顺序升级；
2. 带历史可研、决策、支付、股权、收益和退出数据升级；
3. 异常重复、孤儿、空引用时前置检查正确阻断；
4. 重复执行由版本工具拒绝或保持幂等；
5. 回填前后记录数和金额汇总一致；
6. V2.4.10约束收紧前所有阻断项归零；
7. 旧读路径在观察期仍可用；
8. 回滚/停用演练恢复到可服务状态；
9. 脚本校验和与已执行记录一致；
10. MySQL、达梦、人大金仓适配脚本达到等价结果。

### 6.5 权限测试

每个接口验证：

- 未登录返回401；
- 有菜单无按钮权限返回403；
- 有查看权限不能执行编制、审核、审批或冻结；
- 编制人不能审批本人版本；
- 党委记录人员不能代替董事会/经理层决策；
- 支付计划、执行和核验职责分离；
- 报告填报人与确认人分离；
- 普通项目查看用户不能查看账户、估值、尽调底稿和敏感决策附件；
- 系统管理员技术角色不自动获得业务审批权；
- 审批委托过期、越权代办和重复回调被拒绝并审计。

### 6.6 数据权限测试

对机会和转化后投资资源分别验证：

| 范围 | 机会 | 投资事项及从属资源 |
|---|---|---|
| ALL | 全部机会 | 全部Project范围内投资事项 |
| ORG | `proposing_org_id=currentOrg` | Project责任组织=currentOrg |
| ORG_AND_CHILDREN | 本组织及下级提出机会 | 本组织及下级Project |
| SELF | 本人提出或显式参与 | 仅Project创建人语义，不自动解释为投资参与人 |
| CUSTOM | 配置组织范围机会 | 配置组织范围Project |

必须验证从属表不能绕过主表授权、ID枚举攻击、批量导出、文件下载和统计接口不会扩大数据范围。CUSTOM空配置必须拒绝而不是退化为ALL。

### 6.7 事务与幂等测试

关键故障注入场景：

1. 机会转化在Project、InvestmentProject或生命周期实例任一步失败，全部回滚；
2. 可研/方案冻结时附件哈希或审批引用失败，不产生半冻结版本；
3. 决策节点重复回调，只记录一次结果和一次领域事件；
4. 附条件关闭与生命周期推进并发，使用版本和事实时间防止TOCTOU；
5. 实缴记录成功但Outbox失败时业务事务整体回滚；
6. 财务回调重复、乱序、延迟或签名错误时不重复入账；
7. 股权登记在关闭旧台账后创建新台账失败，整个事务回滚；
8. 收益核验与现金流生成保持原子性；
9. 退出清算成功但股权变化失败时不得标记COMPLETED；
10. Redis不可用时安全操作失败关闭或使用数据库幂等，不得放弃幂等校验。

### 6.8 API与前端测试

- Controller参数、Bean Validation、HTTP方法和统一异常返回；
- OpenAPI契约、DTO字段、金额精度和日期格式；
- 前端路由和菜单按权限生成；
- `v-permission`隐藏按钮，同时直接请求仍被后端拒绝；
- 状态对应按钮矩阵、重复提交防护和冲突提示；
- 附件上传/下载权限、超限、恶意文件名和审计；
- 长列表分页、筛选、导出和空状态；
- 审批时间线、版本对比和错误恢复。

### 6.9 性能与容量测试

建议基线场景：

- 投资事项10万、周期指标1000万、现金流1000万、审计日志更大量级；
- 常用分页P95不高于目标SLA，查询计划使用预期索引；
- 禁止N+1加载版本、节点、指标和股权历史；
- 批量指标导入分批事务并提供失败明细；
- 回报重算、报表汇总和历史导出异步化；
- V2.4.9大表回填采用分批、断点和限速策略，评估锁与日志空间。

具体SLA和数据量需由业务及运维确认后冻结。

## 7. 发布、灰度与监控

### 7.1 发布顺序

```text
备份与恢复演练
  → Migration前置检查
  → 扩展DDL
  → 新后端（功能开关关闭）
  → 回填与差异报告
  → 约束收紧
  → 权限/字典初始化
  → 灰度用户启用
  → 双读差异观测
  → 分批扩大
  → 生命周期新模板启用
```

### 7.2 功能开关

建议按能力开关：

```text
investment.opportunity.enabled
investment.argumentation.enabled
investment.decision.enabled
investment.implementation.enabled
investment.postManagement.enabled
investment.exit.enabled
investment.lifecycleFacts.enabled
```

功能开关不能绕过权限、状态机或数据库约束，只用于发布流量控制和安全停用。

### 7.3 监控指标

- API成功率、延迟、4xx/5xx、慢查询；
- 状态机冲突和乐观锁失败；
- 审批/财务回调失败、重复和积压；
- Outbox未投递数量与最老事件年龄；
- 周期报告逾期、指标缺失、CRITICAL风险；
- 支付/现金流/股权/收益对账差异；
- 生命周期条件失败、超时和人工豁免；
- 敏感文件下载、权限拒绝和异常导出。

## 8. 交付物与代码审查门禁

每个批次必须交付：

- 数据库设计、Migration及回滚说明；
- Domain、Application、Infrastructure、Controller代码；
- DTO/VO、错误码和OpenAPI文档；
- 前端API、页面、权限和状态交互；
- 单元、集成、权限、数据权限、事务和Migration测试；
- 数据回填与差异报告；
- 监控、告警、运维和应急说明。

以下为代码审查阻断项：

1. Controller直连Mapper或Repository；
2. Domain依赖MyBatis/Spring Web；
3. 投资从属资源绕过ProjectAccessPolicy；
4. 查询Service漏接数据权限；
5. 通用状态修改接口绕过聚合命令；
6. 冻结版本或正式决策被原地覆盖；
7. 支付、回调、股权和退出无幂等；
8. 敏感账户、Token、报告正文进入日志；
9. 修改历史Migration或无回滚/回填验证；
10. 新生命周期条件修改历史项目快照。

## 9. 风险与待确认事项

| 风险/问题 | 影响 | 处理建议 |
|---|---|---|
| V2.2/V2.3仅是设计预留 | 版本认知混乱 | 以本文件V2.4链为唯一候选，文档标记前序规划已收敛 |
| 未引入Migration执行器 | 无自动校验和版本锁 | 先评审Flyway或受控DBA流水线，不手工散执行 |
| `investment_plan`命名冲突 | 年度计划与项目方案混淆 | 保留年度表，项目方案使用`investment_scheme` |
| 人员ID口径未冻结 | 外键和权限错误 | 冻结sys_user/hr_employee使用边界后再建约束 |
| 外部审批/财务接口未定 | 决策和支付无法闭环 | 先定义端口与契约测试，使用Mock不伪造生产事实 |
| 历史投资数据质量未知 | 回填与收紧失败 | V2.4.0只读审计并形成业务确认清单 |
| 国产数据库差异 | 上线迁移风险 | 每个脚本提供适配版与等价验收 |
| 生命周期条件一次全开 | 未上线能力阻断项目 | 按开发批次发布新模板版本 |
| 投后数据量增长快 | 查询和存储压力 | 期间索引、归档、异步汇总和容量测试 |

实施前仍需确认：

1. V2.4执行器、版本元数据表和校验和管理方式；
2. Project/Investment一对一规则及历史冲突处理；
3. 人员、企业、合作方和合同主数据引用口径；
4. 审批、三重一大、会议、财务、资金和文件接口；
5. 投资决策权限矩阵、职责分离和委托规则；
6. 财务收益、现金流和回报计算口径；
7. 部分退出、终局退出及剩余应收允许规则；
8. 每批上线SLA、灰度组织、数据规模和回滚窗口。

上述事项未确认前，不得将临时值固化到Migration、Controller、权限种子或生命周期模板。
