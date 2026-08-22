# Sprint 2-3.7-WF5.21 Realtime Eligibility Capability Governance Design

## 1. Current Baseline

- 数据库：V2.6.15，`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- SHA-256：`db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44`
- Flyway checksum：`538981271`
- Migration SHA：37/37
- `PRODUCTION_ROLE_DIRECTORY_ADAPTER_FRAMEWORK_READY`
- `PRODUCTION_ROLE_DIRECTORY_INTEGRATION_READINESS_READY`
- `DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED`
- `PRODUCTION_ROLE_DIRECTORY_CONNECTIVITY_BLOCKED`
- `ROLE_DIRECTORY_V1_PREPARED_NON_EXECUTABLE`
- `ROLE_RUNTIME_DISABLED`

WF5.21只冻结设计，不改变任何上述状态。40项Directory资源仍为MISSING，设计完成不构成真实Directory或Realtime Eligibility可用证明。

## 2. Scope

目标是回答：已在冻结Candidate Pool中的用户，在Claim发生时是否仍具备处理该Task的实时资格。

本设计覆盖Capability契约、固定校验链、Directory/DataScope/SoD/RBAC边界、Prepare/Verify/Commit、TOCTOU、Evidence/Hash/Audit、失败分类、Legacy兼容及未来持久化边界。

不包含Production Adapter、真实Directory调用、ROLE Runtime启用、Candidate Pool刷新、Task/Claim实现、Investment集成或Migration。

## 3. Candidate vs Realtime Eligibility

| 维度 | Candidate Membership | Realtime Eligibility |
|---|---|---|
| 回答 | Task创建时谁是候选人 | Claim时该候选人现在是否可Claim |
| effectiveAt | NodeExecution首次激活时点 | claimAt |
| 数据性质 | 冻结历史事实 | 当前权威事实 |
| Revision | candidateDirectoryRevision | claimDirectoryRevision |
| 结果 | 固定Candidate集合 | 单一candidateUserId的ELIGIBLE/INELIGIBLE/INDETERMINATE |
| 变更行为 | 永不刷新、覆盖或补人 | 只判断，不修改Pool/Member |

首个硬门禁必须是 `candidateUserId ∈ Frozen Candidate Pool`。非候选人即使当前Directory拥有Role，也返回 `NOT_CANDIDATE`；禁止先查Directory再把用户加入Pool。

## 4. Capability 模型

建议在纯Domain中定义：

```text
RealtimeEligibilityPort
  RealtimeEligibilityResult assess(RealtimeEligibilityQuery query)

RealtimeEligibilityQuery       不可变输入
RealtimeEligibilityResult      三态结果、failure、evidence
RealtimeEligibilityEvidence    完整治理事实
RealtimeEligibilityDecision    最终判定及允许继续Claim标记
RealtimeEligibilityFailure     稳定失败代码和类别
RealtimeEligibilityCanonical   ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1
```

`RealtimeEligibilityPort`只回答指定Frozen Candidate是否合格，不返回候选集合，不写Task/Pool/Claim。Production Adapter留待外部Directory Connectivity通过后的独立阶段；WF5.22可先实现Port、Domain及Fake契约，但必须保持ROLE Runtime Disabled。

现有 `RealtimeEligibilityChecker` 只返回allowed及user/employment/org/position摘要，可作为Legacy USER/Claim兼容端口，不能静默扩展成ROLE权威Directory契约。建议新契约独立版本化，再由Application编排层适配。

## 5. Query 契约

`RealtimeEligibilityQuery`至少包含：

| 字段 | 来源 | 约束 |
|---|---|---|
| enterpriseId | Workflow Instance持久化事实 | 必填、不可由Controller覆盖 |
| workflowInstanceId | Task/Instance关联 | 正数 |
| taskId | 路径与持久化Task交叉核验 | 正数 |
| nodeExecutionId | Task持久化事实 | 正数 |
| candidatePoolId | Task关联Pool | 正数 |
| candidateUserId | 当前安全Principal | 必须已在Pool |
| roleCode | Node Runtime Binding | 大写稳定业务键 |
| organizationId | Binding/业务组织事实 | V1精确BUSINESS_ORG |
| claimAt | 服务器可信时钟 | 不接受客户端时间 |
| directoryRevisionReference | Candidate Pool冻结证据 | candidate revision/hash引用 |
| candidatePoolHash | Pool持久化事实 | 小写SHA-256 |
| runtimeBindingHash | Runtime Binding Snapshot | 小写SHA-256 |
| businessScopeReference | Instance业务范围引用 | 不含业务敏感载荷 |
| correlationId | 服务端追踪上下文 | 必填 |

Controller只允许提交claim幂等键等命令字段。roleCode、organizationId、revision reference、Pool/Binding Hash必须从数据库重建，禁止请求体覆盖。

## 6. effectiveAt

- Candidate Pool：`effectiveAt = NodeExecution首次激活时间`，用于冻结当时资格。
- Realtime Eligibility：`effectiveAt = claimAt`，来自服务器可信Clock。

二者不可混用。历史Pool仍按生成时点解释；Claim只查询claimAt当前权威事实。所有时间统一为UTC Instant，Directory成员有效区间为 `[effectiveFrom,effectiveTo)`。

## 7. 固定校验链

顺序冻结如下，任何失败立即fail closed且记录已执行步骤：

| 顺序 | 门禁 | 失败类别 |
|---:|---|---|
| 1 | ROLE Runtime Gate | INDETERMINATE/BLOCKED |
| 2 | Kill Switch | INELIGIBLE |
| 3 | Task存在 | INELIGIBLE |
| 4 | Task状态允许Claim | INELIGIBLE |
| 5 | Candidate Pool存在 | INELIGIBLE |
| 6 | Pool属于Task | INELIGIBLE |
| 7 | Candidate User存在于Pool | INELIGIBLE/NOT_CANDIDATE |
| 8 | Candidate Member未逻辑失效 | INELIGIBLE |
| 9 | Runtime Binding一致 | INDETERMINATE |
| 10 | Role一致 | INDETERMINATE |
| 11 | Organization一致 | INDETERMINATE |
| 12 | Enterprise一致 | INDETERMINATE |
| 13 | User当前有效 | INELIGIBLE |
| 14 | 当前组织关系有效 | INELIGIBLE |
| 15 | 当前Approval Role Membership有效 | INELIGIBLE |
| 16 | Directory Result Complete | INDETERMINATE |
| 17 | Directory Revision合法 | INDETERMINATE |
| 18 | DataScope | DENY或INDETERMINATE |
| 19 | Platform SoD | DENY或INDETERMINATE |
| 20 | Business SoD | DENY或INDETERMINATE |
| 21 | Existing Active Claim | INELIGIBLE/幂等返回 |
| 22 | Task Assignee约束 | INELIGIBLE |
| 23 | Claim Idempotency | 原结果或冲突 |
| 24 | Audit Capability | INDETERMINATE |
| 25 | Feature Flag | INELIGIBLE |
| 26 | Canary Scope | INELIGIBLE |
| 27 | Final Eligibility Hash | INDETERMINATE |

RBAC入口权限应在Application Service边界先行检查，但不替代链中任何业务资格项。

## 8. Role Membership

Candidate Pool中的ROLE证据只证明历史Candidate Membership。Claim时重新确认 `candidateUserId + enterpriseId + organizationId + roleCode + claimAt`。

唯一权威来源是Approval Role Directory。禁止用 `sys_role`、`sys_user_role`、`workflow:approve`、岗位名称或行政职务代替PROCESS_APPROVAL_ROLE。Directory只返回该用户的资格判定/证据，不能返回新候选集合。

## 9. Directory Revision

必须分别记录：

- `candidateDirectoryRevision`：Pool生成时，例如R10。
- `claimDirectoryRevision`：Claim Prepare时，例如R15。

R10→R15本身不代表失败；应判断用户在R15、claimAt时点是否仍满足资格。Revision必须属于相同enterprise/org/role聚合范围，符合Provider单调/不可复用契约，并与resultHash、contractHash共同形成Fence。

## 10. Correction

Directory发布R16更正历史事实时：

- 不更新Candidate Pool、Candidate Member或其Hash。
- 未Claim任务的下一次Prepare读取claimAt可获得的最新权威事实。
- 已成功Claim的历史Evidence保持不变。
- Evidence记录correctionReference、claim revision、effectiveAt和resultHash。

Correction不能作为自动补人、换人或重写历史资格的机制。

## 11. Complete

Realtime Directory Result必须 `complete=true`。timeout、partial、分页不完整、source conflict、revision conflict、hash/contract mismatch统一为 `INDETERMINATE`，Claim拒绝。

“无法证明”不能转换为ELIGIBLE，也不能fallback旧缓存、EXPLICIT_USER或Candidate生成时证据。

## 12. Result 状态

```text
ELIGIBLE      所有27项门禁通过，可进入短事务Verify
INELIGIBLE    权威事实明确拒绝，如角色撤销、用户停用、SoD deny
INDETERMINATE 能力不可用或证据不完整，fail closed
```

只有ELIGIBLE允许继续。INELIGIBLE用于可解释业务拒绝；INDETERMINATE用于系统无法可靠判断，便于重试/告警与业务拒绝区分。

## 13. User Status

统一规范状态：`ACTIVE / DISABLED / LOCKED / TERMINATED / SUSPENDED / UNKNOWN`。

只有ACTIVE可通过。Directory/HR适配器负责将源状态映射为规范状态并携带映射版本；UNKNOWN为INDETERMINATE。`sys_user.status`只表示平台账号/RBAC状态，不能单独作为权威人员状态，但平台账号禁用仍可作为额外拒绝门禁。

## 14. Organization Membership

V1只允许精确 `BUSINESS_ORG` 关系，不含parent、child、集团全域或隐式继承。组织关系必须在claimAt有效，并与Query中的organizationId一致。调岗、离职或组织停用导致明确INELIGIBLE；目录不可用则INDETERMINATE。

## 15. DataScope

定义 `DataScopeResult = PASS / DENY / INDETERMINATE`，包含policyVersion、scopeReference、evidenceHash。Candidate Membership不等于DataScope PASS。

现有Claim使用SecurityPrincipal的all/allowedOrg判断；ROLE路径应通过独立Capability Port输出可审计结果。DENY为明确拒绝，INDETERMINATE fail closed。

## 16. Platform SoD

平台SoD至少输入申请人、提交人、前序处理人、当前候选人、关键业务参与人及规则版本。Workflow消费 `PlatformSoDResult`，不自行猜测业务规则。结果为PASS/DENY/INDETERMINATE，证据Hash纳入最终Eligibility Hash。

## 17. Business SoD

定义独立 `BusinessSoDPort`。未来Investment只提供businessSoDRule/reference和必要业务事实，禁止提供审批userId。Workflow负责编排与证据冻结。本Sprint不接Investment；缺少Business SoD能力时ROLE Claim为INDETERMINATE。

## 18. RBAC 边界

`workflow:approve`仅授予进入/执行审批功能的系统权限，不代表Candidate Membership、Role Membership、DataScope或SoD通过。

同样，Realtime ROLE资格通过也不自动授予RBAC。最终Claim必须同时满足RBAC与全部业务门禁。

## 19. Claim 事务

冻结三阶段：

```text
A Prepare（事务外）
  读取不可变Workflow引用 → Directory → DataScope → SoD → Feature/Canary/KillSwitch
  生成有TTL的RealtimeEligibilityEvidence

B Verify（短事务）
  固定锁顺序 Task → Pool → Candidate Member → NodeExecution → Instance
  重查Task/Pool/Member/Binding/Active Claim/幂等键
  校验Evidence未过期及关键Fence未漂移

C Commit（同一短事务）
  insert Claim + append Audit + CAS Task + CAS Pool
  任一步失败整体回滚
```

禁止持有Task/Pool高争用锁时调用远程Directory。

现状差距：`TaskClaimTransactionService` 当前在事务和固定行锁内调用 `RealtimeEligibilityChecker`、DataScope和SoD。WF5.22若引入远程ROLE能力，必须新增Prepare编排和短事务Verify/Commit接口；不能直接替换现有Checker实现为远程调用。USER/DIRECT和Legacy链路保持原状。

## 20. TOCTOU

Prepare到Commit之间必须携带：`eligibilityVerifiedAt`、`eligibilityExpiresAt`、Directory revision/result/contract Fence、candidatePoolHash、runtimeBindingHash和evidenceHash。

Verify时任一条件成立则废弃Evidence并重新Prepare：

- 当前时间不早于expiresAt。
- Pool/Binding/Task关键Hash或version漂移。
- Directory提供可比较的新Revision Fence且不匹配。
- Feature/Kill Switch/Canary policy version变化。

不得在短事务内远程刷新Evidence；应释放事务后重新Prepare。

## 21. TTL

定义配置项 `maxEligibilityAge`，必须为有限正时长且小于用户交互/Claim总预算。生产数值由Directory SLA、网络P99和安全风险评估决定，本设计不拍定。

`expiresAt = min(verifiedAt + maxEligibilityAge, Directory evidence expiry, policy expiry)`。超时返回 `EVIDENCE_EXPIRED`，不得继续Claim。

## 22. Evidence

`RealtimeEligibilityEvidence`至少包含：

- candidatePoolHash、candidateUserId、roleCode、organizationId
- candidateDirectoryRevision、claimDirectoryRevision、directoryResultHash
- correctionReference（可空）
- userStatusResult、organizationMembershipResult、roleMembershipResult
- dataScopeResult、platformSoDResult、businessSoDResult
- featureFlagResult、canaryResult、killSwitchResult、auditCapabilityResult
- runtimeBindingHash、businessScopeReference
- verifiedAt、expiresAt、correlationId
- canonicalVersion、evidenceHash

不保存手机号、身份证、地址、薪资、完整HR档案、Secret或不必要成员列表。

## 23. Canonical Hash

冻结 `ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1`。Canonical使用字段名固定顺序、UTC毫秒时间、小写hex SHA-256、枚举稳定代码、空值显式null。集合字段先按稳定业务键排序。

Hash覆盖所有影响判定的输入、三态Capability结果、policy/contract/revision/hash、verifiedAt/expiresAt和最终状态；排除数据库ID、创建/更新时间、操作人显示名、日志文本和技术重试次数。

输入字段排列变化Hash不变；Role、Org、Revision、Directory Hash、任何Capability结果、TTL或Policy Version变化均改变Hash。

## 24. Audit

必须可回答“用户U为何在时间T能/不能Claim任务X”。审计链使用Hash/稳定引用连接：

`Runtime Binding → Candidate Pool → Candidate Member → Realtime Eligibility → Directory Revision → DataScope → Platform/Business SoD → Claim → Task`。

ELIGIBLE、INELIGIBLE、INDETERMINATE均生成审计事件；只在Claim成功时关联最终Claim ID。失败审计也必须记录stage、failure code、evidence hash和correlationId，不记录PII/Secret。

## 25. Failure 模型

| Code | Result | 类型 |
|---|---|---|
| NOT_CANDIDATE | INELIGIBLE | 业务拒绝 |
| TASK_NOT_CLAIMABLE | INELIGIBLE | 业务拒绝 |
| ROLE_MEMBERSHIP_LOST | INELIGIBLE | 业务拒绝 |
| USER_INACTIVE | INELIGIBLE | 业务拒绝 |
| ORG_MEMBERSHIP_LOST | INELIGIBLE | 业务拒绝 |
| DIRECTORY_UNAVAILABLE | INDETERMINATE | 系统不可判定/可有限重试 |
| DIRECTORY_PARTIAL | INDETERMINATE | 系统不可判定 |
| DIRECTORY_CONFLICT | INDETERMINATE | 系统不可判定 |
| REVISION_INVALID | INDETERMINATE | 证据错误 |
| DATASCOPE_DENIED | INELIGIBLE | 业务拒绝 |
| DATASCOPE_UNAVAILABLE | INDETERMINATE | 系统不可判定 |
| PLATFORM_SOD_DENIED | INELIGIBLE | 业务拒绝 |
| BUSINESS_SOD_DENIED | INELIGIBLE | 业务拒绝 |
| FEATURE_DISABLED | INELIGIBLE | 治理拒绝 |
| CANARY_DENIED | INELIGIBLE | 治理拒绝 |
| KILL_SWITCH_ACTIVE | INELIGIBLE | 紧急治理拒绝 |
| EVIDENCE_EXPIRED | INDETERMINATE | 必须重新Prepare |
| BINDING_MISMATCH | INDETERMINATE | 完整性错误 |
| HASH_MISMATCH | INDETERMINATE | 完整性错误 |

错误不得映射为fallback、默认允许或自动加入Candidate Pool。

## 26. Kill Switch

沿用政策语义 `STOP_NEW_AND_CLAIM`：激活后禁止新ROLE Admission和Claim，但不删除Pool、历史Claim或Evidence。

现有 `RoleRuntimeKillSwitch` 使用OPEN表示允许Admission、CLOSED表示阻断，命名容易与Circuit语义混淆。WF5.22必须通过显式适配器映射政策状态，禁止按枚举字面猜测或反转。Claim时重新检查，Admission通过不形成永久豁免。

## 27. Feature Flag

Claim时重新检查global、enterprise、definition version三级Flag及policyVersion。任何关闭或超出作用域均拒绝。Admission阶段证据只作关联，不替代Claim时事实。

## 28. Canary

首轮Canary限制为单enterprise、单definition version、单ROLE node。Claim Query必须与Canary Scope精确匹配；超出返回CANARY_DENIED。Canary不能隐式扩大到组织、定义其他版本或其他节点。

## 29. Cache

V1不以缓存替代权威实时资格。若未来加入短缓存，每项必须携带revision、effectiveAt、expiresAt、complete、resultHash、contractHash和canonicalVersion；无法证明新鲜/完整则INDETERMINATE。

Candidate Pool历史证据不是Realtime Eligibility缓存。Circuit OPEN时禁止读取旧成员缓存放行。

## 30. Timeout / Retry

Realtime Claim是交互链路，必须定义端到端预算和每个Capability子预算。只对NETWORK_TIMEOUT/TEMPORARY_UNAVAILABLE等明确瞬态错误有限重试；Auth、Contract、Canonical、Partial、Conflict、业务拒绝零重试。

超出预算立即INDETERMINATE。禁止无限重试、长时间持锁、fallback旧缓存或EXPLICIT_USER。

## 31. Circuit Breaker

Directory Circuit OPEN时返回 `INDETERMINATE/DIRECTORY_UNAVAILABLE` 并拒绝Claim。HALF_OPEN只允许专用探测，不承载Claim流量；CLOSED也仍需完整资格检查。

## 32. Legacy / USER

- `EXPLICIT_USER_V1`继续ACTIVE。
- USER + DIRECT不进入ROLE Realtime Eligibility。
- SINGLE_NODE_LEGACY不进入。
- 历史MULTI_NODE USER不进入。
- 历史Task不重新解析Resolver、不生成ROLE Pool、不补造Evidence。

新框架必须按Assignment Strategy/Mode显式路由，禁止通用Claim代码把USER流量送入ROLE Port。

## 33. Persistence Decision

结论：Claim SUCCESS必须持久化足以复核的Realtime Eligibility Evidence；失败Evidence至少进入可靠append-only审计。现有`workflow_task_claim`已有eligibilitySnapshotHash、realtimeEligibilityResult、dataScopeResult、sodResult、rbacResult，可保持现有USER兼容，但字段不足以表达ROLE完整27步证据和Revision/TTL/Capability Hash链。

未来推荐V2.6.16新增独立append-only `workflow_role_realtime_eligibility_evidence`，以evidenceId/evidenceHash关联Claim、Task、Pool、Member、Binding；禁止UPDATE/DELETE，并用FK/CHECK/Trigger保护Hash和关联一致性。备选方案是只扩展Claim JSON，但可查询性、完整性和数据库不可变治理较弱，不推荐。

本Sprint不创建V2.6.16。具体DDL、历史兼容和真实MySQL验收必须拆分独立Sprint。

## 34. Directory 阻断边界

当前 `DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED` 与 `PRODUCTION_ROLE_DIRECTORY_CONNECTIVITY_BLOCKED` 原样保留。WF5.21设计不得引用Fake结果宣称真实Eligibility，不配置Production Endpoint，不读取Secret，不解除Resolver阻断。

真实ROLE Eligibility实现前必须先完成资源交付门禁、重新通过WF5.20.2 Connectivity，并完成专用Integration Query验证。

## 35. Test Matrix

后续至少覆盖：

1. Candidate + 当前ROLE有效 → ELIGIBLE。
2. Candidate但ROLE撤销 → INELIGIBLE。
3. Candidate但用户DISABLED/LOCKED/TERMINATED/SUSPENDED → INELIGIBLE。
4. Candidate离开BUSINESS_ORG → INELIGIBLE。
5. 非Candidate但Directory当前有角色 → NOT_CANDIDATE且Directory调用0。
6. Revision变化、资格仍有效 → ELIGIBLE并记录R10→R15。
7. Revision变化且资格失效 → INELIGIBLE。
8. Directory timeout → 有限重试后INDETERMINATE。
9. Directory partial → INDETERMINATE、零fallback。
10. Source conflict → INDETERMINATE。
11. DataScope deny → INELIGIBLE。
12. DataScope unavailable → INDETERMINATE。
13. Platform SoD deny → INELIGIBLE。
14. Business SoD deny → INELIGIBLE。
15. Feature Flag关闭 → INELIGIBLE。
16. Canary不匹配 → INELIGIBLE。
17. Kill Switch激活 → INELIGIBLE，Directory调用0。
18. Evidence TTL过期 → 释放事务并重新Prepare。
19. Binding Hash变化 → 旧Evidence拒绝。
20. Candidate Pool Hash变化 → 旧Evidence拒绝。
21. Existing Claim → 幂等结果或明确冲突。
22. 双Session Claim → 单赢家。
23. Prepare后Directory Revision变化 → Fence拒绝/重新Prepare。
24. Claim Commit失败 → Claim/Task/Pool/Audit事务回滚。
25. EXPLICIT_USER回归 → 行为不变。
26. Legacy回归 → 不进入ROLE Port。

另需覆盖固定校验顺序、Capability异常/null、Hash稳定/漂移、PII扫描、远程调用不持锁及Audit链完整性。

## 36. Migration Decision

- 本Sprint Migration变化：0。
- V2.6.16：不存在。
- V2.6.15保持Canonical状态和既有SHA/Flyway checksum。
- 未来若持久化完整Evidence，单独规划V2.6.16候选，不修改V2.6.15或历史Migration。

## 37. Risks

- Directory资源与Connectivity仍阻断，无法验证真实Role Membership、Revision、TTL和SLA。
- 当前Claim事务在锁内调用Eligibility，直接接远程Adapter会放大锁等待与死锁风险。
- 现有DatabaseRealtimeEligibilityChecker查询平台用户/任职/组织/岗位事实，不是Approval Role Directory权威资格。
- Kill Switch OPEN/CLOSED命名与Circuit语义相反，适配错误可能造成安全事故。
- Business SoD尚未接入，默认允许不可接受。
- Evidence只保存摘要会降低审计可解释性；完整持久化需增量Migration治理。
- TOCTOU无法彻底消除，只能通过短TTL、Fence、短事务和CAS降低风险。

## 38. Next Sprint

只建议：

1. `WF5.22 Realtime Eligibility Capability Framework`：仅实现纯Domain契约、固定Gate编排、Fake Capability、Prepare/Verify接口和测试，不接真实Directory、不启用ROLE Runtime；或
2. 等待Directory资源全部VERIFIED后重新执行WF5.20.2。

不得自动进入Production Adapter、ROLE Claim、CandidatePool Runtime或Investment集成。

最终状态：

- `ROLE_REALTIME_ELIGIBILITY_GOVERNANCE_READY`
- `DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED`
- `ROLE_DIRECTORY_V1_PREPARED_NON_EXECUTABLE`
- `ROLE_RUNTIME_DISABLED`
