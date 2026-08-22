# Approval Role Directory 最终契约冻结

状态：DESIGN_FROZEN / WF5.1_READY

适用Sprint：2-3.7-WF5.0.2。本文件冻结WF5.1框架编码契约，不代表真实目录、Migration、ROLE生产启用或预生产发布已经完成。

## 1. Owner / RACI

| 治理角色 | 责任 | 关键权限 |
|---|---|---|
| Directory Business Owner | 审批角色业务语义、roleCode申请、成员与有效期业务批准 | A |
| Directory Data Owner | 数据模型、质量、Revision、冲突与更正 | A/R |
| Organization/HR Owner | 组织、员工和来源事实正确性 | R/C |
| Workflow Owner | Port契约、Resolver消费、冻结证据 | R；无目录写权限 |
| Security/Audit Owner | 敏感角色双人复核、审计和越权检查 | A/R |
| Release Approver | 契约/目录版本发布与GO/NO-GO | A |

职责分配：

- 创建/修改/停用审批角色、修改组织归属：Business Owner申请，Data Owner执行，HR会签，敏感角色由Security复核。
- 增加/移除成员、调整有效期：Business Owner批准，HR核实人员事实，Data Owner在一个变更集中执行。
- 发布Revision、历史更正、目录冲突处置：Data Owner执行，Business Owner与Security/Audit会签，Release Approver批准发布。
- Workflow Owner只读Port并验证Hash；Workflow管理员不得单独维护角色或成员。

真实目录上线前需在变更单绑定具名人员与替补；WF5.1框架编码以本RACI角色为责任边界。

## 2. Role语义

PROCESS_APPROVAL_ROLE是enterprise内、BUSINESS_ORG范围、有效期受治理的业务审批职责。它不等于sys_role、岗位、行政职务、人员身份、workflow:approve或最终Task Assignee。

稳定键为enterpriseId + organizationId + roleCode。roleName仅展示。roleCode发布后不可复用；业务语义不兼容变更必须新roleCode。

## 3. RoleAssignment

ApprovalRoleAssignment表示user在指定组织和半开时间区间承担审批角色，包含稳定assignmentKey、roleCode、organizationId、userId、可选employeeId、status、effectiveFrom/to、sourceType/ref、assignmentRevision和审计。

允许同一角色多人，也允许同一user通过多个互不矛盾来源命中。Candidate层按user合并，但Directory Result保留全部sources。

## 4. Revision模型

Revision键固定为enterpriseId + organizationId + roleCode，值为单调递增、不可复用、不可回退的聚合级版本。

发布策略选择“一批完整业务变更一次递增”：

1. 创建DRAFT change set并绑定baseRevision；
2. 校验角色、组织、成员、时间区间、来源冲突和审批；
3. 锁定聚合并确认currentRevision=baseRevision；
4. 同一事务写入角色/assignment变更、审计与新Revision；
5. 计算完整集合Hash；
6. 提交后发布RevisionPublished事件。

单条成员变化也构成一个完整change set；批量调整只能在全部成员校验通过后一起发布。禁止边改成员边发布多个中间Revision。

## 5. Revision原子性

ApprovalRole、全部受影响Assignment、Revision记录和审计必须同一数据库事务提交。查询通过Revision对应的一致快照读取，不能看到“新Revision+旧成员”或“旧Revision+新成员”。

数据库Adapter使用一致性读和聚合Revision锁；远程目录必须在单响应中返回directoryRevision、complete和resultHash。任何版本撕裂、计数不符或Hash不符均Fail Closed。

撤销Revision 10的变更必须发布Revision 11，不得恢复成9。Revision发布事件只在事务提交后由Outbox产生。

## 6. Correction

历史错误禁止静默UPDATE。Correction change set至少记录correctionReason/ref、correctedBy/time、previousRevision、newRevision、affectedEffectivePeriod、before/after事实Hash、approvalRef和auditRef。

更正发布新Revision，并保留原记录及更正链。之后对历史effectiveAt的新Directory查询返回更正后的权威事实；已经生成的历史Candidate Pool、Member、Claim和Audit永不重算或修改。目录事实修正与Workflow执行证据冻结是两套边界。

敏感或影响已办流程的Correction必须由Business、Data、Security三方复核，并产生影响评估清单，但不得自动重开历史Task。

## 7. Source Conflict

来源枚举：MANUAL_GOVERNANCE、HR_SYNC、EXTERNAL_SYNC。来源优先级只用于显示元数据补全与同义重复证据排序：MANUAL_GOVERNANCE > HR_SYNC > EXTERNAL_SYNC；它不得覆盖membership业务矛盾。

同一user/role/org在重叠时段出现ACTIVE与INACTIVE、不同组织归属、不同人员标识映射或互斥有效期事实时，返回DIRECTORY_SOURCE_CONFLICT并Fail Closed。Resolver不得猜测。

多个来源均证明ACTIVE且事实不矛盾时允许共存，按Canonical sources排序合并。相同稳定sourceRef的重叠区间禁止；不同source的合法重叠可存在。

## 8. Time Semantics

有效区间固定[effectiveFrom,effectiveTo)，effectiveTo=null表示正无穷。所有Port时间为UTC Instant，精度毫秒；ROLE Resolver effectiveAt固定NodeExecution首次激活时间。

同一assignmentKey不得存在重叠有效区间。历史更正后新查询按新Revision返回更正事实，旧Pool不受影响。

## 9. Organization

ROLE_DIRECTORY_PORT_V1仅支持BUSINESS_ORG和includeSubOrganizations=false。organizationId必须来自冻结Instance业务组织并在Directory服务端作为强条件；禁止集团全查后本地过滤。

组织在effectiveAt无效或当前新Pool生成时已停用，返回BUSINESS_ORG_INACTIVE。角色归属与组织不匹配返回ROLE_ORG_MISMATCH。历史Pool不受组织后续状态变化影响，但Claim实时资格仍检查。

## 10. Canonical Serialization

正式规范名ROLE_CANONICAL_JSON_V1：

- 字节编码UTF-8，无BOM；Unicode字符串先NFC，再按schema规定trim首尾空白。
- roleCode、枚举、schema/version常量使用大写规范值；稳定ID保持原始大小写，不做locale转换。
- Object键按Unicode code point升序；输出紧凑JSON，无空格、缩进、换行。
- null输出字面量null；Boolean为true/false；整数为无前导零十进制；V1禁止浮点进入Hash。
- 时间转UTC，固定yyyy-MM-dd'T'HH:mm:ss.SSS'Z'，不允许时区偏移等价写法进入Hash。
- JSON转义遵循RFC 8259：引号、反斜杠和控制字符必须转义；非ASCII字符直接UTF-8，不使用实现相关ensure-ascii。
- Map先规范化为Object再排序；数组保持领域Canonical顺序，而不是任意JSON顺序。
- nodes按nodeOrder,nodeCode；bindings按nodeOrder,nodeCode；members按userId；sources按sourceType,sourceRef,effectiveFrom,assignmentKey。
- Hash算法SHA-256，输出64位小写Hex。
- 禁止依赖Jackson默认字段顺序、Map迭代顺序、数据库返回顺序或平台默认时区。

## 11. Directory Result Hash

ROLE_DIRECTORY_RESULT_HASH_V1输入固定包含schema、contractVersion、enterpriseId、organizationId、roleCode/type、effectiveAt、directoryRevision、roleStatus、complete、rawMembershipCount、deduplicatedUserCount及全部members。

Member包含userId、eligibilityHash、sortOrder和sources；Source包含assignmentKey、sourceType/ref、effectiveFrom/to、revision。resolvedAt、显示名、审计时间和数据库自增ID不进入Hash。

同语义不同返回顺序先Canonical排序，因此Hash一致。complete必须true后才允许计算可消费Result Hash。

## 12. 五层Hash向量

标准输入A：

- roleCode=INVESTMENT_FINANCE_REVIEWER；
- organizationId=ORG-QIHE-DIGITAL；
- effectiveAt=2026-08-13T01:02:03.004Z；
- directoryRevision=12；
- USER-001来源MANUAL_GOVERNANCE/APR-2026-001/ARA-001/revision 7；
- USER-002来源HR_SYNC/HR-7788/ARA-002/revision 3；
- Resolver=ROLE_DIRECTORY/ROLE_DIRECTORY_V1；
- Node=FINANCE_REVIEW，Workflow=INVESTMENT_DECISION。

变体：B仅Object字段/Member输入顺序变化；C roleCode改INVESTMENT_LEGAL_REVIEWER；D org改ORG-QIHE-INDUSTRY；E revision改13；F仅Member输入反序；G移除USER-002；H effectiveAt改2026-08-14T01:02:03.004Z。

按层语义，Graph/Rule不包含实际Revision、成员和激活时刻；Contract描述契约而非某次业务输入；Binding包含规则但不包含运行期目录结果；Candidate Pool包含全部运行期事实。

## 13. Expected Hash

以下值是ROLE_CANONICAL_JSON_V1离线固定测试向量，不得在测试运行时动态生成Expected值：

| Layer | A | B | C | D |
|---|---|---|---|---|
| Graph | 67db14c9194bb4655dfcc9333361e6457ba1ac62b9ed2e7f9017313dac991359 | 67db14c9194bb4655dfcc9333361e6457ba1ac62b9ed2e7f9017313dac991359 | 5320b6eec74aa6b93302730085a01fd0033994087ac5a4a56934674c02aa4418 | 9a2e717a775ba153570cda16c1ddc514b4cbba818407fab2a96a1d7404c1c656 |
| Role Rule | fc1d389a9da0a3b9858a4f1d3a4bb154cd4619ff95354b74d2532839b1cb7255 | fc1d389a9da0a3b9858a4f1d3a4bb154cd4619ff95354b74d2532839b1cb7255 | 743bc0fb2b080c884748a5e284b07088a4d0c60e9e6b787032f66a73ade1f9a1 | 5b731fb1daa4300477e7714e9f0a63d9d963b91be3b58aba95dcaf6937935fef |
| Contract | 5e8f9870effc7f886671f3cc5077a7bf0419cd9a6698dd6ca01e9de0c01f708d | same A | same A | same A |
| Binding Set | 3778be780a063d9a9156c8210c795424a22bdbab1a33474f0d8a84742711fbc0 | same A | 737090144583e9f856484029d3a09db24dc918e1f57826862e4f5c465b01bfb4 | ef06a969554b27c137c5c6bed9abb3de6d480b3d09f5ef18c1250d19c0d63c85 |
| Candidate Pool | b80558f5fcba7c82f263f2eef7790bbc37b6d30b4f49fdaba75e7ae4a1a91498 | same A | c3e037aecedb05519185da37c579c9c579a702ad21070fc46d1526fb018e2e42 | b57547416a72b156ef0979385f97e73d38c559bc67835035ca0613353e2bf16b |

| Layer | E | F | G | H |
|---|---|---|---|---|
| Graph | same A | same A | same A | same A |
| Role Rule | same A | same A | same A | same A |
| Contract | same A | same A | same A | same A |
| Binding Set | same A | same A | same A | same A |
| Candidate Pool | 9fe5a2c8b9c049f833d5a4185bbe4037baf848cfcb00818bd7696186a9d97575 | same A | 69270181663a13640f35aa665a0bcd8108349a46498d16f8aaa3141cbedae788 | ea17b8c144b6cf3ccd1ea7b26729908ab20179080899df04c2fc4bf21f8a222c |

Directory Result中间向量A为a5811caa8aaa1b8b32a61faddadcd6ce8c8b23dccac2d02c553e5ff363762abd；B/F同A；C=4203b8aaf5e3d410d65997bfe4e2fd7bddf8d2ed3ab33932caae7ed980102b62；D=fb0179017c1f752dfa91461d5f20c5230e1eabc8af15665e75e7cc3fc22984c4；E=aec256ce48bcf60ff0ee0469e1fa0aa8f81806f453ddaa212164c1129c86d2c9；G=70db6ccf2871094397af41c4d12ec16bb414610156de058aba4c305a29bc5e5b；H=6d749cbeefa659c95f10bef49a3651a4013c377580f688244cd23b2c6cc7382a。

固定Contract A的Canonical JSON：

```json
{"canonicalSerialization":"ROLE_CANONICAL_JSON_V1","completeSemantics":"ATOMIC_COMPLETE_ONLY","dedupPolicy":"MERGE_BY_USER_ID","effectiveAtSemantics":"NODE_ACTIVATED_AT_UTC_INSTANT","errorSemantics":"FAIL_CLOSED_V1","memberSchema":"ROLE_DIRECTORY_MEMBER_V1","mode":"CANDIDATE_POOL","organizationScope":"BUSINESS_ORG","portContract":"ROLE_DIRECTORY_PORT_V1","querySchema":"ROLE_DIRECTORY_QUERY_V1","resolverCode":"ROLE_DIRECTORY","resolverVersion":"ROLE_DIRECTORY_V1","resultSchema":"ROLE_DIRECTORY_RESULT_V1","revisionSemantics":"ENTERPRISE_ORG_ROLE_MONOTONIC","roleType":"PROCESS_APPROVAL_ROLE","schema":"RESOLVER_CONTRACT_HASH_V1","strategyType":"ROLE","timeoutFailureSemantics":"BOUNDED_RETRY_NO_FALLBACK"}
```

Java、SQL辅助校验和其他语言实现必须使用同一Canonical输入并匹配Expected。

## 14. Contract Hash

ROLE_DIRECTORY_PORT_V1绑定Query/Result/Member schema、PROCESS_APPROVAL_ROLE、BUSINESS_ORG、Node activated UTC effectiveAt、聚合Revision、complete、错误/重试、Canonical V1、按user去重、Result Hash及Fail Closed语义。

Contract Hash固定为5e8f9870effc7f886671f3cc5077a7bf0419cd9a6698dd6ca01e9de0c01f708d。

兼容规则：

- 仅新增可选审计字段且不进入Hash/控制流，可发布V1兼容补丁，但Contract Hash不得变化；
- 必填字段、有效期/Revision/complete/error、Canonical/Hash、排序/去重、范围或失败策略变化均是Breaking Change，必须ROLE_DIRECTORY_PORT_V2和新Resolver版本；
- Resolver Descriptor显式绑定Port contractVersion和Hash，V1 Resolver不得静默消费不兼容V2。

## 15. Error Code

| 错误码 | 可重试 |
|---|---|
| ROLE_NOT_FOUND、ROLE_DISABLED、ROLE_ORG_MISMATCH、BUSINESS_ORG_INACTIVE、NO_ROLE_MEMBER | 否 |
| DIRECTORY_SOURCE_CONFLICT、DIRECTORY_REVISION_MISMATCH、CANDIDATE_LIMIT_EXCEEDED、DIRECTORY_CONTRACT_MISMATCH、INVALID_DIRECTORY_QUERY | 否 |
| DIRECTORY_TIMEOUT、DIRECTORY_UNAVAILABLE | 仅在retryable=true且预算允许 |
| DIRECTORY_PARTIAL_RESULT | 否；重新发起必须是新的完整调用 |

错误不返回其他组织成员、SQL或敏感字段。

## 16. Retry

每次Runtime解析有总deadline；timeout取版本化平台配置与剩余deadline较小值。最多两次尝试（首次+一次重试），只有连接失败、503/明确瞬时不可用和超时可重试；使用有上限退避与jitter。

重试保持requestId、effectiveAt和首次取得的expectedDirectoryRevision。若首次未取得完整Revision，重试仍必须获得单一原子响应；Revision变化则DIRECTORY_REVISION_MISMATCH，不静默接受。

业务错误、冲突、部分响应、Contract mismatch和超限绝不重试。禁止无限重试、EXPLICIT_USER fallback、旧缓存或旧Pool fallback。

## 17. Candidate Limit

平台最大值由Workflow Platform Governance Owner管理并版本化；业务模块只能请求更低阈值。

effectiveLimit=min(platformLimit,businessLimit)；businessLimit缺失时使用platformLimit。目录和Resolver都校验raw membership、去重user及payload大小。超过阈值Fail Closed，禁止截断前N人。阈值具体数值在WF5.1压测后由治理配置冻结，不硬编码在Domain。

## 18. Audit

目录必须回答谁、何时、为何、把谁加入/移出哪个组织下哪个角色，并关联Revision。

审计包含created/updated、source、changeReason、approvalRef、auditRef、reviewedBy/time及before/after Hash。党委会、董事会、经理层、投资审批等敏感角色默认maker-checker：制单人与复核人不同，Business Owner审批，Security可审计。

## 19. Revision Event

未来事件RoleDirectoryRevisionPublished包含eventId、enterpriseId、organizationId、roleCode、previous/newRevision、aggregateHash、changeType、affectedPeriod、publishedAt、traceId、approvalRef和contractVersion。

事件用于目录侧缓存失效、监控和审计。Workflow可以失效未使用的请求级元数据，但绝不刷新历史Candidate Pool或重算历史Task。事件采用Outbox和消费者幂等；本Sprint不实现。

## 20. Data Ownership

approval_role、approval_role_assignment、approval_role_revision属于Organization / Governance Directory bounded context，不属于Workflow Runtime。

Workflow拥有RoleDirectoryPort需要侧接口、Adapter契约、Resolver版本绑定和冻结Candidate证据；没有目录写权限。Investment仅提供roleCode和业务语义。

## 21. Migration边界

目录表进入组织人事/治理目录独立Migration版本链，不默认占用Workflow V2.6.9。其版本号由该上下文治理，避免跨限界上下文混用。

Workflow V2.6.9候选仅允许增强Node/Binding/Assignment/Pool的ROLE结构化快照、directoryRevision和hash schema；必须独立设计、创建和真实MySQL验收。本Sprint未创建任何Migration。

## 22. WF5.1范围

允许：

- RoleDirectoryPort interface；
- Query/Result/Member与错误模型；
- ROLE Resolver纯Domain；
- Application wiring；
- Static Registry的非ACTIVE准备；
- 测试Fake Directory Adapter；
- 固定Canonical与Expected Hash契约测试。

禁止：

- 真实目录数据库与Production Adapter；
- ACTIVE注册和生产启用ROLE Resolver；
- Investment真实接入；
- POSITION/ORG；
- 修改历史Migration。

权威目录仍不存在时，WF5.1只能交付框架和Fake测试，运行时保持EXPLICIT_USER_V1。

## 23. Final Gate

18项门禁结论：

1. Directory Owner/RACI：PASS；
2. Data Owner：PASS（Directory Data Owner）；
3. roleCode稳定规则：PASS；
4. Revision批次发布：PASS；
5. Revision原子性：PASS；
6. Correction：PASS；
7. Source conflict：PASS；
8. Node activated effectiveAt：PASS；
9. BUSINESS_ORG/no children：PASS；
10. complete原子响应：PASS；
11. Error Code：PASS；
12. Retry：PASS；
13. ROLE_CANONICAL_JSON_V1：PASS；
14. 五层Hash向量：PASS；
15. Expected Hash：PASS；
16. Contract Hash：PASS；
17. Candidate Limit Owner：PASS；
18. sys_role完全隔离：PASS。

结论：DESIGN_FROZEN / WF5.1_READY。此结论仅准入框架编码，不是Production Directory或发布准入。

## 24. Acceptance Matrix

未来至少验证：单/多/无成员、role不存在/停用、org停用/mismatch、历史effectiveAt、Revision单调与原子、Correction、新旧Pool隔离、source conflict、duplicate user合并、partial、timeout、瞬时错误单次重试、业务错误不重试、candidate limit、Canonical ordering、全部Expected Hash、Contract mismatch、cross-org leak、目录审计、Legacy与USER+DIRECT不变。

还需跨Java与独立第二语言验证固定向量，证明不依赖Jackson默认顺序或平台时区。

## 25. Risk List

1. 权威目录和具名Owner尚未实际部署/任命；WF5.1只能框架实现。
2. 聚合Revision需要目录存储事务和一致快照能力。
3. 历史Correction可能影响未启动流程的业务判断，需要影响评估。
4. 多来源质量差会触发Fail Closed并阻断Task创建。
5. 无成员缓存提高正确性但增加目录可用性和延迟要求。
6. 两次尝试仍可能延长Runtime事务，调用与持久化边界需谨慎设计。
7. Canonical JSON跨语言实现存在Unicode、时间和排序偏差风险。
8. 候选上限尚无实测具体数值，必须压测后冻结。
9. V2.6.9与目录Migration分属不同上下文，发布依赖需要明确编排。
10. WF4.2.6仍是PREPROD_RESOURCE_NOT_READY，任何后续成果不得描述为生产可发布。

下一步：进入WF5.1时严格限制为Port、模型、ROLE Resolver框架、Fake Adapter和固定Hash测试；不得ACTIVE注册、创建真实目录或接入Investment。
