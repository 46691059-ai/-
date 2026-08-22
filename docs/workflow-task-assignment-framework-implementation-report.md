# Workflow Task Assignment Framework 实施报告

> Sprint：2-3.7-WF3.4
> 基线：V2.6.1 / WF3.3 Assignment Design Frozen
> 状态：`IMPLEMENTED / V2.6.2_CANDIDATE_NOT_EXECUTED`

## 1. 修改文件清单

### 领域与仓储契约

- `domain/assignment/AssignmentStrategy.java`
- `domain/assignment/AssignmentContext.java`
- `domain/assignment/AssignmentResult.java`
- `domain/assignment/AssignmentSnapshot.java`
- `domain/assignment/ExplicitUserAssignmentStrategy.java`
- `domain/repository/WorkflowTaskAssignmentSnapshotRepository.java`

### Application与接口

- `application/service/WorkflowRuntimeApplicationService.java`
- `application/service/WorkflowLinearExecutionApplicationService.java`
- `application/service/WorkflowTaskAssignmentApplicationService.java`
- `application/vo/WorkflowTaskAssignmentDetail.java`
- `interfaces/rest/WorkflowTaskController.java`

### Infrastructure

- `infrastructure/persistence/entity/WorkflowTaskAssignmentSnapshotEntity.java`
- `infrastructure/persistence/mapper/WorkflowTaskAssignmentSnapshotMapper.java`
- `infrastructure/persistence/WorkflowTaskAssignmentSnapshotEntityMapper.java`
- `infrastructure/persistence/WorkflowTaskAssignmentSnapshotRepositoryImpl.java`

### 数据库与治理资产

- `database/migration/mysql/V2.6.2__create_workflow_task_assignment_snapshot.sql`
- `database/migration/mysql/SHA256SUMS`
- `database/migration/mysql/README.md`
- `database/flyway/migration-inventory.yml`

### 测试

- `WorkflowTaskAssignmentDomainTest.java`
- `WorkflowTaskAssignmentRepositoryTest.java`
- `WorkflowTaskAssignmentApplicationServiceTest.java`
- `WorkflowTaskAssignmentCreationIdempotencyTest.java`
- `WorkflowTaskAssignmentMigrationContractTest.java`
- `WorkflowLinearExecutionApplicationServiceTest.java`
- `WorkflowEntityMappingTest.java`

未修改Investment代码及V2.5.0—V2.6.1历史Migration。

## 2. 数据库变化

V2.6.2候选新增 `workflow_task_assignment_snapshot`，保存：

- Task、Instance、WorkflowVersion、Node及NodeExecution归属；
- `strategy_type`、`target_type`、`target_snapshot`；
- 规范化 `resolved_users` 与人数；
- `resolve_time`、`audit_info`、`trace_id`；
- 创建/更新审计字段、逻辑删除、`delete_token`和乐观锁版本。

同时为 `workflow_task` 增加任务归属复合唯一键，使快照通过复合外键证明Task与NodeExecution归属一致。表包含任务一对一唯一约束、查询索引、策略/目标CHECK、非空证据CHECK、逻辑删除CHECK和乐观锁CHECK。

V2.6.2 SHA-256：

`9750454efa98402c7d22503d80e16579e07c964a01f7b4baa5da933db01a998f`

当前状态为 `CANDIDATE / NOT_EXECUTED`，`flyway_checksum=null`。本Sprint未执行真实MySQL Migration。

## 3. 领域模型

| 模型 | 职责 |
| --- | --- |
| `AssignmentStrategy<T>` | 分配策略纯领域端口；冻结USER/ROLE/POSITION/ORG类型 |
| `AssignmentContext` | 冻结Task、实例、版本、节点、企业、发起人与解析审计上下文 |
| `AssignmentResult` | 返回规范化目标、唯一有序用户集合、解析时间和审计依据 |
| `AssignmentSnapshot` | 将上下文和解析结果组合成不可变持久化证据 |
| `ExplicitUserAssignmentStrategy` | 当前唯一可执行策略；输入正数userId并返回单用户结果 |

以上类型不依赖Spring、MyBatis或Entity。ROLE/POSITION/ORG没有Resolver或策略实现。

## 4. Assignment流程

新线性Task创建流程：

```text
NodeExecution创建
  -> 解析节点中的显式USER目标
  -> 构造AssignmentContext
  -> ExplicitUserAssignmentStrategy.resolve(userId)
  -> 生成AssignmentResult
  -> 生成AssignmentSnapshot
  -> 保存NodeExecution
  -> 保存Task
  -> 保存AssignmentSnapshot
  -> 更新Instance当前执行游标
```

上述写入位于现有 `@Transactional` Application边界。快照保存失败会回滚任务推进；重复流程启动沿用实例幂等键，在返回既有实例时不创建重复Task或快照。下一节点Task也使用同一分配链路。

V2.6.2之前的历史Task无结构化快照时，查询接口返回明确标记的 `LEGACY_TASK_FIELDS` 投影，不推断角色、岗位或组织来源。

## 5. API

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/workflow/tasks/{taskId}/assignment` | `workflow:view` | 查询结构化快照；Legacy任务返回兼容投影 |

接口同时强制Workflow Instance数据范围。未开放管理员配置、直接改派、候选认领或人员解析接口。

## 6. 测试结果

覆盖项目要求：

1. USER策略正向解析及非法userId拒绝；
2. Snapshot字段、时间、用户集合和版本冻结；
3. Snapshot保存失败触发事务回滚；
4. 重复启动返回既有实例，不重复创建Task或Snapshot；
5. 历史Task无快照时返回Legacy投影；
6. ORG等未实现策略在任务推进前被拒绝；
7. Repository映射、重复键异常转换、Entity表映射；
8. Migration字段、复合归属、约束及Investment隔离契约；
9. 查询数据范围和既有线性执行回归。

Java 21编译通过。后端全量测试：274项通过，0失败、0错误、0跳过；Spring Boot上下文启动通过。Migration清单24/24 SHA-256匹配。

## 7. 风险

1. V2.6.2尚未经过真实MySQL/Flyway Fresh与Upgrade验收，禁止晋级或部署。
2. 新代码要求数据库先应用V2.6.2；应用先发布会导致快照查询/写入表不存在。
3. 当前节点配置枚举与历史数据库尚未原生支持ROLE类型；ROLE仅为Assignment领域扩展类型，不能配置或运行。
4. `resolved_users`当前只承载单USER规范数组；多候选、认领、转派和目录修订不在本Sprint范围。
5. Legacy投影用于兼容读取，不构成补造的历史审计证据。

## 8. 下一步建议

先执行独立Sprint完成V2.6.2真实MySQL/Flyway双路径验收，包括结构、复合外键、唯一约束、负向测试、事务一致性和Schema fingerprint。验收通过后再评估后续候选池或Resolver Adapter，禁止直接进入ROLE/POSITION/ORG人员解析。
