# Workflow ROLE Runtime Execution Admission Framework 实施报告

## 1. 修改文件清单

新增 Domain 包 `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/admission/`：

- `RoleRuntimeExecutionAdmissionRequest.java`
- `RoleRuntimeExecutionAdmissionDecision.java`
- `RoleRuntimeExecutionAdmissionEvidence.java`
- `RoleRuntimeExecutionAdmissionStatus.java`
- `RoleRuntimeExecutionBlockReason.java`
- `RoleRuntimeExecutionAdmissionPolicy.java`
- `RoleRuntimeExecutionAdmissionValidator.java`
- `RoleRuntimeExecutionAdmissionValidation.java`
- `RoleRuntimeExecutionAdmissionCheck.java`
- `RoleRuntimeExecutionAdmissionFacts.java`
- `RoleRuntimeExecutionAdmissionEvidenceSource.java`
- `RoleRuntimeExecutionAdmissionHash.java`
- `ExecutableRuntimeBindingCandidate.java`
- `RoleRuntimeExecutionCapabilityPorts.java`
- `RoleRuntimeCapabilityReadiness.java`
- `RoleRuntimeCapabilityResult.java`
- `RoleRuntimeFeatureFlagPolicy.java`
- `RoleRuntimeCanaryScope.java`
- `RoleRuntimeKillSwitch.java`

新增 Application Service：

- `backend/src/main/java/cn/gov/enterprise/modules/workflow/application/service/RoleRuntimeExecutionAdmissionApplicationService.java`

新增测试与Fake Adapter：

- `backend/src/test/java/cn/gov/enterprise/modules/workflow/support/FakeRoleRuntimeExecutionCapabilityAdapters.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/domain/WorkflowRoleRuntimeExecutionAdmissionFrameworkTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/application/WorkflowRoleRuntimeExecutionAdmissionProductionDependencyScanTest.java`

新增本报告。未修改 Controller、Investment、Registry、Migration、数据库Inventory、配置或既有运行服务。

## 2. Current Canonical Baseline

- V2.6.14：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- SHA-256：`a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442`
- Flyway checksum：`-1734980808`
- `EXPLICIT_USER_V1`：`ACTIVE`
- `ROLE_DIRECTORY_V1`：`PREPARED / NON_EXECUTABLE`
- ROLE Runtime：`DISABLED`

本次没有重新执行WF5.14.2或WF5.14.3，没有修改当前Canonical资产。

## 3. Domain模型

Admission请求冻结Activation、Promotion、Binding、Candidate、Resolver、Directory、企业、业务范围、流程版本、节点、有效时点、Canary及Feature Flag事实。所有关键文本非空；Hash强制为64字符小写SHA-256；Resolver身份强制大写稳定键。

Decision只允许返回治理状态、阻断原因、Evidence和可选的`ExecutableRuntimeBindingCandidate`。Candidate构造时强制`runtimeEnabled=false`，且不包含WorkflowInstance、NodeExecution、Task、CandidatePool或Claim引用。

Evidence Source是只读端口，只加载已经冻结的治理事实，不刷新Directory或读取“最新配置”。

## 4. 状态机

实现合法主链：

```text
CREATED -> VALIDATING -> ELIGIBLE -> APPROVED_FOR_EXECUTION
```

异常状态：`BLOCKED / REJECTED / REVOKED / EXPIRED`。终态不能自动恢复；`REVOKED`和`EXPIRED`不能重新进入`ELIGIBLE`或`APPROVED_FOR_EXECUTION`。枚举不存在`ENABLED`状态。

## 5. 固定28项Validator

Validator严格按冻结顺序执行并在首个失败处停止：

1. Candidate存在；2. Snapshot状态；3. Promotion证据；4. Activation证据；
5. Activation未撤销；6. Promotion未撤销；7. Resolver Code；8. Resolver Version；
9. Resolver Contract Hash；10. Activation Hash；11. Promotion Hash；12. Binding Hash；
13. Candidate Hash；14. Directory Revision与Result Hash；15. Effective At；
16. Business Scope；17. Definition Version；18. Node Binding；19. Registry Descriptor；
20. Resolver Admission状态；21. Directory；22. Realtime Eligibility；23. DataScope；
24. SoD；25. Audit；26. Feature Flag；27. Kill Switch；28. Canary Scope。

每一步写入稳定序号、检查码和阻断原因。异常、空Capability结果、`DEGRADED`、`NOT_READY`和`BLOCKED`全部Fail Closed，无fallback、自动修正、重新解析或重新生成Candidate。

## 6. Capability抽象

`RoleRuntimeExecutionCapabilityPorts`提供纯Domain端口：

- `DirectoryCapabilityPort`
- `RealtimeEligibilityCapabilityPort`
- `DataScopeCapabilityPort`
- `SoDCapabilityPort`
- `AuditCapabilityPort`
- `FeatureFlagCapabilityPort`
- `KillSwitchCapabilityPort`
- `CanaryScopePort`

统一状态为`READY / NOT_READY / DEGRADED / BLOCKED`，仅`READY`放行。本Sprint只有测试Fake Adapter，不存在生产服务连接。

## 7. Feature Flag

抽象支持`GLOBAL / ENTERPRISE / DEFINITION_VERSION`语义。Fake实现只接受企业与精确Definition Version、Node匹配的`DEFINITION_VERSION`策略，不提供真实全局开关或生产配置入口。

## 8. Canary Scope

`RoleRuntimeCanaryScope`冻结`enterpriseId / definitionId / definitionVersionId / nodeId`。Request必须与自身Canary Scope完全一致，且Validator还需通过独立Canary端口确认该范围获准。

## 9. Kill Switch

`RoleRuntimeKillSwitch`实现`OPEN / CLOSED`抽象。只有`OPEN`允许新Admission；`CLOSED`Fail Closed。当前没有生产控制入口，也不修改任何历史证据。

## 10. Registry边界

Validator仅调用`ResolverRegistry.requireDescriptor`读取元数据。当前ROLE Admission只允许`PREPARED + enabled=false + contractHash一致`的Metadata检查；这不会使Resolver可执行。测试确认调用前后Descriptor列表和状态完全一致。

未实现`PREPARED -> EXECUTION_ELIGIBLE`或`EXECUTION_ELIGIBLE -> ACTIVE`。

## 11. ExecutableRuntimeBindingCandidate

通过28项校验后只生成内存治理结果`ExecutableRuntimeBindingCandidate`。它继承全部冻结事实并强制`runtimeEnabled=false`，不是WorkflowResolverBinding或任何运行对象。Application Service没有运行Repository或写服务依赖。

## 12. Canonical Hash

实现`ROLE_RUNTIME_EXECUTION_ADMISSION_CANONICAL_V1`，使用UTF-8、固定字段顺序、长度前缀字段编码和`Instant.toString()` UTC格式，输出64字符小写SHA-256。

覆盖Activation、Promotion、Binding、Candidate、Resolver、Directory、Enterprise、Business Scope、Definition、Node、EffectiveAt、Canary Scope和Feature Flag Policy。Hash不依赖数据库ID、审计时间、本地时区或Map遍历顺序。

## 13. Application链路

```text
Admission Request
  -> EvidenceSource加载冻结事实
  -> 28项Validator
  -> Admission Decision
  -> Admission Evidence
  -> ExecutableRuntimeBindingCandidate（仅通过时）
```

`RoleRuntimeExecutionAdmissionApplicationService`是未标注Spring注解的内部类，全部内存执行，不持久化、不注册Bean、不开放API。

## 14. 测试结果

- Java：`21.0.12`，编译通过；
- Maven：`3.9.9`；
- Admission专项测试：7项通过；
- Production Dependency Scan：2项通过；
- Domain纯净专项：1项通过；
- Spring Boot Context：通过；
- 后端全量：`461`项通过，`0`失败，`0`错误，`0`跳过。

专项覆盖附件要求的30类场景，包括全部READY、证据缺失/撤销/Hash漂移、7类Capability失败、Feature Flag、Kill Switch、Canary、Resolver缺失/非法、状态机终态、运行对象隔离和Registry不变。

## 15. Production Dependency Scan

自动扫描Admission Domain与Application Service，禁止依赖：

- Investment；
- 真实Role Directory Adapter；
- Spring/MyBatis/Entity；
- Task Controller；
- WorkflowInstance、NodeExecution、Task写Repository；
- CandidatePool写服务；
- Claim写服务。

同时确认无Admission Controller、无V2.6.15文件、V2.6.14 SHA保持不变。结果：`PASS`。

## 16. Migration状态

- Migration新增：`0`
- Migration修改：`0`
- V2.6.15：`不存在`
- Migration SHA：`36/36`匹配
- V2.6.14状态、SHA和Flyway checksum保持Canonical基线

## 17. V2.6.15 Persistence Decision

结论：`PERSISTENCE_REQUIRED`。

理由：当前纯内存框架适用于契约和治理逻辑验证，但生产Execution Admission必须支持跨进程恢复、Admission撤销、Canary审计、Kill Switch审计、监管追溯和故障恢复。复用V2.6.13/V2.6.14会混淆Activation、Promotion、Binding与Admission语义，也无法完整冻结Definition Version和Node Admission证据。

该结论不授权创建V2.6.15；后续必须单独完成数据库设计、Migration候选和真实MySQL验收。

## 18. 剩余风险

- 当前Evidence Source与所有Capability均为内存/Fake，未接生产数据源；
- `EXECUTION_ELIGIBLE`仍只有治理语义，没有Registry实现；
- V2.6.15尚未设计实现，Admission不能跨进程恢复；
- 真实Directory Revision Fence、SLA、DataScope、SoD和Audit尚未接入；
- 当前Feature Flag、Canary和Kill Switch均为抽象，不具备生产控制能力；
- 本次未进行任何ROLE实例、任务、候选池或Claim运行验证，这是刻意保持的边界。

## 19. 下一步建议

保持`ROLE_RUNTIME_DISABLED`。下一阶段应先独立冻结V2.6.15持久化模型及真实Capability契约，不得直接进入ROLE Task、Candidate Pool Runtime、Claim Runtime或Investment Integration。

最终状态：

- `ROLE_RUNTIME_EXECUTION_ADMISSION_FRAMEWORK_READY`
- `ROLE_RUNTIME_DISABLED`
