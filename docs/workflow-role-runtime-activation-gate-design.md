# Workflow ROLE Runtime Activation Gate Design

## 1. 设计状态

Sprint 2-3.7-WF5.8 建立 ROLE Runtime 正式启用之前的治理门禁。该门禁只回答
“是否具备提交激活批准的条件”和“批准证据是否完整”，不执行 Resolver、
不生成 Runtime Binding、不创建 Task/Candidate Pool/Claim，也不改变 Registry
运行状态。

冻结结论：`ROLE_RUNTIME_ACTIVATION_READY / ROLE_RUNTIME_DISABLED`。

## 2. Activation领域模型

### 2.1 RoleRuntimeActivationRequest

冻结以下输入：

- Resolver Code、Resolver Version、Resolver Contract Hash
- Binding Contract Hash
- Candidate Contract Hash
- Directory Contract Hash
- Effective Time
- Business Scope
- Requester

请求创建后不可修改；关键字段变化必须创建新请求并重新审批。

### 2.2 RoleRuntimeActivationDecision

每个决策冻结：Decision、Approver Role、Approver、Reason、Timestamp、Evidence
Hash。决策记录不可覆盖。

### 2.3 RoleRuntimeActivationSnapshot

只有完整通过三方 RACI 批准的 Activation 才能生成不可变 Snapshot。Snapshot
包含请求、Activation Hash、完整决策集合、批准时间和 Evidence Hash。

`APPROVED != ENABLED`。Snapshot 不具备运行能力。

## 3. 状态机

```text
DRAFT -> ELIGIBLE -> APPROVED
   |         |          |
   v         v          v
 BLOCKED   REJECTED   REVOKED

ENABLED：仅预留，WF5.8 无任何可达转换。
```

规则：

- DRAFT 只能由 Gate 评估为 ELIGIBLE 或 BLOCKED。
- ELIGIBLE 必须依次收齐业务、安审、发布三类批准，才能成为 APPROVED。
- 任一拒绝进入 REJECTED。
- APPROVED 可被发布批准人撤销为 REVOKED。
- BLOCKED、REJECTED、REVOKED 不允许生成 Snapshot。
- 不提供 fallback、自动批准、自动降级或 ENABLE 转换。

## 4. Activation Gate规则

固定校验顺序：

1. Resolver Registry 中 Code/Version 必须存在。
2. Resolver 状态必须为 PREPARED 或 ACTIVE；这只允许治理检查。
3. Resolver Contract Hash 必须一致。
4. Binding Contract Hash 必须一致。
5. Candidate Contract Hash 及 Candidate Rule 必须一致、有效。
6. Directory Contract Hash 必须一致。
7. Business Scope 与 Effective Time 必须一致。
8. SoD 检查必须通过。
9. Audit Evidence 必须完整。

任一步失败均返回 BLOCKED，并保留明确 Reason Code。后续规则不补偿前序失败。

## 5. RACI与审批边界

| 角色 | 职责 | Activation批准权 |
|---|---|---|
| Workflow Administrator | 维护定义域、查看治理状态 | 无 |
| Business Owner | 确认角色语义、业务范围与有效时间 | 必须确认 |
| Security/Audit | 确认SoD、审计证据和合同Hash | 必须确认 |
| Release Approver | 确认上线窗口与最终治理证据 | 最终批准/撤销 |

同一 Approver Role 只能提交一次决定；重复批准必须拒绝。

## 6. Hash治理

Canonical 版本：`ROLE_RUNTIME_ACTIVATION_CANONICAL_V1`。

Activation Request Hash 覆盖：

- Resolver Code/Version/Contract Hash
- Binding Contract Hash
- Candidate Contract Hash
- Directory Contract Hash
- Business Scope
- Effective Time

Snapshot Evidence Hash 额外覆盖所有决策角色、动作、批准人、原因、时间与决策
证据 Hash。数据库ID、创建时间、乐观锁等持久化字段不进入 Canonical。

## 7. Approved Registry策略

新增只读语义的内存 Approved Registry：

- 只接收已经生成的不可变 Activation Snapshot。
- 同一 Activation Hash 只允许登记一次。
- 支持查询批准证据。
- 不修改 Resolver Registry。
- 不产生 Runtime Binding。
- 不提供 enable/disable API。

因此 `Approved Registry` 与执行 Registry 相互隔离。

## 8. Persistence与Migration决策

WF5.8 不需要 Migration：

- 当前目标是 Gate 和状态/Hash 契约，不是跨进程正式激活。
- V2.6.9—V2.6.12 已冻结，保持不变。
- Activation Snapshot 暂为不可变内存治理对象。
- 在未来真正启用前，如需跨重启审计证据，应另行设计 append-only Migration，
  禁止复用可更新业务表。

Migration 数量：0。

## 9. API与运行边界

本 Sprint 不新增 Controller/API。不开放：

- Activation 提交或批准接口
- Resolver 状态切换接口
- Runtime enable接口
- ROLE Task/Candidate Pool/Claim接口

`EXPLICIT_USER_V1` 保持 ACTIVE；`ROLE_DIRECTORY_V1` 保持 PREPARED 且
non-executable。

## 10. 风险与后续边界

- 内存批准证据不具备跨进程恢复能力，不可作为生产启用记录。
- 当前 SoD/Audit 以完整证据输入契约表达，真实组织/安审系统尚未接入。
- Business Scope 尚未形成正式业务字典。
- ENABLED 状态虽在模型中预留，但无合法转换，后续不得绕过 Gate 直接开放。

后续如进入新的 Activation Persistence Sprint，应先冻结 append-only 存储、
权限、审计、双人复核与回滚规则。不得直接进入 ROLE Task 实现。
