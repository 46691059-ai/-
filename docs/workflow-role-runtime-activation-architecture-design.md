# Workflow ROLE Runtime 正式启用架构设计

## 1. 当前架构状态

### 1.1 基线

当前 Workflow ROLE Resolver 已完成以下治理能力，但尚未进入真实运行链路：

- WF5.1：`RoleDirectoryPort`、`RoleDirectoryResolver` 与 `ROLE_DIRECTORY_V1` 框架已建立；
- WF5.2：ROLE Directory 结果到 Candidate Pool Draft 的适配与 Canonical Hash 已建立；
- WF5.3：节点级 ROLE Resolver Binding Proposal 与 Multi-Resolver Binding 融合已建立；
- WF5.4：Proposal Eligibility、Runtime Binding Candidate 与内存审计模型已建立；
- `EXPLICIT_USER_V1` 是唯一可执行的 ACTIVE Resolver；
- `ROLE_DIRECTORY_V1` 与 ROLE Candidate Adapter 保持 PREPARED；
- ROLE 仅能生成 Proposal、Eligibility 和 Draft，不得创建真实 Task、Candidate Pool 或 Runtime Binding。

当前冻结状态：

```text
ROLE_RUNTIME_ACTIVATION_DESIGN_READY（本设计完成后）
ROLE_RUNTIME_DISABLED
```

### 1.2 现有持久化能力

现有 V2.6.x 数据结构已提供：

- Resolver Binding Set、Resolver Binding、节点 Resolver Binding Snapshot；
- Candidate Pool、Candidate Member 及其合同、规则和资格快照 Hash；
- Task Claim、Claim Audit 与候选池所有权、Claim 完整性约束；
- USER + DIRECT 的冻结、执行和历史兼容能力。

上述结构是 ROLE Runtime 的复用基础，但当前尚未持久化“谁批准启用该 ROLE Binding”、目录 Revision、ROLE 业务键及最终 Activation Manifest 等强审计证据。

## 2. ROLE Runtime 目标架构

ROLE Runtime 正式启用必须经过完整、不可跳过的治理链：

```text
Node Resolver Binding Proposal
        ↓
Runtime Eligibility Check
        ↓
Runtime Binding Candidate
        ↓
RuntimeBindingApproval（人工治理审批）
        ↓
RoleRuntimeBinding（不可变运行绑定）
        ↓
RoleDirectoryPort（按冻结条件解析）
        ↓
Candidate Pool（不可变候选池）
        ↓
Claim（实时执行资格复核）
        ↓
Task Action
```

各阶段职责必须分离：

| 阶段 | 产物 | 是否可执行 | 核心门禁 |
| --- | --- | --- | --- |
| Proposal | 节点 Resolver 配置提案 | 否 | 配置完整、Hash 可重现 |
| Eligibility | 治理资格结论 | 否 | Resolver、合同、角色、组织、候选规模均有效 |
| Runtime Binding Candidate | 待批准运行清单 | 否 | 与 Proposal、Eligibility 完整关联 |
| Approval | 批准或拒绝记录 | 否 | Maker-Checker、精确 Hash 绑定、有效期 |
| Runtime Binding | 不可变节点运行绑定 | 是 | Approval=APPROVED 且所有 Hash 未漂移 |
| Candidate Pool | 不可变候选资格快照 | 是 | Directory Revision 和结果 Hash 与批准清单一致 |
| Claim | 单一任务处理人 | 是 | 冻结候选资格 + 实时执行资格双重校验 |

任何环节失败均 Fail Closed；禁止 fallback 到 `EXPLICIT_USER_V1`，禁止自动升级或降级 Resolver，禁止绕过审批直接创建 Runtime Binding。

## 3. Proposal 晋级机制

### 3.1 RuntimeBindingApproval

新增治理概念 `RuntimeBindingApproval`：

| 字段 | 含义 |
| --- | --- |
| proposalHash | 被审批的 Proposal Canonical Hash |
| eligibilityHash | Eligibility 结论及其输入 Hash |
| resolverContractHash | Resolver 实现契约 Hash |
| approvedBy | 审批人用户标识 |
| approvedAt | 审批时间 |
| status | `PENDING`、`APPROVED`、`REJECTED`、`EXPIRED` |

建议持久化时同时记录 `approval_id`、申请人、申请时间、审批意见、审批依据、TraceId、有效截止时间和审计事件引用；这些属于审计证据，不进入 Canonical 业务 Hash。

### 3.2 状态机

```text
PENDING ──approve──> APPROVED
   │                   │
   ├──reject──────> REJECTED
   │
   └──expire──────> EXPIRED

APPROVED ──governed input drift / timeout──> EXPIRED
```

- `REJECTED`、`EXPIRED` 为终态，不允许原记录重新批准；
- 重新申请必须创建新的 Proposal、Eligibility 和 Approval 记录；
- `APPROVED` 只批准一份精确的 Activation Manifest，不是对某个角色或 Resolver 的长期通用授权；
- Proposal 创建人、业务申请人和审批人必须满足职责分离策略，至少禁止同一人员单独完成提出与批准；
- 未达到 `APPROVED` 时，任何代码路径都不得生成 `RoleRuntimeBinding`。

### 3.3 漂移处理

以下任一输入变化均使原 Approval 失效：

- 流程定义、版本或节点配置变化；
- Resolver Code、Version、Contract Hash 或状态变化；
- roleCode、organizationId、businessMeaning、approvalRoute 变化；
- Directory Revision、目录结果 Hash 或候选规则变化；
- effectiveAt 语义或候选上限变化；
- Proposal、Eligibility、Binding Hash 变化。

失效后必须重新执行 Proposal → Eligibility → Approval，禁止局部补签或仅更新 Hash。

## 4. Runtime Binding 模型

### 4.1 RoleRuntimeBinding

`RoleRuntimeBinding` 是某一流程版本、某一节点的不可变运行合同：

| 字段 | 说明 |
| --- | --- |
| resolverCode | 固定为经批准的 Resolver Code |
| resolverVersion | 固定 Resolver Version |
| contractHash | 固定 Resolver Contract Hash |
| roleCode | 业务审批角色代码 |
| organizationId | 角色解析组织边界 |
| effectiveAt | 目录解析的冻结业务时点 |
| bindingHash | ROLE Binding Canonical Hash |

运行绑定还必须关联：definition/version/node、binding set、approval、directory contract、candidate rule version 和 Activation Hash。

### 4.2 不可变规则

- Runtime Binding 一经生成不得原位修改；
- 配置变化必须生成新 Proposal、新 Approval 和新 Binding；
- 已启动实例始终使用实例/节点快照绑定，不读取 Registry 的“最新版本”；
- Resolver 由 PREPARED 晋级 ACTIVE 只能通过独立发布门禁完成，本设计不执行该状态切换；
- Runtime 启动时必须按 code + version 精确查找 Resolver，并校验 ACTIVE 与 Contract Hash；禁止 fallback；
- Binding 与流程版本、节点必须属于同一聚合版本，禁止跨版本引用。

### 4.3 激活事务

ROLE 节点首次激活时建议采用以下事务边界：

1. 锁定 Workflow Instance 与 NodeExecution；
2. 读取节点冻结的 Runtime Binding；
3. 校验 Approval 为 APPROVED 且未过期；
4. 校验 Activation Hash、Resolver 状态和 Contract Hash；
5. 按批准的 effectiveAt、roleCode、organizationId 和预期 Revision 调用 Directory；
6. 校验 Directory Result Hash、成员有效期、候选上限；
7. 创建 Task（Candidate Pool 模式下 `assignee_user_id` 为空）；
8. 创建不可变 Candidate Pool 与 Candidate Members；
9. 激活 NodeExecution 和 Task；
10. 写入审计证据。

第 5 步属于外部只读调用，不应持有长事务数据库锁。推荐先取得带 Revision 的目录快照，再在短事务内按预期 Revision/Hash 做 compare-and-create；若 Revision 已变化则整体阻断并使 Approval 过期，不允许使用新结果静默继续。

## 5. Candidate Pool 生命周期

### 5.1 创建时点

Candidate Pool 在 ROLE 节点首次真实激活时创建，而不是在 Workflow Instance 启动时预建。原因是：

- 未到达节点不应提前授予候选资格；
- effectiveAt 应对应节点激活的治理时点；
- 避免长流程中目录变化导致预建候选池与批准证据脱节；
- 未执行节点无需产生人员数据快照。

### 5.2 冻结规则

Pool 创建成功即冻结：

- 禁止刷新、覆盖、追加或删除成员；
- 禁止自动同步角色成员、组织或人员状态变化；
- 禁止因 Resolver 新版本发布而重新解析；
- Candidate Canonical 排序、去重、来源证据、Directory Revision、Directory Result Hash、Candidate Hash 必须保存；
- 同一 Task 只能存在一个活动 Candidate Pool；重复创建按幂等键返回既有结果或被唯一约束拒绝。

### 5.3 重新解析规则

- Pool 创建前：若受治理输入发生变化，只能废止原 Approval，并开启新一轮 Proposal/Eligibility/Approval；
- Pool 创建后：永不重新解析；
- 全部候选人实时资格失效时：Task 进入治理阻断/人工异常处理，不重写 Pool，不自动追加候选人；
- 未来若支持转交或重新分配，必须创建独立、可审计的新治理动作，不得修改原 Pool 证据。

## 6. Claim 影响

ROLE Candidate Pool 的 Claim 沿用现有单赢家、幂等、固定锁顺序、唯一约束与 CAS 治理，并增加 ROLE 实时资格复核。

固定门禁顺序建议保持：

```text
RBAC基础能力
→ Task状态与所有权
→ NodeExecution
→ WorkflowInstance
→ RuntimeBinding与Approval
→ CandidatePool
→ CandidateMember
→ 人员实时有效状态
→ Role/Organization实时执行资格
→ DataScope
→ SoD
→ Claim CAS与审计
```

必须明确：

- `workflow:approve` 仅代表进入审批功能的基础 RBAC 能力，不等于当前 Task 的 Claim 或审批资格；
- 用户必须存在于冻结 Candidate Pool；
- Claim 时可以只读查询当前 Directory/人员状态进行资格确认，但不得据此重新生成或修改 Pool；
- 离职、停用、角色撤销、组织调动、职责分离冲突均应拒绝 Claim；
- 某一候选人失效不影响其他冻结候选人按实时规则 Claim；
- Claim 成功只确定处理人，不等于完成审批动作；
- Hash、Approval 或 Runtime Binding 异常时必须安全阻断并写审计事件。

## 7. Role Directory 边界

调用边界：

```text
Workflow RoleDirectoryPort
        ↓（只读契约）
Approval Role / Organization Directory
```

Workflow 只负责：

- 根据稳定业务键和有效时点读取目录；
- 校验 Directory Contract、Revision 和结果 Hash；
- 冻结运行证据；
- 执行 Candidate Pool、Claim 和审计规则。

Workflow 禁止：

- 创建或维护审批角色；
- 修改角色成员、岗位、组织或有效期；
- 修正目录脏数据；
- 在目录不可用时用本地猜测或历史结果替代；
- 把 RBAC 角色直接当作审批角色目录。

Directory 必须提供稳定的 roleCode、organizationId、effectiveAt、revision、成员有效期、成员状态、来源证据和可验证的 Canonical Hash。目录超时、Revision 漂移或合同不匹配均应阻断激活。

## 8. Migration 评估

### 8.1 决策

**Migration Decision：ROLE Runtime 真正启用前需要 V2.6.9 增量 Migration；本 Sprint 不创建 SQL。**

原因：现有 V2.6.5—V2.6.8 能承载通用 Binding、Candidate Pool 和 Claim，但不足以用结构化约束保存 Runtime Approval、ROLE Directory Revision 与最终 Activation Manifest。仅将这些证据写入 JSON 会降低唯一性、关联完整性、检索和审计可验证性。

### 8.2 V2.6.9 建议边界

后续候选 Migration 应采用前向兼容方式，且不得修改历史 Migration：

1. 新增 `workflow_runtime_binding_approval`（或等价命名）保存 Proposal/Eligibility/Contract/Activation Hash、状态与审批审计；
2. 新增 `workflow_role_runtime_binding_snapshot` 作为通用 Resolver Binding 的 1:1 ROLE 扩展，保存 roleCode、organizationId、effectiveAt、directoryRevision、directoryResultHash、candidateRuleVersion、activationHash 与 approvalId；
3. Candidate Pool 继续复用 `workflow_task_candidate_pool` 与 `workflow_task_candidate_member`，不新建第二套 ROLE Pool；
4. Candidate Pool 通过外键关联 ROLE Runtime Binding Snapshot；如通用字段无法表达，可只增加可空的 snapshot/approval 引用，不把 ROLE 字段平铺到所有任务；
5. 通过增量 CHECK/唯一约束受控扩展 `ROLE + CANDIDATE_POOL`，保持 `USER + DIRECT` 原约束及行为；
6. 新字段对历史数据应可空，不回填、不推断、不重新解析 Legacy/USER Task；
7. Directory 自身数据不属于 Workflow Migration，必须由组织/治理目录独立管理。

### 8.3 不建议的方案

- 不修改 V2.6.5、V2.6.6、V2.6.7、V2.6.8；
- 不创建 ROLE 专用 Task、Pool 或 Claim 表；
- 不把 Directory 全量成员复制为 Workflow 主数据；
- 不用数据库自增 ID 参与 Canonical Hash；
- 不在 Migration 中把 `ROLE_DIRECTORY_V1` 直接改为 ACTIVE；
- 不对历史实例做猜测性回填。

## 9. Investment 集成边界

Investment 负责投资决策业务语义，Workflow 负责审批执行治理。

Investment 向 Workflow 提供：

- `roleCode`：审批职责业务键；
- `businessMeaning`：党委前置研究、董事会决策、经理层决策等业务语义；
- `approvalRoute`：经业务规则选定的审批路线及其 Hash；
- 业务 ID、决策快照 ID、风险门禁与审计关联标识。

Investment 禁止提供候选 `userId`，也不得绕过 Role Directory 指定处理人。

Workflow 负责：

- Resolver 版本与合同治理；
- Proposal、Eligibility、Approval 与 Runtime Binding；
- Directory 解析、Candidate Pool 冻结；
- Claim、任务状态、审批动作与流程审计。

Workflow 禁止直接修改 Investment 决策状态。Workflow 只发布带幂等键、事件序号和签名的结果事件；Investment 根据自身状态机消费事件并完成业务状态变更。

三重一大职责分离规则由 Investment 提供业务约束语义，由 Workflow 在 Eligibility 和 Claim 阶段执行并留痕。党委、董事会、经理层角色不得硬编码在 Workflow 引擎中。

## 10. Hash 治理

### 10.1 Canonical 版本

冻结 Canonical 规范：

```text
ROLE_RUNTIME_ACTIVATION_CANONICAL_V1
```

Canonical 输入至少包含：

- Workflow Definition Code/ID、Version、Definition Content Hash；
- Node Code/ID、顺序、类型及节点规则 Hash；
- Resolver Code、Version、Mode、Contract Hash；
- roleCode、organizationId、effectiveAt；
- Directory Contract、Revision、Result Hash；
- Proposal Hash、Eligibility Hash、Binding Hash；
- businessMeaning 与 approvalRoute Hash；
- Candidate Rule Version、候选上限、去重规则、排序规则、有效期语义；
- Approval Evidence Hash；
- Candidate Count 与 Candidate Pool Hash（形成最终激活证据时）。

禁止包含：数据库自增 ID、创建/更新时间、操作人展示名称、乐观锁 version、非业务审计字段。

### 10.2 Hash 层次

建议保留可独立验证的 Hash 链：

```text
DefinitionContentHash
  → NodeRuleHash
  → ResolverContractHash
  → ProposalBindingHash
  → EligibilityHash
  → ApprovalEvidenceHash
  → DirectoryResultHash
  → CandidatePoolHash
  → RoleRuntimeActivationHash
  → ClaimEligibilitySnapshotHash
```

同一语义数据经字段排序、用户 ID 升序、来源合并和稳定序列化后必须产生相同 Hash；任一运行关键因素变化必须产生不同 Hash。

### 10.3 重新审批规则

Approval 必须绑定含 Directory Revision/Result 的完整 Activation Manifest。节点激活时按预期 Revision 再读取目录：

- Revision、结果或任何 Canonical 输入一致：允许生成 Runtime Binding/Pool；
- 任一输入变化：原 Approval 变为 EXPIRED，节点保持阻断，重新执行完整审批；
- 禁止自动接受“更高 Revision”，即使候选集合看似相同；
- 禁止只更新 Hash 而沿用旧 Approval。

Hash 算法固定为 SHA-256，小写十六进制、ASCII/BINARY 比较；跨语言实现必须使用同一 Canonical JSON/字节规范和测试向量。

## 11. 兼容设计

| 场景 | 兼容规则 |
| --- | --- |
| `SINGLE_NODE_LEGACY` | 不创建新 Binding/Pool，不回填、不重新解析，保持原有行为 |
| `MULTI_NODE_LINEAR_V1 + USER` | 继续使用 `EXPLICIT_USER_V1 + DIRECT`，ROLE 设计不得进入其调用链 |
| `MULTI_NODE + ROLE Proposal` | 只允许生成 Proposal/Eligibility；未批准、Resolver 未 ACTIVE 时不得运行 |
| ROLE 启用后的历史 Task | 永远使用创建时冻结的 Binding、Resolver 版本和 Pool，不读取最新目录重建 |
| USER/ROLE 混合流程 | 每个节点按自身冻结 Binding 执行；ROLE 节点失败不得影响已完成 USER 节点的历史证据 |

历史任务不得因 Registry、Resolver、Directory、角色成员或组织变化而重新执行 Resolver。

## 12. 风险清单

| 风险 | 影响 | 治理措施 |
| --- | --- | --- |
| Approval 与目录数据陈旧 | 错误人员获得候选资格 | Approval 绑定 Revision/Result Hash，漂移即过期 |
| Directory 不可用或弱一致 | 节点无法安全激活 | Fail Closed、超时、熔断、可观测告警；禁止缓存兜底执行 |
| 跨语言 Canonical 不一致 | Hash 误判 | 固定字节规范、排序规则和共享测试向量 |
| 候选规模过大 | 性能和隐私风险 | Eligibility 阶段强制上限，超限阻断 |
| 外部读取与本地事务断裂 | 部分写入 | Revision compare-and-create、短事务、幂等键和唯一约束 |
| 人员离职/调岗 | 冻结候选失去资格 | Claim 实时复核，不修改 Pool |
| SoD/DataScope 变化 | 越权审批 | Claim 时实时强制校验并审计 |
| Resolver 状态误切换 | ROLE 意外运行 | 独立发布门禁、启动检查、默认 Disabled、无 fallback |
| 混合 Resolver 回归 | USER 流程受影响 | 分支隔离、兼容测试、Feature Flag 与灰度范围 |
| 并发 Claim/激活 | 重复 Task 或多赢家 | 固定锁序、CAS、唯一约束、幂等键 |
| V2.6.9 历史脏数据 | Upgrade 失败 | 上线前审计、只增加可空兼容字段、禁止推断回填 |
| 审批证据被修改 | 审计不可追溯 | 不可变记录、Hash 链、操作日志和保留策略 |
| 当前真实环境未就绪 | 无法证明生产可用 | 保持 ROLE_RUNTIME_DISABLED，单独完成真实目录和预生产验收 |

## 13. WF6 实施边界

后续实施必须拆分为独立、可回退的门禁阶段；本设计不代表自动进入实施：

1. **数据库候选阶段**：设计并创建 V2.6.9 候选 Migration，单独完成 Fresh/Upgrade、约束、Hash 与双路径 Schema 验收；
2. **Approval 持久化阶段**：实现 RuntimeBindingApproval、ROLE Snapshot Repository 和审计，不创建真实 ROLE Task；
3. **Directory 接入阶段**：接入真实只读 Approval Role Directory，完成合同、Revision、超时、签名和数据质量验收；
4. **Shadow 阶段**：只生成 Proposal、Eligibility、Approval Preview 和差异报告，不落真实 Pool；
5. **发布门禁阶段**：经配置、审计和双人批准后，独立将指定版本 Resolver 从 PREPARED 晋级 ACTIVE；默认仍为 Disabled；
6. **ROLE 节点激活阶段**：仅对白名单流程/组织启用 Runtime Binding 与 Candidate Pool 创建；
7. **Claim 闭环阶段**：完成实时资格、SoD、DataScope、并发单赢家及异常阻断验收；
8. **Investment 适配阶段**：另行接入三重一大业务路线，只传 roleCode/businessMeaning/approvalRoute，不传 userId。

首个实施阶段仍禁止：POSITION/ORG Resolver、条件路由、会签、自动降级、Pool 刷新、动态 Resolver 切换以及默认生产启用。

## 14. 设计冻结结论

- ROLE Runtime 启用必须经过 Proposal → Eligibility → Runtime Binding Candidate → Approval → Runtime Binding → Candidate Pool → Claim 全链路；
- Approval、Binding 和 Pool 均是不可变审计证据；任一关键 Hash 漂移必须重新审批；
- Candidate Pool 创建后永不刷新，Claim 只做实时执行资格复核；
- Investment 不得传递 userId，Workflow 不得维护角色目录或直接修改 Investment 状态；
- 真正启用前需要 V2.6.9 前向 Migration，但本 Sprint 不创建任何 Migration；
- `EXPLICIT_USER_V1` 行为保持不变，`ROLE_DIRECTORY_V1` 继续 PREPARED；
- 当前状态保持：

```text
ROLE_RUNTIME_ACTIVATION_DESIGN_READY
ROLE_RUNTIME_DISABLED
```

