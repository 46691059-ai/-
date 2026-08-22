# ROLE Runtime External Capability Integration Governance Design

> Sprint 2-3.7-WF5.19
> 冻结状态：ROLE_RUNTIME_EXTERNAL_CAPABILITY_GOVERNANCE_READY / ROLE_RUNTIME_DISABLED
> 本文只做设计，不授权Adapter实现、Registry推进或业务运行。

## 1. Current Canonical Baseline

| 资产 | 状态 | SHA-256 | Flyway checksum |
|---|---|---|---:|
| V2.6.15 | CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED | db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44 | 538981271 |
| V2.6.14 | CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED | a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442 | -1734980808 |

EXPLICIT_USER_V1仍是唯一ACTIVE业务Resolver。ROLE_DIRECTORY_V1保持
PREPARED/NON_EXECUTABLE，ROLE Runtime保持DISABLED。本设计不创建
WorkflowInstance、NodeExecution、ROLE Task、CandidatePool Runtime或Claim。

## 2. Existing Capability Audit

WF5.16已有八个独立Domain Port：Directory、Realtime Eligibility、DataScope、SoD、
Audit、Feature Flag、Kill Switch、Canary Scope。它们统一接收完整AdmissionRequest，
返回RoleRuntimeCapabilityResult。现有优点是Domain无框架依赖、异常和NULL结果会转为
BLOCKED、统一状态已经包含READY/NOT_READY/DEGRADED/BLOCKED，而且只有READY继续。
当前生产代码不存在真实实现，Fake仅在测试范围，未发现生产依赖泄漏。

通用返回目前只有capability、readiness和最长200字符的evidence文本，不能作为结构化
生产证据。它缺少稳定错误码、provider/policy/contract/schema版本、evidenceHash和
checkedAt。

RoleDirectoryPort相对成熟：Query已有enterpriseId、organizationId、roleCode、
effectiveAt、contractVersion和traceId；Result已有revision、complete、排序后的成员、
resultHash、contractHash和source。缺口是Query没有expectedContractHash，Result没有
resolvedAt/providerVersion，contractHash没有严格SHA-256校验，complete=false未在
Result边界直接阻断，也没有SLA、认证、签名Revision Token或生产选择门禁。

## 3. Capability Gap Matrix

| Capability | 现有输出 | Fail Closed | 生产泄漏 | 主要缺口 | 适配结论 |
|---|---|---|---|---|---|
| Directory Capability | readiness+文本 | 是 | 无 | 无成员/revision token/版本化Evidence | 必须桥接RoleDirectoryPort |
| RoleDirectoryPort | revision/complete/members/hash | 部分 | 无 | expected hash、resolvedAt、providerVersion、SLA | 可作为WF5.20基础 |
| Realtime Eligibility | readiness+文本 | 是 | 无 | 无user、状态维度、reason、时间、版本/hash | 不可直接生产 |
| DataScope | readiness+文本 | 是 | 无 | 无业务对象、task、scopeDecision、Policy权威源 | 不可直接生产 |
| SoD | readiness+文本 | 是 | 无 | Platform/Business未分层，无actor/rule facts | 不可直接生产 |
| Audit | readiness+文本 | 是 | 无 | 无写入确认、Outbox/回执和不可抵赖证据 | 不可直接生产 |
| Feature Flag | readiness+文本 | 是 | 无 | 无typed scope、decision、policy/time/hash | 需生产契约 |
| Kill Switch | readiness+文本 | 是 | 无 | 无层级、模式、存量任务策略 | 需生产契约 |
| Canary | readiness+文本 | 是 | 无 | 无生命周期、范围审批和窗口 | 需生产契约 |

结论：Port边界可以保留，但不得把evidence字符串包装成生产Evidence。

## 4. Unified Status Model

| 状态 | 语义 | Admission | Claim |
|---|---|---|---|
| READY | 当前检查可证明满足契约 | 继续 | 继续 |
| NOT_READY | 未准备或无法形成肯定结论 | 拒绝 | 拒绝 |
| DEGRADED | 依赖降级、熔断或SLA不满足 | 拒绝 | 拒绝 |
| BLOCKED | 明确违反安全、策略或业务规则 | 拒绝 | 拒绝 |

异常、NULL、不完整、未知枚举、超时、版本不匹配或Evidence不可验证一律非READY。
DEGRADED默认Fail Closed，禁止自动降级放行、Resolver替换或旧缓存fallback。

## 5. Production Role Directory

规划ProductionRoleDirectoryAdapter实现RoleDirectoryPort，但本Sprint不实现。查询契约：
enterpriseId、businessOrgId、roleCode、effectiveAt、expectedContractVersion、
expectedContractHash、traceId。结果契约：directoryRevision、resultHash、complete、
members、resolvedAt、providerCode/providerVersion、contractVersion/contractHash、
evidenceSchemaVersion，以及可选revisionToken。

成员只含稳定userId、assignmentId、roleCode、organizationId、有效期、来源、
sourceRef和revision。complete=false、分页未完成、分片失败、未知来源或成员证据缺失
均为PARTIAL_RESULT并Fail Closed。0成员拒绝，N成员全部保留，禁止截断、取第一人或
默认负责人。

## 6. Revision Fence

固定Prepare → Verify → Commit：

1. Prepare在事务外调用Directory，取得完整成员、revision、resultHash、contractHash
   和resolvedAt，并校验完整性、候选上限、Canonical及契约。
2. Verify在事务开始前或事务首段，逐字节比较Candidate Snapshot冻结的revision、
   resultHash、contractHash、effectiveAt和组织/角色范围。
3. Commit只消费Prepare且已Verify的不可变结果；禁止再次查询最新Directory并覆盖。

Prepare与Commit之间发现漂移时，本次执行作废并重新Admission，禁止静默刷新。

## 7. Revision Token

| 方案 | 一致性 | 防伪/跨服务 | 运维复杂度 | 审计 |
|---|---|---|---|---|
| A 纯revision | 弱 | 弱 | 低 | 低 |
| B revision+resultHash+contractHash | 强快照 | 依赖服务认证 | 中 | 高 |
| C signed revision token | 最强 | 强，可离线验签 | 高 | 最高 |

目标推荐C。在签名基础设施落地前，V1最低采用B并要求mTLS或等价服务认证。任一项
不一致即REVISION_MISMATCH、HASH_MISMATCH或CONTRACT_MISMATCH，不得自动修正。

## 8. Directory SLA

必须采集P50/P95/P99、timeoutRate、errorRate、partialRate、revisionMismatchRate、
hashMismatchRate和candidateCount，并按enterprise/definition/version观察。生产阈值
必须来自预生产压测和故障注入，本设计不设具体数字。超过阈值映射为NOT_READY、
DEGRADED或BLOCKED；三者均拒绝执行。

## 9. Error Model

Retryable仅包括NETWORK_TIMEOUT、TEMPORARY_UNAVAILABLE、
TRANSIENT_DEPENDENCY_FAILURE，且只允许Prepare阶段在总时间预算内有限重试。

Non-Retryable包括ROLE_NOT_FOUND、ROLE_DISABLED、ORG_INACTIVE、
ROLE_ORG_MISMATCH、PARTIAL_RESULT、SOURCE_CONFLICT、CONTRACT_MISMATCH、
REVISION_MISMATCH、HASH_MISMATCH、CANDIDATE_LIMIT_EXCEEDED。

现有错误码由显式映射兼容。禁止fallback到EXPLICIT_USER、旧Pool、旧Directory缓存、
管理员或默认负责人。

## 10. Realtime Eligibility

规划RealtimeEligibilityPort，输入至少包含userId、enterpriseId、organizationId、
businessObjectId、taskId和checkedAt；检查用户存在/启用、任职和组织关系有效、账号
可用、未停职、未离职。输出READY/BLOCKED、reasonCode、checkedAt、
providerVersion、policyVersion和evidenceHash。

Pool生成使用Directory effectiveAt历史事实；Claim使用当前实时状态。人员今天离职
不修改历史Pool，但今天Claim必须拒绝。禁止跨请求缓存实时判定。

## 11. DataScope

规划RoleRuntimeDataScopePort，输入userId、enterpriseId、businessObjectType、
businessObjectId、organizationId、taskId、nodeId；输出READY/BLOCKED、
scopeDecision、policyVersion、evidenceHash和checkedAt。

权威来源只能是统一DataScope/Access Policy。Workflow不得查询sys_role、
sys_user_role、HR Mapper或Organization Mapper自行推断。Role Membership、RBAC、
Candidate Membership和DataScope Decision是四个不同事实。

## 12. Platform SoD

PlatformSoDPort至少支持initiator != claimant、initiator != approver、
previousCriticalNodeActor != currentActor，以及配置节点间禁止同一Actor。输出
READY/BLOCKED、ruleCode、policyVersion、evidenceHash和checkedAt。

## 13. Business SoD

业务模块只提供businessRuleCode和businessFacts，不得返回approvedUserId。Workflow
Business SoD Adapter对当前Claimant判定；Platform和Business结果分别留痕，任一非
READY均阻断。

## 14. Investment Boundary

Investment未来只提供roleCode、businessMeaning、approvalRoute、businessSoDFacts和
businessObjectId。不得指定候选用户/claimant，不得解析角色、修改Pool或绕过Workflow。
本Sprint不接Investment。

## 15. Audit Capability

生产Audit必须证明Admission、Activation、Promotion、Binding Candidate、Directory
revision/result hash、Capability Root、Pool Hash、Claim、Task和Task Action，并返回
provider/policy/schema版本及Evidence Hash。Audit不可用或Evidence不可恢复时Fail Closed。

## 16. Audit Transaction Strategy

| 方案 | 一致性 | 事务长度 | 恢复性 | 结论 |
|---|---|---|---|---|
| 事务内同步外部Audit | 外部即时 | 长、耦合故障 | 一般 | 不推荐 |
| 本地事实+Outbox | 本地原子，外部最终一致 | 短 | 强 | 推荐 |
| 无治理双写 | 易不一致 | 不定 | 弱 | 禁止 |

推荐核心审计事实与业务同事务落库，Outbox异步投递并保存外部回执。本地事实成功不等于
外部Audit Capability READY；还必须证明Worker、端点、重试、死信和积压处于批准范围。

## 17. Feature Flag

最小粒度为enterpriseId + definitionId + definitionVersionId，可选nodeId；禁止仅有
GLOBAL布尔值。输出scope、decision、policyVersion、checkedAt和evidenceHash。Flag
变化不更新历史Admission；新执行必须重新Admission。全局开关只能作为总闸。

## 18. Canary

首轮仅允许单enterprise、单definition、单published version、单ROLE node。生命周期：
DRAFT → READY → RUNNING → PAUSED/COMPLETED，异常终态REVOKED。它是运行治理状态，
不等于Resolver Registry状态。范围、窗口和批准证据均进入Hash。

## 19. Kill Switch

RoleRuntimeKillSwitchPort至少支持GLOBAL、ENTERPRISE、DEFINITION_VERSION层级。
关闭时阻止新ROLE Instance、新ROLE节点Pool和新ROLE Task；宽范围关闭优先于窄范围允许。

## 20. Kill Switch Existing Task Policy

| 模式 | 新运行 | 未Claim | 已Claim | 用途 |
|---|---|---|---|---|
| STOP_NEW_ONLY | 阻止 | 可Claim | 可处理 | 温和暂停 |
| STOP_NEW_AND_CLAIM | 阻止 | 禁止 | 按实时策略 | V1默认 |
| FREEZE_ALL_PENDING | 阻止 | 禁止 | 禁止审批 | 严重事件 |

V1默认推荐STOP_NEW_AND_CLAIM，并保留经授权的FREEZE_ALL_PENDING应急模式。均不删除
历史Task/Pool、不撤销已完成动作。恢复必须新增治理事件并重新检查实时Capability。

## 21. Admission vs Claim Capability

| Capability | Admission阶段 | Claim阶段 |
|---|---|---|
| Directory | 服务、契约、revision/result及完整候选READY | 不重建Pool，只核对冻结成员 |
| Realtime | 服务能力READY | 具体user实时判定 |
| DataScope | Policy服务READY | 具体user+业务对象判定 |
| SoD | 规则服务READY | 具体actor和历史动作判定 |
| Audit | 审计链READY | Claim事实可原子记录和投递 |
| Feature Flag | 执行范围允许 | 紧急状态复核，不改历史 |
| Kill Switch | 新执行允许 | 按存量模式判定 |
| Canary | Admission在批准范围 | Task仍属于冻结范围 |

Admission的服务READY不能冒充Claim的具体用户判定。

## 22. Version Governance

每个Evidence必须携带providerCode、providerVersion、contractVersion、policyVersion、
evidenceSchemaVersion、checkedAt、reasonCode和小写SHA-256。历史Evidence按原版本解释；
禁止使用当前Adapter重算或升级，未知版本Fail Closed。

## 23. Contract Hash

冻结逻辑契约名：ROLE_DIRECTORY_CAPABILITY_V1、
REALTIME_ELIGIBILITY_CAPABILITY_V1、DATASCOPE_CAPABILITY_V1、
PLATFORM_SOD_CAPABILITY_V1、BUSINESS_SOD_CAPABILITY_V1、AUDIT_CAPABILITY_V1、
FEATURE_FLAG_CAPABILITY_V1、KILL_SWITCH_CAPABILITY_V1和CANARY_CAPABILITY_V1。

contractHash是规范化Manifest（字段、必填性、枚举、错误、Canonical、安全约束）的
SHA-256。选择Adapter必须同时匹配contractVersion和contractHash。

## 24. Adapter Registration

显式Descriptor包含capabilityCode、providerCode/version、contractVersion/hash、
evidenceSchemaVersion、environmentClass和executable。禁止依赖Primary或Bean顺序。
重复Descriptor、未知Provider或多个可执行实现使Startup Gate BLOCKED。

## 25. Fake / Production Isolation

- Fake只允许test/sandbox环境身份，不得进入生产组件扫描；
- Production由typed配置和environment identity显式选择，不能与Fake同时eligible；
- 生产启动扫描Fake marker、包、Descriptor和依赖，发现任何Fake即P0失败；
- CI执行production artifact依赖扫描和无Fake Bean上下文测试；
- 缺生产配置时没有可执行Adapter，绝不能选择Fake。

## 26. Startup Gate

顺序固定为：环境身份 → Required Adapter唯一存在 → 非Fake → provider/contract/schema
版本 → contractHash → typed配置 → 服务认证/Secret引用 → endpoint allowlist →
timeout/retry/circuit策略 → Canonical自检 → Production Dependency Guard。

全通过只把Capability Framework标记READY；不得推进Registry或启用ROLE Runtime。

## 27. Registry Promotion Gate

ROLE_DIRECTORY_V1从PREPARED到EXECUTION_ELIGIBLE必须同时满足：Production Directory
READY、SLA、Revision Fence、Canonical Cross Test、Candidate Limit、Realtime、
DataScope、SoD、Audit、Feature Flag、Kill Switch、Canary和Preprod全部PASS。任何证据
过期或漂移即撤回资格。EXECUTION_ELIGIBLE仍不等于ACTIVE。

## 28. ACTIVE Governance

EXECUTION_ELIGIBLE到ACTIVE还需正式Release Approval、Feature Flag、Canary Scope、
Change Window和Production Readiness联合批准。应用启动、Migration、配置刷新或单一
管理员均不得自动完成。变化必须有不可变证据和可撤销总闸。

## 29. Production Config

配置收敛为单一typed configuration；配置服务/部署清单提供非敏感项，Secret Manager
提供凭据，environment identity证明环境。禁止散落application.yml、明文Secret或未校验
字符串。日志仅输出存在性、版本和Hash，不输出Token/Secret。

## 30. Timeout / Retry

每个远程Capability独立冻结connectTimeout、readTimeout、overallBudget、maxRetry、
backoff/jitter和retryableErrors，最终数值由预生产压测决定。禁止无限重试、事务内远程
重试、对Non-Retryable重试或跨请求复用失败结果。

## 31. Circuit Breaker

Directory、Realtime和外部Audit必须独立熔断；远程DataScope/SoD同样适用。OPEN或
HALF_OPEN映射DEGRADED/BLOCKED并Fail Closed。禁止fallback旧成员、旧权限或本地猜测。

## 32. Cache

Directory V1不得跨请求缓存成员结果；Realtime、DataScope和SoD判定禁止缓存。只允许
短期缓存Descriptor、contract metadata和验签公钥，并带版本、TTL和主动失效。任何结果
缓存需后续独立设计。

## 33. Candidate Limit

platformMax与businessMax独立，effectiveLimit=min(platformMax,businessMax)。缺失、
非法或超限均Fail Closed；禁止截断前N人、采样、选负责人或改用EXPLICIT_USER。
candidateCount进入Evidence、指标和告警。

## 34. Cross-language Canonical

规划Java + Go参考实现，对Directory Result Hash、单Capability Evidence Hash和Root
提供固定向量。覆盖乱序、Unicode、NULL、时间精度、大小写、成员增删及版本/状态变化。
两实现及MySQL可验证Root逐字节一致前，Production Capability不能READY。

## 35. Security / PII

服务间使用mTLS或等价认证、最小权限Token、Secret轮换和endpoint allowlist。Evidence
只保存稳定userId、角色/组织事实、来源、有效期、版本、reasonCode和Hash；禁止身份证、
手机号、住址、私人联系方式或完整HR档案。日志和指标不含成员明细。

## 36. Monitoring

至少采集directory_latency/error、revision_mismatch、hash_mismatch、candidate_count、
realtime_eligibility_block、datascope_block、sod_block、audit_failure、
feature_flag_block、kill_switch_block、canary_block和admission_block_reason。按
enterprise/definition/version/node观察，userId不得作为高基数通用标签。

## 37. Alerts

P0包括Revision漂移、Contract/Canonical Hash漂移、生产加载Fake、Audit不可用、
Kill Switch异常、Candidate异常暴增和SoD服务失效。告警携带trace、scope、
provider/policy版本和稳定reasonCode，不含Secret/PII，并定义责任人和Fail Closed动作。

## 38. Preprod Matrix

进入EXECUTION_ELIGIBLE前必须通过：真实Directory、SLA负载、Revision Fence、partial
response、source conflict、Candidate Limit、Realtime、DataScope、Platform SoD、
Business SoD Fake Integration、Audit、Feature Flag、Canary、Kill Switch、timeout、
retry、Circuit Breaker、应用重启、Admission跨进程恢复和EXPLICIT_USER回归。还必须
包含故障注入、固定向量和生产包Fake扫描。

## 39. Implementation Order

1. Phase A：Production Role Directory Adapter Framework，仍NON_EXECUTABLE；
2. Phase B：Realtime Eligibility；
3. Phase C：统一DataScope Policy Adapter；
4. Phase D：Platform SoD，再接Business SoD事实；
5. Phase E：本地审计事实、Outbox和外部Audit健康；
6. Phase F：Feature Flag、Canary、Kill Switch；
7. Phase G：预生产集成与Registry资格评审。

每Phase单独设计、实现、验收，禁止一个Sprint一次接入全部Capability。

## 40. Migration Decision

WF5.19与WF5.20决策为NO_MIGRATION。Adapter框架、Descriptor、Startup Gate和隔离规则
可在代码/配置契约完成；不得创建V2.6.16。

生产执行前需重新评估Evidence是否显式保存providerCode、contractVersion、
contractHash和evidenceSchemaVersion。当前表已有capabilityCode、providerVersion、
policyVersion、checkedAt、evidenceHash和canonicalVersion，但缺独立Capability
contract三元组；禁止把语义塞入自由文本。如必须持久化，只能在后续独立Sprint采用新
前向Migration，不得修改V2.6.15。

## 41. WF5.20 Boundary

WF5.20最多实现单一Production Role Directory Adapter Framework：生产契约、Adapter
骨架、Startup Gate、Fake/Production隔离、Contract Hash和Fake Server集成测试。必须
NON_EXECUTABLE；不得接真实生产端点、推进Registry、启用Runtime、创建运行对象或接
Investment。

## 42. NO-GO

以下任一未完成即NO-GO：生产Adapter唯一选择、Fake隔离、Revision Fence、
complete/partial语义、contractVersion+Hash、timeout/retry、错误分类、Candidate
Limit、结构化Evidence、Startup Gate、监控、Audit边界、预生产准入和
EXECUTION_ELIGIBLE条件。任何fallback设计自动NO-GO。

## 43. Risks

1. 通用CapabilityResult不足以承载生产Evidence，过早实现会形成字符串协议；
2. Prepare/Commit间成员变化会导致TOCTOU，必须Revision Fence；
3. DataScope/SoD直查本地RBAC或HR表会造成权威来源漂移；
4. 外部Audit无治理双写会产生不可解释证据；
5. Fake/Production误选是P0供应链风险；
6. Candidate过多会放大Directory、Pool和Claim负载，禁止截断掩盖；
7. Feature Flag、Canary、Kill Switch与Registry混用会导致未经批准启用；
8. 尚无签名Revision Token基础设施，V1三元组依赖强服务认证；
9. Capability contract元数据未完整持久化，生产运行前需重做Migration决策。

## 44. Next Step

下一步只能在明确任务下进入WF5.20，且限定Production Role Directory Adapter
Framework。开始前冻结生产Contract Manifest、固定Hash向量、Fake marker、
environment identity和Startup Gate测试。完成后仍保持ROLE_DIRECTORY_V1
PREPARED/NON_EXECUTABLE及ROLE_RUNTIME_DISABLED。

## 冻结结论

    ROLE_RUNTIME_EXTERNAL_CAPABILITY_GOVERNANCE_READY
    ROLE_RUNTIME_DISABLED
    ROLE_DIRECTORY_V1 = PREPARED / NON_EXECUTABLE
    V2.6.16 = NOT_CREATED
