# Workflow V1 RC2-S6 — ROLE Directory Runtime Bridge 实施报告

## 1. 结论与边界

RC2-S6 已完成 Instance 冻结 ROLE Binding 到 Candidate Pool 基础对象的受控桥接。实现仅提供内部框架，不注册可执行 ROLE Runtime，不新增 Controller，不触发 Claim、Admission 或 Realtime Eligibility，不创建 Migration，也未访问 RC1 TEST 数据库。

最终状态：`RC2_S6=PASS`、`ROLE_RUNTIME=DISABLED`、`CANARY=NOT_AUTHORIZED_NOT_ENABLED`。

## 2. 修改文件清单

新增生产代码：

- `backend/src/main/java/cn/gov/enterprise/modules/workflow/application/service/RoleDirectoryRuntimeBridge.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/application/service/RoleCandidatePoolTransactionService.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/runtime/RoleDirectoryRuntimeTechnicalGate.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/runtime/RoleDirectoryRuntimeContext.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/runtime/RoleDirectoryCandidateEvidence.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/runtime/PreparedRoleCandidatePool.java`

修改生产代码：

- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/model/WorkflowTask.java`

新增测试：

- `backend/src/test/java/cn/gov/enterprise/modules/workflow/application/RoleDirectoryRuntimeBridgeTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/application/RoleCandidatePoolTransactionServiceTest.java`

新增报告：

- `docs/workflow-role-directory-runtime-bridge-report.md`

未修改 Migration、Flyway Inventory、SHA256SUMS、RC1 验证资产、Investment、Controller 或配置。

## 3. 既有能力复用审计

| 能力 | 复用对象 |
|---|---|
| Directory Port | `RoleDirectoryPort` |
| Directory Resolver | `RoleDirectoryResolver` |
| Directory Provider 门面 | `ApprovalRoleDirectoryProviderFacade`；生产适配器仍非自动注册 Bean |
| Candidate 转换 | `RoleCandidateAdapter`、`RoleCandidatePoolDraftBuilder` |
| Candidate Pool 持久化 | 既有 Task、Assignment Snapshot、Candidate Pool、Candidate Member Repository |
| Node ACTIVE 入口 | `NodeExecutionService.enter`，由 `WorkflowRuntimeApplicationService.freezeRoleInstance` 持久化 |
| 技术门禁 | `RoleDirectoryProductionStartupGate`、`RoleDirectoryTechnicalGateProperties`，S6 另以显式 fail-closed runtime gate 约束调用 |

未创建第二套 Directory Port、DTO、Candidate Pool 或 Hash 校验器。

## 4. 执行链路与事务策略

链路：

`Frozen Instance Binding -> ACTIVE NodeExecution -> RoleDirectoryRuntimeContext -> Technical Gate -> RoleDirectoryResolver -> RoleCandidateAdapter -> PreparedRoleCandidatePool -> transactional Task/Assignment/Pool/Member freeze`

固定规则：

- Directory 查询时点唯一取持久化的 `NodeExecution.activatedTime`，时区按 `Asia/Shanghai` 转换；禁止取系统当前时间。
- 组织范围仅支持 `FIXED_ORG`；有效时间策略仅支持 `NODE_ACTIVATED_AT`。
- 外部 Directory 调用在数据库写事务外完成。
- 写事务开始后按 ID 锁定 NodeExecution，并复核 ID、乐观锁版本、`ACTIVE` 状态和激活时间；漂移即整体阻断。
- 同一 NodeExecution 已有 Task 时，只在 Task、Resolver、Contract、候选用户和冻结证据完全一致时返回原 Pool；部分状态或证据漂移均 fail closed。
- 当前只接受节点恰有一个 ROLE binding；多 ROLE binding 未冻结运行语义，明确阻断。
- USER / DIRECT 路径不访问 Directory。

## 5. 冻结证据与数据库充分性

`ROLE_DIRECTORY_POOL_EVIDENCE_V1` 冻结：roleCode、organizationId、effectiveAt、directoryRevision、directoryResultHash、candidateHash、resolverContractHash 和 candidateCount，并生成独立 evidenceHash。

已有 V2.6.6 结构足够承载本阶段证据：

- `workflow_task.candidate_snapshot` 保存候选池级 canonical evidence；
- `workflow_task_assignment_snapshot` 保存冻结候选用户与审计上下文；
- `workflow_candidate_pool` 保存 resolver/binding/rule/hash 关联；
- `workflow_candidate_pool_member.source_ref_snapshot` 保存 ROLE、组织和 Directory revision；
- `workflow_candidate_pool_member.eligibility_snapshot` 保存候选级冻结证据。

因此 `CANDIDATE_POOL_SCHEMA_SUFFICIENT=YES`，本 Sprint 无需 V2.6.23。Pool 形成后不会因 Directory revision 或成员变化自动刷新。

## 6. 失败与职责边界

- 技术门禁失败发生在 Directory Port 调用前。
- Directory 错误、Contract/Result Hash 漂移、零候选、重复 assignment 证据、非法用户键均不产生任何数据库写入。
- Pool 或 Member 保存失败由单一事务整体回滚。
- 零候选采用 `NO_ELIGIBLE_ROLE_CANDIDATE` 等价 fail-closed 语义，禁止创建空且可 Claim 的 Pool。
- Directory Result Hash 的权威校验继续由 `RoleDirectoryResolver` 及 Provider Adapter canonical contract 负责；桥接层不重算另一套结果 Hash。
- SoD、DataScope 和实时人员资格仍属于未来 Claim/Admission 阶段；Pool 构建期间调用次数均为 0。
- S6 不创建 Claim、不执行 Admission、不执行 Realtime Eligibility、不启用 ROLE Runtime。

## 7. 测试结果

RC2-S6 定向测试共 19 项，19 通过：

- Context/activation time、固定组织、revision/resultHash/candidate freeze；
- 技术门禁、Provider/Bridge 默认不启用、Contract 漂移、Directory Hash 错误、Directory 故障；
- 空候选、重复 assignment、候选上限与无 fallback；
- 多 ROLE binding 阻断、USER/Legacy Directory 零调用；
- Directory 后续漂移不刷新 prepared/persisted evidence；
- Task/Assignment/Pool/Member 原子创建、幂等重试；
- Node activation 漂移、Pool/Member 保存失败整体回滚；
- 事务服务无 Claim/Admission/Realtime Eligibility 依赖。

全量后端回归：

- Java 21 compile：PASS（850 个生产源文件）；
- Spring Boot Context：PASS；
- Backend full tests：675 run，669 pass，0 failure，0 error，6 skipped；
- S3 Golden Hash：PASS；
- S4 Release：PASS；
- S5 Instance Freeze：PASS；
- Legacy USER：PASS；
- Migration SHA：44/44 PASS；
- `git diff --check`：PASS。

S6 未增加 Entity 或数据库字段，`NEW_RC2_S6_MAPPING_DRIFT_COUNT=0`；保留 3 项前置 Mapping Drift，不在本 Sprint 修复。

## 8. 资产与运行状态

- V2.6.21 SHA-256：`cb9555ba5f81c292654286fe8c7396cdead4b71c9387c4b73d09139d4147296e`，未变化。
- V2.6.22 SHA-256：`658b4939a7e92295527ef3a5116d20814b0d17e4c34e46f3955e4c21c67dcc05`，未变化。
- 历史 Migration SHA 变化：NO。
- RC1 验证资产变化：NO（本 Sprint）。
- 数据库修改：NO。
- RC1 TEST 数据库触碰：NO。
- 新 Migration：NO。
- `ROLE_DIRECTORY_V1`：保持 PREPARED / NON_EXECUTABLE。
- `ROLE_RUNTIME`：DISABLED。
- Canary：NOT_AUTHORIZED_NOT_ENABLED。
- Kill Switch：STOP_NEW_AND_CLAIM。
- S4 真实 MySQL 并发债务：OPEN。

## 9. 风险与下一步

- 多 ROLE binding 的合并、优先级和冲突语义尚未冻结，目前正确地 fail closed。
- 本 Sprint 使用 Repository mock/事务契约验证，没有执行真实 MySQL 并发；S4 并发债务继续为 OPEN。
- 生产 Directory Provider 与 Runtime 开关仍关闭，尚不能开展 ROLE Runtime Canary。
- 下一阶段可进入 RC2-S7，但不得据此直接启用 ROLE Runtime 或 Canary。

## 10. 机器可读结论

```text
RC2_S6_FILES_ADDED=9
RC2_S6_FILES_MODIFIED=1
EXISTING_DIRECTORY_PORT=RoleDirectoryPort
EXISTING_DIRECTORY_RESOLVER=RoleDirectoryResolver
EXISTING_DIRECTORY_PROVIDER=ApprovalRoleDirectoryProviderFacade
EXISTING_CANDIDATE_POOL_SERVICE=CandidatePoolApplicationService_AND_EXISTING_REPOSITORIES
EXISTING_NODE_ACTIVE_ENTRY=NodeExecutionService.enter
EXISTING_ROLE_TECHNICAL_GATE=RoleDirectoryProductionStartupGate_AND_RoleDirectoryTechnicalGateProperties
DIRECTORY_RESOLUTION_TIMEPOINT=NODE_ACTIVATED_AT
SUPPORTED_DIRECTORY_ORG_SCOPE=FIXED_ORG
ROLE_DIRECTORY_TECHNICAL_GATE_POLICY=FAIL_CLOSED
NODE_ACTIVATION_CONTRACT_DRIFT_POLICY=LOCK_VERSION_STATUS_ACTIVATED_TIME_RECHECK_FAIL_CLOSED
DIRECTORY_EXTERNAL_CALL_TRANSACTION_POLICY=RESOLVE_OUTSIDE_WRITE_TRANSACTION_THEN_LOCK_AND_REVALIDATE
CANDIDATE_POOL_IDEMPOTENCY_POLICY=RETURN_MATCHING_FROZEN_POOL_OTHERWISE_FAIL_CLOSED
RESULT_HASH_VALIDATION_OWNER=RoleDirectoryResolver_AND_PROVIDER_ADAPTER
ZERO_CANDIDATE_POLICY=FAIL_TRANSACTION_NO_EMPTY_POOL
SOD_EXECUTION_STAGE=CLAIM_ADMISSION_REALTIME_STAGE
MULTI_ROLE_BINDING_RUNTIME_POLICY=FAIL_CLOSED_WHEN_BINDING_COUNT_NOT_ONE
CANDIDATE_POOL_SCHEMA_SUFFICIENT=YES
ROLE_RESOLVER_CONTEXT=PASS
DIRECTORY_RESOLUTION=PASS
DIRECTORY_REVISION_FREEZE=PASS
DIRECTORY_RESULT_HASH_VALIDATION=PASS
CANDIDATE_DRAFT=PASS
CANDIDATE_POOL_FOUNDATION=PASS
CANDIDATE_POOL_IDEMPOTENCY=PASS
ZERO_CANDIDATE_HANDLING=PASS
DUPLICATE_CANDIDATE_REJECTED=YES
DIRECTORY_FAILURE_ATOMICITY=PASS
CANDIDATE_POOL_FAILURE_ATOMICITY=PASS
FAILURE_INJECTION_MATRIX=PASS
DIRECTORY_ACCESSED_AT_INSTANCE_CREATION=NO
REALTIME_ELIGIBILITY_EXECUTED_DURING_POOL_BUILD=NO
CLAIM_EXECUTED=NO
ADMISSION_EXECUTED=NO
LEGACY_USER_DIRECTORY_INVOCATION_COUNT=0
LEGACY_USER_REGRESSION=PASS
NEW_RC2_S6_MAPPING_DRIFT_COUNT=0
PRE_EXISTING_MAPPING_DRIFT_COUNT=3
S3_GOLDEN_HASH_UNCHANGED=YES
S4_RELEASE_REGRESSION=PASS
S5_INSTANCE_FREEZE_REGRESSION=PASS
JAVA21_COMPILE=PASS
TESTS_RUN=675
TESTS_PASS=669
TESTS_FAIL=0
TESTS_ERRORS=0
TESTS_SKIPPED=6
SPRING_CONTEXT=PASS
MIGRATION_SHA_VALIDATION=44/44_PASS
V2621_SHA_UNCHANGED=YES
V2622_SHA_UNCHANGED=YES
HISTORICAL_MIGRATION_SHA_CHANGED=NO
RC1_VALIDATION_ASSETS_CHANGED=NO
GIT_DIFF_CHECK=PASS
S4_REAL_MYSQL_CONCURRENCY_DEBT=OPEN
DATABASE_MODIFIED=NO
RC1_TEST_DATABASE_TOUCHED=NO
NEW_MIGRATION_CREATED=NO
ROLE_RUNTIME=DISABLED
CANARY=NOT_AUTHORIZED_NOT_ENABLED
KILL_SWITCH=STOP_NEW_AND_CLAIM
RC2_S6=PASS
READY_TO_START_RC2_S7=YES
READY_FOR_ROLE_RUNTIME_CANARY_ACTIVATION=NO
```
