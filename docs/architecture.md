# 工程架构

```text
enterprise-platform
├── backend       Spring Boot 3 后端
├── frontend      Vue 3 管理端
├── database      数据库版本脚本
├── docs          架构与接口文档
└── deploy        本地基础设施及部署配置
```

后端基础包：

- `common.api`：统一响应
- `common.exception`：统一异常
- `common.web`：Web 请求链基础能力
- `config`：MyBatis-Plus、Redis、跨域配置
- `security`：JWT、实时身份快照、令牌撤销与 Spring Security
- `modules.project.security`：项目组织/本人数据范围策略
- `modules.project.service.ProjectLifecycleQueryService`：只读查询与分页
- `modules.project.service.impl.ProjectLifecycleServiceImpl`：事务命令编排
- `modules.project.service.ProjectTaskCommandService`：任务写模型与层级约束
- `modules.project.service.ProjectMemberCommandService`：成员写模型与负责人同步
- `modules.project.service.ProjectStageTransitionPolicy`：阶段状态机与业务约束
- `modules.project.service.ProjectLifecycleAssembler`：Entity/DTO 装配

业务模块必须按 `controller / dto / entity / mapper / service` 分层，模块间通过公开服务或 DTO 交互。
数据库先执行 `01_database.sql`、`02_sys.sql`、`03_hr.sql` 建立物理库、系统基础域
和组织人事域，随后以
`V1.0.0__enterprise_platform_v1.sql` 为主数据和核心业务基线，
`V1.1.0__investment_data_risk_bi.sql` 扩展投资、数据资产、风险合规和 BI 分域；
统一使用审计字段、逻辑删除唯一键、乐观锁、复合外键和查询索引。旧版数据使用
独立迁移目录人工升级。
客户端退出登录调用 `POST /api/auth/logout`，服务端按 JWT `jti` 将令牌加入 Redis 撤销表直到其自然过期。
