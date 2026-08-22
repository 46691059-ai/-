# Workflow 运行域基础设计与实现报告

## 1. Sprint 结论

Sprint 2-3.7-WF2.3 已完成 Workflow Lite 运行域基础能力：V2.5.1 Candidate Migration、纯 Java 运行领域模型、MyBatis Plus 持久化适配、流程启动事务、实例与任务查询接口及安全测试。

本阶段没有实现任务办理、审批动作、会签、条件路由、撤回、动作日志或完整 BPM 能力。

## 2. 数据库变化

新增候选 Migration：

- `database/migration/mysql/V2.5.1__create_workflow_runtime.sql`
- SHA-256：`e52ca6bdd00426bc7fee1c9b92cd3dc3fecd5084e3533f955c89c4867ceb4469`
- 状态：`CANDIDATE / NOT_EXECUTED`
- 依赖：V2.5.0

新增表：

- `workflow_instance`：冻结定义编码、版本号、业务引用、发起人、当前节点、幂等证据与实例状态。
- `workflow_task`：冻结节点编码、节点名称、参与者、候选人规则、允许动作及任务状态。

两表均包含 `created_by/created_time/updated_by/updated_time/deleted/delete_token/remark/version`。数据库通过复合外键强制实例版本、当前节点和任务节点的版本归属一致，通过唯一约束保护实例号、幂等键、业务尝试和任务参与者。

V2.5.0 SHA-256 保持 `5b41f9b787bbf4bdf0927b5f30dc487c6d88085d81aedf19e784d44474c02926`，Investment 源码没有变化。

## 3. 领域与调用链

```text
WorkflowRuntimeController
  -> WorkflowRuntimeApplicationService
    -> WorkflowDefinition/Version/Node Repository Port
    -> WorkflowInstance/Task Repository Port
      -> MyBatis Repository Adapter
        -> workflow_instance / workflow_task
```

领域层新增：

- `WorkflowInstance`
- `WorkflowTask`
- `WorkflowInstanceRepository`
- `WorkflowTaskRepository`

领域对象只依赖 Java 标准库，不依赖 Spring、MyBatis 或 Entity。

## 4. 流程启动规则

1. 要求 `workflow:start` 权限。
2. 从当前登录上下文获取发起用户和组织，禁止请求伪造发起人。
3. 同企业、同幂等键、同请求哈希返回已有实例；不同哈希返回冲突。
4. 定义必须为 `ACTIVE`，企业与业务类型必须匹配，定义归属组织必须位于发起人数据范围内。
5. 当前版本必须为 `PUBLISHED` 并属于该定义。
6. 必须存在启用的首节点；Workflow Lite 当前只接受单审批首节点。
7. 同一事务写入 `RUNNING` 实例和首个 `PENDING` 任务，任一步失败整体回滚。
8. 实例和任务查询除 `workflow:view` 外，还校验发起人、组织范围或全部数据范围。

## 5. API

| Method | Path | Authority | Purpose |
|---|---|---|---|
| POST | `/workflow/instances` | `workflow:start` | 启动流程并生成首个任务 |
| GET | `/workflow/instances/{id}` | `workflow:view` | 查询流程实例 |
| GET | `/workflow/instances/{id}/tasks` | `workflow:view` | 查询实例任务 |

## 6. 测试结果

- Java 21 Maven编译：通过。
- Spring Boot上下文启动：通过。
- Workflow专项测试：通过。
- 后端全量测试：205个通过，失败0，错误0，跳过0。
- Domain：实例状态、任务状态、定义/版本/节点快照约束通过。
- Repository：实例和任务映射、审计初始化通过。
- Transaction：首任务保存失败触发整体回滚。
- Permission：`workflow:start`、`workflow:view`接口契约通过。
- Migration SHA256SUMS全量校验：通过。
- `git diff --check`：通过。

测试使用H2启动上下文，不代表V2.5.1已完成真实MySQL Flyway验收。

## 7. 风险与下一步

1. V2.5.1 尚未执行真实MySQL 8 Fresh/Upgrade验收，不能晋级为 `CANONICAL_IMMUTABLE`。
2. 当前仅物化首个单审批任务；会签、条件节点和多节点推进必须在后续状态机Sprint实现。
3. `assignment_rule_config`当前作为不可变候选快照保存，尚未实现组织/岗位/用户规则解析器。
4. V2.5.2应独立新增 `workflow_action_log`，并实现只追加审批审计记录；不得修改V2.5.1。
5. V2.5.3统一落地Workflow RBAC种子前，运行权限只存在于代码契约中。
