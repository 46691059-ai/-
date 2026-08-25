# Workflow V1 RC2-S4 Version Resolver Binding Release Integration Report

## 1. 结论与边界

RC2-S4 在既有 `WorkflowDefinitionApplicationService.publishVersion` 发布链路内完成 Version Resolver Binding 发布集成，没有建立平行发布服务。实现覆盖 Legacy 与 RC2 两种发布模式、Binding 覆盖与 Contract 校验、Manifest/Combined Hash 冻结、Version/Release 快照、旧版本退役、Definition 指针切换、事务回滚及并发保护契约。

本 Sprint 未执行 Flyway、DDL、DML、Fixture 或真实 MySQL；未连接 RC1 TEST 数据库；未创建 V2.6.22；未实现 Instance Freeze、Directory Runtime Bridge、Candidate Pool、Task、Claim、ROLE Runtime 或 Canary。

## 2. 修改文件清单

RC2-S4 新增：

- `backend/src/main/java/cn/gov/enterprise/modules/workflow/application/service/VersionResolverBindingApplicationService.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/service/ResolverBindingCoveragePolicy.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/application/VersionResolverBindingApplicationServiceTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/application/WorkflowVersionResolverBindingReleaseIntegrationTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/domain/ResolverBindingCoveragePolicyTest.java`
- `docs/workflow-v2621-version-release-integration-report.md`

RC2-S4 修改：

- `backend/src/main/java/cn/gov/enterprise/modules/workflow/application/service/WorkflowDefinitionApplicationService.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/binding/VersionNodeResolverBinding.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/model/WorkflowVersion.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/repository/WorkflowVersionRepository.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/WorkflowVersionRepositoryImpl.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/mapper/WorkflowVersionMapper.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/application/WorkflowTransactionContractTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/infrastructure/WorkflowVersionV2621MappingTest.java`

工作区中其余未提交 RC2-S1/S2/S3 资产均予保留，本 Sprint 未清理、提交或改写其历史边界。

## 3. 现有发布链路审计

- 发布入口：`POST /workflow/definitions/{definitionId}/versions/{versionId}/publish`
- Controller：`WorkflowDefinitionController.publishVersion`
- Application Service：`WorkflowDefinitionApplicationService.publishVersion`
- 事务边界：发布 Application Service 方法上的 Spring `@Transactional`
- Definition 锁：`WorkflowDefinitionMapper.findByIdForUpdate` 的 `SELECT ... FOR UPDATE`
- Version 锁：`WorkflowVersionMapper.findByIdForUpdate` 的 `SELECT ... FOR UPDATE`
- Graph 校验：`WorkflowLinearGraphValidator`；Legacy 单节点规则由既有发布校验保持
- Graph Hash：`WorkflowVersionContentHasher.hashGraph/hash`
- Release 持久化：`WorkflowVersionReleaseRepository.save`
- 旧版本退役：持锁后通过 `WorkflowVersion.retire` 与乐观版本 CAS 更新
- 并发保护：Definition 行锁优先、Version 行锁、Definition/Version expected-version CAS
- 异常模型：复用 `BusinessException` 与既有 B25xx/B26xx 错误码

## 4. 发布模式与覆盖策略

`LEGACY_USER_ONLY` 不读取 Binding、不生成 Manifest，`bindingCount=0`、`manifestHash=null`，`contentHash` 继续使用既有 Graph/Node Hash，避免 RC1 Hash 回归。

`VERSION_RESOLVER_BINDING_CAPABLE` 执行完整绑定发布链。`ResolverBindingCoveragePolicy` 的冻结规则为：仅启用的 `APPROVAL` 和 `COUNTERSIGN` 节点要求至少一个 Binding；`CONDITION` 及当前其他非人工节点不要求 Binding。校验比较 required node set 与 bound node set，因此允许同一节点多个合法 Binding，同时拒绝缺失、孤儿、跨 Definition、跨 Version 和不存在节点的 Binding。

每条 Binding 在构造时执行完整 Domain Contract 校验；发布时再读取 Resolver Descriptor，验证 code/version 存在、status 为 `PREPARED` 或 `ACTIVE`、strategy/mode 一致、Contract Hash 一致。Contract 漂移一律 Fail Closed，不自动改写 Draft Binding。

## 5. 原子发布链路

事务内固定执行：

1. 锁定 Definition；
2. 锁定目标 Draft Version；
3. 校验 expected version、权限、Version 状态；
4. 加载并校验 Graph；
5. RC2 模式加载 Binding，执行覆盖、Domain 与 Resolver Contract 校验；
6. 使用 `ResolverBindingManifestCanonical` 计算 Binding Hash 与 Manifest；
7. 持久化 Draft Binding 的服务端计算 Hash；
8. 使用 `WorkflowVersionContentHasher` 计算 Graph Hash；
9. 使用 `WorkflowCombinedContentHasher` 计算 Combined Content Hash；
10. 锁定并校验旧 Published Version（如有）；
11. 追加 Immutable Manifest；
12. 发布目标 Version；
13. 退役旧 Version；
14. 切换 Definition `currentVersionId`；
15. 追加 `workflow_version_release`；
16. 提交事务。

V2.6.21 的数据库 Trigger 要求 Manifest 写入前，所属 Version 仍为 DRAFT 且 Manifest 快照字段已准备完成。因此步骤 7 后执行一次 DRAFT-only CAS 快照准备，再追加 Manifest。该中间更新与后续步骤处于同一事务，不会对外形成半状态；任一步失败均回滚。Hash 仅由 S3 冻结组件计算，Repository 不重新计算，发布服务中没有 `MessageDigest` 或自建 SHA 实现。

## 6. 不可变与一致性

- `VersionResolverBindingApplicationService` 仅允许 DRAFT Version 的 add/update/delete；PUBLISHED 与 RETIRED 在 Application 层 Fail Fast。
- Manifest Repository 仅暴露 `append/find`，没有 update/delete Application 路径；数据库不可变 Trigger 继续作为第二道防线。
- Published Version 与 Release 同时冻结 resolver binding model、manifest hash、binding count、canonical version 和 content hash。
- 同一 Version 重复发布因非 DRAFT 状态被拒绝，不生成第二条 Manifest 或 Release。
- 旧 Published Version 在新版本成功发布的同一事务内转为 RETIRED；首次发布路径不执行退役。

## 7. 测试结果

覆盖内容：

- Legacy 发布与历史 Hash 回归；
- 两个人工审批节点的 RC2 Happy Path；
- APPROVAL/COUNTERSIGN/CONDITION 节点覆盖策略；
- 缺失、孤儿、跨 Definition、跨 Version、一个节点多 Binding；
- Resolver 不存在、Descriptor 不兼容与 Contract 漂移；
- Version/Release/Manifest 快照一致性；
- Published/Retired Binding add/update/delete 拒绝；
- 重复发布拒绝；
- 首次失败、事务回滚、修正后重试成功；
- Manifest append、目标发布、旧版本退役、Definition 切换、Release append 五个失败注入点全部回滚；
- Definition 锁优先与陈旧 expected-version 并发发布拒绝；
- S2 persistence、S3 canonical/golden hash、mapping、Spring Context 与全量后端回归。

结果：Java 21 compile PASS；后端 639 tests run，633 passed，0 failures，0 errors，6 skipped；Spring Context PASS。H2/Mockito 契约测试不能等价证明 MySQL 多会话锁行为，真实 MySQL 并发发布验证按边界延期至 RC2-S10 或独立验收任务。

Mapping 结论：RC2-S4 新增映射漂移 0；保留 3 个既有 Realtime Eligibility Evidence `remark` 漂移，本 Sprint 不处理。

## 8. Migration与Git治理

- Migration SHA 清单：43/43 PASS。
- V2.6.21 SHA-256：`cb9555ba5f81c292654286fe8c7396cdead4b71c9387c4b73d09139d4147296e`，未变化。
- 未创建 V2.6.22。
- 未修改历史 Migration 内容。
- `git diff --check`：PASS。
- 未 commit、tag 或 push。

## 9. 风险与下一步

- 同一 Definition 的真正双 Session 并发发布仍需在隔离真实 MySQL 上验证唯一成功版本、行锁等待和失败事务无孤儿证据。
- V2.6.21 的 DRAFT 快照准备属于数据库 Trigger 约束驱动的事务内步骤；后续数据库验收应专门验证失败时无可见残留。
- ROLE Resolver 仍为非运行边界；本 Sprint 的 PREPARED Descriptor 只允许发布期 Contract 校验，不代表 Runtime 可执行。
- RC2-S5 可在本发布快照基础上设计 Instance Freeze，但不得由本 Sprint 自动进入。

## 10. 机器可读状态

```text
RC2_S4_FILES_ADDED=6
RC2_S4_FILES_MODIFIED=8

EXISTING_PUBLISH_ENTRY=WorkflowDefinitionController.publishVersion -> WorkflowDefinitionApplicationService.publishVersion
EXISTING_TRANSACTION_BOUNDARY=Spring_@Transactional_ON_EXISTING_APPLICATION_SERVICE
EXISTING_DEFINITION_LOCK=WorkflowDefinitionMapper.SELECT_FOR_UPDATE
EXISTING_VERSION_LOCK=WorkflowVersionMapper.SELECT_FOR_UPDATE
EXISTING_GRAPH_VALIDATION=WorkflowLinearGraphValidator_AND_LEGACY_SINGLE_NODE_VALIDATION
EXISTING_RELEASE_PERSISTENCE=WorkflowVersionReleaseRepository.save

RESOLVER_BINDING_REQUIRED_NODE_POLICY=ENABLED_APPROVAL_OR_COUNTERSIGN
RESOLVER_CONTRACT_DRIFT_POLICY=FAIL_CLOSED

LEGACY_RELEASE_INTEGRATION=PASS
RC2_RELEASE_INTEGRATION=PASS
BINDING_COVERAGE_VALIDATION=PASS
RESOLVER_CONTRACT_VALIDATION=PASS

MANIFEST_PERSISTENCE=PASS
VERSION_SNAPSHOT=PASS
RELEASE_SNAPSHOT=PASS
COMBINED_CONTENT_HASH_INTEGRATION=PASS

PUBLISHED_BINDING_IMMUTABILITY=PASS
MANIFEST_IMMUTABILITY=PASS

ATOMIC_PUBLISH_TRANSACTION=PASS
FAILURE_INJECTION_MATRIX=PASS
CONCURRENT_PUBLISH_PROTECTION=PASS
REAL_MYSQL_CONCURRENT_PUBLISH_VALIDATION_REQUIRED=YES

DUPLICATE_PUBLISH_REJECTED=YES
FAILED_DRAFT_RETRYABLE=YES
PREVIOUS_VERSION_RETIREMENT=PASS
DEFINITION_CURRENT_VERSION_SWITCH=PASS

S3_GOLDEN_HASH_UNCHANGED=YES
LEGACY_CONTENT_HASH_REGRESSION=PASS
LEGACY_USER_REGRESSION=PASS

NEW_RC2_S4_MAPPING_DRIFT_COUNT=0
PRE_EXISTING_MAPPING_DRIFT_COUNT=3

JAVA21_COMPILE=PASS
TESTS_RUN=639
TESTS_PASS=633
TESTS_FAIL=0
TESTS_ERRORS=0
TESTS_SKIPPED=6
SPRING_CONTEXT=PASS

MIGRATION_SHA_VALIDATION=43/43_PASS
V2621_SHA_UNCHANGED=YES
HISTORICAL_MIGRATION_SHA_CHANGED=NO
RC1_VALIDATION_ASSETS_CHANGED=NO
GIT_DIFF_CHECK=PASS

DATABASE_MODIFIED=NO
RC1_TEST_DATABASE_TOUCHED=NO
REAL_MYSQL_VALIDATION_DEFERRED=YES

INSTANCE_FREEZE_IMPLEMENTED=NO
DIRECTORY_RUNTIME_BRIDGE_IMPLEMENTED=NO

ROLE_RUNTIME=DISABLED
CANARY=NOT_AUTHORIZED_NOT_ENABLED
KILL_SWITCH=STOP_NEW_AND_CLAIM

RC2_S4=PASS

READY_TO_START_RC2_S5=YES
READY_FOR_ROLE_RUNTIME_CANARY_ACTIVATION=NO
```
