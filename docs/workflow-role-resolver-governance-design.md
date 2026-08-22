# Workflow ROLE Resolver 治理设计

状态：DESIGN_FROZEN / IMPLEMENTATION_NOT_STARTED

适用Sprint：2-3.7-WF5.0。WF4.2.6仍为BLOCKED / PREPROD_RESOURCE_NOT_READY；独立研发可以继续，但不得标记PRODUCTION_READY。

## 1. 背景

现有Workflow已冻结Resolver版本路由、多Resolver Binding、Candidate Pool和Claim Runtime。当前唯一可执行Resolver仍是EXPLICIT_USER_V1。ROLE Resolver只负责把冻结ROLE规则解析为冻结候选集合，不选择最终经办人、不自动Claim/Approve，也不修改Task、NodeExecution、Instance或业务状态。

固定链路：ROLE Assignment → ROLE Resolver → Candidate Pool → Candidate Member[] → Claim → Task Assignee → Approve。

## 2. ROLE语义

必须区分：

| 类型 | 语义 | 权威来源 | ROLE_V1候选来源 |
|---|---|---|---|
| SYSTEM_RBAC_ROLE | 菜单、接口、按钮和数据范围准入 | sys_role/sys_user_role | 否 |
| ORG_BUSINESS_ROLE | 组织内业务职责 | 组织人事职责目录 | 否，后续扩展 |
| PROCESS_APPROVAL_ROLE | 可被流程节点稳定引用的审批职责 | 组织人事侧审批角色目录 | 是 |

当前sys_role同时服务系统授权和data scope，缺少审批角色类型、业务有效期和组织职责来源，不能直接等同业务审批角色。workflow:approve仅是能力准入，绝不是候选来源。

稳定业务键使用roleCode，不使用可变roleName。roleId仅可作为目录内部证据快照，不能替代跨环境稳定roleCode。

## 3. RoleAssignmentRule

冻结模型至少包含：

- roleCode、roleType=PROCESS_APPROVAL_ROLE；
- organizationScope、organizationRef、includeSubOrganizations；
- effectiveAtPolicy、candidateStatusPolicy；
- emptyCandidatePolicy、multipleCandidatePolicy；
- maxCandidatePolicyRef、resolverCode、resolverVersion、ruleVersion。

ROLE_V1固定：organizationScope=BUSINESS_ORG、includeSubOrganizations=false、effectiveAtPolicy=NODE_ACTIVATED_AT、emptyCandidatePolicy=FAIL_TASK_CREATION、multipleCandidatePolicy=FREEZE_ALL_AND_REQUIRE_CLAIM。

规则使用字段名固定、枚举大写、UTF-8、显式缺省的Canonical JSON。roleCode必须通过受控格式校验，Adapter只能参数化查询。

## 4. Resolver Context

RoleResolverContext至少包含workflowInstanceId、definitionId/versionId、nodeId/code、nodeExecutionId、enterpriseId、workflowOrganizationId、businessOrganizationId、initiatorUserId、effectiveAt、dataScopeContextReference、resolverVersionBinding、nodeResolverBindingSnapshot、ruleSnapshot和traceId。

输入只能由Instance、Version、Node Binding、NodeExecution及业务关联快照组装。Controller不得覆盖organizationId、roleCode、effectiveAt或Resolver版本；事实缺失、跨enterprise或互相冲突时Fail Closed。

## 5. Role Directory Port

Workflow Domain只定义RoleDirectoryPort，不依赖Spring、MyBatis、Entity或组织人事Mapper：

- 输入：roleCode、roleType、orgScope/ref、includeSubOrg、effectiveAt、enterpriseId、traceId；
- 输出：authoritativeRevision、observedAt、complete、roleFact、organizationFact、memberships及candidates；
- Candidate事实包含稳定userId、可选employeeId、组织/角色/membership稳定引用、有效期及状态，不含密码、身份证、手机号等敏感信息。

分层固定为Domain/Application → Port → Infrastructure Adapter → 组织人事权威服务或只读事实视图。Workflow禁止直接读HR/组织Mapper、sys_user_role或组织Entity，也不建立第二套组织人事主库。

## 6. 时间有效性

启动时间过早；Task创建时间可能受重试漂移；当前时间不可重放。ROLE_V1选择NodeExecution首次激活时间作为effectiveAt，Task/Pool重试复用同一冻结时间。目录按valid_from <= effectiveAt < valid_to判断。

Pool生成后成员永久冻结。后加入角色者不进入旧Pool；后移除者保留历史Member证据，但Claim时由实时资格检查拒绝。

## 7. 组织范围

扩展枚举规划CURRENT_ORG、BUSINESS_ORG、SPECIFIED_ORG、PARENT_ORG、ROOT_ENTERPRISE。ROLE_V1只支持BUSINESS_ORG且不含下级，organizationRef来自Instance冻结业务组织，不能由调用人传入。

CURRENT_ORG易受操作者变化影响；SPECIFIED_ORG需要配置授权；PARENT/ROOT范围过宽，均不在V1实现。目录必须校验组织属于同enterprise且在effectiveAt有效。

## 8. 多候选治理

- 0人：返回NO_ELIGIBLE_CANDIDATE，Task/AssignmentSnapshot/Pool/Member事务整体失败；
- 1人：仍创建Candidate Pool并要求Claim，不自动转换DIRECT；
- N人：全量冻结，不默认取第一人、不随机选择；
- 超上限：返回CANDIDATE_LIMIT_EXCEEDED，不截断、不生成部分Pool。

## 9. 空候选治理

不得创建空AVAILABLE Pool或无人可Claim的Task。必须区分角色不存在/停用、组织失效、无有效membership、用户均不可用、目录不完整等原因并写非敏感审计。修复主数据后只能通过受控重试创建新事实，禁止刷新既有Pool。

## 10. 候选上限

使用有版本的maxCandidatePolicyRef，具体值必须由组织规模、payload、事务写成本和Claim SLO压测后冻结，不在设计阶段任意硬编码。目录总数、原始membership数、去重人数和响应大小必须一致；complete=false或超限即拒绝。

## 11. Candidate排序

ROLE_V1不支持业务优先级，按candidateUserId数值升序；同一用户的来源证据按roleCode、organizationCode、membershipStableRef字典序排序后合并。sortOrder从1连续生成。

排序算法版本固定CANDIDATE_SORT_USER_ID_ASC_V1，并进入Contract Hash、rule snapshot和Pool Hash。禁止依赖数据库默认顺序、真实姓名、roleName或本地自增membership ID。

## 12. 重复候选

一个Pool内同一userId只生成一个CandidateMember。多来源先去重再Canonical合并，source/eligibility snapshot保存全部稳定来源。冲突事实不得静默择一，应以DIRECTORY_CONFLICT失败。现有(pool_id,candidate_user_id,delete_token)唯一键作为最终防线。

## 13. 实时资格

阶段一在effectiveAt解析并冻结当时角色事实；阶段二在Claim当前时点继续执行RealtimeEligibilityChecker。角色/组织不存在或停用导致Resolver整体失败；用户/员工在effectiveAt停用、离职或membership无效则过滤，过滤后为空即失败。

Pool生成后的离职、停用、调岗或角色撤销不删除历史Member，但实时Claim必须拒绝。

## 14. SoD边界

Resolver发现权威角色事实，不执行完整SoD。发起人若确实属于角色，默认仍进入Pool，以保留事实；Claim时由SoD拒绝。若未来要在解析阶段排除，必须成为有版本规则并影响全部Hash，不能是隐式代码条件。

## 15. DataScope边界

候选发现不能使用当前操作者DataScope，否则相同流程会得到不同Pool。Resolver范围由冻结businessOrganization和Role规则决定；Claim时再校验候选用户自己的DataScope。RBAC能力、Resolver查询范围和Claim DataScope是三种不同语义。

## 16. Resolver版本

命名对齐现有规范：

- resolverCode=ROLE_DIRECTORY；
- resolverVersion=ROLE_DIRECTORY_V1；
- strategyType=ROLE；
- mode=CANDIDATE_POOL；
- status只有在实现及验收完成后才能ACTIVE。

Contract Hash采用长度前缀Canonical SHA-256，覆盖code/version/type/mode、支持角色类型与组织范围、includeSubOrg、effectiveAt/status/空候选/多候选/重复/上限策略、canonicalSortVersion、目录契约版本和Hash schema版本；不包含实现类名、数据库ID或时间戳。

## 17. Registry规划

第一阶段沿用Static Registry，按code+version+contractHash精确注册。本Sprint不注册ROLE，生产运行保持EXPLICIT_USER_V1唯一实现。

未来Database Registry只提供已批准版本路由元数据，不能上传实现或改写历史绑定。新Instance只选唯一ACTIVE版本；历史Instance使用冻结版本，禁止fallback、自动升级或降级；SECURITY_BLOCKED必须阻断。

## 18. Node Assignment

ROLE节点冻结以下Canonical事实：strategyType=ROLE、assignmentMode=CANDIDATE_POOL、resolverCode/version、roleCode/type、organizationScope、organizationRefSource=INSTANCE_BUSINESS_ORG、includeSubOrganizations=false、effectiveAtPolicy及ruleVersion。

事实进入workflow_node_resolver_binding_snapshot的target_value_snapshot与rule_snapshot。实例启动后禁止重读最新Node配置。

## 19. Graph Hash

Graph Hash必须覆盖roleCode/type、orgScope/ref source、includeSubOrg、resolverCode/version、assignmentMode、effectiveAtPolicy、ruleVersion/hash及候选治理策略。任何审批角色规则变化必须产生新Workflow Version Hash。

现有Hasher已纳入assignmentRuleType/config；实现前需Canonical契约测试，保证JSON字段顺序不影响语义Hash，而字段值变化必然改变Hash。

## 20. Binding Set Hash

ROLE Binding进入Instance Resolver Binding Set。manifest按nodeCode稳定排序，覆盖resolver code/version/contractHash、strategy/mode、role rule hash和nodeBindingHash。角色规则变化必须改变Node Binding及Binding Set manifestHash。运行期禁止查询“最新Resolver”。

## 21. Candidate Pool Hash

ROLE Pool Hash必须覆盖Resolver Binding、ruleHash、effectiveAt、directoryRevision、去重排序后的userId、每个Member的合并source snapshot、org/role快照、eligibility snapshot/hash、sortOrder、状态及排序/重复策略版本。

不得包含DB自增ID、created/updated time、显示名称。现有CandidatePoolHash已覆盖Resolver、rule与Member证据，但未显式纳入effectiveAt和directory revision；ROLE需新Hash schema，历史Pool继续原算法，禁止重算。

## 22. Legacy

USER+DIRECT、SINGLE_NODE_LEGACY和旧Multi-Node DIRECT不执行ROLE解析。历史Task不重新Resolver，历史Pool不刷新成员。ROLE只对发布后、Node Binding明确为ROLE_DIRECTORY_V1的新Instance生效。

## 23. API规划

不新增公开Resolver执行、刷新候选、动态注册或版本切换API。ROLE Resolver只能由Runtime内部调用。现有GET /workflow/tasks/{taskId}/candidates继续查询冻结Pool，但只返回最小必要信息。禁止管理员点击“刷新候选人”。

## 24. 数据库规划

现状：

- V2.6.5 Binding Snapshot可用TEXT冻结ROLE规则，且V2.6.6已允许ROLE+CANDIDATE_POOL；
- V2.6.2 Assignment Snapshot支持ROLE和resolved users；
- V2.6.6 Pool支持ROLE、resolver/rule/effectiveTime/hash；
- Candidate Member已有source、org/role snapshot与eligibility hash。

功能上可复用现有快照，但关键ROLE规则只藏JSON会削弱数据库约束、索引和审计，因此建议未来V2.6.9做最小结构化增强。

## 25. 组织人事边界

组织人事域拥有组织、用户/员工、岗位、审批角色定义和membership有效期，并提供带authoritativeRevision的完整事实。Workflow只拥有Resolver输入快照、版本、响应摘要、Pool和Claim证据，不复制可维护主数据。

## 26. RBAC边界

workflow:approve只代表审批功能准入，用户仍须满足Task归属、Candidate Member、实时资格、DataScope和SoD。若未来复用sys_role，必须先治理role semantic/type、有效期、组织职责来源，并分离系统授权membership和业务职责membership；此前RoleDirectory Adapter不得直连sys_user_role。

## 27. Investment边界

Investment负责党委前置研究、董事会、经理层节点的业务语义、角色编码、SoD规则和决策状态。Workflow只解析冻结规则、创建Pool、Claim并执行Task，不修改Investment。角色编码由Investment/治理配置提供并随Workflow Version冻结，不能硬编码在Workflow。

## 28. 安全与故障

必须治理角色越权、全集团泄漏、用户/角色/组织停用、membership漂移和重复、缓存陈旧、结果过大、超时/部分响应、组织错配、roleCode注入、规则篡改和Pool操纵。

错误分类至少包括ROLE_NOT_FOUND、ROLE_DISABLED、ORG_INVALID、NO_ELIGIBLE_CANDIDATE、CANDIDATE_LIMIT_EXCEEDED、DIRECTORY_TIMEOUT、DIRECTORY_UNAVAILABLE、DIRECTORY_PARTIAL_RESPONSE、DIRECTORY_CONFLICT、CONTRACT_MISMATCH。

调用失败必须Fail Closed：不回退EXPLICIT_USER、不使用无revision旧缓存、不生成部分Pool。只允许在无持久化副作用前对明确瞬时错误有限退避；熔断、部分响应或重试耗尽均使Task创建失败。

## 29. V2.6.9规划

候选名V2.6.9__enable_workflow_role_resolver_governance.sql，仅规划，不创建：

1. Node Binding Snapshot增加role_code/type、organization_scope/ref、include_sub_org结构化快照；CHECK要求ROLE时必填且V1仅允许PROCESS_APPROVAL_ROLE/BUSINESS_ORG/false，非ROLE为NULL。
2. Assignment Snapshot增加directory_revision_snapshot与resolution_hash_schema，ROLE时必填。
3. Candidate Pool增加directory_revision_snapshot与pool_hash_schema，ROLE时必填。
4. Candidate Member保持现有结构，多来源Canonical证据继续由TEXT+hash保护。
5. USER+DIRECT、Legacy及历史Pool不回填、不推断、不重算Hash。
6. 索引必须由真实审计查询计划证明必要性，避免无依据写放大。

该Migration未来必须接受Fresh/Upgrade/Legacy、CHECK、摘要、Fingerprint和真实MySQL验收。

## 30. 实施准入条件

进入实现前必须冻结：审批角色目录Owner与编码命名空间、RoleDirectoryPort契约、BUSINESS_ORG/no-children及时间策略、空候选/上限/排序规则、五层Hash schema和测试向量；V2.6.9需独立评审。

Static Registry默认不注册ROLE，直到Migration与端到端验收通过。独立feature必须证明USER+DIRECT、Legacy及Claim并发无回归。研发准入和发布准入分离，WF4.2.6阻断不等于ROLE研发阻断。

## 31. 验收矩阵

未来至少覆盖25项：单候选、多候选、0候选、重复候选、用户停用、离职、角色停用、组织失效、跨组织角色、新增人员不进入旧Pool、移除人员保留旧证据、Claim实时拒绝、排序稳定、Pool Hash稳定、Contract Hash稳定、Graph Hash变化、Binding Set Hash变化、Registry精确匹配、Resolver不存在、Resolver超时、Directory部分失败、Legacy不变、USER+DIRECT不变、Claim并发单赢家、Claim后Approve正常。

另需覆盖候选上限、revision漂移、陈旧缓存、roleCode注入、组织错配、事务回滚、Domain无框架依赖和Spring上下文。

## 32. 风险清单

1. 当前缺少独立权威的流程审批角色目录，这是实现前P0依赖。
2. sys_role语义混合，直接复用会把系统权限错误升级为业务审批权。
3. TEXT可承载ROLE，但结构化数据库治理不足，建议V2.6.9增强。
4. 现有Pool Hash未显式包含effectiveAt和directory revision。
5. 目录调用引入超时、部分响应、事务时长和缓存一致性风险。
6. 大候选集和宽快照会放大Task创建事务及存储成本。
7. ROLE配置错误可能产生跨组织候选泄漏。
8. SoD/DataScope必须保留在Claim阶段，进入Pool不等于已授权。
9. WF4.2.6仍未达到预生产发布条件，ROLE成果不得宣称生产可用。

下一步建议：先冻结审批角色目录与RoleDirectoryPort契约，再进入独立WF5.1实施规划；依赖未冻结前不得编码ROLE Resolver、注册Resolver或创建V2.6.9。

