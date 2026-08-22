# Workflow Assignment Resolver Framework实施报告

> Sprint：2-3.7-WF3.6.1
> 基线：V2.6.2 `ASSIGNMENT_SNAPSHOT_FOUNDATION_FROZEN`
> 结论：`USER + DIRECT` Resolver框架已实现；ROLE/POSITION/ORG未实现。

## 1. 修改文件清单

### 新增Domain模型

- `AssignmentResolver.java`
- `AssignmentResolverContext.java`
- `AssignmentResolutionResult.java`
- `CandidatePool.java`
- `CandidateUser.java`
- `ExplicitUserResolver.java`

### 新增Application VO

- `WorkflowAssignmentResolutionDetail.java`

### 修改调用链和接口

- `WorkflowRuntimeApplicationService.java`
- `WorkflowLinearExecutionApplicationService.java`
- `WorkflowTaskAssignmentApplicationService.java`
- `WorkflowTaskController.java`

### 测试

- `WorkflowTaskAssignmentDomainTest.java`
- `WorkflowAssignmentDomainPurityTest.java`
- `WorkflowTaskAssignmentApplicationServiceTest.java`
- `WorkflowLinearExecutionApplicationServiceTest.java`

## 2. 数据库变化

无数据库变化，未创建V2.6.3。

V2.6.2的`workflow_task_assignment_snapshot`已经可以持久化：

- `strategy_type/target_type`
- `target_snapshot`
- `resolved_users/resolved_user_count`
- `resolve_time`
- `audit_info`
- Task、Instance、Version、Node和NodeExecution归属关系

新Resolver版本与原因被写入不可变`audit_info`，无需为当前仅支持的USER + DIRECT扩表。未修改V2.5.0—V2.6.2历史Migration。

## 3. Domain模型

- `AssignmentResolver`：统一纯Domain解析端口，暴露支持类型、Resolver版本和`resolve(context)`。
- `AssignmentResolverContext`：绑定原Assignment上下文与Strategy输出，校验解析时间一致。
- `CandidateUser`：保存非敏感候选证据、来源、顺序和有效窗口。
- `CandidatePool`：冻结确定性候选集合；当前强制USER候选唯一。
- `AssignmentResolutionResult`：从候选池确定DIRECT处理人，并兼容映射为V2.6.2 `AssignmentResult`。
- `ExplicitUserResolver`：唯一可执行Resolver，版本`EXPLICIT_USER_V1`。

以上类型均不依赖Spring、MyBatis、Entity或Infrastructure。

## 4. Resolver调用链

```text
NodeExecution
  -> ExplicitUserAssignment（解析节点USER配置）
  -> ExplicitUserAssignmentStrategy（生成规则结果）
  -> ExplicitUserResolver（生成CandidatePool）
  -> AssignmentResolutionResult（DIRECT选择唯一候选）
  -> WorkflowTask
  -> AssignmentSnapshot
```

首次线性任务和后续节点任务均使用同一链路。Task、NodeExecution和AssignmentSnapshot在现有Application事务中保存；快照保存失败会导致事务回滚。

## 5. API列表

| 方法 | URL | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/workflow/tasks/{taskId}/assignment` | `workflow:view` | 查询V2.6.2 Assignment Snapshot；保留Legacy投影 |
| GET | `/workflow/tasks/{taskId}/assignment/resolution` | `workflow:view` | 查询冻结Resolver结果和候选集合；不会重新运行Resolver |

没有开放规则配置、候选修改、Claim、Assign或Transfer接口。

## 6. 测试结果

环境：Java 21.0.12、Maven 3.9.9。

- Java 21编译：通过，428个主源码文件。
- 定向测试：23通过，0失败，0错误。
- 全量后端测试：280通过，0失败，0错误，0跳过。
- Spring Boot上下文：通过。

覆盖：

1. USER Resolver确定性解析；
2. CandidatePool生成、排序及DIRECT选择；
3. Task创建与Snapshot持久化事务边界；
4. Task处理人与Snapshot候选一致；
5. Legacy Task只读兼容且不伪造Resolver执行；
6. ROLE等非法Resolver输入拒绝；
7. Domain无Spring、MyBatis、Entity或Infrastructure依赖；
8. Assignment Repository和V2.6.2 Migration契约不回归。

## 7. Migration状态

- V2.6.2：保持冻结，不修改。
- V2.6.3：未创建、未登记、未执行。

## 8. 剩余风险

1. Resolver当前由Application Service内显式实例化；在仅一个USER Resolver时可避免Spring进入Domain，但未来扩展需引入Application层Registry。
2. V2.6.2没有独立`resolver_version`列，当前版本证据位于`audit_info`；如果后续需要数据库级版本检索，应通过V2.6.3增量字段实现。
3. Legacy任务只有Task字段，无法还原历史解析时间与真实Resolver版本，API明确标记为`LEGACY_UNVERSIONED`。
4. ROLE/POSITION/ORG、多人候选、Claim和自动选人全部保持关闭。
5. 测试存在Mockito动态Agent的JDK未来兼容警告，不影响本次测试结果，后续可在构建治理Sprint显式配置测试Agent。

## 9. 下一步建议

下一Sprint仅在明确需要时建立Application层Resolver Registry及V2.6.3候选结构；ROLE/POSITION/ORG必须分别完成主数据边界、数据权限、候选上限和审计设计后才能注册。不得将RBAC权限解释为具体Task处理权。
