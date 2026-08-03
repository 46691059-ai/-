# Sprint 1-6 数据权限中心验收记录

验收日期：2026-08-03

## 1. 实现范围

- 统一数据权限上下文：用户ID、用户名、组织ID、角色ID、有效数据范围、可访问组织集合。
- 统一 `@DataScope` 注解及 AOP 生命周期管理。
- MyBatis Plus 查询拦截器，在分页拦截前使用 JSQLParser 追加后端强制过滤条件。
- 支持 `ALL`、`ORG`、`ORG_AND_CHILDREN`、`SELF`、`CUSTOM`。
- 开发环境测试接口：`GET /api/test/dataScope`；非开发环境未显式开启时不注册。
- 未修改任何数据库表结构，未修改项目生命周期模块。

## 2. SQL 过滤规则

| 数据范围 | 过滤行为 |
|---|---|
| ALL | 不追加条件 |
| ORG | `org_field IN (当前组织)` |
| ORG_AND_CHILDREN | `org_field IN (当前组织及全部有效下级组织)` |
| SELF | 按注解配置的本人字段过滤 |
| CUSTOM | 使用 `sys_role_org` 的角色自定义组织集合；未配置时追加 `1 = 0` |

现有基础表的 `create_by` 为 VARCHAR，且审计填充器写入登录用户名。框架因此默认使用 `USERNAME` 比较；数值型 `owner_user_id/user_id` 字段必须显式配置 `SelfValueType.USER_ID`。字段名只接受安全标识符或单层表别名，非法配置直接拒绝查询。

## 3. 数据库核查

隔离 MySQL 8.0.46 实例核查结果：

- `sys_role.data_scope_type` 存在，类型 `varchar(32)`，默认 `SELF`。
- `sys_role_org` 存在，可作为 CUSTOM 数据范围关联表，无需新增表。
- `SUPER_ADMIN=ALL`、`DEPT_MANAGER=ORG_AND_CHILDREN`、`COMMON_USER=SELF`。
- 数字产业部范围返回：103、10301、10302、10303，即数字产业部及数据标注、数据采集、平台研发三个团队。
- 当前验收数据中的 `PROJECT_MANAGER` 仍为 `ORG_AND_CHILDREN`，且 CUSTOM 组织绑定数为 0；本 Sprint 只完成 CUSTOM 框架预留与代码测试，不改动既有验收数据。

## 4. 测试结果

- Java 21 Maven 全量测试：64 个通过，0 失败，0 错误，0 跳过。
- 数据权限专项测试：16 个通过，包括 AOP 上下文建立/异常清理、SQL 条件生成、SQL 改写、五类范围解析、默认拒绝和字段注入防护。
- Spring Boot 上下文测试：通过。
- 前端 Vitest：8 个测试文件、21 个测试通过。
- Vue TypeScript 检查：通过。
- Vite 生产构建：通过。
- MySQL 真实核查：ALL 可见 5 条有效账号数据；数字产业部 ORG_AND_CHILDREN 可见 3 个测试账号；SELF 仅返回 employee_test；CUSTOM 当前未配置组织，按框架规则将默认拒绝。

## 5. 风险与后续约束

1. 数据权限采用注解启用。后续业务查询 Service 必须标注 `@DataScope`，代码评审应将漏标作为阻断项。
2. CUSTOM 的读取和 SQL 过滤已完成，但角色管理尚无自定义组织选择/保存接口；启用 CUSTOM 前必须补齐 `sys_role_org` 配置能力。
3. MyBatis/JSQLParser 仅拦截 MyBatis SELECT；JdbcTemplate、原生 JDBC、异步线程和外部查询引擎不会自动继承该上下文。
4. 大型组织树会生成较长的 `IN` 条件；组织规模显著增长后应评估闭包表或临时权限表方案。
5. 复杂 UNION/特殊 SELECT 当前采用不支持即拒绝策略，业务 SQL 需要使用外层普通 SELECT 包装。
