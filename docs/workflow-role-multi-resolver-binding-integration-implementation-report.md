# Workflow ROLE Multi-Resolver Binding Integration 实施报告

Sprint：2-3.7-WF5.3
日期：2026-08-13
状态：`ROLE_BINDING_INTEGRATION_READY / ROLE_RUNTIME_DISABLED`

本 Sprint 将 ROLE Resolver 纳入 Multi-Resolver Binding 的元数据计算框架，只生成可校验的节点级 Proposal 与版本级 Proposal Set。Proposal 不映射 V2.6.5 运行表，不创建 Runtime Binding、Task 或 CandidatePool。

## 1. 修改文件清单

新增 Domain：

- `RoleResolverBindingProposal.java`
- `ResolverBindingProposalSet.java`
- `RoleBindingValidationResult.java`
- `RoleResolverBindingRequest.java`
- `RoleBindingCanonical.java`
- `RoleResolverBindingProvider.java`

修改：

- `ResolverRegistry.java`：增加 PREPARED 安全的元数据查询及 code/version/status/contractHash 精确校验，不改变运行时 `require` 门禁。
- `WorkflowAssignmentResolverConfiguration.java`：注册 `RoleResolverBindingProvider`，ROLE Resolver 与 Candidate Adapter 状态仍强制 PREPARED。

新增测试：

- `WorkflowRoleMultiResolverBindingIntegrationTest.java`

新增文档：

- `docs/workflow-role-multi-resolver-binding-integration-implementation-report.md`

## 2. Domain 模型

### RoleResolverBindingProposal

表示一个节点的 ROLE Resolver 绑定提案，包含：

- nodeId；
- resolverCode / resolverVersion / contractHash；
- roleCode / organizationId / effectiveAt；
- candidateMode=`ROLE_POOL_PREVIEW`。

它不是 `NodeResolverBinding`，不能进入 Repository 或 Runtime。

### ResolverBindingProposalSet

表示一个 Workflow Definition Version 下的 ROLE 提案集合：

- definitionId；
- versionId；
- 按 nodeId 规范排序的 proposals；
- bindingHash。

同一节点出现两个 ROLE Proposal 时直接拒绝。

### RoleBindingValidationResult

状态：`VALID`、`INVALID_ROLE`、`INVALID_CONTRACT`、`INVALID_HASH`、`INVALID_ORGANIZATION`。

### RoleResolverBindingProvider

只依赖 `ResolverRegistry`。其职责限定为读取 PREPARED 描述符、生成 Proposal、冻结 Binding Hash 和校验提案，不依赖任何 Repository、Mapper、Task Service 或 CandidatePool Service。

## 3. Binding 链路

```text
Workflow Definition Version
  -> RoleResolverBindingRequest（节点规则）
  -> ResolverRegistry.requireDescriptor
  -> ROLE_DIRECTORY / ROLE_DIRECTORY_V1 / PREPARED
  -> RoleResolverBindingProposal
  -> ResolverBindingProposalSet.freeze
  -> ROLE_BINDING_CANONICAL_V1 Hash
  -> Proposal Draft
```

只有元数据冻结链路。没有以下转换：

```text
Proposal -X-> WorkflowResolverBinding
Proposal -X-> NodeResolverBinding
Proposal -X-> WorkflowTask
Proposal -X-> CandidatePool
```

既有 `EXPLICIT_USER_V1` 仍按 ACTIVE 的 V2.6.5/V2.6.6 Runtime 链路运行。

## 4. Hash 规则

Canonical：`ROLE_BINDING_CANONICAL_V1`。

Hash 输入：

- definitionId / versionId；
- nodeId；
- resolverCode；
- resolverVersion；
- resolverContractHash；
- roleCode；
- organizationId；
- effectiveAt；
- candidateMode。

Proposal 按 nodeId 排序，使用显式 UTF-8、UTC 毫秒时间和 SHA-256 小写 Hex。数据库 ID、创建时间、操作人和乐观锁 version 不进入 Hash。

测试确认：Proposal 输入顺序变化 Hash 不变；roleCode、organizationId、contractHash 变化时 Hash 改变，其中错误 Contract 会被 `INVALID_CONTRACT` 阻断。

## 5. Registry 状态

| Resolver | 状态 | Enabled | 用途 |
|---|---|---:|---|
| `EXPLICIT_USER / EXPLICIT_USER_V1` | ACTIVE | true | 保持原运行行为 |
| `ROLE_DIRECTORY / ROLE_DIRECTORY_V1` | PREPARED | false | 仅 Proposal 元数据计算 |
| `ROLE_CANDIDATE_ADAPTER / ROLE_CANDIDATE_ADAPTER_V1` | PREPARED | false | 仅候选领域草稿 |

新增 `requireDescriptor(code, version)` 及 `requireDescriptor(code, version, status, hash)`。这两个接口只返回描述符；原 `require` 仍要求 ACTIVE/Enabled/Implementation。调用 ROLE 运行 Resolver 继续返回 `DISABLED`。

## 6. API

新增 Controller API：`0`。

未开放 Binding 配置、ROLE 查询、Runtime Preview、Resolver 切换或启停接口。

## 7. 测试结果

环境：Java `21.0.12`、Maven `3.9.9`、Spring Boot `3.5.9`。

- 定向集成测试：35 项通过；
- 后端全量测试：361 项通过，0 失败，0 错误，0 跳过；
- Java 21 编译：通过，共编译 528 个生产源码；
- Spring Boot 上下文：通过；
- Domain 纯净检查：通过；
- SINGLE_NODE_LEGACY / MULTI_NODE_LINEAR_V1 / EXPLICIT_USER：既有全量回归通过；
- ROLE PREPARED 元数据查询：通过；
- ROLE Runtime 调用阻断：通过；
- ROLE Proposal 不具备 Repository、Task 或 CandidatePool 创建能力：通过。

## 8. Migration 状态

- Migration 变化：`0`；
- V2.6.9：未创建；
- V2.6.5、V2.6.6、V2.6.8及其他历史 Migration：未修改；
- `SHA256SUMS` 中30项摘要要求保持不变。

## 9. 风险

1. Proposal 仅为内存模型，尚无跨进程恢复或持久化能力。
2. 真实 Approval Role Directory 仍不存在，Proposal 不能用于真实人员解析。
3. ROLE 的 Node Rule 来源尚未接入 Workflow Definition 配置界面或 API。
4. Proposal 尚未具备转换为 Runtime Binding 的安全晋级规则；这是刻意门禁。
5. Canonical Hash 尚需独立第二语言固定向量验证。
6. ROLE Runtime 开启前仍需真实 Directory、V2.6.9 候选设计、MySQL 验收和安全评审。
7. 当前结果不代表预生产或生产发布就绪。

## 10. 下一步建议

保持 `ROLE_RUNTIME_DISABLED`。进入 WF5.4 前，应先冻结 Proposal 到 Runtime Binding 的晋级门禁、真实目录 SLA/Revision 语义、结构化快照持久化边界和失败回滚方案；未经独立授权不得创建 V2.6.9、接入真实 Directory 或 Investment。

最终状态：`ROLE_BINDING_INTEGRATION_READY / ROLE_RUNTIME_DISABLED`。
