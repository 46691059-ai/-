# Workflow ROLE Runtime Execution Admission 治理设计

## 1. 文档状态与冻结基线

- Sprint：`2-3.7-WF5.15`
- 文档状态：`ROLE_RUNTIME_EXECUTION_ADMISSION_DESIGN_READY`
- 实现状态：`IMPLEMENTATION_NOT_STARTED`
- ROLE Runtime：`DISABLED`
- `EXPLICIT_USER_V1`：`ACTIVE`
- `ROLE_DIRECTORY_V1`：`PREPARED / NON_EXECUTABLE`
- V2.6.14：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- V2.6.14 SHA-256：`a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442`
- V2.6.14 Flyway checksum：`-1734980808`

本 Sprint 只冻结“持久化 Runtime Binding Candidate 能否获准进入执行准备”的治理规则，不启用 ROLE Runtime，不创建 ROLE Task、Candidate Pool 或 Claim，不接入真实 Approval Role Directory，不修改 Investment，也不创建 V2.6.15 SQL。

## 2. Execution Admission 语义与边界

Execution Admission 是 Activation、Promotion、Runtime Binding 持久化之后，生产执行之前的最后一道独立门禁：

```text
Activation Evidence
  -> Promotion Decision
  -> Persisted Runtime Binding Snapshot
  -> Execution Admission
  -> Executable Runtime Binding Candidate
```

门禁通过仅表示证据完整、契约一致、依赖能力和发布控制满足执行准入条件，不表示：

- `ROLE_DIRECTORY_V1` 已切换为 `ACTIVE`；
- ROLE Runtime 已全局启用；
- 已创建 WorkflowInstance、NodeExecution、Task、Candidate Pool 或 Claim；
- 已获得具体任务审批权；
- Investment 已接入 ROLE 路由。

`APPROVED_FOR_EXECUTION` 与 `ROLE_RUNTIME_ENABLED` 必须保持两个不同的状态和职责边界。前者是证据判定，后者属于后续受控发布操作。

## 3. Domain 模型

以下模型均属于 Workflow Domain，禁止依赖 Spring、MyBatis、Entity 或数据库 DTO。

### 3.1 RoleRuntimeExecutionAdmissionRequest

表示一次不可复用的执行准入请求，至少包含：

- admissionId、requestId、requestedBy、requestedAt；
- activationId、activationHash、promotionId、promotionHash；
- runtimeBindingSnapshotId、bindingHash、candidateHash；
- resolverCode、resolverVersion、resolverContractHash；
- directoryRevision、directoryResultHash、effectiveAt；
- businessScope；
- definitionId、definitionVersionId、nodeId；
- featureFlagPolicy、canaryScope、killSwitchObservedState；
- admissionCanonicalVersion。

### 3.2 RoleRuntimeExecutionAdmissionDecision

包含 `status`、`reasonCodes`、`decidedBy`、`decidedAt`、`admissionHash` 和完整校验结果摘要。决定必须可审计、不可原位覆盖。

### 3.3 RoleRuntimeExecutionAdmissionEvidence

冻结每个校验步骤的输入版本、结果、证据 Hash、能力状态、检查时间和检查主体。证据不得包含完整人员目录或敏感凭据。

### 3.4 ExecutableRuntimeBindingCandidate

只有 `APPROVED_FOR_EXECUTION` 才可生成。它是后续实例启动服务的只读输入，不是 Runtime Binding、Task 或 Candidate Pool。其内容必须继承 Admission 已核验的全部标识与 Hash，不得重新解释或补值。

### 3.5 RoleRuntimeExecutionAdmissionPolicy

定义固定校验顺序、Fail Closed、时效、撤销、重试、幂等、并发、Feature Flag、Canary 和 Kill Switch 规则。

### 3.6 RoleRuntimeExecutionBlockReason

阻断码必须稳定且机器可判定，至少覆盖：证据缺失、状态非法、Hash 漂移、定义/节点不一致、Registry 不满足、Directory/DataScope/SoD/Audit 能力未就绪、Flag 未开启、Kill Switch 开启、Canary 越界、有效期失效。

### 3.7 RoleRuntimeExecutionAdmissionHash

封装 Canonical Version、SHA-256 格式验证和常量时间比较。Hash 必须使用小写十六进制、大小写敏感的二进制语义。

## 4. 状态机

```text
CREATED -> VALIDATING -> ELIGIBLE -> APPROVED_FOR_EXECUTION
              |             |
              +-> BLOCKED   +-> REJECTED

CREATED / VALIDATING / ELIGIBLE / APPROVED_FOR_EXECUTION
              -> REVOKED

ELIGIBLE / APPROVED_FOR_EXECUTION
              -> EXPIRED
```

规则：

- 仅允许显式定义的单向流转；终态不得原位恢复。
- `BLOCKED` 表示依赖或证据暂不满足，可用新请求重试；不得修改旧决定。
- `REJECTED` 表示治理审批否决，必须新建请求后再评估。
- `REVOKED` 保留旧证据并立即阻断后续新执行。
- `EXPIRED` 由有效期、能力证据时效或 Canary 窗口失效触发。
- `APPROVED_FOR_EXECUTION` 仍不得自动创建任何运行对象或改变 Registry 状态。

## 5. 固定准入校验链

校验必须按以下顺序执行并逐项留痕，任一步失败立即 Fail Closed：

1. Persisted Runtime Binding Candidate 存在；
2. Runtime Binding Snapshot 状态合法；
3. Promotion Evidence 完整；
4. Activation Evidence 完整；
5. Activation 未撤销；
6. Promotion 未撤销；
7. Resolver Code 一致；
8. Resolver Version 一致；
9. Resolver Contract Hash 一致；
10. Activation Hash 一致；
11. Promotion Hash 一致；
12. Binding Hash 一致；
13. Candidate Hash 一致；
14. Directory Revision 一致；
15. `effectiveAt` 语义和时效合法；
16. Business Scope 一致且当前主体获准；
17. Definition Version 一致且已发布、未失效；
18. Node Binding 一致；
19. Registry Descriptor 存在；
20. Resolver 状态允许参与 Admission；
21. Directory Capability 为 READY；
22. 实时人员资格校验能力为 READY；
23. DataScope 能力为 READY；
24. SoD 能力为 READY；
25. Audit 能力为 READY；
26. Feature Flag 为 READY；
27. Kill Switch 为 READY；
28. Canary Scope 允许当前请求。

禁止 fallback、自动升级/降级 Resolver、重新执行 Binding Proposal、重新生成 Candidate、以 EXPLICIT_USER 代替 ROLE 或使用旧缓存绕过失败。

## 6. Resolver Registry 状态模型

未来将 Resolver 的“可治理”与“可执行”拆为三层：

- `PREPARED`：Descriptor、Contract 和 Preview 能力可检查，但不可参与真实执行；
- `EXECUTION_ELIGIBLE`：已满足生产准入能力，可参与 Admission 校验，但仍未启用执行；
- `ACTIVE`：经独立发布动作允许生产运行。

本设计不修改现有枚举或 Registry。当前 `ROLE_DIRECTORY_V1` 继续保持 `PREPARED / NON_EXECUTABLE`；Admission 设计完成不产生任何状态迁移。

## 7. ROLE_DIRECTORY_V1 启用前置条件

从 `PREPARED` 晋级到 `EXECUTION_ELIGIBLE` 至少需同时满足：

1. 生产级 `ProductionRoleDirectoryAdapter` 已实现；
2. Directory Business Owner 具名；
3. Directory Data Owner 具名；
4. Organization/HR Owner 具名；
5. Revision 可验证、可追溯且不可伪造；
6. 返回结果能证明 `complete=true`；
7. Contract Version 和 Contract Hash 冻结且一致；
8. Canonical Hash 跨实现一致；
9. BUSINESS_ORG 强过滤语义冻结；
10. 候选上限及超限策略已压测并冻结；
11. Directory SLA 指标已实测通过；
12. 实时人员资格校验能力 READY；
13. DataScope 校验能力 READY；
14. SoD 校验能力 READY；
15. Audit 证据链 READY；
16. Feature Flag READY；
17. Kill Switch READY；
18. Sandbox 验证通过；
19. Integration Test 通过；
20. 预生产真实演练通过。

`EXECUTION_ELIGIBLE` 到 `ACTIVE` 还需单独的发布审批和人工操作，禁止由代码自动完成。

## 8. Production Role Directory 边界

未来适配器仅通过冻结的 `RoleDirectoryPort` 读取目录事实。

输入：`enterpriseId`、`roleCode`、`businessOrgId`、`effectiveAt`、期望 Contract Version/Hash。

输出：`directoryRevision`、`complete`、`resultHash`、`members`、`resolvedAt`、`contractHash`。

Workflow 不维护角色、组织、成员和有效期，也不回写 Directory。任何关键输入或输出缺失均拒绝。

错误分类：

- 可重试：瞬时网络失败、超时、明确的临时不可用；只能在总 Deadline 内有限重试。
- 不可重试：Contract/Hash/Revision 不一致、部分结果、非法组织或角色、数据格式错误、候选超限、权限拒绝。

任何错误都不得回退到旧 Candidate Pool、缓存成员、默认负责人、管理员或 EXPLICIT_USER。

## 9. Directory SLA Gate

准入只冻结指标和验收方法，不虚构阈值。生产阈值须由 Directory Owner、Workflow Owner、SRE 和 Security/Audit 共同批准。

必须观测：P50/P95/P99 延迟、超时率、部分结果率、不可用率、Revision 漂移率、Contract/Result Hash 不一致率、候选数量分布和超限率。未配置获批阈值、告警和熔断策略时，Directory Capability 不得标记 READY。

## 10. Runtime Binding 实例化边界

Executable Candidate 必须无损继承：Activation Hash、Promotion Hash、Binding Hash、Candidate Hash、Resolver Code/Version/Contract Hash、Directory Revision/Result Hash、Business Scope、Definition ID/Version ID、Node ID、EffectiveAt、Feature Flag 和 Canary Evidence。

实例化阶段不得重新计算、替换、刷新或从“最新配置”补齐这些字段。任一字段缺失或不一致，必须重新发起 Admission，而不是修改旧 Candidate。

## 11. Instance 启动事务与 Directory 事务模式

三种模式对比：

| 模式 | 优点 | 主要风险 | 结论 |
|---|---|---|---|
| A：Directory 调用位于数据库事务内 | 表面上读取与写入接近 | 长事务、持锁等待外部网络、死锁和连接池耗尽 | 不采用 |
| B：Directory 调用完全位于事务前 | 事务短、锁竞争低 | Resolve 与 Commit 间存在 TOCTOU | 单独使用不足 |
| C：Prepare / Verify / Commit | 兼顾外部调用隔离、Revision Fence 和原子写入 | 需要可验证 Revision Token、幂等与 CAS | 推荐 |

推荐模式 C：

1. 在数据库事务外按 Deadline 调用 Directory，得到完整结果、Revision、Result Hash 和验证凭证；
2. 生成冻结 Candidate Draft，并完成 Admission；
3. 开启短事务但尚不获取高争用业务行锁，通过签名 Revision Token、本地权威副本或等价轻量 Revision Fence 再验证凭证；若必须远程验证，应在事务外完成，并在事务内只验证不可伪造 Token；
4. 验证通过后按固定顺序加锁；
5. 原子写入 Instance、Resolver Binding Set、NodeExecution、Candidate Pool、Candidate Member、Task 和 Audit；
6. 由唯一约束、CAS 和幂等键防止重复提交；Revision 已变化则整体放弃并重新 Prepare。

任何远程 Directory 网络调用不得发生在持有高争用数据库行锁期间。若 Directory 无法提供签名 Revision Token、Lease 或等价的轻量验证机制，则保持 NO-GO。

## 12. Candidate Pool 生成规则

- 只从获准的 Executable Runtime Binding Candidate 和冻结 Directory Result 生成；
- 必须满足 `complete=true`，Revision、Result Hash、Candidate Hash 与 Admission Evidence 一致；
- Candidate 按 Canonical 规则排序、去重并保留来源证据；
- Pool、Member 和 Assignment Snapshot 必须不可变、可追溯；
- Pool 创建后禁止刷新、覆盖或因人员变化自动同步；
- 0 成员、候选超限、部分结果或 Hash 漂移必须拒绝；
- DIRECT 与 Legacy Task 继续不创建 Candidate Pool。

## 13. Claim 实时资格

冻结候选资格不等于实时执行资格。Claim 时不得重新调用 Directory 生成候选，但必须按固定顺序校验：

1. RBAC 基础功能权限；
2. Task、NodeExecution、Instance 状态；
3. Candidate Pool 与 Candidate Member 归属；
4. 候选人的实时启用状态；
5. 人员与企业/组织的实时有效关系；
6. DataScope；
7. 平台级 SoD；
8. 业务级 SoD 结果；
9. Kill Switch 与撤销状态；
10. 幂等、行锁和版本 CAS。

Claim 仅确定处理人，不自动审批，也不授予业务决策权。

## 14. SoD 与 DataScope

SoD 分两层：

- 平台级：发起人与审批人冲突、同一人跨互斥节点、系统管理员越权等通用规则；
- 业务级：由 Investment 等业务上下文提供职责分离事实和策略结果，Workflow 只执行已冻结规则，不解释三重一大业务语义。

DataScope 与 ROLE 成员资格、RBAC 权限相互独立，三者必须全部通过。拥有 `workflow:approve` 不能替代 Task Candidate、Assignee、DataScope 或 SoD 校验。

## 15. 审计证据链

审计链至少记录：Admission Request/Decision/Hash、Activation/Promotion/Binding/Candidate Hash、Directory Contract/Revision/Result Hash、Definition Version、Node、Business Scope、能力门禁结果、Feature Flag、Canary、Kill Switch、验证主体与时间、失败码、重试与幂等结果。

证据仅允许 append/query，禁止 update/delete；敏感 Token、密钥和完整目录成员信息不得写入审计。

## 16. Feature Flag、Canary 与 Kill Switch

Feature Flag 采用三层拒绝优先模型：全局、企业、流程定义。最小安全开启粒度建议为“单企业 + 单已发布 Definition Version”；全局 Flag 只作为总开关，任一上层关闭都必须拒绝。

首轮 Canary 限定为：一个非生产/获批企业、一个流程定义及发布版本、一个 ROLE 节点、固定测试组织和受控账号。不得用模糊比例随机放量替代业务范围。

Kill Switch 开启后：

- 立即阻断新的 ROLE Admission、Instance Start 和 Task 生成；
- 已持久化 Evidence、Snapshot、Pool、Task 不删除、不重写；
- 未 Claim 的 ROLE Task 转入受控暂停/人工处置；
- 已 Claim 的任务不自动撤销，后续动作是否允许由安全策略判定并完整留痕；
- 恢复必须经过原因关闭、证据复核和显式重新开放，禁止自动恢复。

## 17. 失败、重试和幂等

- 暂态外部错误可在有界 Deadline 内重试；治理、契约、Hash、范围和资格错误不可重试。
- 每次失败必须产生稳定 Block Reason 和 Audit Evidence。
- 重试不得修改旧请求或旧证据，应引用原请求创建新 Attempt。
- `executionAdmissionKey`：约束同一证据集合只形成一个有效 Admission；
- `instanceStartKey`：约束同一业务启动请求；
- `candidatePoolKey`：约束同一 Instance/NodeExecution 的活动 Pool；
- `taskCreationKey`：约束同一 NodeExecution 的任务创建。

相同幂等键与相同 Canonical 输入返回既有结果；相同键但输入 Hash 不同必须拒绝。

## 18. 并发与锁顺序

固定锁顺序：

```text
WorkflowInstance
  -> WorkflowResolverBindingSet
  -> WorkflowNodeExecution
  -> WorkflowCandidatePool
  -> WorkflowTask
```

以唯一约束、版本 CAS、幂等键和固定锁顺序实现单赢家。不得在持有上述高争用行锁时调用远程 Directory。冲突方只能获得幂等结果或明确的乐观锁/唯一约束失败，不得重复创建运行对象。

## 19. Admission Hash

冻结 Canonical：`ROLE_RUNTIME_EXECUTION_ADMISSION_CANONICAL_V1`。

Hash 输入必须包含：Activation Hash、Promotion Hash、Binding Hash、Candidate Hash、Resolver Code、Resolver Version、Resolver Contract Hash、Directory Revision、Directory Result Hash、Business Scope、Definition ID、Definition Version ID、Node ID、EffectiveAt、Canary Scope、Feature Flag Policy。

排除：数据库自增 ID、创建/更新时间、操作人、乐观锁版本等非业务字段。Canonical 采用固定字段名、固定顺序、明确空值表示和 UTF-8 编码。任何输入变化都必须创建新的 Admission，旧结果不得自动复用。

## 20. Persistence Decision 与 V2.6.15 规划

| 方案 | 审计/恢复 | 语义清晰度 | 结论 |
|---|---|---|---|
| A：仅内存 Gate | 进程重启后不可恢复，无法证明历史决定 | 较清晰但证据不足 | 不推荐 |
| B：复用 V2.6.13/V2.6.14 | 有持久化基础 | Activation/Promotion/Binding 与 Admission 语义混杂，且现有 V2.6.14 未完整承载 Definition Version/Node Admission 证据 | 不推荐 |
| C：新增独立 Admission Evidence | 可恢复、可撤销、可审计、可做 Canary/Kill Switch 追踪 | 边界最清晰 | 推荐 |

因此未来建议规划 V2.6.15，但本 Sprint 不创建 SQL。候选结构可包括：

- `workflow_role_execution_admission`：请求、决定、所有业务引用与 Hash、Definition Version/Node、Feature/Canary/Kill Switch、状态、Admission Hash、幂等键；
- `workflow_role_execution_admission_evidence`：逐项验证证据和失败码，append-only；
- 必要的唯一键、稳定外键、大小写敏感 Hash/Resolver Version、CHECK、Guard 和禁止 UPDATE/DELETE Trigger。

Repository 只允许 insert/query；不得以更新旧记录表达状态变化。最终字段和表数必须在 WF5.16 前经数据库设计评审冻结。

## 21. 权限规划

仅规划、不初始化：

- `workflow:role-runtime:view`
- `workflow:role-runtime:request`
- `workflow:role-runtime:approve`
- `workflow:role-runtime:revoke`

审批权应遵循 Business Owner、Security/Audit、Release Approver 的职责分离。Workflow 管理员不得因 `workflow:manage` 自动获得生产 Activation 或 Admission 批准权。RBAC 只表示使用功能的基础能力，不等于 Directory 成员资格、Task Claim 资格或业务决策权。

## 22. Legacy 与 EXPLICIT_USER 兼容

- `SINGLE_NODE_LEGACY`、`USER + DIRECT`、`MULTI_NODE_LINEAR_V1 + EXPLICIT_USER_V1` 完全绕过 ROLE Admission；
- 不迁移、不补造、不重新解析历史 Instance、Task、Assignment Snapshot 或 Candidate Pool；
- ROLE Flag、Canary 或 Kill Switch 不得改变 EXPLICIT_USER 执行行为；
- Registry 中新增的治理状态不得使现有 `EXPLICIT_USER_V1 ACTIVE` 失效。

## 23. Investment 边界

未来 Investment 只提供 roleCode、businessMeaning、approvalRoute、businessScope 和业务级 SoD 事实，不得传入 userId 绕过 ROLE Resolver。

Workflow 负责 Resolver、Admission、Runtime Binding、Candidate Pool、Claim 和运行审计，但不得修改 Investment 决策状态。Investment 状态仍由 Workflow 事件回调驱动。本 Sprint 不修改或接入 Investment。

## 24. 预生产验收清单

1. Production-equivalent Directory Adapter 验收；
2. Directory SLA Test；
3. Candidate Limit Load Test；
4. Revision Drift Test；
5. Partial Response Test；
6. Execution Admission Persistence Test；
7. ROLE Instance Start Transaction Test；
8. Candidate Pool Atomic Creation Test；
9. Dual-session Concurrency Test；
10. Feature Flag Test；
11. Canary Test；
12. Kill Switch Test；
13. Claim Realtime Eligibility Test；
14. SoD Test；
15. DataScope Test；
16. Application Rollback Test；
17. Explicit User Regression Test。

上述验收还必须同时证明历史 Migration 无漂移、28 步 Gate Fail Closed、全 Hash 链一致、固定锁顺序正确、Audit 完整，且验收过程不启用生产 ROLE Runtime。

## 25. NO-GO 条件

任一项成立即禁止上线或启用：

1. Directory Owner 未具名；
2. Directory Adapter 未 READY；
3. Directory Revision 不可证明；
4. `complete=true` 语义不可证明；
5. Contract Hash 不一致；
6. Canonical Hash 跨实现不一致；
7. Candidate Limit 未冻结；
8. Directory SLA 未达标；
9. 实时人员资格能力未接入；
10. DataScope 未接入；
11. SoD 未接入；
12. Audit 未接入或证据不完整；
13. Execution Admission 不可恢复；
14. Feature Flag 未实现；
15. Canary 未实现或范围越界；
16. Kill Switch 未验证；
17. Instance 事务回滚未验证；
18. 并发单赢家未验证；
19. 预生产演练未通过；
20. 从 `ROLE_RUNTIME_DISABLED` 到启用状态未经过正式变更流程。

## 26. WF5.16 实施边界

下一 Sprint 若获授权，只允许实现：

- Admission Domain 模型、状态机、Policy、Block Reason 和 Canonical Hash；
- 固定顺序 Eligibility Validator；
- Feature Flag、Canary、Kill Switch 抽象；
- Fake Production Directory Capability Adapter 与内部 Application Service；
- 不创建生产运行对象的契约、Domain 纯净和回归测试；
- 经再次评审后，决定是否只设计或候选实现 V2.6.15。

仍禁止：将 `ROLE_DIRECTORY_V1` 设为 ACTIVE、启用 ROLE Runtime、连接真实 Directory、创建真实 ROLE Task/Candidate Pool/Claim、修改 Investment、自动生成 Runtime Binding。

## 27. 风险清单

- V2.6.14 持久化对象未完整承载 Definition Version ID 与 Node ID，不能单独证明执行归属；
- Directory Revision Fence 能力尚未由真实 Directory 契约证明；
- Directory SLA 仅有指标框架，尚无获批阈值；
- DataScope、平台 SoD 与 Investment 业务 SoD 的真实适配尚未完成；
- Kill Switch 对已 Claim 任务的处置仍需安全与业务联合决策；
- Candidate 数量、目录抖动和高并发启动可能造成容量风险；
- 多系统时钟偏差会影响 `effectiveAt` 和证据过期判断；
- Canonical 跨语言实现若未做 Golden Vector 测试，存在 Hash 漂移风险；
- Admission Evidence 若不独立持久化，重启恢复和监管追溯不足；
- 当前所有设计完成均不构成生产启用授权。

## 28. 结论

本设计推荐采用独立 Execution Admission、Prepare/Verify/Commit、独立 V2.6.15 Admission Evidence（后续评审与实现）、分层 Registry、最小范围 Canary 和拒绝优先 Kill Switch。当前最终状态为：

- `ROLE_RUNTIME_EXECUTION_ADMISSION_DESIGN_READY`
- `ROLE_RUNTIME_DISABLED`
- `ROLE_DIRECTORY_V1 PREPARED / NON_EXECUTABLE`
- `V2.6.15 NOT_CREATED`

在全部预生产条件通过并获得独立发布授权前，禁止进入 ROLE Task、Candidate Pool Runtime、Claim Runtime 或 Investment Integration。
