# 数据权限规范

版本：V1.0.0

## 1. 范围定义

| 范围 | 过滤规则 |
| --- | --- |
| ALL | 不追加数据范围条件 |
| ORG | 仅当前组织 |
| ORG_AND_CHILDREN | 当前组织及全部有效下级组织 |
| SELF | 仅当前用户归属数据 |
| CUSTOM | 仅 `sys_role_org` 配置的有效组织；空配置返回空集 |

禁止使用历史名称 `DEPT_AND_CHILD`。

## 2. 强制规则

- 所有企业业务数据查询 Service 必须添加 `@DataScope`。
- 业务模块存在主从表时，主表查询入口必须添加 `@DataScope`；缺少组织字段的从表只能在主表授权成功后按主表外键查询，禁止向从表拼接不存在的组织字段。
- `orgField`、`userField` 必须是可信的数据库列名，可带固定表别名，禁止接收客户端输入。
- Controller 不得直接调用 Mapper，前端菜单/按钮隐藏不得替代后端过滤。
- 数据权限拦截器必须位于分页拦截器之前。
- CUSTOM 组织由角色数据权限接口维护，配置人不得授予超出自身可管理范围的数据。
- 异步任务必须重新建立可信身份上下文，禁止复用请求线程上下文。

## 3. 审查与测试

每个业务查询至少验证 ALL、ORG、ORG_AND_CHILDREN、SELF、CUSTOM 五类边界，以及 CUSTOM 空配置拒绝。业务查询 Service 漏标 `@DataScope`、从表查询绕过已授权主表入口、字段类型不匹配、绕过 Service、手工拼接权限 SQL 均为代码审查阻断项。

Sprint 1.8 已将 project 分页和统一项目主表访问入口接入 `@DataScope`。`project_stage`、`project_task`、`project_member` 没有独立组织字段，必须先通过 `project_info.department_id` 与 `project_info.create_by` 完成主表授权，再按 `project_id` 查询。

详细开发示例见 `data-scope-development-rule.md`。
