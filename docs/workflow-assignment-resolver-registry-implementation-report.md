# Workflow Assignment Resolver Registry实施报告

> Sprint：2-3.7-WF3.6.2
> 基线：V2.6.2 + WF3.6.1
> 结论：启动期静态、运行期只读的Resolver Registry已实现；仅注册USER + DIRECT。

## 1. 修改文件清单

### Domain新增

- `AssignmentResolverDescriptor.java`
- `ResolverCode.java`
- `ResolverVersion.java`
- `ResolverRegistry.java`
- `AssignmentResolverRegistryException.java`

### Domain修改

- `AssignmentResolver.java`
- `ExplicitUserResolver.java`

### Application与接口新增

- `WorkflowAssignmentResolverApplicationService.java`
- `AssignmentResolverDetail.java`
- `WorkflowResolverController.java`

### Infrastructure新增

- `WorkflowAssignmentResolverConfiguration.java`

### 调用链修改

- `WorkflowRuntimeApplicationService.java`
- `WorkflowLinearExecutionApplicationService.java`

### 测试新增

- `WorkflowAssignmentResolverRegistryTest.java`
- `WorkflowAssignmentResolverApplicationServiceTest.java`

## 2. 数据库变化

无数据库变化。Registry是代码级启动配置，不保存动态状态。V2.6.2继续冻结，未创建V2.6.3。

## 3. Resolver架构

```text
Spring启动配置
  -> 收集AssignmentResolver Bean
  -> 构建不可变ResolverRegistry
  -> Application Service按strategyType + expectedVersion选择
  -> ExplicitUserResolver
  -> CandidatePool
  -> Task + AssignmentSnapshot
```

- `ResolverCode`：稳定业务键，当前为`EXPLICIT_USER`。
- `ResolverVersion`：不可变契约版本，当前为`EXPLICIT_USER_V1`。
- `AssignmentResolverDescriptor`：描述代码、版本、策略类型和启用状态。
- `ResolverRegistry`：启动时校验重复、实现匹配和策略唯一性，运行时不允许注册或修改。
- `WorkflowAssignmentResolverApplicationService`：将Registry异常转换为统一业务异常，并提供只读查询。

异常覆盖：未注册、禁用、版本不匹配、空策略及非法注册。

## 4. API列表

| 方法 | URL | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/workflow/resolvers` | `workflow:view` | 查询当前启用的Resolver描述信息 |
| GET | `/workflow/tasks/{taskId}/assignment` | `workflow:view` | 查询冻结Assignment Snapshot |
| GET | `/workflow/tasks/{taskId}/assignment/resolution` | `workflow:view` | 查询冻结Resolver结果，不重新解析 |

未提供动态注册、启停、版本切换或规则配置接口。

## 5. 测试结果

环境：Java 21.0.12、Maven 3.9.9。

- Java编译：通过，437个主源码文件。
- 定向测试：26通过，0失败，0错误。
- 全量测试：286通过，0失败，0错误，0跳过。
- Spring Boot上下文：通过。
- Domain纯净检查：通过。

覆盖USER注册与发现、版本校验、未注册/禁用/非法Resolver拒绝、USER + DIRECT任务链路、Legacy只读兼容及历史快照不重算。

## 6. Migration状态

- V2.5.0—V2.6.2：未修改。
- V2.6.3：未创建、未登记、未执行。

## 7. 风险

1. Registry当前按Strategy Type唯一选择Resolver；未来同类型多版本并存需先设计版本路由规则，不能直接注册第二个USER实现。
2. 启停状态来自启动期代码描述，不支持运行时管理；这是本Sprint的安全边界。
3. Resolver版本仍冻结在Snapshot的`audit_info`中；如需数据库级索引，应使用后续增量Migration。
4. ROLE/POSITION/ORG没有实现、Bean或注册项。
5. Mockito动态Agent存在未来JDK兼容警告，不影响本次结果。

## 8. 下一步建议

保持Registry只读。在进入任何ROLE/POSITION/ORG实现前，先冻结同类型多版本选择、主数据Resolver端口、候选上限、超时和数据权限规则；具体Task审批权仍必须由任务归属校验，不能由RBAC或Resolver注册状态替代。
