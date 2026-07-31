# 项目全生命周期 REST API（V1.0）

基础路径为 `/api/projects`。所有接口要求 JWT，并同时校验功能权限和组织数据范围。

## 项目接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/projects` | `project:lifecycle:list` | 分页查询 |
| GET | `/projects/{id}` | `project:lifecycle:list` | 生命周期详情 |
| POST | `/projects` | `project:lifecycle:create` | 新增并初始化八阶段 |
| PUT | `/projects/{id}` | `project:lifecycle:update` | 编辑 |
| DELETE | `/projects/{id}` | `project:lifecycle:delete` | 逻辑删除 |

列表参数：`page`、`size`、`keyword`、`status`、`stageCode`、`departmentId`。

```json
{
  "projectNo": "PRJ-2026-001",
  "projectName": "县域数据运营项目",
  "projectType": "04",
  "projectMode": "SELF_OPERATED",
  "leaderId": 10010,
  "departmentId": 10001,
  "startDate": "2026-08-01",
  "endDate": "2027-07-31",
  "budgetAmount": 5000000,
  "expectedIncome": 800000,
  "expectedProfit": 200000,
  "riskLevel": "MEDIUM",
  "remark": "建设县域数据资源运营体系"
}
```

编辑时必须提交响应中的 `version`。`status`、`currentStageCode` 和 `progress`
由后端根据阶段状态反算，不接受前端直接修改。

项目类型编码：`01` 投资项目、`02` 中标项目、`03` 工程项目、`04` 数字化项目、
`05` 研发项目、`06` 运营项目。

## 子资源接口

| 资源 | GET | POST | PUT | DELETE |
|---|---|---|---|---|
| 阶段 | `/projects/{id}/stages` | - | `/projects/{id}/stages/{stageId}` | - |
| 任务 | `/projects/{id}/tasks` | `/projects/{id}/tasks` | `/projects/{id}/tasks/{taskId}` | `/projects/{id}/tasks/{taskId}` |
| 成员 | `/projects/{id}/members` | `/projects/{id}/members` | `/projects/{id}/members/{memberId}` | `/projects/{id}/members/{memberId}` |

阶段依次为 `RESERVE`、`DEMONSTRATION`、`INITIATION`、`IMPLEMENTATION`、
`ACCEPTANCE`、`OPERATION`、`EVALUATION`、`ARCHIVE`。阶段负责人、任务负责人和项目成员字段分别为
`responsiblePerson`、`responsiblePerson`、`employeeId`，全部引用员工主数据。

统一响应：`{"code":"0","message":"success","data":...,"traceId":"..."}`。
