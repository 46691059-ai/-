# Approval Role Directory 与 RoleDirectoryPort 契约设计

状态：DESIGN_FROZEN / IMPLEMENTATION_NOT_STARTED

适用Sprint：2-3.7-WF5.0.1。本文件冻结WF5.1编码前的权威目录与Port契约；不代表实现、Migration或发布完成。

## 1. 背景

WF5.0已冻结ROLE_V1：strategyType=ROLE、assignmentMode=CANDIDATE_POOL、resolverCode=ROLE_DIRECTORY、resolverVersion=ROLE_DIRECTORY_V1、roleType=PROCESS_APPROVAL_ROLE、orgScope=BUSINESS_ORG。当前P0缺口是平台尚无权威审批角色目录，也没有稳定RoleDirectoryPort契约。

目录只提供“某审批角色在某业务组织、某历史时点有哪些成员”的权威事实。它不选择Task Assignee、不执行Claim/Approve、不代替RBAC、DataScope或SoD。

## 2. PROCESS_APPROVAL_ROLE语义

PROCESS_APPROVAL_ROLE表示可由流程定义稳定引用、在指定业务组织和有效期内承担审批职责的业务角色，例如INVESTMENT_FINANCE_REVIEWER。

它明确不等于：

- sys_role中的系统权限角色；
- hr_position组织岗位或行政职务；
- 用户/员工身份；
- workflow:approve能力权限；
- 最终Task经办人。

roleCode是跨环境稳定业务键，采用受治理命名空间；roleName仅显示。ROLE_V1角色实例由enterpriseId + businessOrganizationId + roleCode唯一定位。

## 3. ApprovalRole

建议领域模型：

- roleId：目录内部逻辑ID，不作为跨环境输入；
- enterpriseId；
- roleCode：稳定、不可复用的业务编码；
- roleName：显示名；
- roleType：V1固定PROCESS_APPROVAL_ROLE；
- organizationId及organizationScope=BUSINESS_ORG；
- status：DRAFT、ACTIVE、INACTIVE、ARCHIVED；
- effectiveFrom、effectiveTo；
- directoryRevision；
- sourceSystem、remark；
- created/updated审计、changeReason、approvalRef、auditRef。

只有ACTIVE且effectiveFrom <= effectiveAt < effectiveTo的角色可解析。角色编码停用/归档后不得被另一个语义复用；语义变更必须新roleCode或新受控版本。

## 4. ApprovalRoleAssignment

表示用户在指定组织和时间范围内承担某审批角色：

- assignmentId、enterpriseId；
- roleId、roleCode、organizationId；
- userId，可选employeeId；
- status：DRAFT、ACTIVE、INACTIVE、REVOKED、EXPIRED；
- effectiveFrom、effectiveTo；
- sourceType：MANUAL_GOVERNED、POSITION_DERIVED、EXTERNAL_SYNC；
- sourceRef：稳定来源引用；
- revision；
- createdTime及完整变更审计。

同一角色允许多人，同一userId也可能由多个有效assignment命中。目录返回原始来源事实；Workflow按Canonical规则合并为一个CandidateMember并保留全部来源证据。

## 5. 时间模型

所有角色与assignment采用半开有效区间[effectiveFrom,effectiveTo)，effectiveTo可空表示无预定结束。领域契约使用带时区的Instant；数据库物理类型和时区策略由目录上下文统一治理，不允许调用方自行解释本地时间。

ROLE Resolver传入NodeExecution首次激活时间作为effectiveAt。目录必须执行历史时点查询，禁止用当前成员替代。若发生追溯更正，目录发布新Revision；已生成Pool不变，新解析使用新事实。

## 6. 组织模型

ROLE_DIRECTORY_PORT_V1只支持BUSINESS_ORG：

- organizationId是强制查询条件；
- includeSubOrganizations固定false；
- 目录在服务端限制organizationId，禁止先查全集团再由Workflow过滤；
- 校验组织属于enterprise、类型允许、effectiveAt有效且与角色归属一致；
- PARENT、ROOT、SUB_ORG、GLOBAL只作为未来新契约版本预留。

组织不匹配返回ROLE_ORG_MISMATCH，不得返回其他组织成员。

## 7. RoleDirectoryPort

接口定义在Workflow需要侧的Domain/Application Port，Adapter位于Workflow Infrastructure：

RoleDirectoryResult resolveRoleMembers(RoleDirectoryQuery query)

契约要求：

- 调用是只读、确定性、完整响应；
- 同一query和同一directoryRevision产生同一语义结果与resultHash；
- 不返回最终assignee；
- 不产生Workflow或目录写副作用；
- 不暴露目录Mapper、Entity或sys_user_role；
- 不返回敏感个人数据。

目录实体及管理能力不属于Workflow Runtime聚合。

## 8. RoleDirectoryQuery

V1字段：

- contractVersion=ROLE_DIRECTORY_PORT_V1；
- enterpriseId；
- roleCode；
- roleType=PROCESS_APPROVAL_ROLE；
- organizationId；
- organizationScope=BUSINESS_ORG；
- effectiveAt；
- expectedDirectoryRevision，可空；
- maxCandidateCount与candidateLimitPolicyVersion；
- traceId、requestId。

字段来自冻结Instance、Node Resolver Binding及NodeExecution。Controller不得覆盖roleCode、organizationId、effectiveAt。expectedDirectoryRevision首次查询可空；同一解析尝试重试时必须带首次取得的Revision，防止重试漂移。

## 9. RoleDirectoryResult

成功结果包含：

- contractVersion、roleCode/type；
- enterpriseId、organizationId/scope；
- effectiveAt；
- directoryRevision；
- roleStatus=ACTIVE；
- members；
- resultHash、hashSchema=ROLE_DIRECTORY_RESULT_HASH_V1；
- sourceSystem、sourceRevision；
- complete=true；
- resolvedAt；
- 原始membershipCount、去重userCount。

resolvedAt仅用于审计，不进入resultHash。响应必须是一个权威Revision下的原子快照；分页细节不得泄漏给Port。

## 10. RoleDirectoryMember

成员事实至少包含：

- userId、可选employeeId；
- assignmentId；
- roleCode、organizationId；
- effectiveFrom、effectiveTo；
- assignmentStatus；
- sourceType、sourceRef；
- assignmentRevision。

禁止返回最终Task Assignee。成员仅证明该用户在effectiveAt属于角色，不证明当前仍可Claim。姓名、手机号、身份证等不进入契约。

## 11. Revision

V1采用“角色-组织聚合级单调Revision”，作用域为enterpriseId + organizationId + roleCode。原因：

- 全局Revision会因无关组织/角色变更造成无意义抖动；
- 单assignment Revision不能证明集合完整性；
- 聚合Revision能标识角色定义与全部membership的原子事实版本。

任何影响该聚合解析结果的角色、组织归属或assignment变更必须在同一目录事务中递增Revision并写revision事件。Revision是不可猜测的单调数值或规范化opaque token；不得回退或复用。

expectedDirectoryRevision存在且不匹配时返回DIRECTORY_REVISION_MISMATCH。历史Task读取直接使用冻结Pool，不重新调用Directory。

## 12. Hash

RoleDirectoryResultHash使用SHA-256、64位小写Hex、长度前缀Canonical编码，覆盖：

- hashSchema、contractVersion；
- enterpriseId、roleCode/type；
- organizationId/scope；
- effectiveAt规范UTC表示；
- directoryRevision、roleStatus；
- 按Canonical顺序排列的全部成员；
- 每个成员的userId、assignmentId稳定表示、有效期、status、sourceType/ref、assignmentRevision；
- 原始membershipCount、去重userCount。

不包含roleName、真实姓名、数据库自增ID（若assignmentId非跨环境稳定则使用sourceRef）、resolvedAt、created/updated审计字段。成员输入顺序不同但语义相同必须得到相同Hash。

## 13. Complete语义

只有complete=true的RoleDirectoryResult可进入Resolver。complete意味着：

- 查询覆盖该Revision下全部有效membership；
- 无未完成分页；
- 所有分片/下游成功；
- 没有超时后拼出的部分结果；
- 成员计数与返回集合一致；
- resultHash已对完整集合计算。

complete=false、缺失、计数不一致或下游部分失败统一Fail Closed并返回DIRECTORY_PARTIAL_RESULT，禁止生成Candidate Pool。

## 14. 错误码

冻结错误契约：

- ROLE_NOT_FOUND；
- ROLE_DISABLED；
- ROLE_ORG_MISMATCH；
- NO_ROLE_MEMBER；
- DIRECTORY_TIMEOUT；
- DIRECTORY_UNAVAILABLE；
- DIRECTORY_PARTIAL_RESULT；
- DIRECTORY_REVISION_MISMATCH；
- DIRECTORY_CONTRACT_MISMATCH；
- DIRECTORY_CONFLICT；
- CANDIDATE_LIMIT_EXCEEDED；
- INVALID_DIRECTORY_QUERY。

错误包含稳定code、retryable、traceId和非敏感message，不附带其他组织成员或内部SQL。NO_ROLE_MEMBER不是技术失败，但在ROLE_V1仍导致Task创建失败。

## 15. 缓存

ROLE_V1不缓存成员结果。允许：

- 同一次解析调用链内的request-scoped复用；
- 不含成员数据的短期健康/契约元数据缓存；
- 目录服务内部在能证明Revision一致性时自行缓存，但Port仍收到权威Revision。

禁止使用过期成员缓存、异常时回退旧结果、跨Revision缓存或通过历史Pool生成新Pool。历史Task查询只读冻结Pool，不调用Directory。

未来如引入Adapter缓存，key必须包含enterprise/org/role/effectiveAt/revision/contractVersion，且每次使用前验证Revision；TTL只能限制资源，不替代Revision正确性。

## 16. 超时/重试

timeout、maxAttempts、backoff和circuit-breaker阈值来自版本化平台治理配置，不在Resolver硬编码。

只允许在没有任何持久化副作用前，对明确retryable的连接/短暂服务错误做有限重试；同一次重试保持requestId、effectiveAt并带expectedDirectoryRevision。超时、熔断、重试耗尽或Revision漂移使Task创建事务失败。

禁止fallback到EXPLICIT_USER、sys_role、旧Pool或缓存旧成员。目录解析建议先完成并验证Hash，再在Runtime事务中重新校验NodeExecution/Binding状态并原子保存Task、AssignmentSnapshot、Pool和Members。

## 17. sys_role边界

sys_role/sys_user_role继续只承担系统RBAC和data scope，不作为PROCESS_APPROVAL_ROLE权威目录，不复用其roleId或membership。

未来若业务决定复用底层存储，必须先引入独立role semantic/type、组织归属、有效期、revision和治理审批，并通过独立Port发布事实；Workflow仍不得直读sys_user_role。当前冻结结论是逻辑与接口完全分离。

## 18. 数据源边界

未来权威实现可选：

A. 组织人事模块自有Approval Role表；
B. 统一组织人员服务提供历史时点目录；
C. 独立治理目录服务。

推荐由组织人事/治理目录上下文拥有数据，Workflow Infrastructure Adapter调用其服务或受控只读事实视图。Adapter负责契约转换、超时、错误映射和Hash复核，不拥有角色成员业务规则。

## 19. 审计

目录预留created_by/time、updated_by/time、source、revision、change_reason、approval_ref、audit_ref。高敏审批角色变更建议双人复核和职责分离。

每次Revision发布记录变更前后Hash、影响角色/组织、成员增删计数、审批引用和发布人。Resolver审计只冻结非敏感query摘要、Revision、resultHash、数量、耗时和错误，不记录凭据或个人敏感字段。

治理责任：

- 组织人事：目录数据质量、有效期和组织归属；
- 业务主管：定义角色语义并申请成员；
- 安全/审计或复核人：敏感角色双人复核；
- 系统管理员：平台运行，不擅自维护业务成员；
- Workflow管理员：配置roleCode引用，不维护目录membership。

## 20. 数据结构规划

建议未来目录上下文拥有：

- approval_role：角色定义、组织归属、状态和有效期；
- approval_role_assignment：用户membership、来源和有效期；
- approval_role_revision：聚合Revision、集合Hash、变更原因与审批审计。

唯一性至少覆盖enterprise+organization+roleCode+逻辑删除令牌；assignment需防止同一稳定来源重复，并支持历史有效区间查询。目录表不属于Workflow Runtime，也不应由Workflow的V2.6.9创建。

版本规划分离：

- 组织人事/治理目录Migration：创建上述权威表及管理能力，使用该上下文独立版本链；
- Workflow V2.6.9候选：只增强Node/Assignment/Pool的ROLE结构化快照、directoryRevision和hash schema，沿用WF5.0规划。

本Sprint不创建任何Migration。

## 21. Contract Hash

ROLE_DIRECTORY_PORT_V1 Contract Hash覆盖：

- query与response schema及必填/可空规则；
- PROCESS_APPROVAL_ROLE和BUSINESS_ORG；
- Node activated effectiveAt语义；
- 半开有效区间语义；
- complete、error和retryable语义；
- Revision作用域与匹配规则；
- member Canonical排序/合并；
- Result Hash schema；
- maxCandidateCount处理；
- 敏感字段禁止清单。

任一语义变化必须形成新contractVersion和Contract Hash，不能静默修改ROLE_DIRECTORY_V1。

## 22. 五层Hash向量

冻结以下Canonical测试向量族，编码阶段生成并提交完整输入与期望64位小写SHA-256：

| 向量 | 固定输入 | 必变字段 | 顺序无关要求 |
|---|---|---|---|
| Graph Hash | 同Workflow/节点/ROLE规则 | roleCode、org来源、resolverVersion变化必变 | JSON字段顺序变化不变 |
| Role Rule Hash | 同role/org/时间/策略 | roleCode、organizationId、effectiveAtPolicy变化必变 | Canonical字段顺序固定 |
| Resolver Contract Hash | 同Port契约 | schema/completeness/error/hash语义变化必变 | 文档排列不影响 |
| Binding Set Hash | 同节点Binding集合 | ruleHash、contractHash、node绑定变化必变 | Binding输入顺序不变 |
| Candidate Pool Hash | 同Revision/成员事实 | org、revision、成员、来源、有效期变化必变 | Member返回顺序变化不变 |

共同断言：同一输入重复计算相同；roleCode变化、org变化、revision变化、成员变化均在相应层产生变化；仅输入排列不同但语义相同不变化。Hash不包含DB ID和审计时间。

## 23. 安全

- organizationId必须在Directory查询端强制，不允许集团全量查询后本地过滤；
- roleCode格式白名单并参数化；
- 目录调用采用服务身份、最小权限、加密链路和审计；
- 返回最小化，不包含敏感个人信息；
- revision、complete、contract和resultHash全部校验；
- 超限、部分响应、冲突、篡改均Fail Closed；
- Candidate Pool持久化后不可刷新，Claim继续执行RBAC、Membership、实时资格、DataScope和SoD。

## 24. Investment边界

Investment未来只配置业务roleCode，例如INVESTMENT_FINANCE_REVIEWER、BOARD_DECISION_MEMBER、PARTY_COMMITTEE_PRE_REVIEW_MEMBER，不传具体userId，也不维护目录成员。

Investment负责集体决策语义、职责分离和业务状态；Workflow通过Port解析、冻结Candidate Pool并执行Claim/Task。目录由组织人事/治理上下文拥有。本Sprint不修改Investment。

## 25. 实施准入

进入WF5.1前必须全部冻结并批准：

1. PROCESS_APPROVAL_ROLE及roleCode规则；
2. ApprovalRole/Assignment及半开有效期；
3. RoleDirectoryPort V1 query/result/member；
4. BUSINESS_ORG强约束；
5. Node activated effectiveAt；
6. role-org聚合Revision；
7. complete响应；
8. 平台治理的maxCandidateCount来源；
9. Canonical排序和去重；
10. 空结果及错误码；
11. V1无成员缓存；
12. timeout/retry/circuit policy引用；
13. 五层Hash完整测试向量；
14. sys_role完全隔离；
15. 目录Owner、管理审批和revision发布流程。

缺一项不得编码ROLE Resolver或注册Resolver。

## 26. 验收矩阵

未来至少验证：

1. role存在单成员；
2. role存在多成员；
3. role无成员；
4. role不存在；
5. role停用；
6. org不匹配；
7. effectiveAt命中历史成员；
8. 新成员不影响旧effectiveAt/旧Pool；
9. 重复user来源合并；
10. 成员返回顺序变化结果稳定；
11. directoryRevision变化；
12. partial response拒绝；
13. timeout拒绝；
14. stale cache不被使用；
15. candidate limit超限拒绝；
16. contract mismatch拒绝；
17. cross-org查询拒绝；
18. 同输入Hash稳定；
19. role/org/revision/member变化Hash变化；
20. USER+DIRECT不受影响；
21. Legacy不受影响。

补充验证Revision原子性、追溯更正、目录冲突、非敏感日志、事务回滚、Domain纯净、Spring context及Claim实时资格/SoD。

## 27. 风险清单

1. 权威审批角色目录尚未实现，是WF5.1前P0依赖。
2. sys_role语义混用风险已隔离，但组织仍需建立新的目录治理流程。
3. 历史有效期与追溯更正需要可靠Revision和审计，否则无法稳定重放。
4. 目录远程调用可能增加节点Task创建延迟并引入可用性依赖。
5. 无缓存V1提高正确性，但需要目录容量与SLO保障。
6. 大候选集会放大响应、Hash、事务和存储成本。
7. POSITION_DERIVED/EXTERNAL_SYNC来源可能产生重复或冲突事实。
8. Contract/Result Hash若Canonical实现不一致会导致跨服务拒绝。
9. Workflow V2.6.9与目录上下文Migration必须独立治理，不能越界建表。
10. WF4.2.6仍未通过预生产，后续feature不得宣称生产可发布。

下一步建议：由组织人事、业务、安全、Workflow共同评审本契约并冻结目录Owner、Revision发布和测试向量；完成后再进入WF5.1实现。
