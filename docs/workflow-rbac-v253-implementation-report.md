# V2.5.3 Workflow RBAC数据库初始化实施报告

项目：《县域国企数字化运营治理平台》
Sprint：2-3.7-WF2.5
日期：2026-08-10
状态：**IMPLEMENTED / CANDIDATE / NOT_EXECUTED**

## 1. 修改文件清单

新增：

- `database/migration/mysql/V2.5.3__initialize_workflow_rbac.sql`；
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/security/WorkflowRbacMigrationContractTest.java`；
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/security/WorkflowTaskMethodSecurityTest.java`；
- `docs/workflow-rbac-v253-implementation-report.md`。

修改：

- `WorkflowPermissions.java`：补齐设计既有的`workflow:manage`目录访问契约；
- `WorkflowPermissionContractTest.java`：冻结9项唯一权限常量；
- `database/flyway/migration-inventory.yml`；
- `database/migration/mysql/SHA256SUMS`；
- `database/migration/mysql/README.md`。

未修改Workflow状态机、流程启动、任务动作规则、Investment代码或V2.4.0—V2.5.2历史Migration。

## 2. 数据库变化

V2.5.3只向现有RBAC表初始化数据：

- `sys_permission`：9项Workflow权限；
- `sys_menu`：1个M目录、3个C菜单、6个B按钮；
- `sys_role_permission`：为`SUPER_ADMIN`补齐9项权限；
- `sys_role_menu`：为`SUPER_ADMIN`补齐10个菜单节点。

不新增业务表，不修改Workflow核心表或RBAC表结构。

`sys_menu`当前不存在`menu_code`字段，因此遵循现有平台模型：M/C以活动`path`作为稳定业务键，B以“父菜单path + permission”作为稳定业务键；父菜单和角色关系均通过稳定业务键关联，不依赖既有父级ID或角色ID。

## 3. 权限矩阵

| 权限代码 | 语义 | 数据库类型 |
|---|---|---|
| workflow:manage | 流程治理中心目录访问，不授予审批权 | BUTTON |
| workflow:definition:view | 查看流程定义 | API |
| workflow:definition:create | 新建流程定义 | API |
| workflow:definition:edit | 创建版本、维护节点 | API |
| workflow:definition:publish | 发布权限预留，当前无发布API | API |
| workflow:view | 查看流程实例和任务 | API |
| workflow:start | 启动流程实例 | API |
| workflow:approve | 批准或驳回任务的基础准入 | API |
| workflow:withdraw | 发起人撤回任务的基础准入 | API |

代码权限常量和Migration权限源数据严格一致，共9项且无重复。没有发现其他Workflow权限常量或`@PreAuthorize`权限代码。

## 4. 权限语义边界

RBAC只负责功能入口，不决定具体任务处理权：

```text
workflow:approve
  ↓ 方法级准入
数据范围与实例归属
  ↓
workflow_task.assignee_user_id / 候选人规则
  ↓
任务状态与乐观锁
  ↓
允许执行审批动作
```

测试证明：拥有`workflow:approve`但不是已分配任务处理人时，Application Service仍返回拒绝，不更新任务或动作记录。没有`workflow:approve`时，Spring Method Security在访问Repository之前拒绝调用。

## 5. 菜单矩阵

| 层级 | 名称 | 稳定键 | 权限 |
|---|---|---|---|
| M | 流程治理中心 | /workflow | workflow:manage |
| C | 流程定义 | /workflow/definitions | workflow:definition:view |
| B | 流程定义新建 | definitions + permission | workflow:definition:create |
| B | 流程定义编辑 | definitions + permission | workflow:definition:edit |
| B | 流程定义发布 | definitions + permission | workflow:definition:publish |
| C | 流程实例 | /workflow/instances | workflow:view |
| B | 流程启动 | instances + permission | workflow:start |
| C | 待办任务 | /workflow/tasks | workflow:view |
| B | 任务审批 | tasks + permission | workflow:approve |
| B | 任务撤回 | tasks + permission | workflow:withdraw |

M/C路径集合和B级“父路径+权限”集合均无重复。

## 6. 角色授权

V2.5.3只匹配活动`role_code='SUPER_ADMIN'`：

- 9项Workflow权限全部授权；
- `/workflow`根目录、3个功能菜单和6个按钮全部授权；
- 不向普通员工、项目经理、部门负责人或其他业务角色默认授权；
- 不创建流程管理员角色。

关系表ID使用V2.5.3保留号段生成，但角色、权限、菜单关联均使用`role_code`、`permission_code`、path及父子关系等稳定业务键。

## 7. API权限对应关系

| Method | API | 权限 |
|---|---|---|
| POST | /workflow/definitions | workflow:definition:create |
| GET | /workflow/definitions/{id} | workflow:definition:view |
| POST | /workflow/definitions/{id}/versions | workflow:definition:edit |
| PUT | /workflow/definitions/{id}/versions/{versionId}/nodes | workflow:definition:edit |
| POST | /workflow/instances | workflow:start |
| GET | /workflow/instances/{id} | workflow:view |
| GET | /workflow/instances/{id}/tasks | workflow:view |
| POST | /workflow/tasks/{id}/approve | workflow:approve + task处理人校验 |
| POST | /workflow/tasks/{id}/reject | workflow:approve + task处理人校验 |
| POST | /workflow/tasks/{id}/withdraw | workflow:withdraw + 发起人/节点校验 |

`workflow:manage`只作为目录访问权限；`workflow:definition:publish`为已冻结设计权限，当前尚无发布接口。

## 8. 测试结果

- Java：OpenJDK 21.0.12；
- Maven：3.9.9；
- Workflow专项测试：33通过，0失败；
- 后端完整测试：220通过，0失败，0错误，0跳过；
- 权限常量与SQL权限代码一致：通过；
- 权限代码唯一：通过；
- 菜单稳定键唯一：通过；
- SUPER_ADMIN权限完整：通过；
- SUPER_ADMIN菜单授权完整：通过；
- 无审批权限时方法级拒绝：通过；
- 有审批权限但非任务处理人仍拒绝：通过；
- 既有Workflow测试：通过。

本Sprint按要求未执行V2.5.3真实MySQL Migration，因此不存在Flyway checksum、history或Schema验收结论。

## 9. Migration SHA-256与治理状态

```text
V2.5.3 SHA-256
6d6904da8028830cdc8fa7b9125994d37d2c916a09277ae9d4526f5646144e26

asset_status: CANDIDATE
execution_status: NOT_EXECUTED
flyway_checksum: null
```

V2.4.0—V2.4.9及V2.5.0—V2.5.2摘要重新计算并与治理清单一致，摘要漂移为0。

## 10. 剩余风险与下一步建议

1. V2.5.3尚未进行Fresh及V2.5.2 Upgrade真实MySQL验收，不能晋级为不可变资产。
2. Workflow前端页面尚未进入当前路由注册表，数据库菜单初始化不会自动生成前端页面。
3. `workflow:definition:publish`尚无业务接口，当前只是权限与菜单契约预留。
4. 无独立流程管理员角色；除SUPER_ADMIN外的授权必须由后续运营配置明确完成。
5. 当前候选人规则尚未完整解析；RBAC权限不能替代任务分配和组织权限校验。

下一步建议单独执行V2.5.3真实MySQL Fresh/Upgrade验收，验证9项权限、10个菜单、SUPER_ADMIN关系、唯一性、幂等性和历史摘要。未经下一步指令，不进入V2.5.4。
