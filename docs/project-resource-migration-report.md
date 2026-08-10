# Sprint 2-0.9 Project 阶段、任务、成员迁移报告

## 1. 迁移范围

本次采用按用例灰度切换，不删除旧 `ProjectLifecycleService`，不修改数据库结构和 REST 路径。

| 资源 | 已迁移至 Application/Domain | 暂留旧 Service |
| --- | --- | --- |
| Stage | 阶段列表读取 | 阶段更新与流转 |
| Task | 分页查询、创建、更新 | 删除 |
| Member | 分页查询、新增、删除 | 更新 |

阶段读取已统一转换为 `LifecycleInstance -> LifecycleStage -> StageSnapshot` 模型，返回阶段快照编码、名称、顺序以及实例状态。当前数据库尚未执行生命周期 V2 migration，因此 `ProjectRepositoryImpl` 从权威基线表 `project_stage` 重建 `LEGACY` 生命周期实例；V2 实体表落地后可在 Repository 适配器内切换来源，不影响 Controller 和领域模型。

## 2. 调用链变化

```text
GET /projects/{projectId}/stages
  -> ProjectStageApplicationService
  -> ProjectResourceAccessService
  -> ProjectAccessPolicy
  -> ProjectRepository -> ProjectMapper / ProjectStageMapper

GET|POST|PUT /projects/{projectId}/tasks
  -> ProjectTaskApplicationService
  -> ProjectResourceAccessService -> ProjectAccessPolicy
  -> ProjectTaskRepository -> ProjectTaskMapper

GET|POST|DELETE /projects/{projectId}/members
  -> ProjectMemberApplicationService
  -> ProjectResourceAccessService -> ProjectAccessPolicy
  -> ProjectMemberRepository -> ProjectMemberMapper
```

所有已迁移子资源在读取或写入 Repository 前，均先调用 `ProjectResourceAccessService.requireAccessible(projectId)`。该入口先执行带数据范围约束的项目访问检查，再重建聚合并按最新组织、创建人和授权范围进行二次校验。

## 3. 旧代码保留情况

- `ProjectLifecycleService`、`ProjectLifecycleServiceImpl` 完整保留。
- `PUT /projects/{projectId}/stages/{stageId}` 继续使用旧阶段流转实现。
- `DELETE /projects/{projectId}/tasks/{taskId}` 继续使用旧任务删除实现。
- `PUT /projects/{projectId}/members/{memberId}` 继续使用旧成员更新实现。
- Controller 请求参数、响应 DTO、权限表达式和接口路径保持兼容。
- 新 Domain 模型不依赖 Spring、MyBatis Plus 或数据库 Entity；Mapper 依赖被限制在 Infrastructure 层。

## 4. 测试结果

Java 21 环境执行 `mvn test`：

- 总计 110 个测试；
- 失败 0；错误 0；跳过 0；
- Spring Boot 测试上下文启动成功；
- 新增 7 个资源迁移测试全部通过；
- 项目创建测试确认一次生成 8 个兼容阶段，首阶段为 `IN_PROGRESS`；
- 阶段测试确认按快照顺序返回编码、名称和状态；
- 任务测试覆盖创建、更新及越权阻断；
- 成员测试覆盖新增、逻辑删除及越权阻断；
- 既有 `ProjectAccessPolicySecurityTest` 和 `ProjectDataScopeSecurityTest` 持续通过。

测试使用 H2 上下文及 Mockito 单元测试，未在本 Sprint 执行真实 MySQL 生命周期 V2 表集成测试。

## 5. 剩余风险

1. 生命周期 V2 migration 尚未落地，当前阶段快照来源是 `project_stage` 的兼容重建，来源标识为 `LEGACY`，不是模板版本快照表。
2. 阶段更新、任务删除、成员更新仍在旧 Service；后续应继续按单个用例迁移，避免双写。
3. Task/Member 的唯一性除应用检查外仍依赖数据库唯一索引处理并发竞争；真实 MySQL 集成测试需覆盖冲突场景。
4. 资源分页当前采用列表查询和计数查询两次访问，这是标准分页行为；后续性能验收需结合真实数据量检查索引与执行计划。
5. Mockito 在 Java 21 测试中提示动态加载 Agent 的未来兼容警告，不影响当前测试，但应在测试构建治理 Sprint 配置显式 Java Agent。
