# Sprint 2-3.7-WF5.22 ROLE Realtime Eligibility Capability Framework Implementation Report

## 1. 修改文件清单

- 新增纯领域包：`backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/eligibility/`，共22个模型、Port、Canonical及Validator文件。
- 新增非Spring、内存编排服务：`RoleRealtimeEligibilityPreparationService`。
- 新增Fake Capability：`FakeRealtimeEligibilityCapabilities`（仅位于测试源码）。
- 新增专项测试：`WorkflowRoleRealtimeEligibilityFrameworkTest`、`WorkflowRoleRealtimeEligibilityIsolationTest`。
- 新增本实施报告。
- 未修改Workflow Claim写链路、Investment、Resolver Registry或Migration资产。

## 2. Current Baseline

- V2.6.15：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。
- SHA-256：`db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44`；Flyway checksum：`538981271`。
- `DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED`。
- `ROLE_DIRECTORY_V1_PREPARED_NON_EXECUTABLE`。
- `ROLE_RUNTIME_DISABLED`。

## 3. Existing Claim Capability Audit

现有`TaskClaimTransactionService`按Task、Pool、CandidateMember、NodeExecution、Instance固定顺序加锁，并在事务内调用原`RealtimeEligibilityChecker`、DataScope和SoD。该Checker读取平台用户/任职事实，不是Approval Role Directory契约。为避免持锁远程调用与改变USER/DIRECT行为，本Sprint未修改该服务，新框架完全旁路且不可执行。

## 4. Domain模型

新增Query、Context、Policy、三态Decision/Result、Failure、Evidence、AuditEvidence、RevisionFence、VerificationToken/Facts、Capability Result、Role Membership Result、User Status、Validator与Canonical。Domain不依赖Spring、MyBatis、Entity、HTTP或Controller。

## 5. 三态语义

- `ELIGIBLE`：27项全部PASS，仅允许进入未来短事务Verify。
- `INELIGIBLE`：权威业务事实明确拒绝。
- `INDETERMINATE`：能力不可用、证据不完整或完整性无法证明，fail closed。

ELIGIBLE不是授权、Claim成功或生产可用证明。

## 6. Query契约

Query包含enterprise/instance/task/node/pool/candidate、role/org、可信claimAt、Candidate Directory revision/result hash、Pool/Binding hash、business scope reference和correlationId。所有代码、ID和小写SHA-256在构造时校验；不包含成员列表或敏感人员字段。

## 7. Candidate第一门禁

Validator在调用Role Directory Port前完成冻结Candidate本地预检。非Candidate返回`NOT_CANDIDATE`，Role Directory调用次数为0，且没有接口可将当前Directory成员补入冻结Pool。

## 8. 27步Validator

固定顺序实现：Runtime Gate、Kill Switch、Task存在/可领取、Pool存在/归属、Candidate存在/有效、Binding/Role/Org/Enterprise、User/Org/Role实时资格、Directory Complete/Revision、DataScope、Platform/Business SoD、Active Claim、Assignee、Idempotency、Audit、Feature、Canary、Final Hash。每步输出code、order、PASS/FAIL/INDETERMINATE、reason、evidenceHash和checkedAt；首个失败立即停止。

## 9. Capability Ports

定义RealtimeRoleMembership、UserStatus、OrganizationMembership、DataScope、PlatformSoD、BusinessSoD、Audit、FeatureFlag、KillSwitch及Canary Port。仅测试源码提供Fake实现；没有Production Adapter或Spring Bean注册。

## 10. Role Membership

Role Port只判断一个已冻结Candidate在enterprise/org/role/claimAt下的资格，返回complete、sourceConflict、claim revision、result/contract/evidence hash和有效期；不返回候选集合。

## 11. User Status

规范状态为ACTIVE、DISABLED、LOCKED、TERMINATED、SUSPENDED、UNKNOWN。仅ACTIVE通过；UNKNOWN映射为INDETERMINATE，其余非ACTIVE状态映射为INELIGIBLE。

## 12. Organization Membership

Port语义固定为精确BUSINESS_ORG关系，不支持父子组织继承、集团全域或模糊匹配。

## 13. Revision Fence

Evidence/Token分别保存candidateRevision和claimRevision，并冻结Directory result hash、contract hash与verifiedAt。Revision从R10变化到R15不自动拒绝；完整性/合同不合法才返回`REVISION_INVALID`。

## 14. DataScope

Capability三态为PASS/DENY/INDETERMINATE。DENY映射`DATASCOPE_DENIED`，不可用映射`DATASCOPE_UNAVAILABLE`；不以Candidate或RBAC替代DataScope。

## 15. Platform SoD

通过Port接收已归一化结果，明确冲突为INELIGIBLE，能力不可证明为INDETERMINATE。本Sprint仅Fake，不接生产SoD。

## 16. Business SoD

保持独立Port，不依赖Investment；缺失/不可用时fail closed，明确冲突返回`BUSINESS_SOD_DENIED`。

## 17. Prepare/Verify模型

`RoleRealtimeEligibilityPreparationService`仅在内存执行Prepare并生成短期Token。Verify重查Pool hash、Binding hash、Task version、Active Claim和TTL；漂移要求释放事务并重新Prepare。服务没有Repository、Mapper或Writer，不能Commit。

## 18. TTL

`maxEligibilityAge`必须为有限正Duration。expiresAt取`verifiedAt + maxEligibilityAge`与Directory证据有效期较早者；过期返回`EVIDENCE_EXPIRED`。

## 19. TOCTOU

Token冻结Pool/Binding hash、Task version、Directory revision/result/contract fence和Eligibility hash。Verify只做本地事实复核，不在未来短事务中调用Directory。

## 20. Canonical Hash

实现`ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1`：固定字段名与顺序、长度前缀、UTF-8、UTC epoch millis、小写SHA-256。覆盖身份、冻结引用、Capability三态与证据、时间和最终状态；排除数据库ID、显示名、日志文本、重试次数和PII。

## 21. Evidence

Evidence保存Candidate、Pool/Binding、Role/Org、双Revision、Directory result/contract、User/Org/Role、DataScope、双SoD、Flag/Canary/KillSwitch/Audit、27步结果、TTL、scope/correlation及canonical hash。早期fail-fast未执行的Capability保持空值，不伪造PASS。

## 22. Audit Evidence

审计值只含taskId、candidateUserId、三态结果、稳定reason code、双Revision、Evidence hash、时间与correlationId，不含手机号、证件、住址、薪资、Secret或完整目录载荷。

## 23. Failure分类

稳定代码区分`BUSINESS_REJECT`与`SYSTEM_INDETERMINATE`，覆盖NOT_CANDIDATE、角色/人员/组织、Directory partial/conflict/unavailable、Revision、DataScope、SoD、Flag/Canary/KillSwitch、TTL、Binding/Hash及Claim冲突。

## 24. Flag/Canary/Kill Switch

三者均通过独立Capability Port注入。Fake结果只用于契约测试；没有生产配置读取。Kill Switch明确DENY为`KILL_SWITCH_ACTIVE`，不存在fallback。

## 25. Timeout/Retry/Circuit

Policy只冻结超时与重试预算抽象；本Sprint没有网络重试。Context中的Circuit Open直接返回Directory不可用；partial、timeout、conflict均不能使用缓存降级为ELIGIBLE。

## 26. DIRECT/Legacy隔离

新服务未被`TaskClaimApplicationService`、`TaskClaimTransactionService`或Controller引用。DIRECT与Legacy继续走原链路，既不调用新框架，也不重新解析历史Task。

## 27. Fake PASS非执行证明

全PASS只返回内存Evidence和VerificationToken。代码无Task/Pool/Claim Repository、写服务、Controller或Runtime Bean依赖，因此Task创建、Pool创建、Claim写入均为0。

## 28. Persistence Decision

本Sprint坚持Migration=0。未来成功Claim需持久化可复核Evidence、失败需可靠append-only审计；是否规划V2.6.16只能在WF5.23设计阶段决定。

## 29. Production Dependency Scan

静态扫描覆盖Domain与Prepare Service，禁止Production Role Directory、Investment、真实DataScope/SoD/Flag/KillSwitch、Controller、Claim Repository写路径、Task Update与CandidatePool Writer依赖。Fake仅在`src/test`。

## 30. 测试结果

环境：Eclipse Temurin Java 21.0.12、Apache Maven 3.9.9、Spring Boot 3.5.9。

- Java 21 compile：PASS。
- Spring Boot Context：PASS。
- Realtime Eligibility专项：20项通过，0失败、0错误、0跳过；测试方法内覆盖冻结清单的30类正向/负向场景。
- Workflow专项（全量报告中`workflow`包）：342项通过，0失败、0错误。
- 后端全量：525项通过，0失败、0错误、0跳过。
- Domain纯净、PII扫描、Production Dependency Scan：PASS。
- Canonical覆盖输入顺序、Candidate、Role、Org、claim revision、User Status、DataScope、SoD及TTL漂移：PASS。
- Spring上下文日志中的H2未建业务表提示来自既有DatabaseMappingChecker诊断，不构成测试失败。

## 31. Migration状态

- Migration变化：0；V2.6.16不存在。
- V2.6.15目标SHA与Flyway checksum保持不变。
- Migration SHA：37/37；V2.6.15实算SHA为`db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44`。
- `git diff --check`及新增文件尾随空白扫描：PASS。

## 32. 剩余风险

- 外部Directory资源仍未交付，Fake PASS不能证明生产可用。
- 新框架尚未持久化Evidence，也未接入真实Claim三阶段事务。
- 现有Claim链仍在持锁事务内执行旧本地资格校验；未来接入前必须单独重构Prepare/Verify/Commit，且需完整并发与故障注入验收。
- 生产DataScope、SoD、Feature、KillSwitch、Canary及Audit能力契约尚未联调。

## 33. 下一步建议

仅建议二选一：进入WF5.23 Realtime Eligibility Evidence Persistence Design；或待Directory资源交付后重新执行WF5.20.2。不得据此启用ROLE Runtime或进入真实Claim。

最终目标状态：`ROLE_REALTIME_ELIGIBILITY_FRAMEWORK_READY / ROLE_REALTIME_ELIGIBILITY_NON_EXECUTABLE`，并继续保持`DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED / ROLE_DIRECTORY_V1_PREPARED_NON_EXECUTABLE / ROLE_RUNTIME_DISABLED`。
