# Workflow ROLE Runtime Eligibility Governance 实施报告

Sprint：2-3.7-WF5.4
日期：2026-08-13
状态：`ROLE_RUNTIME_GOVERNANCE_READY / ROLE_RUNTIME_DISABLED`

本 Sprint 建立 Proposal 到 Runtime Eligibility 的只读治理检查框架。`READY` 仅表示当前内存证据满足未来晋级的治理条件，不表示 Resolver 已启用、任务可创建或 Runtime Binding 已生成。

## 1. 修改文件清单

新增 Domain：

- `RoleRuntimeEligibility.java`
- `RoleRuntimeEligibilityRequest.java`
- `ResolverEligibilityResult.java`
- `RuntimeEligibilityRegistry.java`
- `RoleRuntimeEligibilityValidator.java`
- `RuntimeBindingCandidate.java`
- `RoleRuntimeAuditEvent.java`
- `RoleRuntimeCanonical.java`

修改：

- `WorkflowAssignmentResolverConfiguration.java`：装配只读 Eligibility Registry 和 Validator；ROLE 描述符仍强制 PREPARED。

新增测试：

- `WorkflowRoleRuntimeEligibilityGovernanceTest.java`

新增文档：

- `docs/workflow-role-runtime-eligibility-governance-implementation-report.md`

## 2. Eligibility 模型

`RoleRuntimeEligibility` 包含 resolverCode、resolverVersion、contractHash、bindingHash、roleCode、organizationId、status 和 reason。

状态语义：

- `READY`：当前治理证据完整，但 Runtime 仍未启用；
- `BLOCKED`：Resolver、Hash、Contract、Candidate 或治理门禁不满足；
- `REJECTED`：Proposal 或业务规则本身非法。

所有失败均 Fail Closed，不 fallback 到 `EXPLICIT_USER_V1`，不自动升级或降级 Resolver。

## 3. 检查链路

```text
ResolverBindingProposalSet + RoleResolverBindingProposal + RoleCandidateResult
  -> RuntimeEligibilityRegistry（只读Resolver元数据）
  -> Resolver存在性、PREPARED/ACTIVE状态、Contract Hash
  -> Binding Set Canonical Hash及Proposal归属
  -> Role/Organization完整性
  -> Candidate角色、组织、Contract与Canonical Hash
  -> Candidate非空及Limit检查
  -> RoleRuntimeEligibility
```

Registry 的 Eligibility 检查允许 PREPARED 或 ACTIVE 描述符进入治理评估，但不会返回 `AssignmentResolver`。实际运行入口 `ResolverRegistry.require(...)` 仍要求 enabled、ACTIVE 且存在实现，因此 `ROLE_DIRECTORY_V1` 继续被阻断。

## 4. Runtime Candidate 模型

`RuntimeBindingCandidate` 仅包含 proposalHash、eligibilityStatus 和 generatedAt。只有 READY Eligibility 可生成该内存对象。

它不等于且不能转换为：

- `WorkflowResolverBinding`；
- `NodeResolverBinding`；
- Workflow Instance Binding；
- Workflow Task；
- CandidatePool。

`RoleRuntimeAuditEvent` 仅为内存审计事实，记录 proposalHash、验证结果、失败原因和时间，不进行数据库持久化。

## 5. Hash治理

Canonical：`ROLE_RUNTIME_CANONICAL_V1`。

输入包含：

- resolverCode / resolverVersion / resolverContractHash；
- bindingHash；
- roleCode / organizationId；
- candidateHash / candidateCount / candidateLimit。

不包含数据库ID、创建时间、操作人或乐观锁version。测试确认 Resolver Contract、Role、Organization、Binding、Candidate Limit 任一变化都会改变 Runtime Hash。

## 6. Registry状态

| Resolver | Resolver状态 | Eligibility可检查 | Runtime可执行 |
|---|---|---:|---:|
| `EXPLICIT_USER_V1` | ACTIVE | 是 | 是，保持原行为 |
| `ROLE_DIRECTORY_V1` | PREPARED / disabled | 是 | 否 |
| `ROLE_CANDIDATE_ADAPTER_V1` | PREPARED / disabled | 未进入运行链 | 否 |

`RuntimeEligibilityRegistry` 还支持显式 disabled-check 列表；命中时返回 BLOCKED，不修改 Resolver 状态。

## 7. API

新增 Controller API：`0`。

未开放 ROLE 配置、Runtime Enable、Resolver 切换、Eligibility 查询或 Preview 接口。

## 8. 测试结果

环境：Java `21.0.12`、Maven `3.9.9`、Spring Boot `3.5.9`。

- 定向治理测试：32项通过；
- 后端全量测试：368项通过，0失败，0错误，0跳过；
- Java 21编译：通过，共编译536个生产源码；
- Spring Boot上下文：通过；
- Domain纯净检查：通过；
- Eligibility成功、Binding/Candidate Hash漂移、Resolver缺失、Contract错误、Role/Org非法、Candidate Limit：通过；
- PREPARED与ACTIVE只读检查：通过；
- ROLE运行调用阻断：通过；
- ROLE无Task、CandidatePool和Repository创建能力：通过；
- `EXPLICIT_USER_V1`完整回归：通过。

## 9. Migration状态

- Migration变化：`0`；
- V2.6.9：未创建；
- V2.6.8及全部历史Migration：未修改；
- `SHA256SUMS`中30项摘要保持一致。

## 10. 风险

1. Eligibility READY不是生产启用许可，当前仍无真实Directory或ROLE运行实现。
2. Audit Event和RuntimeBindingCandidate均为内存模型，不能用于跨进程恢复或法定审计留存。
3. Organization合法性当前只能验证结构及上下文一致性，无法查询真实组织状态。
4. Candidate Limit尚未形成平台配置版本和压测结论。
5. Canonical Hash尚需第二语言固定向量交叉验证。
6. Proposal到正式Runtime Binding的持久化、审批和回滚边界尚未设计实施。
7. 当前结果不代表预生产或生产发布就绪。

## 11. 下一步建议

保持 `ROLE_RUNTIME_DISABLED`。WF5.5之前应先冻结真实Directory准入、Eligibility证据持久化、V2.6.9必要性、组织实时校验、治理审批以及READY到正式Runtime Binding的单向晋级协议。未经独立授权，不得启用ROLE Resolver、接入Investment或创建真实ROLE Task。

最终状态：`ROLE_RUNTIME_GOVERNANCE_READY / ROLE_RUNTIME_DISABLED`。
