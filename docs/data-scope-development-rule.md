# 数据权限开发强制规范

适用范围：所有涉及企业业务数据查询的后端模块，包括项目、合同、经营、投资、数据资产、风险合规及后续扩展模块。

## 1. 强制规则

所有涉及业务数据查询的 Service 方法必须添加 `@DataScope`，并明确查询SQL中的组织字段和本人归属字段。

```java
@DataScope(
    orgField = "p.org_id",
    userField = "p.owner_user_id",
    selfValueType = SelfValueType.USER_ID
)
public List<ProjectVO> queryProjects(ProjectQuery query) {
    return projectMapper.selectProjects(query);
}
```

现有表的 `create_by` 保存登录用户名时，可以使用默认本人标识方式：

```java
@DataScope(orgField = "c.org_id", userField = "c.create_by")
```

## 2. 禁止事项

1. 禁止 Controller 直接调用 Mapper 或绕过 Service 数据权限入口。
2. 禁止仅通过前端菜单、按钮或查询参数实现数据隔离。
3. 禁止将客户端提交的 `orgIds`、`userId` 直接拼接到权限SQL。
4. 禁止在业务模块中重复实现 ALL、ORG、ORG_AND_CHILDREN、SELF、CUSTOM 判断。
5. 禁止在异步线程中直接复用当前数据权限上下文；异步任务必须重新建立可信身份上下文。
6. 禁止为了适配数据权限直接修改既有业务表，字段不足时先提交数据库设计评审。
7. 数据权限配置人员不得授予自身范围之外的CUSTOM组织；只有ALL范围的配置人员可以授予ALL。

## 3. SQL约束

- `orgField`、`userField` 必须是数据库列名，可以携带单层表别名，例如 `p.org_id`。
- 复杂 UNION 查询必须使用外层普通 SELECT 包装，确保统一拦截器可以安全追加条件。
- 分页查询必须由 MyBatis Plus 分页插件执行；数据权限拦截器必须位于分页插件之前。
- CUSTOM 未配置任何有效组织时必须返回空结果，不得降级为 ALL、ORG 或 SELF。

## 4. 代码审查阻断项

出现以下任一情况，代码审查不得通过：

- 业务查询 Service 缺少 `@DataScope`。
- Controller 绕过 Service 直接访问 Mapper。
- 业务代码自行拼接组织范围SQL。
- SELF 字段类型与 `SelfValueType` 配置不一致。
- 新查询使用了 MyBatis 之外的数据访问方式但没有等价的后端数据权限措施。
- 数据权限相关测试未覆盖 ALL、组织范围、SELF 或 CUSTOM 的实际边界。

## 5. 验收要求

每个业务模块至少提供以下测试：

1. ALL 用户可以查看授权模块全部数据。
2. ORG 用户只能查看本组织数据。
3. ORG_AND_CHILDREN 用户只能查看本组织及有效下级组织数据。
4. SELF 用户只能查看本人数据。
5. CUSTOM 用户只能查看 `sys_role_org` 中配置的组织数据，空配置必须返回空结果。
