# 项目全生命周期 REST API

基础路径：`/api/projects`。所有接口要求 JWT，并返回统一 `ApiResponse<T>`。
JWT 只携带用户标识和令牌版本；权限、用户状态及组织数据范围以数据库当前状态为准。
除接口权限外，所有项目读写均校验角色数据范围；`SELF` 范围只允许访问本人负责或作为有效成员参与的项目。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/projects` | `project:lifecycle:list` | 项目分页查询 |
| `GET` | `/projects/{id}` | `project:lifecycle:list` | 项目聚合详情 |
| `POST` | `/projects` | `project:lifecycle:create` | 新增项目并初始化五个阶段 |
| `PUT` | `/projects/{id}` | `project:lifecycle:update` | 编辑项目 |
| `DELETE` | `/projects/{id}` | `project:lifecycle:delete` | 逻辑删除项目 |
| `GET` | `/projects/{id}/stages` | `project:lifecycle:list` | 阶段列表 |
| `PUT` | `/projects/{id}/stages/{stageId}` | `project:lifecycle:update` | 更新阶段 |
| `GET` | `/projects/{id}/tasks?page=1&size=20&stageId=` | `project:lifecycle:list` | 任务分页，单页最多100条 |
| `POST` | `/projects/{id}/tasks` | `project:lifecycle:update` | 新增任务 |
| `PUT` | `/projects/{id}/tasks/{taskId}` | `project:lifecycle:update` | 编辑任务 |
| `DELETE` | `/projects/{id}/tasks/{taskId}` | `project:lifecycle:update` | 删除任务 |
| `GET` | `/projects/{id}/members?page=1&size=20` | `project:lifecycle:list` | 成员分页，单页最多100条 |
| `POST` | `/projects/{id}/members` | `project:lifecycle:update` | 添加成员 |
| `PUT` | `/projects/{id}/members/{memberId}` | `project:lifecycle:update` | 编辑成员 |
| `DELETE` | `/projects/{id}/members/{memberId}` | `project:lifecycle:update` | 移除成员 |

项目创建请求：

```json
{
  "projectCode": "PRJ-2026-001",
  "projectName": "县域数据资源运营项目",
  "projectType": "DIGITAL",
  "orgId": 10001,
  "managerUserId": 10010,
  "plannedStartDate": "2026-08-01",
  "plannedEndDate": "2027-07-31",
  "investmentAmount": 5000000,
  "expectedIncome": 800000,
  "riskLevel": "MEDIUM",
  "description": "建设县域数据资源运营体系"
}
```

项目编辑请求不接受 `projectStatus` 和 `progress`。两者由阶段状态和阶段进度在后端事务内反算。
阶段只能按 `NOT_STARTED → IN_PROGRESS → COMPLETED` 顺序流转；允许未开始阶段标记为
`SKIPPED`。立项与验收阶段必须审批通过，且阶段下不存在未完成任务后才能完成。

聚合详情仅返回前20条任务和成员，同时返回 `taskTotal`、`memberTotal`；其余数据通过对应分页接口获取。
