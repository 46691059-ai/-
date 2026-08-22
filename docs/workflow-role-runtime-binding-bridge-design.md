# Workflow ROLE Runtime Binding Bridge Design

> Sprint：2-3.7-WF5.11
> 基线：V2.6.13 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
> 设计状态：`ROLE_RUNTIME_BINDING_BRIDGE_DESIGN_READY`
> 运行状态：`ROLE_RUNTIME_DISABLED`

## 1. 目标

本设计定义持久化 Activation Evidence 与 Workflow Runtime Binding 之间的安全桥接，但不执行桥接、不写入运行绑定，也不启用 ROLE Runtime。

目标链路如下：

```text
Persistent Activation Request / Approval / Evidence
                         ↓
ApprovedRuntimeBindingDecision
                         ↓
RuntimeBindingPromotionRequest
                         ↓
RuntimeBindingPromotionPolicy
                         ↓
RuntimeBindingPromotionResult
                         ↓
Runtime Binding Candidate（治理对象）
                         ╳
WorkflowResolverBindingSet / WorkflowResolverBinding / NodeResolverBinding
```

末端的 `╳` 表示本 Sprint 的硬边界：只证明候选具备后续物化资格，不创建或修改任何 Binding Set、Resolver Binding、Node Binding、Workflow Instance、Task、Candidate Pool 或 Claim。

核心不变量：

1. Activation 的 `APPROVED` 只表示治理审批成立；持久化记录还必须处于 `PERSISTED`。
2. `APPROVED != PROMOTED != ENABLED`。
3. 本文中的 `PROMOTED` 仅表示“已生成不可变 Runtime Binding Candidate”，不表示已写入生产运行绑定。
4. `ROLE_DIRECTORY_V1` 保持 `PREPARED / NON_EXECUTABLE`；`EXPLICIT_USER_V1` 保持唯一 ACTIVE 业务 Resolver。
5. 所有校验 fail closed；禁止 fallback、自动升级、自动降级和 EXPLICIT_USER 替代。

## 2. 边界

### 2.1 Activation Evidence 上游职责

上游 V2.6.13 权威保存：

- Activation Request 及其 resolver、contract、binding、candidate、directory、scope、effective time 快照；
- Business Owner、Security/Audit、Release Approver 的不可变决策；
- 与具体 Approval 强关联的 Evidence；
- Activation Hash、Approval Evidence Hash 及证据链完整性。

Bridge 只能按权威记录读取，禁止修改、补齐、推断或重新计算历史审批事实。

### 2.2 Bridge 职责

Bridge 负责：

- 将一组持久化证据解释为 `ApprovedRuntimeBindingDecision`；
- 校验目标 Workflow Definition Version、Node 与证据批准范围是否一致；
- 生成一次确定性的 Promotion Request；
- 执行固定顺序的 Promotion Policy；
- 输出不可变 Runtime Binding Candidate、Evidence Link 和审计结果。

Bridge 不负责：

- 修改 Resolver Registry 状态；
- 创建或更新 Workflow Instance；
- 写入 Binding Set、Resolver Binding、Node Binding；
- 调用 Role Directory 或解析候选人；
- 创建 Candidate Pool、Task 或 Claim；
- 将 ROLE Runtime 从 DISABLED 改为 ENABLED；
- 解释 Investment 业务语义。

### 2.3 下游 Binding 边界

未来物化器只能消费 `PROMOTED` 且未撤销的 Runtime Binding Candidate，并在新的、尚未启动的 Workflow Instance 冻结事务中创建：

```text
WorkflowResolverBindingSet
  └─ WorkflowResolverBinding
       └─ NodeResolverBinding
```

即使候选已 PROMOTED，以下条件仍会阻止物化：ROLE Runtime 未人工启用、Resolver 不可执行、候选已撤销、目标版本/节点漂移或证据超过有效期。

## 3. Domain 模型

以下模型均为纯 Domain 对象，禁止依赖 Spring、MyBatis、Entity 或数据库 DTO。

### 3.1 ApprovedRuntimeBindingDecision

表示 V2.6.13 权威证据被完整读取、校验后形成的批准事实。

建议字段：

| 字段 | 含义 |
| --- | --- |
| `activationId` | 稳定 Activation 业务键 |
| `activationHash` | Activation 权威摘要 |
| `approvalEvidenceHash` | 完整审批证据摘要 |
| `resolverCode/resolverVersion` | 获准 Resolver 身份 |
| `resolverContractHash` | 获准 Resolver 契约 |
| `bindingHash` | 获准 Binding 规则摘要 |
| `candidateHash` | 获准 Candidate 规则摘要，不是成员列表 |
| `directoryContractHash` | Directory 接口契约摘要 |
| `directoryRevision` | 获准的目录水位 |
| `businessScope` | 获准业务范围 |
| `effectiveAt` | 生效基准时间 |
| `decisionEvidence[]` | 三类批准记录的稳定引用和 Hash |
| `persistedAt` | 证据持久化时间，仅作审计，不进入业务规则 Hash |

构造条件：Activation 业务状态为 APPROVED、持久化状态为 PERSISTED，三类批准齐全且均为 APPROVED，Evidence 与 Approval 强关联，所有冻结 Hash 一致。任一条件不满足不得构造该对象。

### 3.2 RuntimeBindingPromotionRequest

表示“将某个批准事实用于某个目标节点”的请求，而非 Runtime 启用请求。

建议字段：

- `promotionId`、`idempotencyKey`；
- `approvedDecision` 或其不可变引用；
- `definitionId`、`definitionVersionId`、`definitionContentHash`；
- `nodeId`、`nodeCodeSnapshot`、`nodeRuleHash`；
- `requestedResolverCode/version/contractHash`；
- `requestedBindingHash`、`requestedCandidateHash`；
- `businessScope`、`effectiveAt`、`requestedAt`、`requestedBy`；
- `promotionEvidenceHash`。

同一幂等键和相同 Request Hash 返回同一结果；相同幂等键但不同 Request Hash 必须拒绝。

### 3.3 RuntimeBindingEvidenceLink

表示 Candidate 对 V2.6.13 证据链的不可变引用，不复制审批正文或目录明细。

建议字段：

- `activationId`、`activationRequestId`；
- `approvalIds[]`、`evidenceIds[]`，均按稳定规则排序；
- `activationHash`、`approvalEvidenceHash`；
- `resolverContractHash`、`bindingHash`、`candidateHash`；
- `directoryContractHash`、`directoryRevision`；
- `evidenceLinkHash`。

禁止通过 Evidence Link 推断 Candidate Member，也禁止用最新 Activation 替换已链接 Activation。

### 3.4 RuntimeBindingPromotionResult

建议字段：

| 字段 | 含义 |
| --- | --- |
| `promotionId` | 本次 Promotion 稳定键 |
| `status` | CREATED/VALIDATED/PROMOTION_ALLOWED/PROMOTED/BLOCKED/REJECTED/REVOKED |
| `promotionHash` | `ROLE_RUNTIME_BINDING_PROMOTION_CANONICAL_V1` 结果 |
| `runtimeBindingCandidate` | 仅 PROMOTED 时存在 |
| `evidenceLink` | 权威证据引用 |
| `reasonCodes[]` | 固定排序的结果或阻断原因 |
| `evaluatedAt` | 评估时间 |
| `runtimeEnabled` | 本阶段恒为 false，仅用于显式证明未启用 |

### 3.5 RuntimeBindingPromotionPolicy

无状态 Domain Policy，输入 Approved Decision、Promotion Request、目标 Version/Node 快照、只读 Registry 投影和 Runtime 开关投影，输出 Result。

Policy 不访问数据库、不读取当前 Directory、不创建持久化对象。Application 层负责一次性装载一致快照，Policy 负责确定性校验。

## 4. 状态机

```text
CREATED
   |
   v
VALIDATED
   |
   v
PROMOTION_ALLOWED
   |
   v
PROMOTED

任一校验失败 -> BLOCKED
治理审批明确拒绝 -> REJECTED
PROMOTION_ALLOWED/PROMOTED 后发现批准撤销 -> REVOKED
```

规则：

- `CREATED → VALIDATED`：请求结构、证据链和目标身份完整。
- `VALIDATED → PROMOTION_ALLOWED`：全部 Promotion Gate 通过。
- `PROMOTION_ALLOWED → PROMOTED`：生成不可变 Runtime Binding Candidate 与 Evidence Link。
- `BLOCKED`：技术、契约、时间、完整性或 Registry 条件不满足；修复后必须提交新 Promotion，不原位修改。
- `REJECTED`：治理主体明确拒绝；终态。
- `REVOKED`：上游 Activation 或批准证据被追加撤销事实；既有 Candidate 失效，禁止物化。
- `PROMOTED` 不得自动转为 ENABLED，本状态机中不存在 ENABLED。

状态记录应 append-only。重复执行相同请求只能返回原结果，不产生第二个活动 Candidate。

## 5. Promotion 规则

固定校验顺序如下，前序失败后不得继续执行后序步骤：

1. **Activation 状态**：业务审批必须为 APPROVED，持久化请求必须为 PERSISTED，且不存在 REVOKED/BLOCKED 后续事实。
2. **Activation Evidence**：Request、三类 Approval、Evidence 均存在，归属关系、数量和 canonical version 完整。
3. **Activation Hash**：Request、Approval、Evidence 与请求携带的 Activation Hash 完全一致，使用二进制比较。
4. **Resolver Contract**：Code、Version、Contract Hash 与批准证据、节点请求和 Registry Descriptor 三方一致。
5. **Binding Hash**：批准 Binding Hash、目标 Node Rule/Binding Hash 与 Promotion Request 一致。
6. **Candidate 规则**：Candidate Hash 与获准 Candidate Contract/Limit/模式一致；这里只校验规则 Hash，不解析成员。
7. **Directory Revision**：Directory Contract Hash、Revision 与批准值一致；不访问最新 Directory，不允许自动刷新。
8. **Effective Time**：当前评估时间在批准的有效范围内，目标 Workflow Version/Node 的有效期也必须覆盖该时间。
9. **Resolver Registry**：精确 Code+Version+Contract Hash 必须存在；ROLE Resolver 必须为 PREPARED 或 ACTIVE 才能做治理校验，但只有 ACTIVE 才具有未来执行资格。当前 PREPARED 因而只能生成治理 Candidate，不能物化运行绑定。
10. **人工运行开关**：ROLE Runtime 必须经独立人工发布门禁开启后才可进入未来物化阶段；当前为 DISABLED，Promotion Result 必须显式携带 `runtimeEnabled=false`。

任何失败均返回稳定 Reason Code，例如：

- `ACTIVATION_NOT_APPROVED`
- `ACTIVATION_EVIDENCE_INCOMPLETE`
- `ACTIVATION_HASH_MISMATCH`
- `RESOLVER_CONTRACT_MISMATCH`
- `BINDING_HASH_MISMATCH`
- `CANDIDATE_RULE_MISMATCH`
- `DIRECTORY_REVISION_MISMATCH`
- `EFFECTIVE_TIME_INVALID`
- `RESOLVER_STATUS_NOT_EXECUTABLE`
- `ROLE_RUNTIME_DISABLED`

其中 `ROLE_RUNTIME_DISABLED` 不否定治理 Candidate 的生成，但必须阻止其物化和执行。

## 6. Hash 规则

Canonical 版本冻结为：`ROLE_RUNTIME_BINDING_PROMOTION_CANONICAL_V1`。

### 6.1 Hash 输入

必须包含：

- Activation ID、Activation Hash、Approval Evidence Hash；
- Resolver Code、Version、Contract Hash；
- Binding Hash、Candidate Hash；
- Directory Contract Hash、Directory Revision；
- Business Scope、Effective Time；
- Definition ID、Definition Version ID、Definition Content Hash；
- Node ID、Node Code Snapshot、Node Rule Hash；
- Promotion Evidence Link Hash；
- Canonical Version。

### 6.2 排除字段

禁止包含：

- 数据库自增 ID（稳定业务引用除外）；
- created_time、updated_time；
- 乐观锁 version、delete_token；
- 临时 traceId、请求线程、数据库连接信息；
- 非冻结的当前时间、当前 Registry 列表顺序；
- Approval 正文和 Directory 成员明细。

### 6.3 规范化

- UTF-8 编码；字段名按规范固定顺序；集合按稳定业务键升序；
- 时间统一 UTC、毫秒精度、ISO-8601；
- Hash 统一 64 位小写十六进制并使用二进制比较；
- `null` 与空字符串不得互换；禁止依赖数据库默认 collation；
- 相同输入必须得到相同 Hash，任一运行关键因素变化必须形成新 Hash 和新 Promotion。

## 7. 权限边界

RBAC 只决定谁可查看或提交治理动作，不产生业务审批权、任务处理权或 Runtime 启用权。

建议未来权限（本 Sprint 不初始化）：

| 权限建议 | 语义 | 明确不包含 |
| --- | --- | --- |
| `workflow:role_runtime:binding:view` | 查看 Evidence Link、Promotion 状态和 Hash | 查看敏感审批正文或目录成员 |
| `workflow:role_runtime:binding:promote` | 提交 Promotion 请求 | 批准 Activation、启用 Runtime |
| `workflow:role_runtime:binding:revoke` | 追加撤销请求 | 删除证据、修改历史 Binding |
| `workflow:role_runtime:enable` | 未来独立发布门禁权限 | 默认授予 Workflow 管理员 |

职责分离：

- Activation Approver 不应自动拥有 Promotion 权限；
- Promotion Operator 不应自动拥有 Runtime Enable 权限；
- Workflow Administrator 可维护流程定义，但不能单人批准并启用 ROLE Runtime；
- 即使拥有上述 RBAC，仍须满足 Business Scope、DataScope、SoD 和证据归属校验。

本 Sprint 不新增权限、不新增 Controller/API。

## 8. Legacy 兼容

### 8.1 USER + DIRECT

- `EXPLICIT_USER_V1` 继续通过既有 ACTIVE Resolver 和冻结 Binding 执行；
- 不经过 ROLE Activation Evidence 或 Promotion Bridge；
- Task、Assignment Snapshot 和处理人语义完全不变。

### 8.2 SINGLE_NODE_LEGACY

- 不要求补建 Activation、Promotion、Binding Set 或 Node Binding；
- 不根据历史 assignee、角色名称或菜单权限推断 ROLE Resolver；
- 继续以原始 Legacy 证据查询和执行。

### 8.3 历史 Multi-Node 实例

- 现有 Binding Set、Node Binding、Task 和 Candidate Pool 不修改；
- 不将 Promotion Candidate 回填到已启动 Instance；
- 历史 Task 永不重新调用 ROLE Resolver，历史 Pool 永不刷新；
- 新 Bridge 只可面向未来、尚未启动且明确采用 ROLE 的实例创建请求。

## 9. Migration 规划

### 9.1 本 Sprint 决策

Migration 数量为 **0**。V2.6.13 及历史 Migration 不变，不创建 V2.6.14。

原因：本阶段只冻结 Domain 契约、校验顺序和 Hash，不产生需要跨进程恢复的 Promotion 事实，也不物化运行绑定。现有 V2.6.13 可作为 Evidence 权威源，V2.6.5 可作为未来 Binding 目标结构。

### 9.2 是否需要 V2.6.14

正式执行 Promotion 前建议需要 V2.6.14，但必须经过独立 Sprint 评审。推荐新增一张 append-only 桥接表，而不是修改 V2.6.13 或覆盖 V2.6.5 Binding 表：

`workflow_role_runtime_binding_promotion`

建议保存：promotion_id、activation_id、target definition/version/node、promotion status、activation/contract/binding/candidate/directory/evidence/promotion hashes、effective time、idempotency key、reason code、created audit fields、delete_token、version。

建议约束：

- Promotion 记录 append-only，禁止 UPDATE/DELETE；
- 精确外键关联 Activation Request；如引用 Approval/Evidence，必须使用已具备 UNIQUE 的完整父键；
- 一个 Activation + Target Node + Promotion Hash 仅允许一条活动记录；
- Hash 为 `ascii_bin` 且符合 64 位小写 SHA-256；
- 状态 CHECK 不包含 ENABLED；
- 对 PROMOTED 记录建立证据完整性 INSERT Trigger；
- 不在该 Migration 中启用 Resolver、不创建 Candidate Pool/Task/Claim 数据。

V2.6.14 只有在 Promotion 需要跨进程恢复、审计查询和与未来 Binding 物化建立不可抵赖关联时才应创建。其 Fresh、V2.6.13 Upgrade、失败安全、append-only、并发幂等和 Schema Fingerprint 验收必须独立完成。

## 10. WF5.12 边界

若进入 WF5.12，建议仅实现 Bridge Domain/Application 框架与只读 Evidence Loader：

允许：

- 实现本文五个 Domain 模型和纯 Domain Policy；
- 从 V2.6.13 只读加载一致证据快照；
- 生成内存 Runtime Binding Candidate；
- 实现 canonical hash、reason code、幂等和 fail-closed 测试；
- 验证 USER/Legacy 完整回归；
- 如经明确批准，单独设计 V2.6.14 候选，但不得在同一阶段自动验收或晋级。

仍然禁止：

- 将 `ROLE_DIRECTORY_V1` 改为 ACTIVE；
- 把 Candidate 写入 Workflow Resolver Binding Set；
- 修改或回填现有 Instance/Task/Pool；
- 调用真实 Role Directory；
- 创建 ROLE Task、Candidate Pool Runtime 或 Claim；
- 接入 Investment；
- 将 APPROVED/PROMOTED 等同于 ENABLED。

进入任何运行物化 Sprint 前，必须额外冻结：Promotion 持久化、双人发布门禁、Runtime Enable 开关、撤销传播、灾难恢复和精确 Resolver 制品保留策略。

## 11. 风险与冻结结论

| 风险 | 等级 | 控制 |
| --- | --- | --- |
| 把证据批准误当 Runtime 启用 | P0 | 明确三段状态；Bridge 无 ENABLED 转换 |
| Evidence 与目标 Node 不匹配 | P0 | Definition/Version/Node/Rule Hash 纳入校验和 Promotion Hash |
| PREPARED Resolver 被误执行 | P0 | 治理校验与执行资格分离；物化前要求 ACTIVE 和人工开关 |
| 历史实例被回填 ROLE Binding | P0 | Bridge 仅面向未来未启动实例，禁止历史写入 |
| Directory 人员变化污染证据 | P0 | 只使用冻结 Revision/Hash；Bridge 不调用 Directory |
| Promotion 重复或并发双写 | P1 | 稳定幂等键、Request Hash、未来唯一约束与 append-only 记录 |
| 撤销后 Candidate 仍被消费 | P0 | 消费前重查撤销事实；REVOKED 必须阻断物化 |
| 权限被误作任务处理资格 | P0 | RBAC、Promotion、Runtime Enable、Task/Claim 资格分离 |

本设计冻结后状态为：

- `ROLE_RUNTIME_BINDING_BRIDGE_DESIGN_READY`
- `ROLE_RUNTIME_DISABLED`

本 Sprint 未修改代码、测试、Registry 或 Migration；未创建真实 Runtime Binding、ROLE Task、Candidate Pool、Claim，也未接入 Directory 或 Investment。
