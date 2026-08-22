# Workflow ROLE Runtime Sandbox Execution Design

## 1. 定位与边界

`ROLE_RUNTIME_SANDBOX` 是 ROLE Runtime 的纯内存、合成数据实验室，用于验证
Resolver、Binding、Eligibility、Activation、Candidate Draft 与 Claim Simulation
之间的契约闭环。它不是生产 Runtime，也不具备任何持久化或业务执行能力。

Sandbox 只接受：

- `SANDBOX-` 前缀 Sandbox ID
- `SANDBOX_ROLE_` 前缀角色
- `SANDBOX_ORG_` 前缀组织
- `SANDBOX_USER_` 前缀候选人
- 固定 Effective Time、Revision 与 Fake Audit Evidence

任何非 Sandbox 标识在适配器边界 Fail Closed。

## 2. Sandbox架构

核心对象：

- `SandboxRoleRuntimeContext`：冻结 Sandbox、角色、组织、时间、Resolver 和限制。
- `FakeRoleDirectoryAdapter`：固定、不可刷新、无 fallback 的目录实现。
- `RoleRuntimeSandboxExecutor`：封闭的纯内存编排器。
- `SandboxAuditEvidence`：每一步输入输出 Hash 台账。
- `SandboxClaimSimulation`：合成领取结果，不是 `TaskClaim`。
- `SandboxExecutionResult`：只暴露 Hash、状态、证据与模拟结果。

Executor 唯一字段依赖为 Fake Directory Adapter，不依赖 Repository、Mapper、
Entity、Spring 或 MyBatis。

## 3. 执行链路

```text
FakeRoleDirectoryAdapter
  -> ROLE_DIRECTORY_V1 Resolver（PREPARED metadata contract）
  -> RoleCandidateAdapter（Candidate Draft）
  -> RoleResolverBindingProvider（Binding Proposal）
  -> RoleRuntimeEligibilityValidator
  -> RoleRuntimeActivationGate + 三方 Fake Approval
  -> RuntimeBindingCandidate（治理对象）
  -> Candidate Pool Draft（不落库）
  -> SandboxClaimSimulation（不落库）
```

链路中没有 WorkflowInstance、WorkflowTask、生产 CandidatePool、TaskClaim 或
Investment 对象。

## 4. 状态机

严格线性状态：

```text
CREATED
 -> DIRECTORY_RESOLVED
 -> BINDING_GENERATED
 -> ELIGIBILITY_PASSED
 -> ACTIVATION_APPROVED
 -> CANDIDATE_GENERATED
 -> COMPLETED
```

任一非终态可以进入 `FAILED`；COMPLETED 后禁止失败或再次推进。状态只能由
Sandbox Executor 内部推进，外部无修改接口。

## 5. Fake Adapter规则

- 构造时冻结 roleCode、organizationId、effectiveAt、revision 和完整成员集合。
- 每次读取返回相同原子目录证据与 Hash。
- 查询范围必须精确一致。
- Enterprise、Role、Organization、User 必须为 Sandbox 标识。
- 空成员由 Resolver 拒绝。
- 不支持动态刷新、fallback、默认负责人或截断候选人。

## 6. Hash验证

复用并串联：

- `ROLE_BINDING_CANONICAL_V1`
- `ROLE_RUNTIME_CANONICAL_V1`
- `ROLE_RUNTIME_ACTIVATION_CANONICAL_V1`

Audit Evidence 记录 Directory、Candidate Adapter、Binding、Eligibility、Activation、
Runtime Binding Candidate、Candidate Draft 和 Claim Simulation 的 Hash。同输入
必须稳定；成员、Contract、Binding 或 Activation 输入变化必须改变对应 Hash。

## 7. Claim Simulation边界

Claim Simulation 只从冻结 Candidate Draft 中选择排序后的第一个合成候选人，
并产生 `CLAIM_SIMULATION_V1` Hash。它：

- 不读取 `workflow_task`。
- 不创建 `workflow_task_claim`。
- 不调用 Claim Application Service。
- 不修改 Candidate Pool。
- 不代表真实领取资格或审批权。

## 8. 禁止生产运行

- ROLE Resolver 仍为 PREPARED/non-executable。
- EXPLICIT_USER_V1 仍是唯一 ACTIVE 业务 Resolver。
- Sandbox 不注册 Spring Bean，不暴露 Controller/API。
- Sandbox 无任何数据库或 Repository 依赖。
- 不创建 Migration，不修改 V2.6.9—V2.6.12。
- 不读取真实用户、组织、角色或业务数据。

## 9. 风险

- Sandbox 只证明契约组合，不证明生产 Directory、权限、并发或持久化能力。
- Claim Simulation 不执行实时资格、DataScope 或真实 SoD 门禁。
- Fake Approval 仅用于验证 Activation 状态链，不构成上线批准。
- 任何把 Sandbox Result 转换为生产 Runtime 对象的适配器均应视为安全违规。

冻结状态：`ROLE_RUNTIME_SANDBOX_READY / ROLE_RUNTIME_DISABLED`。
