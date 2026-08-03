# Project 模块访问安全规范

版本：V1.0.0

生效 Sprint：1.8.2

## 1. 统一访问入口

- 项目详情、阶段、任务、成员查询必须先调用 `ProjectAccessPolicy.requireAccessible(projectId)`。
- 项目分页必须通过带 `@DataScope` 的 `ProjectLifecycleQueryService.page()` 查询。
- `project_stage`、`project_task`、`project_member` 没有独立组织字段，其权限必须继承 `project_info`，禁止直接把 `project_id` 当作组织字段。
- Controller 只能调用 `ProjectLifecycleService`，禁止依赖 Mapper 或 Repository。
- Service 禁止绕过 Policy 直接返回按项目 ID 查询的数据。
- 禁止新增可直接读取 Project 数据且不经过 Policy/`@DataScope` 的 Repository、Mapper 门面或旁路接口。

绕过 `ProjectAccessPolicy`、从 Controller 直连 Mapper、从 Service 建立未授权读取路径，均属于代码审查阻断项。

## 2. SELF 规则冻结

Project 模块的 SELF 定义为：

```text
project_info.create_by = 当前登录用户名
```

项目负责人、项目阶段负责人、任务负责人或项目成员身份均不自动取得 SELF 数据范围。禁止将 SELF 解释为“项目成员可见”。

未来确需支持项目成员访问时，必须新增独立、可审计的权限类型和访问策略，并单独完成数据库设计、安全评审及越权测试；不得修改 SELF 既有语义进行兼容。

## 3. 查询约束

| 查询 | 强制入口 | 数据归属字段 |
| --- | --- | --- |
| 项目分页 | `ProjectLifecycleQueryService.page()` | `project_info.department_id/create_by` |
| 项目详情 | `ProjectAccessPolicy.requireAccessible()` | `project_info.department_id/create_by` |
| 项目阶段 | 先执行 `requireAccessible(projectId)` | `project_stage.project_id -> project_info.id` |
| 项目任务 | 先执行 `requireAccessible(projectId)` | `project_task.project_id -> project_info.id` |
| 项目成员 | 先执行 `requireAccessible(projectId)` | `project_member.project_id -> project_info.id` |

## 4. 测试门禁

每次修改 Project 查询链路必须验证：

1. ALL 用户可访问全部项目。
2. CUSTOM 用户只能访问授权组织的项目。
3. SELF 用户只能访问自己创建的项目。
4. 跨组织访问被拒绝。
5. 详情、阶段、任务、成员查询均先调用 `requireAccessible()`。
6. Controller 不得新增 Mapper/Repository 依赖。
