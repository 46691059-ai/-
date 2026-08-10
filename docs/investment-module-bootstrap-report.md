# Investment模块基础骨架建设报告

## 1. 建设结论

Sprint 2-2 已完成 Investment 模块基础骨架建设。

- 建立 `modules.investment` 的 domain、application、infrastructure、controller 边界；
- 建立4个框架无关领域对象；
- 建立2个Repository端口，暂不提供持久化实现；
- 建立5项统一RBAC权限常量；
- 通过V2.2.0 Migration补充权限、菜单及超级管理员初始授权；
- 新增领域、权限和Spring上下文测试；
- Java 21完整后端测试125项全部通过；
- V2.2.0在真实MySQL 8.4.9隔离库中执行、校验及幂等复跑通过；
- 未实现投资业务接口、完整流程、Mapper、Entity或前端业务页面。

## 2. 模块结构

```text
modules/investment
├── domain
│   ├── model
│   │   ├── InvestmentProject.java
│   │   ├── InvestmentOpportunity.java
│   │   ├── InvestmentFeasibility.java
│   │   └── InvestmentDecision.java
│   └── repository
│       ├── InvestmentProjectRepository.java
│       └── InvestmentOpportunityRepository.java
├── application
│   ├── package-info.java
│   └── security
│       └── InvestmentPermissions.java
├── infrastructure
│   └── package-info.java
└── controller
    └── package-info.java
```

分层职责：

| 分层 | 当前职责 | 本阶段限制 |
| --- | --- | --- |
| domain | 领域状态、不变量、Repository端口 | 不依赖Spring、MyBatis、Entity或其他模块实现 |
| application | 权限契约与未来用例编排位置 | 暂无业务ApplicationService |
| infrastructure | 未来Mapper、Entity、Repository适配器位置 | 暂无持久化实现和Spring Bean |
| controller | 未来REST适配器位置 | 暂无Controller和HTTP接口 |

Investment与Project之间只保存 `projectId` 标识，不直接引用 `ProjectAggregate`，避免跨聚合对象耦合。

## 3. 领域对象

### 3.1 InvestmentProject

投资事项聚合边界，包含：

- Project聚合标识；
- 投资编号、名称、类型；
- 投资金额、投资比例；
- 责任组织、业务负责人；
- DRAFT至CLOSED的基础状态集合。

当前只负责数据不变量，不实现投资决策、实施或投后流转。

### 3.2 InvestmentOpportunity

投资机会对象，包含：

- 政府项目、企业合作、市场发现、集团下达、自主策划五类来源；
- 提出组织、负责人、投资方向、合作方和初步收益；
- 登记、初筛、分析、评估、拒绝、转项目和关闭状态；
- 转项目后必须同时记录Project和InvestmentProject标识。

### 3.3 InvestmentFeasibility

可研版本对象，包含：

- 投资事项标识和报告版本；
- 编制组织、总投资、预计年收益和ROI；
- 风险结论、附件标识及报告状态。

本对象不包含审批流实现。

### 3.4 InvestmentDecision

投资决策记录对象，包含：

- 党委前置研究、三重一大、董事会、经理层决策类型；
- 决策日期、决策人、结果和外部关联；
- 附条件批准要求及决策状态；
- 附条件批准必须填写条件要求。

## 4. Repository端口

### InvestmentProjectRepository

- `findById()`；
- `findByProjectId()`；
- `existsByInvestmentNo()`；
- `save()`。

### InvestmentOpportunityRepository

- `findById()`；
- `existsByOpportunityNo()`；
- `save()`。

接口位于domain层，仅依赖Java标准库和领域模型。基础设施实现将在后续业务Sprint中按数据库基线单独落地。

## 5. 权限设计

| 权限 | 用途 |
| --- | --- |
| `investment:view` | 进入投资管理并查看基础信息 |
| `investment:create` | 创建投资机会或投资事项 |
| `investment:edit` | 编辑投资机会或投资事项 |
| `investment:approve` | 执行投资论证审核/审批 |
| `investment:decision` | 记录或处理投资决策 |

权限常量集中在 `InvestmentPermissions`，后续Controller必须使用相同字符串执行 `@PreAuthorize`，禁止另行发明同义权限编码。

### 5.1 菜单结构

```text
投资管理（investment:view）
├── 投资机会（investment:view）
│   ├── 投资事项创建（investment:create）
│   └── 投资事项编辑（investment:edit）
├── 投资论证（investment:view）
│   └── 投资论证审批（investment:approve）
└── 投资决策（investment:view）
    └── 投资决策处理（investment:decision）
```

V2.2.0只写入菜单元数据，尚未增加前端路由注册或页面。按钮节点不可见，仅用于权限分配。初始化只给 `SUPER_ADMIN` 授权，不扩大其他角色权限。

## 6. Migration结果

Migration：`V2.2.0__bootstrap_investment_module_rbac.sql`

| 项目 | 结果 |
| --- | --- |
| SHA-256 | `ba6752751f98970da424cb84b4eec95343c06fdd08dc4791db8f5971e9513a31` |
| Flyway checksum | `487347589` |
| MySQL版本 | 8.4.9 |
| Flyway版本 | 13.0.0 |
| 当前Schema版本 | 2.2.0 |
| Flyway成功历史 | 6条（baseline + 5条Migration） |
| Flyway失败历史 | 0条 |
| 迁移后validate | 通过 |
| 第二次migrate | 无需执行，幂等通过 |
| 权限数据 | 5项齐全 |
| 菜单数据 | 8个节点齐全 |
| SUPER_ADMIN权限授权 | 5项齐全 |
| SUPER_ADMIN菜单授权 | 8项齐全 |

V2.2.0没有修改投资业务表结构，仅新增RBAC和菜单数据。

## 7. 测试结果

### 7.1 新增测试

| 测试 | 数量 | 结果 |
| --- | ---: | --- |
| InvestmentDomainModelTest | 3 | 通过 |
| InvestmentPermissionContractTest | 2 | 通过 |
| InvestmentModuleContextTest | 1 | 通过 |
| 定向测试合计 | 6 | 通过 |

覆盖：

- 领域对象正常构造；
- 投资比例和附条件批准等基础不变量；
- Domain类型无Spring/MyBatis/Entity字段依赖；
- 权限集合精确且不可修改；
- Spring上下文可加载；
- 未实现Repository不会被错误注册为Bean。

### 7.2 回归测试

执行：`mvn -f backend/pom.xml test`

结果：125项测试通过，0失败，0错误，0跳过。

测试日志仍包含项目既有H2空库映射检查警告和Mockito动态Agent提示，不是本次Investment模块引入的失败。

## 8. 下一阶段计划

建议下一阶段按小闭环开发投资机会管理：

1. 以已批准数据库设计确认 `investment_opportunity` 和 `investment_project` 的权威字段；
2. 通过新Migration落地缺失结构，禁止修改历史Migration；
3. 实现Opportunity Repository适配器和ApplicationService；
4. 实现机会登记、查询、初筛和转项目最小闭环；
5. 接入 `@DataScope`、ProjectAccessPolicy等价投资访问策略和操作审计；
6. 增加REST接口和前端页面前，先完成真实MySQL映射与权限测试。

## 9. 当前边界与风险

- Repository尚无实现，不能持久化领域对象；
- controller目录没有接口，不对外提供Investment业务能力；
- 菜单已进入数据库，但前端尚未注册Investment路由，因此不会形成可用业务页面；
- 领域状态只是基础词汇，不能替代后续审批流和生命周期V2门禁；
- 达梦、人大金仓的V2.2.0等价Migration尚未实现；
- 生产及其他受管环境没有执行V2.2.0，Inventory不会把隔离验收误记为生产已应用。
