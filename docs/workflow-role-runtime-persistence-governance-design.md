# Workflow ROLE Runtime 持久化治理设计

## 1. 当前架构状态

### 1.1 基线

当前 ROLE Runtime 已完成框架、候选适配、Binding Proposal、Eligibility 与激活架构设计，但仍处于禁止执行状态：

- `EXPLICIT_USER_V1` 是唯一 ACTIVE 的业务 Resolver，现有行为必须完全保持；
- `ROLE_DIRECTORY_V1` 保持 PREPARED，不得进入真实 Task 创建链路；
- ROLE 当前只允许生成 Proposal、Eligibility、Runtime Binding Candidate 和 Candidate Pool Draft；
- 尚未接入真实 Approval Role Directory；
- 尚未创建 ROLE Task、真实 Candidate Pool 或可执行 Runtime Binding；
- V2.6.5—V2.6.8 已提供通用 Resolver Binding、Candidate Pool、Claim 与完整性约束；
- 本 Sprint 只冻结持久化边界，不创建 V2.6.9 SQL。

当前状态：

```text
ROLE_RUNTIME_PERSISTENCE_DESIGN_READY（本设计完成后）
ROLE_RUNTIME_DISABLED
```

### 1.2 持久化治理目标

ROLE Runtime 的数据库只保存“流程执行所需且必须长期证明的事实”，不复制组织目录主数据。设计必须回答：

1. 哪些治理结论必须跨进程、跨部署、跨时间保留；
2. 哪些运行事实必须在并发和事务下具备唯一性与引用完整性；
3. 哪些对象只是计算中间产物；
4. 哪些事实应由 Organization Directory 持有；
5. 如何在不改变 USER/DIRECT 和历史任务行为的前提下接入 ROLE。

## 2. Persistence Boundary Matrix

| 对象/事实 | 权威上下文 | Workflow 是否持久化 | 持久化形态 | 生命周期与理由 |
| --- | --- | --- | --- | --- |
| Role Resolver 实现 | Workflow Code/Registry | 否 | 代码与发布资产 | 实现本身不进入业务数据库 |
| Resolver Descriptor | Workflow Registry | 否，现阶段静态 | Code、Version、Status、Contract Hash | 运行时按冻结版本精确校验；未来 Registry 数据化另行设计 |
| RoleResolverBindingProposal | Workflow Governance | 否，审批前为内存对象 | Canonical Proposal + Hash | 可重复计算；未进入审批不构成运行事实 |
| RoleRuntimeEligibility | Workflow Governance | 否，审批前为内存对象 | Eligibility Result + Hash | 计算结论；只有被审批的精确摘要需要进入审批证据 |
| RuntimeBindingCandidate | Workflow Governance | 否 | 内存候选清单 | 未获批准不得转为运行事实 |
| RuntimeBindingApproval | Workflow Runtime Governance | **是** | 独立不可变审批记录 | 跨部署证明谁在何时批准了哪份精确清单，是生成 Runtime Binding 的强门禁 |
| RoleRuntimeBinding | Workflow Runtime | **是** | 通用 Binding 的 ROLE 扩展快照 | 固定 Resolver、Role、Organization、Directory Revision、Hash；实例运行不可依赖内存 |
| Assignment Strategy/Resolver Result Draft | Workflow Application | 否 | 内存 Draft | 失败可丢弃；成功后由 Binding/Pool/Snapshot 形成运行证据 |
| Task Assignment Snapshot | Workflow Runtime | 是，复用现有结构 | `workflow_task_assignment_snapshot` | 保存任务创建时的策略与解析摘要，但不替代多人 Candidate Pool |
| Role Candidate Pool | Workflow Runtime | **是，复用现有 Pool** | `workflow_task_candidate_pool` | Task 级冻结候选资格与单池唯一性 |
| Role Candidate Member | Workflow Runtime | **是，复用现有 Member** | `workflow_task_candidate_member` | 保存冻结候选用户及最小来源/资格证据 |
| Candidate Pool Draft | Workflow Domain/Application | 否 | 内存对象 | 只有激活事务成功后才成为真实 Pool |
| Claim / Claim Audit | Workflow Runtime | 是，复用现有结构 | `workflow_task_claim`、`workflow_task_claim_audit` | 保存单赢家、幂等及执行资格审计 |
| Directory Role Definition | Organization Directory | 否 | Directory 主数据 | Workflow 只通过稳定 roleCode 读取 |
| Role Membership | Organization Directory | 否 | Directory 主数据 | 禁止 Workflow 维护或修正成员关系 |
| Organization/Position/User 状态 | Organization Directory | 否 | Directory 主数据 | Claim 时读取实时资格，Workflow 不复制全量目录 |
| Directory Revision | Directory 权威生成；Workflow 冻结引用 | **是，保存引用值** | Role Runtime Snapshot 字段 | 证明解析使用了哪一目录版本 |
| Directory Result Hash | Workflow 校验并冻结 | **是** | Role Runtime Snapshot 字段 | 证明当时解析结果未被篡改 |
| Directory Source Evidence | Directory 权威；Workflow 保存最小证据 | **是，最小快照** | 证据 Hash、来源类型、引用、有效时点 | 用于审计，不复制完整目录行 |
| 实时人员资格结果 | Organization Directory + Workflow Claim | 是，保存校验摘要 | Claim Audit/Eligibility Snapshot Hash | 保存执行时结论，不修改冻结 Candidate Pool |

边界结论：Proposal、Eligibility 和 Draft 在晋级前保留于内存；Approval、Runtime Binding、Candidate Pool、Claim 是不可丢失的 Workflow Runtime 事实；角色、人员和组织主数据始终属于 Organization Directory。

## 3. Runtime Binding 存储方案

### 3.1 RuntimeBindingApproval 必须持久化

`RuntimeBindingApproval` 不能只存在内存，原因如下：

- 服务重启后仍必须证明 Runtime Binding 的授权来源；
- 并发激活必须由数据库唯一约束保证同一批准清单只晋级一次；
- 审计需要保留申请、批准、拒绝、过期及 Hash 漂移记录；
- 生产争议需要还原批准时的 Proposal、Eligibility 和 Contract；
- Approval 是安全边界，不能依赖应用日志或缓存作为唯一证据。

建议字段边界：

| 字段组 | 建议字段 |
| --- | --- |
| 业务关联 | definition_id、version_id、node_id、binding_set_id、node_binding_id |
| 精确批准对象 | proposal_hash、eligibility_hash、resolver_contract_hash、activation_manifest_hash |
| 状态 | status：PENDING、APPROVED、REJECTED、EXPIRED |
| 审批证据 | requested_by、requested_at、approved_by、approved_at、decision_reason、evidence_ref、trace_id |
| 幂等/唯一性 | approval_no、request_id/idempotency_key、活动批准唯一约束 |
| 标准治理 | created_by/time、updated_by/time、deleted、delete_token、version |

Approval 状态变化必须通过受控 Application Service，禁止直接 SQL 修改。`APPROVED` 只批准精确 Hash；任何关键输入漂移必须生成 EXPIRED 事件并重新申请。

### 3.2 RoleRuntimeBinding 必须进入 Workflow Runtime

真实运行不能依赖 Registry 当前状态或内存 Proposal。建议将 `RoleRuntimeBinding` 作为现有通用 Resolver Binding 的 1:1 扩展快照，而不是创建第二套 Binding 聚合。

建议保存：

- resolver_code、resolver_version、resolver_contract_hash；
- role_code、organization_id、effective_at；
- directory_contract_version、directory_revision、directory_result_hash；
- role_rule_hash、candidate_rule_version、candidate_rule_hash；
- proposal_hash、eligibility_hash、binding_hash、persistence_hash、activation_hash；
- approval_id、binding_id/node_binding_id；
- source_evidence_hash、source_evidence_ref；
- 标准审计、逻辑删除和乐观锁字段。

不可变规则：

- 已激活 Binding 不得原位更新；
- 新规则、新 Revision 或新 Resolver Version 必须生成新 Proposal、Approval 与 Snapshot；
- 历史实例始终引用创建时的 Snapshot；
- 禁止从当前 Directory 或 Registry 重建历史 Binding；
- 删除只允许受控归档语义，运行和审计记录不得物理删除。

## 4. Candidate Snapshot 方案

### 4.1 不以 Assignment Snapshot 替代 Candidate Pool

`workflow_task_assignment_snapshot` 适合保存“使用了什么策略、Resolver 和解析摘要”，但不适合单独承载 ROLE 多候选人的资格冻结：

- 多候选 Claim 需要成员级唯一约束和所有权；
- Claim 必须高效判断当前用户是否属于冻结 Pool；
- 候选成员需要独立的来源、资格与有效期证据；
- JSON 列表无法可靠支撑外键、唯一性、并发 Claim 和审计查询。

因此采用双层证据：

```text
Task Assignment Snapshot
  └─ 保存策略、Resolver、Binding、Directory 与 Candidate Hash 摘要

Task Candidate Pool
  ├─ 保存Pool级冻结事实
  └─ Task Candidate Member
      └─ 保存成员级冻结资格与来源证据
```

### 4.2 复用现有 Candidate Pool

ROLE 不创建专用 Candidate Pool 表。复用现有：

- `workflow_task_candidate_pool`；
- `workflow_task_candidate_member`；
- Candidate Pool 与 Task/NodeExecution/Instance 的既有所有权约束；
- Candidate Pool Claim 状态和 V2.6.8 完整性约束。

ROLE 特有的 Directory Revision、Role/Organization、Activation Hash 等应主要放在 Role Runtime Snapshot，并由 Pool 通过稳定外键引用。Pool 只保存运行和 Claim 所需的通用字段及 Pool Hash，避免把 ROLE 语义扩散到 USER/DIRECT 行为。

### 4.3 Pool 生命周期

- ROLE 节点首次激活时，按已批准 Snapshot 创建 Task、Pool 和 Members；
- Pool 创建与 Task/NodeExecution 状态更新处于同一受控事务；
- Pool 创建成功后禁止刷新、覆盖、追加、删除或自动同步；
- Directory 后续人员变化只影响 Claim 实时资格，不改变冻结成员集合；
- 全部候选失效时进入治理阻断，不重新解析 Pool；
- 相同幂等键只能产生一个 Pool；并发创建由唯一约束和 CAS 保证单一结果。

## 5. Directory Evidence 方案

### 5.1 保存范围

Workflow 必须保存足以证明解析过程的最小证据：

- directory_contract_code/version；
- directory_revision；
- role_code、organization_id、effective_at；
- directory_result_hash；
- source_evidence_type、source_evidence_ref、source_evidence_hash；
- candidate_count、candidate_canonical_version、candidate_hash；
- Resolver Code/Version/Contract Hash；
- 解析请求 TraceId、解析完成时间和响应签名摘要（如契约支持）。

成员级只保存：userId、来源规则、资格有效期快照、eligibility_hash 及必要的证据引用。不得保存与审批无关的完整人员档案。

### 5.2 禁止保存范围

Workflow 不得复制：

- 组织树、岗位目录和角色定义全量数据；
- 用户手机号、身份证、住址等非审批必需信息；
- Directory 内部数据库主键或内部实现字段；
- 可通过业务键和证据引用验证、但与运行无关的冗余属性；
- 密钥、Token、完整签名原文。

### 5.3 权威关系

- Directory 是角色、组织、人员关系和有效期的权威来源；
- Workflow Snapshot 是“某次运行当时使用了什么目录事实”的权威证据；
- Snapshot 不反向修正 Directory；
- Directory 变化不反向刷新 Snapshot；
- Claim 的实时查询结论写入 Claim Audit，但不得覆盖激活时证据。

## 6. V2.6.9 Decision Record

### 6.1 决策问题

ROLE Runtime 正式启用前，是否需要新增 V2.6.9，以及采用何种持久化结构。

### 6.2 方案对比

| 维度 | 方案A：不新增表，复用现有结构 | 方案B：增加通用 Workflow Runtime Snapshot | 方案C：独立 ROLE Runtime Snapshot |
| --- | --- | --- | --- |
| 实现方式 | 将证据写入现有 JSON/Hash 字段 | 扩充通用 Binding/Snapshot 结构容纳各 Resolver | 新增 Approval 与 ROLE 1:1 扩展 Snapshot，复用通用 Binding/Pool |
| 审计能力 | 弱；审批人、Revision、Activation Manifest 难以结构化验证 | 强；可统一治理 | **强；ROLE 证据语义明确，Approval 可独立审计** |
| 历史追溯 | 依赖 JSON 约定，查询与约束弱 | 较强 | **最强；可精确关联 Approval、Binding、Directory、Pool** |
| 查询性能 | JSON 查询和索引能力有限 | 通用表字段可能稀疏但可索引 | **ROLE 查询走专用索引，通用路径不受影响** |
| 数据一致性 | 难建立外键、唯一和 CHECK | 可建立，但通用模型复杂度增加 | **可建立 1:1、状态、Hash 和活动记录约束** |
| Migration 复杂度 | 最低 | 高；影响通用表和 USER 路径 | 中；新增扩展表及少量引用/约束 |
| USER/DIRECT 回归风险 | 数据库低、运行审计高 | 较高 | **最低；ROLE 扩展隔离** |
| 扩展到 POSITION/ORG | JSON 可容纳但缺少治理 | 通用性最好，但需提前抽象 | 可通过同类扩展或后续抽象，当前不过度设计 |
| 结论 | 不满足生产审计与完整性要求 | 暂不采用 | **推荐** |

### 6.3 最终决策

选择 **方案C：独立 ROLE Runtime Snapshot，复用通用 Binding、Assignment Snapshot、Candidate Pool 和 Claim**。

V2.6.9 在 ROLE Runtime 真正启用前是必要的，但本 Sprint 只记录设计，不创建 SQL。建议边界：

1. 新增 `workflow_runtime_binding_approval`；
2. 新增 `workflow_role_runtime_binding_snapshot`；
3. 为 Candidate Pool 或 Assignment Snapshot 增加可空的 Runtime Snapshot 引用（若现有通用关联不能可靠反查）；
4. 增量扩展 ROLE + CANDIDATE_POOL 的 CHECK/唯一约束；
5. 所有 ROLE 新字段对历史记录可空，不回填、不推断；
6. 不修改 V2.6.5—V2.6.8 历史 Migration；
7. 不在 Migration 中启用 Resolver 或生成业务数据。

### 6.4 方案C约束建议

- Approval 对同一 node binding + activation manifest 的活动批准必须唯一；
- ROLE Snapshot 与通用 node resolver binding 为 1:1；
- Snapshot 必须引用 APPROVED Approval；
- Approval、Snapshot、Pool 必须属于同一 definition version、instance/node execution 运行链；
- Hash 使用 ASCII/BINARY 比较并约束为 64 位小写十六进制；
- 状态字段使用 CHECK，删除语义兼容 `deleted + delete_token`；
- 物理删除受审计保护；历史运行事实不得级联删除。

## 7. Hash 治理

### 7.1 Canonical 规范

冻结：

```text
ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1
```

持久化 Hash 用于证明数据库中的 Runtime Snapshot 与获批运行清单完全一致。Canonical 输入包含：

- workflow definition/version/node 稳定业务键及内容 Hash；
- resolverCode、resolverVersion、resolverContractHash；
- roleCode、organizationId、effectiveAt；
- directoryContractVersion、directoryRevision、directoryResultHash；
- roleRuleVersion/Hash；
- candidateRuleVersion/Hash、排序、去重、上限和有效期语义；
- proposalHash、eligibilityHash、bindingHash、approvalEvidenceHash；
- sourceEvidenceHash；
- Candidate Pool Hash（生成最终运行快照时）。

禁止包含：数据库自增 ID、created/updated 时间、操作人显示名、乐观锁 version、TraceId 等非业务内容。关联对象使用稳定业务键或其 Canonical Hash。

### 7.2 必须产生新 Hash 的变化

以下变化必须生成新的 Persistence Hash，并触发重新 Eligibility/Approval：

- Resolver Code、Version、Contract Hash 或运行状态变化；
- roleCode、organizationId、effectiveAt 变化；
- Directory Contract、Revision、Result Hash 或来源证据变化；
- Role Rule、Candidate Rule、候选上限、排序/去重规则变化；
- Definition、Version、Node 或 Approval Route 变化；
- Proposal、Eligibility、Binding、Approval Evidence 或 Candidate Pool Hash 变化。

成员输入顺序变化但 Canonical 用户集合及证据语义不变时，Hash 必须保持一致。成员增加、删除、有效期或来源变化必须改变 Hash。

### 7.3 存储与校验

- 算法：SHA-256；
- 编码：UTF-8 Canonical bytes；
- 展示：64 位小写十六进制；
- 数据库：ASCII + binary collation 或等价二进制类型；
- 写入前、读取后、激活前均校验；
- Hash 不一致必须 Fail Closed，禁止自动重算后覆盖历史值。

## 8. Claim 影响

ROLE Candidate Pool 进入 Claim 后采用“双时点证据”：

1. **Pool 创建时**保存冻结候选证据：Role Runtime Snapshot、Directory Revision/Result Hash、Pool Hash、Member Eligibility Snapshot；
2. **Claim 执行时**保存实时资格证据：人员状态、当前角色/组织执行资格、RBAC、DataScope、SoD、校验时间和结果 Hash。

Claim 规则：

- 用户必须存在于冻结 Candidate Pool；
- `workflow:approve` 只代表基础功能权限，不等于当前 Task 处理权；
- Claim 可以读取 Directory 验证实时资格，但不得用返回结果新增、删除或替换候选成员；
- 禁止 Claim 调用 Directory 重新生成 Candidate Pool；
- Directory Revision 变化本身不修改 Pool；若当前用户已失去资格则拒绝 Claim；
- 相同用户与幂等键返回原结果；并发 Claim 继续由固定锁序、唯一约束和 CAS 保证单赢家；
- Claim Audit 必须关联 Runtime Snapshot、Pool、Member 和实时 Eligibility Hash；
- 无可用候选人时进入治理异常，不 fallback 到 USER/DIRECT 或默认负责人。

## 9. Legacy 兼容

| 模式 | 持久化与运行规则 |
| --- | --- |
| `SINGLE_NODE_LEGACY` | 不新增 Approval、ROLE Snapshot 或 Pool；不回填、不重算，保持历史行为 |
| `MULTI_NODE_USER` | 继续走 `EXPLICIT_USER_V1 + DIRECT`；不读取 ROLE 表，不受 V2.6.9 新结构影响 |
| `MULTI_NODE_ROLE_PREVIEW` | 只生成内存 Proposal/Eligibility/Draft；不得落 Runtime Snapshot、Task 或 Pool |
| 未来已启用 ROLE 的历史 Task | 使用创建时冻结的 Snapshot、Pool 与 Resolver 版本；永不按最新目录重新解析 |
| USER/ROLE 混合流程 | 节点级隔离；USER 节点保持原链路，ROLE 节点必须通过 Approval 与 Snapshot 门禁 |

兼容迁移原则：

- 新表不要求历史数据回填；
- 新增引用字段必须允许历史记录为空，并通过模式/类型 CHECK 区分；
- 禁止通过 Task 名称、角色名称或 assignee 推断 ROLE Snapshot；
- 历史 Candidate Pool 永不刷新；
- Legacy Task 不因 Registry、Directory 或 Resolver 升级而重新执行 Resolver。

## 10. 风险

| 风险 | 影响 | 治理措施 |
| --- | --- | --- |
| 仅用 JSON 保存证据 | 难以约束和审计 | 采用独立 Approval + ROLE Snapshot |
| Snapshot 与通用 Binding 脱节 | 跨节点/版本错误执行 | 1:1 外键、归属校验、统一 Activation Hash |
| Approval 被重复晋级 | 重复 Pool/Task | 活动批准唯一约束、幂等键、CAS |
| Directory Revision 漂移 | 获批清单与实际候选不同 | 漂移即过期并重新审批，禁止静默接受 |
| Directory 数据被过量复制 | 隐私与主数据双写 | 只保存最小证据和 Hash/引用 |
| Hash Canonical 不一致 | 误判篡改或错误放行 | 固定 Canonical V1、二进制比较、共享测试向量 |
| ROLE 字段污染 USER 路径 | EXPLICIT_USER 回归 | ROLE 扩展表隔离，历史字段可空 |
| Claim 重新解析候选池 | 历史证据被覆盖 | 强制 Pool 不可变，仅做实时资格复核 |
| 全部候选失效 | Task 无人可领 | 治理阻断、人工异常流程；禁止自动替补 |
| 远程 Directory 与本地事务不一致 | 部分成功 | Revision compare-and-create、短事务、幂等与审计 |
| V2.6.9 Upgrade 脏数据 | Migration 失败 | Fresh/Upgrade 预检，不回填推断，前向约束 |
| 审批人与申请人职责冲突 | 自批风险 | Maker-Checker 与 SoD 门禁 |
| ROLE 误启用 | 生产越权 | Resolver 继续 PREPARED、Feature Flag 默认关闭、独立发布验收 |

## 11. 设计冻结结论

1. `RuntimeBindingApproval` 必须持久化；
2. `RoleRuntimeBinding` 必须作为 Workflow Runtime 的不可变 ROLE 扩展快照持久化；
3. Candidate Snapshot 采用 Assignment Snapshot 摘要 + 现有 Candidate Pool/Member 明细，不创建 ROLE 专用 Pool；
4. Directory 继续持有人员、角色、组织主数据，Workflow 只保存 Revision、Hash、有效时点和最小来源证据；
5. 推荐 V2.6.9 采用方案C：Approval 表 + ROLE Runtime Snapshot 表 + 最小通用引用/约束扩展；
6. 本 Sprint 不创建 V2.6.9 SQL，不修改任何历史 Migration；
7. Claim 不得重新调用 Directory 生成候选，只校验实时执行资格；
8. Legacy、USER/DIRECT 与历史 Candidate Pool 均不回填、不刷新、不重新解析；
9. `ROLE_DIRECTORY_V1` 继续 PREPARED，不创建 ROLE Task 或真实 Candidate Pool；
10. 最终状态保持：

```text
ROLE_RUNTIME_PERSISTENCE_DESIGN_READY
ROLE_RUNTIME_DISABLED
```

