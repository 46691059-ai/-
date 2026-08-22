# Workflow ROLE Candidate Pool Adapter 实施报告

Sprint：2-3.7-WF5.2
日期：2026-08-13
状态：`ROLE_CANDIDATE_ADAPTER_READY / ROLE_RUNTIME_DISABLED`

本 Sprint 只实现从已校验 Approval Role Directory 证据到 Candidate Pool 领域草稿的转换框架。没有接入真实 Directory、没有持久化 Candidate Pool、没有创建 ROLE Task 运行链路、没有修改 Investment，也没有创建 Migration。

## 1. 修改文件清单

新增 Domain：

- `CandidateSource.java`
- `CandidateResolutionMode.java`
- `RoleCandidateContext.java`
- `RoleCandidateUser.java`
- `RoleCandidateResult.java`
- `RoleCandidateErrorCode.java`
- `RoleCandidateException.java`
- `RoleCandidateCanonical.java`
- `RoleCandidatePoolDraftBuilder.java`
- `RoleCandidateAdapter.java`

修改：

- `RoleDirectoryResolver.java`：增加返回已校验原子目录证据的 `resolveDirectory`，原 `resolve` 行为保持兼容。
- `WorkflowAssignmentResolverConfiguration.java`：登记 PREPARED Candidate Adapter 描述符并强化 ROLE Runtime 启动门禁。
- `WorkflowRoleResolverContractTest.java`：覆盖两个 ROLE 描述符均不可执行。

新增测试：

- `WorkflowRoleCandidateAdapterTest.java`

新增文档：

- `docs/workflow-role-candidate-pool-adapter-implementation-report.md`

## 2. Domain 模型

- `RoleCandidateContext`：冻结 instanceId、nodeExecutionId、roleCode、organizationId、effectiveAt 和 resolverBinding。
- `RoleCandidateUser`：按 userId 合并候选人，并保留该用户全部 assignment 证据。
- `RoleCandidateResult`：包含 roleCode、organizationId、revision、候选用户、Directory Hash、Resolver Contract Hash 和 Candidate Hash。
- `CandidateSource`：包含 `ROLE_DIRECTORY`、`EXPLICIT_USER`；WF5.2 只产出前者。
- `CandidateResolutionMode`：仅 `ROLE_POOL_PREVIEW`，明确结果不是运行态 Candidate Pool。
- `RoleCandidatePoolDraftBuilder`：执行有效成员筛选、证据去重、用户合并、上限校验和草稿构建。

所有新增模型位于纯 Domain，不依赖 Spring、MyBatis、Entity 或数据库 DTO。

## 3. Resolver Adapter 链路

```text
Fake RoleDirectoryPort
  -> RoleDirectoryResolver.resolveDirectory()
  -> 完整性/作用域/Contract Hash/Result Hash 校验
  -> RoleDirectoryResult
  -> RoleCandidateAdapter
  -> Revision与冻结Binding校验
  -> RoleCandidatePoolDraftBuilder
  -> RoleCandidateResult（领域草稿，不落库）
```

适配规则：

- 0 个有效成员：`EMPTY_ROLE_MEMBER`；上游 Directory Resolver 保持 `NO_ROLE_MEMBER`。
- 1 个成员：生成单候选草稿。
- N 个成员：全部保留，不截断、不选第一人、不使用默认负责人。
- 同 userId 多来源：按用户合并，但完整保留每条 assignment 证据。
- 相同 assignmentId 重复：`DUPLICATE_MEMBER`，Fail Closed。
- 超过受控 candidateLimit：`CANDIDATE_LIMIT_EXCEEDED`，禁止截取前 N 人。

## 4. Canonical Hash 规则

规范：`ROLE_CANDIDATE_CANONICAL_V1`。

- 候选人按 userId 升序；
- assignment 证据按 assignmentId、sourceType、sourceRef 排序；
- 使用显式 UTF-8 JSON、UTC 毫秒时间及 SHA-256 小写 Hex；
- 包含 roleCode、organizationId、revision、Directory Hash、Resolver Contract Hash、source、mode、用户及全部 assignment 证据；
- 排除数据库 ID、创建时间、操作人和乐观锁 version；
- 不依赖 Jackson、Map、数据库返回顺序或默认时区。

已验证：成员输入顺序变化 Hash 不变；roleCode、organizationId、revision 或成员变化时 Hash 改变。

## 5. Registry 状态

| 组件 | Code / Version | 状态 | Enabled |
|---|---|---|---|
| ROLE Resolver | `ROLE_DIRECTORY / ROLE_DIRECTORY_V1` | `PREPARED` | `false` |
| Candidate Adapter | `ROLE_CANDIDATE_ADAPTER / ROLE_CANDIDATE_ADAPTER_V1` | `PREPARED` | `false` |

Candidate Adapter Contract Hash：`bc7fa92a5eb138e5f1ab44ca0facce98223c897c6e29a07318272cc62acaa02f`。

Spring 启动配置显式阻断任一 ROLE 组件变为非 PREPARED 状态。Registry 的运行时 `require` 对两个描述符均返回拒绝；不存在 ACTIVE ROLE Resolver。

## 6. API

新增 Controller API：`0`。

没有开放 ROLE 查询、Candidate Preview、Resolver 调用、动态注册或启停接口。

## 7. 测试结果

环境：Java `21.0.12`、Maven `3.9.9`、Spring Boot `3.5.9`。

- 定向回归：23 项通过，0 失败；
- 后端全量测试：355 项通过，0 失败，0 错误，0跳过；
- Java 21 编译：通过；
- Spring Boot 上下文：通过；
- Domain 纯净检查：通过；
- 单成员、多成员、无成员、有效期、重复用户合并、重复证据、Revision 漂移、Contract/Result Hash 漂移、Source Conflict、Candidate Limit：通过；
- `EXPLICIT_USER_V1` Registry 与全量回归：通过。

## 8. Migration 状态

- Migration 变化：`0`；
- V2.6.9：未创建；
- V2.5.0—V2.6.8：未修改；
- `database/migration/mysql/SHA256SUMS` 中 30 项历史摘要保持一致。

## 9. 风险

1. 真实 Approval Role Directory、Revision 原子读和生产 Adapter 尚未实现。
2. Candidate Result 仅是内存领域草稿，未具备运行态持久化和恢复能力。
3. ROLE Resolver 与 Candidate Adapter 均为 PREPARED/disabled，不能创建真实 ROLE Task。
4. candidateLimit 由构造参数注入，但平台治理值仍需压测后冻结。
5. Canonical Hash 尚需第二语言固定向量交叉验证。
6. Directory Correction、跨组织泄漏和高并发目录变化需后续集成测试。
7. 当前结果不代表预生产或生产发布就绪。

## 10. 下一步建议

保持 ROLE Runtime Disabled。WF5.3 开始前应先冻结真实 Directory Adapter 的安全边界、SLA、Revision 一致性、超时重试、Candidate Limit 和跨语言 Hash 验证方案；未经单独评审，不得接真实目录、创建 V2.6.9、启用 ROLE Resolver 或接入 Investment。

最终状态：`ROLE_CANDIDATE_ADAPTER_READY / ROLE_RUNTIME_DISABLED`。
