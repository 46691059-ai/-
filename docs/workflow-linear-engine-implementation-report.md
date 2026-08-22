# Workflow 线性多节点推进引擎实施报告

## 1. Sprint 结论

Sprint 2-3.7-WF3.2 已实现 `MULTI_NODE_LINEAR_V1` 第一版线性推进能力。支持显式 DIRECT 图上的 `START → A → B → END`，实现节点执行创建/激活、任务完成、Transition 解析、下一节点执行和任务生成，以及末节点流程结束。

本 Sprint 未修改 Investment、V2.5.0—V2.6.0 历史 Migration，未实现条件路由、并行、会签、子流程或自动选人。

## 2. 修改文件清单

### 2.1 新增后端文件

- `domain/model/WorkflowEngineMode.java`
- `domain/model/WorkflowContentHashAlgorithm.java`
- `domain/service/NodeExecutionService.java`
- `domain/service/TransitionResolver.java`
- `domain/service/WorkflowLinearExecutor.java`
- `domain/service/WorkflowLinearGraphValidator.java`
- `domain/service/ExplicitUserAssignment.java`
- `application/service/WorkflowLinearExecutionApplicationService.java`
- `application/vo/WorkflowLinearCompletionResult.java`

### 2.2 修改后端文件

- `domain/model/WorkflowVersion.java`
- `domain/model/WorkflowVersionRelease.java`
- `domain/model/WorkflowInstance.java`
- `domain/model/WorkflowTask.java`
- `domain/service/WorkflowVersionContentHasher.java`
- `domain/repository/WorkflowInstanceRepository.java`
- `domain/repository/WorkflowTaskRepository.java`
- `application/service/WorkflowDefinitionApplicationService.java`
- `application/service/WorkflowRuntimeApplicationService.java`
- `application/service/WorkflowTaskActionApplicationService.java`
- `interfaces/rest/WorkflowTaskController.java`
- Workflow Version/Release/Instance/Task Entity、Mapper、Repository Adapter 和 `WorkflowEntityMapper`

### 2.3 新增测试

- `WorkflowLinearEngineDomainTest.java`
- `WorkflowLinearExecutionApplicationServiceTest.java`
- `WorkflowLinearRuntimeMigrationContractTest.java`

### 2.4 数据库与文档

- 新增 `database/migration/mysql/V2.6.1__enable_workflow_linear_runtime.sql`
- 更新 `database/flyway/migration-inventory.yml`
- 更新 `database/migration/mysql/SHA256SUMS`
- 更新 `database/migration/mysql/README.md`
- 新增本文档

## 3. 数据库变化

V2.6.1 仅做增量关联：

| 表 | 变化 |
|---|---|
| `workflow_version` | 增加 `engine_mode`、`content_hash_algorithm` |
| `workflow_version_release` | 冻结引擎模式和哈希算法 |
| `workflow_instance` | 增加运行模式、哈希算法快照、`current_node_execution_id` 权威游标 |
| `workflow_task` | 增加可空 `node_execution_id`，Legacy 任务保持 NULL |
| `workflow_node_execution` | 增加供复合外键引用的运行归属唯一键 |

Migration 包含引擎/哈希配对 CHECK、执行游标外键、Task→Execution 复合外键、执行任务唯一键和查询索引。历史记录通过默认值保持 `SINGLE_NODE_LEGACY / NODE_V1_SHA256`，不伪造执行记录。

## 4. 领域模型

- `NodeExecutionService`：创建并激活一次节点访问、完成节点执行；
- `TransitionResolver`：只接受唯一启用的 APPROVE + DIRECT 出边；
- `WorkflowLinearExecutor`：在纯 Domain 中完成 Task/Execution 决策并返回下一 Transition；
- `WorkflowLinearGraphValidator`：校验唯一入口、节点可达、无环、无分叉；
- `ExplicitUserAssignment`：仅解析明确 USER 配置，不执行组织/岗位自动选人；
- `WorkflowInstance`：显式区分 Legacy/Linear，使用当前执行游标推进或终结；
- `WorkflowTask`：线性任务必须绑定匹配的 NodeExecution 和明确处理人。

上述 Domain 类均不依赖 Spring、MyBatis 或 Entity。

## 5. 执行链路

### 5.1 启动

```text
WorkflowRuntimeApplicationService
  -> 校验已发布 MULTI_NODE_LINEAR_V1 / GRAPH_V2_SHA256
  -> 校验线性图并重算发布 Hash
  -> 创建 WorkflowInstance
  -> 创建并激活入口 NodeExecution
  -> 冻结明确 USER 处理人并创建 Task
  -> 更新实例 current_node_execution_id
```

### 5.2 完成节点

```text
POST /workflow/tasks/{id}/complete
  -> 锁定 Instance 与 Task
  -> 校验 RBAC + DataScope + assignee + 运行归属
  -> 保存 TaskAction
  -> 完成 Task
  -> CAS 完成 NodeExecution
  -> 解析唯一 DIRECT Transition
  -> 有下一节点：创建/激活下一 NodeExecution 和 Task，实例保持 RUNNING
  -> 无下一节点：实例进入 APPROVED
```

整个完成链路标注 `@Transactional`。任一步抛出异常时由 Spring 回滚。任务动作幂等键、请求哈希、Task/Execution/Instance 乐观锁和数据库唯一键共同阻断重复推进。

Legacy 实例继续使用原 V2.5 接口；旧 `approve/reject/withdraw` 服务显式拒绝 Linear 实例，避免绕过统一执行器。

## 6. API

| 方法 | URL | 权限 | 用途 |
|---|---|---|---|
| POST | `/workflow/tasks/{taskId}/complete` | `workflow:approve` + 当前任务 assignee | 完成当前线性节点并自动生成下一节点/任务 |

响应 `WorkflowLinearCompletionResult` 明确区分：

- `taskStatus`
- `currentNodeStatus`
- `instanceStatus`
- `nextNodeExecutionId`
- `nextNodeStatus`
- `nextTaskId`
- `eventSequence`

没有开放直接推进或人工修改 NodeExecution 状态的 REST 接口。

## 7. 测试结果

环境：Java 21.0.12、Maven 3.9.9、Spring Boot 3.5.9。

专项测试：10 个通过，覆盖：

1. A→B→C 顺序完整执行；
2. 完成节点生成下一 NodeExecution/Task；
3. C 无出边时实例正确结束；
4. 相同 TaskAction 幂等重放；
5. 乐观锁并发冲突阻断下一节点；
6. Action 持久化失败阻断后续写入，事务边界由 `@Transactional` 保证回滚；
7. 多个 APPROVE Transition 被拒绝；
8. 跨版本 Transition 被拒绝；
9. V2.6.1 Legacy 兼容与字段约束契约；
10. 禁止 Investment/条件/会签结构进入 V2.6.1。

全量测试：`260` 个通过，`0` Failure，`0` Error，`0` Skipped；Spring Boot 上下文启动通过。

## 8. Migration 状态

| 项目 | 值 |
|---|---|
| 版本 | V2.6.1 |
| 文件 | `V2.6.1__enable_workflow_linear_runtime.sql` |
| 资产状态 | `CANDIDATE` |
| 执行状态 | `NOT_EXECUTED` |
| SHA-256 | `50314ff572253eaef6e0002acc2b1f9109fe7332a7360f8c8b14c4e8fde1a03e` |
| Flyway checksum | `null` |

本 Sprint 未执行真实 MySQL Migration，不得标记为 `CANONICAL_IMMUTABLE` 或 `EPHEMERAL_MYSQL8_VALIDATED`。

## 9. 剩余风险

1. V2.6.1 尚未完成真实 MySQL Fresh/V2.6.0 Upgrade 验收；
2. Transition 已纳入 Graph Hash 和发布校验，但尚无面向管理员的受控配置写 API；
3. WF3.2 只允许 `assignment_rule_type=USER` 且配置包含唯一 `userId`；组织、岗位、候选池、认领和委托尚未实现；
4. REJECT 回退、指定节点返回、会签、条件路由、并行和子流程未实现；
5. TaskAction 暂通过 Task 间接关联 NodeExecution，独立动作运行证据字段可在后续 Migration 评审；
6. 真实 MySQL 上的双线程事务锁竞争和故障注入仍需专项验收；
7. Investment 三重一大尚未接入，Workflow 仍不得直接修改 Investment 状态。

## 10. 下一步建议

先执行独立的 V2.6.1 真实 MySQL/Flyway 验收：Fresh、V2.6.0 Upgrade、strict validate、二次 no-op、Schema 指纹、外键/CHECK/唯一键负向测试及真实并发事务测试。通过并晋级后，再进入 WF3.3 任务分配策略；不得提前进入 Investment 流程改造。
