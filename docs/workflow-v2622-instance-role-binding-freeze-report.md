# Workflow V1 RC2-S5 Instance ROLE Resolver Binding Freeze Report

## 1. 最终结论

RC2-S5 已完成 V2.6.22 候选 Migration、Version Binding 到 Instance Resolver Binding Set/Node Snapshot 的冻结转换，以及 Workflow Instance 启动事务集成。ROLE 分支只创建 Instance、Resolver 冻结证据和首个 NodeExecution，不创建 Task、Assignment Snapshot、Candidate Pool、Claim 或 Admission，也不访问 Role Directory。

V2.6.22 未执行 Flyway 或真实 MySQL，资产保持 `CANDIDATE / NOT_EXECUTED`。ROLE Runtime 与 Canary 保持关闭。

## 2. Preflight真实差异

- V2.6.5 已建立 `workflow_instance_resolver_binding_set`、`workflow_instance_resolver_binding`、`workflow_node_resolver_binding_snapshot`。
- V2.6.6 已前向允许 Instance Resolver 的 `ROLE + CANDIDATE_POOL` 和 Node Snapshot 的 `ROLE + ROLE + CANDIDATE_POOL`；因此 V2.6.22 不重复替换这些 CHECK。
- 原 Node Snapshot 唯一键为 `(instance_id,node_id,delete_token)`，不能表达同一节点多个 Version Binding。
- 原 Node Snapshot 没有 Version Binding 来源、Binding Order/Hash、Role、组织范围、解析后组织、Effective Time、Binding Schema 或独立 Contract Hash 字段。
- 原 Binding Set `manifest_version VARCHAR(32)` 无法保存 `VERSION_RESOLVER_BINDING_MANIFEST_V1`，V2.6.22 扩展至 64。
- 原 Legacy 线性启动固定冻结 `EXPLICIT_USER_V1 + USER + DIRECT`，随后立即生成 Assignment/Candidate/Task。
- Workflow Instance 没有独立、权威的 business organization 字段，因此 `INSTANCE_BUSINESS_ORG` 无法可靠解析，本 Sprint Fail Closed。

## 3. Migration变化

V2.6.22：`V2.6.22__extend_workflow_instance_role_resolver_binding.sql`

- 新表：0。
- 修改表：2（Binding Set、Node Resolver Snapshot）。
- 新增字段：Version Binding ID、Order、生成式 Slot、Binding Hash、Resolver Contract Hash Snapshot、Role Code、Organization Scope、Resolved Organization ID、Effective Time Policy、Binding Schema Version。
- 多 Binding：唯一槽位改为 `(instance_id,node_id,version_binding_slot,delete_token)`；Legacy 使用 Slot 0，ROLE 使用 Version `binding_order`。
- 来源约束：新增 Version Binding 复合 FK，确保 source Binding、Version 和 Node 同属。
- 组合约束：保留 Legacy USER+USER+DIRECT；保留历史 pre-V2.6.22 ROLE Snapshot 读取兼容；新 RC2 ROLE Snapshot 必须完整满足 ROLE+ROLE+CANDIDATE_POOL、FIXED_ORG 和全部 Hash/Schema 条件。
- 不可变：新增 Node Snapshot `BEFORE UPDATE`、`BEFORE DELETE` Trigger。
- 明确未新增 `directory_revision`、Directory Result Hash 或候选用户数据。

## 4. 冻结链路

`WorkflowRuntimeApplicationService.startWorkflow` 严格按 Version 的 `resolver_binding_model` 分流：

- `LEGACY_USER_ONLY`：完全保留既有 Explicit User 冻结、Candidate Resolution、Assignment Snapshot 和 Task 创建链路。
- `VERSION_RESOLVER_BINDING_CAPABLE`：进入 `VersionResolverBindingInstanceFreezer`，不读取旧 Node Assignment 字段作为 fallback。

RC2 冻结顺序：

1. 验证 Version 为 PUBLISHED 且绑定模型完整；
2. 查询唯一 Manifest 与 Release；
3. 校验 Version/Manifest/Release 的 count、hash、canonical version 和 content hash；
4. 加载全部 Version Bindings并执行 required-node coverage；
5. 复用 S3 Canonical 重新计算并校验每条 Binding Hash 与 Manifest Hash；
6. 使用 S3 Combined Hasher 校验 Graph+Manifest content hash；
7. 元数据方式读取 Resolver Descriptor，再次校验 Contract、strategy、mode、status；PREPARED Descriptor 不执行 Resolver；
8. 仅将 FIXED_ORG 的 organizationId 冻结为 resolvedOrganizationId；
9. 按 `nodeId ASC,bindingOrder ASC,bindingHash ASC` 冻结全部 Resolver/Node Snapshot；
10. 同一 Spring 事务内保存 Instance、Binding Set、Resolver Binding、Node Snapshot、首个 NodeExecution，并更新 Instance 当前执行指针。

Directory Revision 的解析时点仍保留为未来 Node ACTIVE/Candidate Pool 阶段。本 Sprint 没有 Directory Port 或 Provider 调用。

## 5. 原子性和负向治理

Failure Injection 覆盖：Instance 保存、Binding Set 保存、Resolver Binding 保存、Node Snapshot 保存、Initial NodeExecution 保存。五个失败点均触发外层 `@Transactional` 回滚。

负向测试覆盖：Manifest 缺失、Manifest count/hash 不一致、required node Binding 缺失、stored Binding Hash 漂移、Resolver Contract 漂移、非 PUBLISHED Version、Unsupported Org Scope、非法 Domain 组合、重复 binding order canonical 拒绝、Migration CHECK/Trigger 合同。

ROLE Happy Path 证明两个人工审批节点及同节点多 Binding 全部冻结，Role、resolved organization、Version Binding Hash、Contract Hash 和 Binding Order 保持一致；Task 与 Assignment Snapshot 保存次数均为 0。

## 6. 测试与资产

- Java 21 compile：PASS。
- 后端全量：656 run，650 passed，0 failures，0 errors，6 skipped。
- Spring Context：PASS。
- S2 Persistence、S3 Golden Hash、S4 Release、Legacy USER 全量回归：PASS。
- Mapping：V2.6.22 新增漂移 0；既有 Realtime Eligibility Evidence `remark` 漂移 3 保留。
- Migration SHA：44/44 PASS。
- V2.6.21 SHA 保持 `cb9555ba5f81c292654286fe8c7396cdead4b71c9387c4b73d09139d4147296e`。
- 未修改 RC1 验收脚本的 2.6.20 历史基准。
- 未执行数据库、Flyway、DDL、DML、Fixture、commit、tag 或 push。

## 7. 风险和下一步

- V2.6.22 的真实 MySQL CHECK、生成列、复合 FK、Trigger、Fresh/Upgrade Schema fingerprint 尚需 RC2-S5.1 隔离验收。
- 历史 pre-V2.6.22 ROLE Snapshot 允许读取但不会被新冻结链路生成；新 RC2 ROLE Snapshot 始终要求完整 Version 来源证据。
- ROLE Instance 目前只冻结到 NodeExecution，不产生审批任务；Candidate Pool、Directory Revision、实时资格和 Task/Claim 属于 S6 及后续边界。
- S4 双 Session 真实 MySQL 并发发布债务仍为 OPEN。

## 8. 机器可读状态

```text
V2622_MIGRATION=V2.6.22__extend_workflow_instance_role_resolver_binding.sql
V2622_SHA256=658b4939a7e92295527ef3a5116d20814b0d17e4c34e46f3955e4c21c67dcc05

NEW_TABLE_COUNT=0
ALTERED_TABLE_COUNT=2
NEW_TRIGGER_COUNT=2
NEW_CONSTRAINT_COUNT=4

SUPPORTED_INSTANCE_FREEZE_ORG_SCOPES=FIXED_ORG

LEGACY_USER_INSTANCE_FREEZE_REGRESSION=PASS
RC2_ROLE_INSTANCE_FREEZE=PASS
VERSION_MANIFEST_INTEGRITY=PASS
VERSION_BINDING_HASH_INTEGRITY=PASS
INSTANCE_CREATION_CONTRACT_DRIFT_POLICY=FAIL_CLOSED

MULTI_BINDING_FREEZE=PASS
FIXED_ORG_FREEZE=PASS
UNSUPPORTED_ORG_SCOPE_FAIL_CLOSED=PASS

DIRECTORY_ACCESSED_DURING_INSTANCE_FREEZE=NO
CANDIDATE_POOL_CREATED_DURING_INSTANCE_FREEZE=NO
ROLE_RUNTIME_EXECUTION_TRIGGERED=NO

INSTANCE_CREATION_ATOMICITY=PASS
FAILURE_INJECTION_MATRIX=PASS
NEGATIVE_MATRIX=PASS

NEW_RC2_S5_MAPPING_DRIFT_COUNT=0
PRE_EXISTING_MAPPING_DRIFT_COUNT=3

S3_GOLDEN_HASH_UNCHANGED=YES
S4_RELEASE_REGRESSION=PASS
LEGACY_USER_REGRESSION=PASS

JAVA21_COMPILE=PASS
TESTS_RUN=656
TESTS_PASS=650
TESTS_FAIL=0
TESTS_ERRORS=0
TESTS_SKIPPED=6
SPRING_CONTEXT=PASS

MIGRATION_SHA_VALIDATION=44/44_PASS
V2621_SHA_UNCHANGED=YES
HISTORICAL_MIGRATION_SHA_CHANGED=NO
RC1_VALIDATION_ASSETS_CHANGED=NO
GIT_DIFF_CHECK=PASS

V2622_STATUS=CANDIDATE_NOT_EXECUTED
REQUIRES_REAL_MYSQL_VALIDATION=YES

S4_REAL_MYSQL_CONCURRENCY_DEBT=OPEN

DATABASE_MODIFIED=NO
RC1_TEST_DATABASE_TOUCHED=NO

DIRECTORY_RUNTIME_BRIDGE_IMPLEMENTED=NO

ROLE_RUNTIME=DISABLED
CANARY=NOT_AUTHORIZED_NOT_ENABLED
KILL_SWITCH=STOP_NEW_AND_CLAIM

RC2_S5=PASS

READY_FOR_RC2_S5_1_REAL_MYSQL_VALIDATION=YES
READY_TO_START_RC2_S6=NO
READY_FOR_ROLE_RUNTIME_CANARY_ACTIVATION=NO
```
