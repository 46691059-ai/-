# Workflow Resolver Version Routing Framework实施报告

> Sprint：2-3.7-WF3.6.4
> 基线：V2.6.2 + WF3.6.1—WF3.6.3
> 结论：EXPLICIT_USER_V1的Instance级版本冻结与精确路由已实现。

## 1. 修改文件清单

### Domain新增

- `ResolverMode.java`
- `ResolverStatus.java`
- `ResolverContractHash.java`
- `ResolverVersionBinding.java`

### Domain增强

- `AssignmentResolverDescriptor.java`
- `AssignmentResolverRegistryException.java`
- `ResolverRegistry.java`
- `ExplicitUserResolver.java`
- `WorkflowInstance.java`

### Application与API

- `WorkflowAssignmentResolverApplicationService.java`
- `WorkflowRuntimeApplicationService.java`
- `WorkflowLinearExecutionApplicationService.java`
- `AssignmentResolverDetail.java`
- `WorkflowResolverBindingDetail.java`
- `WorkflowRuntimeController.java`

### Persistence

- `WorkflowInstanceEntity.java`
- `WorkflowEntityMapper.java`

### Migration治理

- `V2.6.3__freeze_workflow_instance_resolver_version.sql`
- `migration-inventory.yml`
- `SHA256SUMS`
- `database/migration/mysql/README.md`

### 测试

- `WorkflowAssignmentResolverRegistryTest.java`
- `WorkflowAssignmentResolverApplicationServiceTest.java`
- `WorkflowLinearExecutionApplicationServiceTest.java`
- `WorkflowResolverVersionBindingApplicationTest.java`
- `WorkflowResolverVersionRoutingMigrationContractTest.java`

## 2. 数据库变化

必须新增V2.6.3候选。原因是Resolver绑定必须跨事务、重启和部署持久化；内存Registry或Task Snapshot不能证明尚未创建的后续Task应继续使用哪个版本。

仅对`workflow_instance`增加：

| 字段 | 含义 |
| --- | --- |
| `resolver_code` | Instance冻结的Resolver业务代码 |
| `resolver_version` | Instance冻结的Resolver契约版本 |
| `resolver_contract_hash` | Instance冻结的实现契约SHA-256 |

同时增加组合查询索引、完整性CHECK、线性Instance强制绑定CHECK和Hash格式CHECK。

兼容策略：

- 既有`MULTI_NODE_LINEAR_V1`实例回填`EXPLICIT_USER / EXPLICIT_USER_V1`及固定Contract Hash；这是V2.6.2前唯一可执行线性Resolver。
- Legacy单节点实例保留NULL，不伪造结构化绑定。
- 不修改`workflow_task_assignment_snapshot`，不重新执行历史Task Resolver。

## 3. Resolver版本链路

```text
Workflow启动
 -> Registry按Code + Version + Contract Hash精确校验
 -> 生成ResolverVersionBinding
 -> 保存WorkflowInstance
 -> NodeExecution
 -> AssignmentStrategy
 -> 读取Instance ResolverVersionBinding
 -> Registry精确查找Frozen Resolver
 -> CandidatePool
 -> Task
 -> AssignmentSnapshot
```

Registry现在支持：

- 按ResolverCode查询；
- 按ResolverCode + Version + Contract Hash精确查询；
- 按Instance Binding查询；
- ACTIVE、enabled、Contract Hash、Strategy和Mode校验；
- 同Code多版本静态共存，但禁止模糊选择；
- 不存在、版本不匹配、Hash漂移、非ACTIVE和禁用均阻断。

禁止fallback、自动升级、自动降级和“当前最新版本”替换。

## 4. API列表

| 方法 | URL | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/workflow/resolvers` | `workflow:view` | 查询Code、Version、Mode、Contract Hash和Status |
| GET | `/workflow/instances/{instanceId}/resolver-binding` | `workflow:view` | 查询Instance冻结绑定 |
| GET | `/workflow/tasks/{taskId}/assignment` | `workflow:view` | 查询原Assignment Snapshot |
| GET | `/workflow/tasks/{taskId}/assignment/resolution` | `workflow:view` | 查询冻结解析结果，不重新解析 |

未提供注册、版本切换、启停、回退或动态规则API。

## 5. 测试结果

环境：Java 21.0.12、Maven 3.9.9。

- Java编译：通过，442个主源码文件。
- 定向测试：29通过，0失败，0错误。
- 全量测试：292通过，0失败，0错误，0跳过。
- Spring Boot上下文：通过。
- Domain纯净检查：通过。

覆盖：

1. EXPLICIT_USER_V1的USER、DIRECT、CandidatePool、Task和Snapshot一致；
2. 启动时生成Instance Resolver绑定；
3. 后续节点继续读取Instance绑定；
4. 新V2测试Resolver与V1并存时，旧绑定仍精确选择V1；
5. Contract Hash不一致拒绝；
6. Resolver/Version不存在拒绝；
7. DEPRECATED等非ACTIVE状态拒绝；
8. Legacy Task继续只读兼容；
9. V2.6.3不修改Assignment Snapshot的Migration契约。

## 6. Migration状态

- 文件：`V2.6.3__freeze_workflow_instance_resolver_version.sql`
- SHA-256：`35c4714708d51ba00f4cdc79b04bea4d897a7e6c72624ec595c9df147f1f41e6`
- Asset：`CANDIDATE`
- Execution：`NOT_EXECUTED`
- Flyway checksum：`null`

本Sprint未执行真实MySQL Migration，不得标记为CANONICAL或VALIDATED。

## 7. 剩余风险

1. V2.6.3尚未经过Fresh和V2.6.2 Upgrade真实MySQL/Flyway验收，当前不能晋级。
2. 新代码部署前必须先执行V2.6.3，否则`workflow_instance`字段映射与数据库不一致。
3. Legacy单节点实例没有结构化Resolver绑定，查询绑定接口会明确返回无绑定错误。
4. 当前生产Registry只有EXPLICIT_USER_V1；同Code多版本的环境级生效时间路由尚未实现。
5. DEPRECATED/RETIRED当前均阻止执行，存量版本退休前必须确认没有未完成Instance。
6. Mockito动态Agent存在未来JDK兼容警告，不影响本次测试结果。

## 8. 下一步建议

下一步应仅执行V2.6.3真实MySQL/Flyway Fresh与Upgrade验收，验证回填、CHECK、Schema Fingerprint和二次migrate no-op。验收前不要进入ROLE/POSITION/ORG，也不要将候选Migration提前晋级。
