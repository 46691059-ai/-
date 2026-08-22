# Workflow ROLE Runtime Sandbox Execution Implementation Report

## 1. 完成状态

- `ROLE_RUNTIME_SANDBOX_READY`
- `ROLE_RUNTIME_DISABLED`
- `ROLE_DIRECTORY_V1: PREPARED / NON_EXECUTABLE`
- `EXPLICIT_USER_V1: ACTIVE`

## 2. 修改文件清单

新增 Sandbox Domain：

- `SandboxExecutionStatus`
- `SandboxExecutionTrace`
- `SandboxRoleRuntimeContext`
- `SandboxExecutionResult`
- `SandboxAuditEvidence`
- `SandboxClaimSimulation`
- `SandboxFault`
- `SandboxHash`
- `FakeRoleDirectoryAdapter`
- `RoleRuntimeSandboxExecutor`

新增测试：

- `WorkflowRoleRuntimeSandboxExecutionTest`

新增文档：

- `docs/workflow-role-runtime-sandbox-execution-design.md`
- `docs/workflow-role-runtime-sandbox-execution-implementation-report.md`

## 3. Sandbox架构与执行链路

实现固定 Fake Directory 到 Claim Simulation 的纯内存闭环。Binding Proposal、
Eligibility 与 Activation 使用现有冻结契约；Runtime Binding Candidate、Candidate
Pool Draft 和 Claim Simulation 均为非持久化治理对象。

所有异常由 Executor 捕获并返回 FAILED + Hash 化审计证据，不进行 fallback。

## 4. Hash验证

- 同一输入重复执行，Binding Hash、Candidate Hash、Activation Hash 和 Runtime
  Hash 完全一致。
- 候选成员变化导致 Candidate 与 Activation Hash 变化。
- Contract Hash 漂移在 Resolver 门禁失败。
- Binding Hash 漂移在 Eligibility Gate 失败。
- Audit Ledger 保存八个步骤的 SHA-256。

## 5. 禁止项验证

- 真实 WorkflowInstance：0
- 真实 WorkflowTask：0
- 真实 CandidatePool：0
- 真实 TaskClaim：0
- Controller/API：0
- Repository/Mapper/Entity依赖：0
- Resolver Registry状态变化：0
- Investment变更：0
- Migration：0

Fake Adapter 拒绝真实角色、组织和用户标识；Sandbox Executor 唯一依赖为
`FakeRoleDirectoryAdapter`。

## 6. 测试结果

新增测试覆盖：

1. 完整 Sandbox 成功链路。
2. Directory 空成员失败。
3. Contract Hash 错误失败。
4. Binding Hash 漂移失败。
5. Activation 未批准失败。
6. Candidate Limit 拒绝且不截断。
7. 三类 Canonical Hash 稳定性/变化性。
8. EXPLICIT_USER_V1 回归。
9. Legacy/无生产依赖兼容。
10. 禁止生产对象生成。

Java 21 编译及 Spring Boot 上下文通过；后端全量测试 425 项通过，0 失败、
0 错误、0 跳过。Sandbox 新增测试 9 项全部通过。`git diff --check` 通过。

## 7. Migration状态

- 新增 Migration：0
- V2.6.9—V2.6.12：未修改
- V2.6.13：未创建
- 历史 Migration SHA：34/34 匹配，无摘要漂移

## 8. 风险与下一步

Sandbox 不提供生产可用性证明，尤其不覆盖真实 Directory SLA、数据权限、
生产 SoD、事务、锁或并发 Claim。下一步若有明确授权，应先设计生产启用
门禁与真实 Directory 非生产联调；不得直接创建 ROLE Task、Candidate Pool
Runtime、Claim Runtime 或 Investment Integration。
