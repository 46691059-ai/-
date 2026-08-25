# Workflow V1 RC2-S2 — Version Resolver Binding Domain & Persistence 实施报告

## 1. 结论

RC2-S2 已完成 V2.6.21 对应的 Version Resolver Binding 领域与持久化基础。实现范围仅包含 Domain、Entity、Mapper、Repository、Version/Release 映射、查询能力与契约测试；未实现 Canonical Hash、发布事务、Instance Freeze、Directory Runtime Bridge、Candidate Pool、ROLE Runtime 或 Canary。

最终状态：`RC2_S2=PASS`，`READY_TO_START_RC2_S3=YES`。

## 2. 修改文件清单

### 2.1 新增文件（17）

- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/model/ResolverBindingModel.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/binding/OrganizationScopeType.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/binding/EffectiveTimePolicy.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/binding/VersionNodeResolverBinding.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/binding/ResolverBindingManifest.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/repository/VersionNodeResolverBindingRepository.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/repository/ResolverBindingManifestRepository.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/entity/WorkflowVersionNodeResolverBindingEntity.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/entity/WorkflowVersionResolverBindingManifestEntity.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/mapper/WorkflowVersionNodeResolverBindingMapper.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/mapper/WorkflowVersionResolverBindingManifestMapper.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/WorkflowVersionResolverBindingEntityMapper.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/VersionNodeResolverBindingRepositoryImpl.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/ResolverBindingManifestRepositoryImpl.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/domain/VersionResolverBindingDomainTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/infrastructure/WorkflowVersionResolverBindingRepositoryTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/infrastructure/WorkflowVersionV2621MappingTest.java`

本报告为第 18 个新增文件。

### 2.2 修改文件（11）

- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/model/WorkflowVersion.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/model/WorkflowVersionRelease.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/repository/WorkflowVersionReleaseRepository.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/WorkflowEntityMapper.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/WorkflowVersionReleaseRepositoryImpl.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/entity/WorkflowVersionEntity.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/entity/WorkflowVersionReleaseEntity.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/persistence/mapper/WorkflowVersionReleaseMapper.java`
- `backend/src/test/java/cn/gov/enterprise/config/DatabaseMappingCheckerTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/infrastructure/WorkflowEntityMappingTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/infrastructure/WorkflowVersionReleaseRepositoryTest.java`

V2.6.21 SQL、历史 Migration、RC1 验证脚本和 RC1 文档均未由 RC2-S2 修改。

## 3. 类型复用与边界

`REUSED_TYPES`：

- `ResolverCode`
- `ResolverVersion`
- `ResolverContractHash`
- `ResolverMode`
- `AssignmentStrategy.Type`
- 现有 Workflow 审计基类、Entity 和 Mapper 约定

`NEW_TYPES`：

- `ResolverBindingModel`
- `OrganizationScopeType`
- `EffectiveTimePolicy`
- `VersionNodeResolverBinding`
- `ResolverBindingManifest`
- 两个 Repository Port 及对应 Persistence Adapter

`INTENTIONALLY_NOT_REUSED_TYPES`：

- `RoleResolverBindingProposal`：属于 ROLE Preview/Proposal，不是 Version 配置事实。
- V2.6.5 `NodeResolverBinding` 与 Instance Binding Repository：属于已冻结运行实例事实，不是 Version 定义配置。
- ROLE Runtime Binding Snapshot 类型：属于受控 Runtime Promotion/Persistence 边界，不应反向承担 Version Binding 配置职责。

## 4. PLAN_SCHEMA_DELTA

- 冻结 V2.6.21 以 `resolver_binding_model` 区分 Legacy 与 Version Resolver Binding 能力，不使用计划阶段可能出现的可选节点 assignment type 方案。
- `organization_scope_type` 同时支持 `FIXED_ORG` 与 `INSTANCE_BUSINESS_ORG`；只有 `FIXED_ORG` 必须携带 `organization_id`。
- Manifest 是发布证据，Java Repository 只暴露 `append` 和查询；本 Sprint 不接入发布事务。
- `approval_role` 仅通过稳定业务键 `role_code` 表达，不引入跨限界上下文外键。
- Resolver、Contract、Binding、Manifest 标识按冻结 SQL 的大小写敏感/稳定字符语义映射；Java 层不发明 Hash 算法。

## 5. Domain 模型与约束

- `ResolverBindingModel` 仅支持 `LEGACY_USER_ONLY`、`VERSION_RESOLVER_BINDING_CAPABLE`。
- `VersionNodeResolverBinding` 完整承载 definition/version/node、顺序、Resolver 契约、策略、模式、目标、角色、组织范围、有效时间策略、Schema 版本和 Binding Hash。
- 单对象约束覆盖正数 ID、`bindingOrder >= 1`、ROLE 固定契约、Role Code 格式、组织范围组合、固定 Canonical 版本和小写 64 位 SHA-256 格式。
- `ResolverBindingManifest` 校验 Version 归属、绑定数量、Canonical 版本、Manifest Hash 格式与生成时间。
- Domain 不依赖 Spring、MyBatis 或 Persistence Entity。
- `WorkflowVersion` 与 `WorkflowVersionRelease` 均表达新增四项绑定事实；Legacy 构造路径默认 `LEGACY_USER_ONLY / count=0 / hash=null`。

## 6. Persistence 与查询

- 两个新增 Entity 完整映射 V2.6.21 普通列；审计字段、`remark`、`deleted`、`delete_token`、`version` 复用现有基类约定。
- Binding Repository 支持 DRAFT 阶段新增、CAS 修改、逻辑删除以及按 ID、Version、Version+Node 查询。
- Binding 查询显式使用稳定顺序 `ORDER BY node_id, binding_order`，不依赖数据库默认顺序。
- Manifest Repository 只支持 append/read，不提供 update/delete/saveOrUpdate/upsert。
- 新 Mapper 使用显式列清单，未使用 `SELECT *`、`INSERT IGNORE`、`REPLACE` 或 upsert。
- Version 与 Release 四个新增字段完成双向映射；Release Repository 读取发布历史快照，不回读当前 Version 覆盖历史事实。

## 7. Mapping Drift

- V2.6.21 两个新增 Entity：`ENTITY_MAPPING_DRIFT=0`。
- `WorkflowVersion` 新增四列：`MAPPING_DRIFT=0`。
- `WorkflowVersionRelease` 新增四列：`MAPPING_DRIFT=0`。
- `NEW_RC2_S2_MAPPING_DRIFT_COUNT=0`。
- `PRE_EXISTING_MAPPING_DRIFT_COUNT=3`：已知三个 Realtime Eligibility Evidence `remark` 漂移，属于 V2.6.23；Investment DB-superset 漂移仍为既有、独立边界，不计入本 Sprint Workflow 新增漂移。

## 8. 测试结果

- Java：21.0.12。
- Maven：3.9.9。
- Java 21 compile：PASS，编译 834 个主源码文件。
- RC2-S2 定向测试：22 项，0 失败，0 错误，0 跳过。
- Backend 全量测试：606 项，0 失败，0 错误，6 跳过（600 项通过）。
- Spring Boot Context：PASS。
- DatabaseMappingChecker 合同：PASS。
- Legacy USER、USER Assignment、Resolver、Instance Binding 回归：PASS。
- Manifest Repository 无修改语义、Binding 稳定排序和 Legacy 默认读取均由测试覆盖。
- 本 Sprint 未连接 MySQL/Redis；Repository 的真实 MySQL 行为需在后续专门回归中验证：`REQUIRES_LATER_REAL_MYSQL_REGRESSION=YES`。

## 9. Git 与 Migration 完整性

- `git diff --check`：PASS。
- RC2-S2 新增文件额外 trailing whitespace 扫描：17/17 PASS。
- V2.6.21 SHA-256：`cb9555ba5f81c292654286fe8c7396cdead4b71c9387c4b73d09139d4147296e`，无变化。
- Migration SHA：43/43 PASS。
- `HISTORICAL_MIGRATION_SHA_CHANGED=NO`。
- 未执行 Flyway、DDL、DML；未触碰 `D:\mysql-rc1\data`。
- 未 commit、tag 或 push。

## 10. 未实现范围与运行安全

- `CANONICAL_HASH_IMPLEMENTED=NO`
- `PUBLISH_INTEGRATION_IMPLEMENTED=NO`
- `INSTANCE_FREEZE_IMPLEMENTED=NO`
- `DIRECTORY_RUNTIME_BRIDGE_IMPLEMENTED=NO`
- `ROLE_RUNTIME=DISABLED`
- `CANARY=NOT_AUTHORIZED_NOT_ENABLED`
- `KILL_SWITCH=STOP_NEW_AND_CLAIM`

## 11. 下一步建议

RC2-S3 可在本次 Domain/Persistence 契约上实现 Canonical Binding Hash、Manifest Hash 与 Combined Content Hash。应继续保持发布事务、实例冻结及 Runtime Bridge 分阶段接入，并在首次真实 Repository 回归中验证显式列映射、稳定排序、唯一约束和 CAS 行为。
