# Workflow ROLE Runtime Activation Gate Implementation Report

## 1. 最终状态

- `ROLE_RUNTIME_ACTIVATION_READY`
- `ROLE_RUNTIME_DISABLED`
- `EXPLICIT_USER_V1: ACTIVE`
- `ROLE_DIRECTORY_V1: PREPARED / NON_EXECUTABLE`

## 2. 修改文件清单

新增 Domain：

- `RoleRuntimeActivationStatus`
- `RoleRuntimeActivationRequest`
- `RoleRuntimeActivationDecision`
- `RoleRuntimeActivationEvidence`
- `RoleRuntimeActivationGateResult`
- `RoleRuntimeActivationCanonical`
- `RoleRuntimeActivationGate`
- `RoleRuntimeActivation`
- `RoleRuntimeActivationSnapshot`
- `RoleRuntimeActivationApprovedRegistry`

新增测试：

- `WorkflowRoleRuntimeActivationGateTest`

新增文档：

- `docs/workflow-role-runtime-activation-gate-design.md`
- `docs/workflow-role-runtime-activation-gate-implementation-report.md`

未修改 Workflow Runtime 执行链、Investment、V2.6.9—V2.6.12 或 Resolver
Registry 的既有状态。

## 3. Activation模型

Activation Aggregate 固定持有 Request、Status、Activation Hash、Reason 和
不可变 Decision 集合。Gate 输入使用独立 Evidence 对象，避免 Request 自证。
Approved Snapshot 冻结请求与三方批准证据，Approved Registry 只允许同一
Activation Hash 注册一次。

## 4. 状态机

- `DRAFT -> ELIGIBLE`：全部 Gate 规则通过。
- `DRAFT -> BLOCKED`：任一规则失败。
- `ELIGIBLE -> APPROVED`：Business Owner、Security/Audit、Release Approver
  三类批准齐全。
- `ELIGIBLE -> REJECTED`：任一有效审批角色拒绝。
- `APPROVED -> REVOKED`：发布批准撤销。
- `ENABLED`：无实现、无转换、不可达。

缺少 Approval 时无法生成 Snapshot；Workflow Administrator 决策和重复角色
批准均被拒绝。

## 5. Gate与Hash规则

Gate 顺序覆盖 Resolver Registry、Resolver Contract、Binding、Candidate、
Directory、Scope/Effective Time、SoD 与 Audit。所有失败均 Fail Closed，无
fallback。

`ROLE_RUNTIME_ACTIVATION_CANONICAL_V1` 覆盖 Resolver Contract Hash、Binding
Hash、Candidate Hash、Directory Contract Hash、Business Scope 与 Effective
Time。测试证明任一关键因素变化均产生新 Hash。

## 6. API情况

新增 API：0。新增 Controller：0。

未开放 Activation 配置、批准、Resolver 状态切换或 Runtime Enable 接口。

## 7. 数据库变化

数据库变化：0。Migration：0。

原因：本 Sprint 只建立 Activation Gate 和不可变内存批准证据；尚未授权跨
进程持久化或真实 Runtime 激活。

## 8. 测试结果

新增 9 项 Activation Domain 测试，覆盖：

1. 三方 RACI 批准与 Snapshot。
2. 缺少 Approval 拒绝。
3. Resolver Contract Hash 漂移拒绝。
4. Binding/Candidate/Directory Hash 漂移拒绝。
5. SoD、Audit、Scope 异常拒绝。
6. 重复 Approval 与 Workflow Administrator 批准拒绝。
7. Reject/Revoke 状态阻断。
8. EXPLICIT_USER/Legacy 兼容及 ROLE non-executable。
9. Domain 无 Spring/MyBatis/Entity 依赖。

Java 21 编译与 Spring Boot 上下文通过。后端全量测试：416 项通过，0 失败、
0 错误、0 跳过。

## 9. Migration状态

- 新增 Migration：0
- V2.6.9—V2.6.12：保持 `CANONICAL_IMMUTABLE`
- Migration SHA：34/34 匹配，无历史摘要漂移

## 10. 风险与下一步建议

- Activation Snapshot 当前仅内存存在，不能作为生产跨重启批准记录。
- SoD/Audit Evidence 尚未连接真实安审服务。
- Approved Registry 不代表 Enabled Registry，调用方不得据此创建 ROLE Task。

下一步应先单独设计 Activation append-only 持久化与正式启用发布门禁；本
Sprint 停止于 `ROLE_RUNTIME_ACTIVATION_READY`，不进入 ROLE Task、Candidate
Pool Runtime、Claim Runtime 或 Investment Integration。
