# Sprint 1 V1.0 验收报告

验收日期：2026-08-03

目标版本：V1.0.0

验收分支：`feature/project-module`

## 1. 完成模块

- 用户管理：分页、新增、编辑、逻辑删除、状态、密码重置、角色分配。
- 组织管理：组织树、组织维护、负责人、上下级组织能力。
- 角色权限：RBAC、菜单/按钮授权、用户多角色、角色数据范围。
- 菜单中心：M/C/B 菜单模型、动态路由、Redis 权限缓存。
- 日志中心：AOP 操作日志、登录日志、异常日志、TraceId、敏感参数脱敏。
- 数据权限：ALL、ORG、ORG_AND_CHILDREN、SELF、CUSTOM 框架及配置入口。
- 前端基础平台：登录、Layout、Pinia、Axios、路由守卫、动态菜单、`v-permission`。
- 部署底座：Java 21、Nginx、MySQL 8.4、Redis 7.4、Docker Compose、备份和日志轮转。

## 2. 代码冻结检查

- 非生成源码/配置/文档共约 312 个文件；后端主源码 171 个、测试源码 25 个、前端源码 63 个、MySQL SQL 22 个。
- 未发现完全相同的重复文件。
- 未发现真实 TODO/FIXME/XXX/HACK；命中的 `TODO` 均为项目任务合法状态值。
- 保留但待治理项：
  - `frontend/src/layouts/ProjectLayout.vue` 未被现有路由引用，属于旧项目布局。
  - `frontend/src/utils/http.ts` 是项目模块兼容导出；新系统 API 使用 `request.ts`。
  - `frontend/src/styles` 与 `frontend/src/assets/styles` 均被入口使用，主题职责需后续统一。
  - `BaseService`、`BaseServiceImpl` 当前没有业务模块继承，属于预留抽象。
  - `DataScopeTestController` 仅在 dev 配置显式开启，生产 profile 不注册。
- 本次按要求未自动删除任何代码。

## 3. 后端验收

| 项目 | 结果 |
| --- | --- |
| Java | OpenJDK 21.0.12 LTS |
| Maven `clean verify` | 通过 |
| 主源码编译 | 167 个 Java 文件通过 |
| 测试源码编译 | 25 个 Java 文件通过 |
| 自动化测试 | 75/75 通过，0 失败、0 错误、0 跳过 |
| Spring Boot 上下文 | 通过 |
| 可执行 JAR | `backend/target/enterprise-platform.jar` 生成成功 |
| MySQL 真实连接 | MySQL 8.0.46，UP |
| MyBatis Plus | 拦截器加载、查询和映射检查通过 |
| JWT | 正常认证、异常令牌、撤销后拒绝均通过 |
| Redis | 权限读取、撤销键写入和查询通过 |
| 日志 | TraceId、操作/异常/认证安全日志测试通过 |

非阻断编译警告：`SensitiveDataSanitizer` 使用废弃 API；Mockito 依赖动态 Agent，未来 JDK 版本需要显式配置测试 Agent。

本机 Redis 为 Windows Redis 5，基础读写可用，但 Actuator `INFO` 健康探针不兼容，导致聚合健康为 DOWN；生产定义为 Redis 7.4，部署前必须在 Linux/Docker 环境复验健康状态。

## 4. 前端验收

| 项目 | 结果 |
| --- | --- |
| `npm install` | 成功，审计 146 个包，0 个已知漏洞 |
| Vitest | 9 个测试文件、24/24 测试通过 |
| TypeScript | `vue-tsc --noEmit` 通过 |
| Vite build | 通过，1716 个模块完成转换 |
| Router / Pinia / Axios | 类型检查和测试通过 |
| 动态菜单 / 按钮权限 | 路由构建与权限测试通过 |

性能提示：最大入口 JavaScript 产物约 435.83 KB（gzip 141.11 KB），未超出当前构建门禁，后续可按模块进一步拆包。

## 5. 数据库一致性

真实 MySQL `information_schema` 与 Entity 核查结果：

| 表 | 结果 |
| --- | --- |
| `sys_user` | 一致 |
| `sys_role` | 一致 |
| `sys_menu` | 一致 |
| `sys_org` | 一致 |
| `sys_log` | 一致 |

五张表的主键、业务字段及 `create_time/create_by/update_time/update_by/deleted/delete_token/remark/version` 均已映射。project 历史差异继续按 `database-mapping-audit.md` 延后处理。

## 6. 安全检查

| 场景 | 实测结果 |
| --- | --- |
| 未登录访问 `/system/user/page` | 401 |
| 畸形 JWT 访问受保护接口 | 401，日志不记录完整 token |
| 普通员工访问 `/system/menu/tree` | 403，统一 `ApiResponse` |
| JWT 注销 | Redis 撤销键写入成功；复用返回 401 |
| 敏感字段 | VO 不返回密码；日志脱敏测试通过 |
| SQL 注入 | Mapper/MyBatis Plus 参数化，未发现用户输入拼接 SQL |

高风险遗留：项目查询 Service 尚未使用 `@DataScope`。功能权限能阻止无项目权限用户，但拥有项目权限的用户仍缺少统一组织数据过滤，项目业务上线前必须整改并补充越权集成测试。

## 7. 数据权限

- ALL、ORG、ORG_AND_CHILDREN、SELF、CUSTOM 的解析与 SQL 生成自动化测试通过。
- 组织递归范围和 `sys_role_org` CUSTOM 配置已在真实数据库核查。
- CUSTOM 空配置按拒绝规则返回空范围。
- 规范要求所有业务查询 Service 使用 `@DataScope`；当前实际使用点仅为验收查询 Service，project 模块不满足该规范。

结论：统一数据权限底座可冻结；业务模块接入不能视为完成。

## 8. 部署验证

- 后端 Dockerfile 使用 Maven/Temurin Java 21 和 Java 21 JRE。
- Compose 使用 MySQL 8.4、Redis 7.4、Nginx，数据网络为 internal。
- MySQL/Redis/JWT 敏感值使用 Docker secrets + Spring configtree，不在生产编排中写死。
- 容器日志使用 local driver 轮转；Nginx 输出 stdout/stderr；生产后端输出控制台日志。
- 当前 Windows 主机未安装 Docker CLI，无法执行 `docker compose config/build/up`，因此容器级验收未完成。

部署前必须在 Linux 验证：Compose 配置展开、镜像构建、Redis 7.4 健康、全栈 healthcheck、备份恢复演练和 HTTPS/WAF 接入。

## 9. 风险与待优化事项

### 上线阻断

1. project 业务查询缺少 `@DataScope`，存在同权限用户跨组织读取风险。
2. Linux/Docker 全栈部署尚未在当前环境实跑。
3. 当前冻结提交位于 `feature/project-module`，按分支规范应经 develop 验收后合并 main，再将生产标签落在 main 发布提交。

### 非阻断

1. project 历史 Entity/表字段映射差异待专项 Sprint 处理。
2. 旧 Project Layout、请求兼容层和双样式目录待有测试保护后治理。
3. 前端入口包可进一步拆分。
4. 处理废弃 API 和 Mockito Agent 警告。
5. 达梦、人大金仓尚需驱动、DDL 和全量兼容测试。

## 10. 验收结论与下一阶段

基础平台代码、自动化测试、MySQL 核心映射、认证授权和权限缓存达到 V1.0 冻结条件；业务上线仍受上述三项阻断约束。建议下一阶段优先完成 Project Lifecycle 数据权限与历史映射专项，再进行 Linux/Docker 预生产演练，随后按 `feature -> develop -> main` 流程发布正式生产标签。
