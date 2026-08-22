# Sprint 2-3.7-WF5.24 ROLE Claim Runtime Integration 实施与验收报告

## 1. Canonical Baseline

- 验收日期：2026-08-20（Asia/Shanghai）。
- V2.6.15：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`，SHA-256 `db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44`，Flyway checksum `538981271`。
- V2.6.16：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`，SHA-256 `d5d0b154eaea4c17dc8b11d7bebda0627c9f31fd96b13043545356a5a2286084`，Flyway checksum `-1570425240`。
- `SHA256SUMS` 实测 38/38 一致；inventory、README 与 Flyway history 均以 V2.6.16 为最高版本。
- 基线状态保持：`EXPLICIT_USER_V1_ACTIVE`、`ROLE_DIRECTORY_V1_PREPARED_NON_EXECUTABLE`、`ROLE_RUNTIME_DISABLED`、`DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED`。

## 2. 修改文件完整清单

### Domain 与 Application

- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/claim/WorkflowClaimRuntimeMode.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/claim/RoleClaimRuntimeGate.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/claim/RoleClaimAdmissionGate.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/claim/TaskClaim.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/repository/RoleRealtimeEligibilityEvidenceRepository.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/repository/RoleRuntimeExecutionAdmissionRepository.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/application/command/RoleClaimRuntimeRequest.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/application/service/RoleClaimEvidenceAssembler.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/application/service/RoleClaimRuntimeIntegrationService.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/application/service/RoleClaimRuntimeTransactionService.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/application/service/RoleClaimNotCommittedEvidenceService.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/application/service/TaskClaimTransactionService.java`

### Infrastructure

- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/eligibility/ConfiguredRoleClaimRuntimeGate.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/eligibility/PersistentRoleClaimAdmissionGate.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/entity/WorkflowTaskClaimEntity.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/entity/WorkflowTaskClaimAuditEntity.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/mapper/WorkflowRoleRealtimeEligibilityEvidenceMapper.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/mapper/WorkflowRoleRealtimeEligibilityEventMapper.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/mapper/WorkflowRoleRuntimeExecutionAdmissionMapper.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/RoleRealtimeEligibilityEvidenceEntityMapper.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/RoleRealtimeEligibilityEvidenceRepositoryImpl.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/RoleRuntimeExecutionAdmissionRepositoryImpl.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/WorkflowTaskClaimEntityMapper.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/TaskClaimAuditRepositoryImpl.java`

### 测试与报告

- `backend/src/test/java/cn/gov/enterprise/modules/workflow/application/service/RoleClaimRuntimeIntegrationTransactionTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/infrastructure/eligibility/ConfiguredRoleClaimRuntimeGateTest.java`
- `docs/workflow-role-runtime-claim-runtime-integration-report.md`

未修改 Investment；未新增 Controller、外部 API 或 Migration。

## 3. Runtime 链路

受治理的内部链路为：Activation Evidence → Promotion → immutable Runtime Binding Snapshot → active Execution Admission → Candidate Pool/Member → Task/NodeExecution → Realtime Eligibility Prepare（事务外）→ 27 Validator/10 Capability → Verify → V2.6.16 Evidence → ROLE Claim 短事务 → Claim Audit → Evidence `CONSUMED`。

现有 `USER_DIRECT` 与新增 `ROLE_CLAIM` 由 `WorkflowClaimRuntimeMode` 显式区分。ROLE 不 fallback 到 EXPLICIT_USER、assignee、系统角色或默认用户。

## 4. Transaction Boundary

远程/Capability 求值位于事务外。ROLE 提交使用独立 `RoleClaimRuntimeTransactionService`，不污染稳定 USER/DIRECT 服务。在同一短事务中使用固定锁序：Task → Candidate Pool → Candidate Member → NodeExecution → Instance → Eligibility Evidence；随后执行 Admission、DataScope、SoD 与运行门禁复核，写 Claim、CAS Task、CAS Pool、写 SUCCESS Audit、追加 `CONSUMED` Event。任一步异常均由 Spring 事务整体回滚。

失败竞争的 Evidence 由独立 `REQUIRES_NEW` 服务追加 `CLAIM_NOT_COMMITTED`，保留失败事实，不删除 Evidence。

## 5. Prepare / Verify / Commit 顺序

1. Prepare：先从冻结 Pool 判断 claimant；非候选人直接拒绝，不调用 Capability/Directory。
2. Verify：现有 `RealtimeEligibilityValidator` 执行 27 个有序 Validator 与 10 个 Capability，输出三态结果及短 TTL token。
3. Assemble：生成 Validator Root、Capability Root、Persistence Hash 和初始 `PREPARED` Event。
4. Commit：重新锁定并验证运行对象、Evidence 归属、Hash、revision fence、TTL、Admission、DataScope、SoD、Feature/Canary/Kill Switch；再完成 Claim 原子提交。

测试证据来源明确记录为 `CONTRACT_TEST`；`CONTRACT_RUNTIME_VALIDATED` 不等于 Production Capability 或真实 Directory 已集成。

## 6. Candidate Pool 规则

- 复用 V2.6.6/V2.6.8 已冻结结构，不创建第二套 Pool。
- Pool/Member 全量冻结、稳定排序、不可刷新/覆盖/补人/删人，不默认选择第一人；单候选仍走 Pool + Claim。
- Pool 保存 resolver code/version/contract、binding set、node binding、rule/pool hash、Definition Version、NodeExecution、Task 和 Instance 归属。
- Claim 前再次锁定候选成员并要求 `INCLUDED`；Directory 当前成员变化不会回写冻结 Pool。

## 7. Evidence 消费规则

Evidence 强绑定 instance/version/node execution/task/pool/member/user、resolver binding、candidate pool hash、runtime binding hash、directory revision、eligibility hash 与 idempotency key。仅 `ELIGIBLE`、27/27、10/10、未过期且处于 `VERIFIED` 的 Evidence 可消费。生命周期固定为 `PREPARED → VERIFIED → CONSUMED`，竞争失败可形成 `CLAIM_NOT_COMMITTED`。V2.6.16 唯一约束与 Trigger 阻止重复消费、非法流转、Hash 漂移和绑定篡改。

## 8. Claim 状态变化

成功后：Task=`CLAIMED`，Pool=`CLAIMED`，NodeExecution 保持 `ACTIVE`，Instance 保持 `RUNNING`，Active Claim=1，SUCCESS Audit=1，Consumed Evidence=1。成员集合、Runtime Binding、Admission 与 Activation Evidence 均不变。`CLAIM != APPROVAL`。

## 9. Audit 链

SUCCESS Audit 与 Claim 共享 task/pool/member/instance/node execution/evidence ID、persistence hash、状态前后值、DataScope/SoD/RBAC 结论、trace 和 idempotency key。数据库 Trigger 校验 Evidence/Persistence Hash 绑定。Audit 写入失败的真实 MySQL 故障注入结果为 Claim=0、Audit=0、Consumed Event=0、Task=`PENDING`、Pool=`AVAILABLE`。

## 10. Feature Flag

提交门禁新增默认值 `workflow.role-runtime.claim-enabled=false`。关闭时在 Evidence/Claim 持久化前 Fail Closed。USER/DIRECT 不读取该配置。

## 11. Canary

Canary 必须同时精确匹配 enterprise、workflow definition、definition version、node 四维；任一缺失、非法或漂移均拒绝。首轮因此只能开放到单企业、单流程版本、单 ROLE 节点。当前未配置，仍关闭。

## 12. Kill Switch

`workflow.role-runtime.kill-switch` 默认 `STOP_NEW_AND_CLAIM`；只有显式 `ALLOW` 且全部 Canary 维度匹配才允许进入 ROLE Claim。Kill Switch 不删除或修改历史 Task、Pool、Evidence、Claim、Audit。

## 13. DataScope

事务内按 Instance 企业/发起组织事实与 `SecurityPrincipal` 的 all-data/allowed-org 范围复核，结果冻结到 Claim/Audit；越权 Fail Closed。Role Membership、RBAC、DataScope 与 Task Eligibility 保持相互独立。

## 14. SoD

Claim 前调用现有 `SegregationOfDutiesPolicy`，保存平台/业务职责分离策略编码、版本和结论。测试 Capability 同时覆盖 Platform SoD 与 Business SoD；任一失败不产生 Claim。候选资格或 `workflow:approve` 均不能替代 SoD 与任务归属判断。

## 15. 并发结果

真实 MySQL 8.4.9 完成两组双 Session：同 Task/同 Candidate/同 Evidence 竞争，以及同 Task/两个合法 Candidate/两个独立 Evidence 竞争。两组均为 1 成功、1 由活动 Claim 唯一键拒绝；每个 Task 最终仅 1 Claim、1 SUCCESS Audit、1 CONSUMED Event，未出现 1213/1205。同 Evidence 重复消费由事件唯一键/状态 Trigger 拒绝；第二组失败方 Evidence 最终为 `CLAIM_NOT_COMMITTED / CONCURRENT_TASK_WINNER`，没有删除竞争证据。

## 16. 幂等结果

事务先按 Task + idempotency key 查询；同 claimant 重试返回原 Claim，其他 claimant 复用 key 被拒绝。数据库活动 Claim 唯一键阻止第二 Active Claim；事件序号/类型唯一键阻止重复消费；SUCCESS Audit 只随首次提交产生。

## 17. 回滚结果

真实 MySQL 注入错误的 Audit persistence hash 后 Trigger 返回 `ROLE_REALTIME_CLAIM_AUDIT_BINDING_INVALID`。回滚后：rollback Claim=0、Audit=0、Consumed=0；Task=`PENDING`/version 0；Pool=`AVAILABLE`/version 0。另有应用级测试覆盖 Audit 异常时不追加 `CONSUMED`。

## 18. MySQL 真实验证

- MySQL Community Server 8.4.9，隔离临时 datadir，端口 38640；未连接生产或未知实例。
- Flyway 13.0.0：39 条 history 全部成功，strict validate PASS，第二次 migrate 输出 no migration necessary。
- V2.6.16 checksum `-1570425240`；V2.6.15 checksum `538981271`。
- 负向矩阵实测拒绝：Evidence UPDATE、Claim Evidence 绑定 UPDATE、Audit Hash 不一致、Evidence/Claim 重用、CONSUMED 后非法事件；事务故障注入完整回滚。
- 实测成功链：Claim=1、SUCCESS Audit=1、CONSUMED Evidence=1，Task/Pool 均为 CLAIMED。

## 19. Migration 变化

`NO_CHANGE`。未创建 V2.6.17；V2.6.16 已提供 Evidence→Claim 单向强绑定、27/10 完整性、append-only 生命周期、活动 Claim 唯一约束及 Audit Hash 绑定，应用层补充 active Admission 与运行门禁即可闭环。所有历史 Migration 字节内容保持不变。

## 20. Schema Fingerprint

在同一 MySQL 8.4.9 实例上，使用仓库 `schema_fingerprint.sql` 规范化 information_schema 输出后计算：

- Full Schema：`fe6709bfa2990bbd9de06fb08106fb063d1c719c67bbc9396fe9a05510379899`
- Workflow Schema：`d7eb9f8efdcb8ee6d40543402dcd60ba42048c578db1ad0f245f66419b26fc1c`

本 Sprint 无 DDL，因此指纹即 V2.6.16 Canonical Schema 当前实测值。

## 21. Legacy / USER 回归

- `TaskClaimTransactionService` 保持 USER/DIRECT 专用，源代码不含 ROLE Realtime Eligibility 依赖。
- DIRECT 不创建 Candidate Pool，不要求 ROLE Evidence；Legacy 不回填、不重解析。
- EXPLICIT_USER_V1 继续 ACTIVE，Task Claim/Action、assignee、RBAC、DataScope、SoD 与 Audit 回归通过。
- Java 21 编译、Spring Boot Context、全量 536 项测试：0 failure / 0 error / 0 skipped。
- Domain purity PASS，Production dependency boundary PASS，`git diff --check` PASS。

## 22. Directory 真实资源阻断

`DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED` 保持。未读取 Production Secret，未访问真实 TEST/PREPROD/Production Directory，未宣称 Directory SLA、真实 Revision Fence 或 Production Directory 集成通过。内部事务与数据库验收使用 deterministic contract fixture；这只证明内部编排与 Fail Closed 边界。

## 23. 剩余风险

1. 真实 TEST/PREPROD Approval Role Directory 未交付，网络超时、限流、revision SLA 与真实成员变化仍待外部联调。
2. 本 Sprint 默认关闭且无外部 ROLE API；Canary 配置必须由后续受控发布流程注入，不能人工绕过。
3. MySQL 并发测试验证了核心单赢家与回滚约束；生产规模热点、连接池和长尾延迟仍需预生产压测。
4. Business SoD 的真实业务规则仍依赖外部 Capability 正式交付；当前验证为契约级，不代表生产规则已接入。

## 24. WF5.25 准入判断

内部准入结论为 `READY_WITH_EXTERNAL_RESOURCE_BLOCK`：ROLE Claim Runtime 内部链、Candidate Pool、Realtime Eligibility、Evidence Persistence、Claim/Audit、事务、并发、幂等、DataScope/SoD 和默认关闭门禁均已验证，可进入 WF5.25 的外部 Capability/Directory 资源联调准备；但在真实 TEST/PREPROD Directory、凭据、流程角色数据和 SLA 交付前不得启用 Runtime。

最终状态：

- `ROLE_CLAIM_RUNTIME_INTEGRATION_VALIDATED`
- `ROLE_RUNTIME_CANARY_READY`
- `ROLE_RUNTIME_DISABLED`
- `DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED`
- `ROLE_DIRECTORY_V1_PREPARED_NON_EXECUTABLE`
- `EXPLICIT_USER_V1_ACTIVE`
