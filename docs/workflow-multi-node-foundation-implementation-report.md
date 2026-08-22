# Workflow 多节点运行基础模型实施报告

## 1. Sprint结论

- Sprint：2-3.7-WF3.1
- 实施结论：`IMPLEMENTED / TESTED`
- Migration：`V2.6.0 CANDIDATE / NOT_EXECUTED`
- 真实 MySQL：未执行，按 Sprint 约束留待独立验收
- 边界：未修改 Investment、V2.5.0—V2.5.5、现有任务状态机或流程启动逻辑

### 1.1 修改文件清单

新增主代码：

- `application/command/CreateWorkflowNodeExecutionCommand.java`
- `application/command/UpdateWorkflowNodeExecutionCommand.java`
- `application/service/WorkflowMultiNodeRuntimeApplicationService.java`
- `domain/model/WorkflowTransition.java`
- `domain/model/WorkflowNodeExecution.java`
- `domain/repository/WorkflowTransitionRepository.java`
- `domain/repository/WorkflowNodeExecutionRepository.java`
- `infrastructure/persistence/entity/WorkflowTransitionEntity.java`
- `infrastructure/persistence/entity/WorkflowNodeExecutionEntity.java`
- `infrastructure/persistence/mapper/WorkflowTransitionMapper.java`
- `infrastructure/persistence/mapper/WorkflowNodeExecutionMapper.java`
- `infrastructure/persistence/WorkflowTransitionRepositoryImpl.java`
- `infrastructure/persistence/WorkflowNodeExecutionRepositoryImpl.java`
- `interfaces/rest/WorkflowTransitionController.java`
- `interfaces/rest/WorkflowNodeExecutionController.java`

修改主代码：`infrastructure/persistence/WorkflowEntityMapper.java`。

新增测试：

- `WorkflowMultiNodeDomainTest.java`
- `WorkflowMultiNodeMigrationContractTest.java`
- `WorkflowMultiNodeRepositoryAdapterTest.java`
- `WorkflowMultiNodeApplicationServiceTest.java`

修改测试：`WorkflowEntityMappingTest.java`、`WorkflowPermissionContractTest.java`。

数据库与治理资产：

- 新增 `V2.6.0__create_workflow_multi_node_foundation.sql`；
- 更新 `migration-inventory.yml`、`SHA256SUMS`、Migration `README.md`；
- 新增本实施报告。

## 2. 代码审计与结构缺口

| 现有结构 | 审计结论 | 本次处理 |
|---|---|---|
| `workflow_node` | 有节点定义、顺序和预留类型，但没有显式有向边 | 新增独立 `workflow_transition`，不改原表 |
| `workflow_instance` | 只有单个 `current_node_id`，没有节点访问历史 | 新增独立 `workflow_node_execution`，不改原表 |
| `workflow_task` | 任务直接关联 instance/node，没有 node execution 引用 | 本 Sprint 保持兼容，关联字段留待后续 Migration |
| `workflow_task_action` | 动作会直接终结当前单节点实例 | 本 Sprint 不修改；自动推进尚未接入 |

现有发布校验仍要求一个启用的 `SINGLE APPROVAL` 节点，现有流程运行行为因此不受本次基础模型影响。

## 3. 数据库变化

候选文件：`database/migration/mysql/V2.6.0__create_workflow_multi_node_foundation.sql`

### 3.1 workflow_transition

记录已冻结 Workflow Version 内的显式节点关系，核心字段包括：

- 版本、稳定迁移编码及名称；
- 来源节点、目标节点；
- `APPROVE/REJECT` 触发类型；
- `DIRECT/CONDITIONAL_RESERVED` 路由类型；
- 优先级、预留条件配置、启用状态；
- 完整审计、逻辑删除、`delete_token` 和乐观锁字段。

约束覆盖：版本内编码唯一、来源路由唯一、同版本复合外键、自环阻断、枚举、条件配置、优先级、逻辑删除和乐观锁。

### 3.2 workflow_node_execution

记录实例中每次节点访问，核心字段包括：

- 实例、版本、节点及节点名称/编码快照；
- `visit_no`；
- 上一次节点执行与来源 Transition；
- `CREATED/ACTIVE/COMPLETED/REJECTED/CANCELLED/FAILED` 状态；
- 进入、激活、完成时间，操作人、结果、失败编码和 TraceId；
- 完整审计、逻辑删除、`delete_token` 和乐观锁字段。

约束覆盖：实例节点访问唯一、同实例前序执行、同版本节点/Transition、状态与时间一致性、终态结果、失败编码、逻辑删除和乐观锁。

本 Migration 只创建两张新表，没有 `ALTER` 现有 Workflow 表。

## 4. Domain模型

### WorkflowTransition

- 纯 Java Record，不依赖 Spring、MyBatis 或 Entity；
- 定义 `TriggerType` 与 `RouteType`；
- 阻断自环、非法优先级、DIRECT 条件配置和空的预留条件配置；
- 本 Sprint 不执行条件表达式。

### WorkflowNodeExecution

- 纯 Java Record，不依赖基础设施；
- 支持 `CREATED -> ACTIVE -> COMPLETED/REJECTED/CANCELLED/FAILED`；
- CREATED 可直接因撤回或失败进入终态；
- 终态不可重新打开；
- 每次状态变化产生新领域值并递增乐观锁版本；
- 失败状态必须携带稳定、非敏感 `failureCode`。

## 5. Repository设计

新增 Domain Interface：

- `WorkflowTransitionRepository`：按 ID、版本、来源节点查询及保存；
- `WorkflowNodeExecutionRepository`：按 ID/实例查询、保存并执行 CAS 状态更新。

Infrastructure Adapter 使用现有 MyBatis Plus、统一审计初始化和逻辑删除规范。数据库唯一键冲突转换为业务冲突；节点执行状态更新以 `id + expectedVersion + deleted=0` 为条件。

## 6. Application能力

`WorkflowMultiNodeRuntimeApplicationService` 提供：

- 查询指定 Definition/Version 的 Transition；
- 查询实例节点执行轨迹；
- 内部创建节点执行记录；
- 内部更新节点执行状态。

内部写操作没有 REST 入口。创建时校验实例数据权限、实例运行状态、节点版本、前序执行、Transition 方向及 APPROVE/REJECT 触发一致性；更新时校验数据权限和乐观锁。本 Sprint 不读取 Transition 自动创建下一任务。

## 7. API列表

| 方法 | URL | 权限 | 用途 |
|---|---|---|---|
| GET | `/workflow/definitions/{definitionId}/versions/{versionId}/transitions` | `workflow:definition:view` | 查询流程节点关系 |
| GET | `/workflow/instances/{instanceId}/node-executions` | `workflow:view` | 查询实例节点执行轨迹 |

未提供创建、更新或删除 Transition/NodeExecution 的人工 REST 接口。

## 8. 测试结果

- Java：21.0.12
- Maven：3.9.11
- 编译：通过
- 新增测试：领域状态约束、Migration 合同、唯一键冲突转换、Entity/Repository 映射、乐观锁更新、事务回滚、只读事务及 API 权限契约
- Spring Boot 上下文：通过
- 后端全量测试：`250 tests, 0 failures, 0 errors, 0 skipped`
- 构建结论：`BUILD SUCCESS`

测试过程中存在 Mockito 动态 Agent 的 JDK 未来兼容警告，不影响本次结果，未在本 Sprint 修改测试基础设施。

## 9. Migration治理

| 项目 | 状态 |
|---|---|
| 版本 | V2.6.0 |
| 文件 | `V2.6.0__create_workflow_multi_node_foundation.sql` |
| 资产状态 | `CANDIDATE` |
| 执行状态 | `NOT_EXECUTED` |
| SHA-256 | `76f2113573eac6af106fadb1681774e49a6f15713860a6af7b37f8d9ebc5c1fa` |
| Flyway checksum | `null` |

`migration-inventory.yml`、`SHA256SUMS` 和 Migration README 已同步。本 Sprint 未连接真实 MySQL，也没有提前晋级资产。

## 10. 剩余风险

1. V2.6.0 尚未经过 Fresh/Upgrade 真实 MySQL 8 Flyway 验收；
2. `workflow_task` 尚未直接关联 `workflow_node_execution`；
3. 现有启动和任务动作链路尚未创建节点执行，也不会自动推进；
4. 版本内容哈希尚未覆盖 Transition，现有发布仍保持 V2.5 单节点规则；
5. Transition 暂无管理写接口，只能作为后续定义域能力的数据底座；
6. 会签、条件路由、自动选人均未实现；
7. Mockito 动态加载 Agent 在未来 JDK 版本可能需要测试基础设施治理。

## 11. 下一步建议

先执行独立 Sprint 对 V2.6.0 进行 Fresh 与 V2.5.5 Upgrade 的真实 MySQL 验收。验收通过后再设计 Transition 编辑/发布校验、GRAPH_V2 内容哈希及任务到 NodeExecution 的关联；不得直接进入自动推进或 Investment 改造。
