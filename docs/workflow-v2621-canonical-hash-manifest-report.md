# Workflow V1 RC2-S3 — Version Resolver Binding Canonical Hash & Manifest Determinism

## 1. 最终结论

RC2-S3 已完成 Version Node Resolver Binding Canonical、Binding Hash、Manifest Canonical、Manifest Hash、Graph+Manifest Combined Content Hash 以及 Legacy Content Hash 兼容契约。所有实现均为无副作用 Domain 计算，不写数据库、不发布 Version，也不进入 Instance 或 ROLE Runtime。

最终状态：`RC2_S3=PASS`，`READY_TO_START_RC2_S4=YES`。

## 2. Hash Architecture Audit

- `EXISTING_GRAPH_HASH_IMPLEMENTATION=WorkflowVersionContentHasher#hashGraph`
- `REUSED_HASH_UTILITY=WorkflowCanonicalHashSupport.sha256`；该工具抽取现有 JDK `MessageDigest`、UTF-8、小写 hex 行为，RC1 Graph Canonical 拼装未改变。
- `REUSED_CANONICAL_PATTERN=length:value|`，沿用现有 Workflow Graph 与 Instance Resolver Binding 的长度前缀边界模式。
- `HASH_ALGORITHM=SHA-256(UTF-8)->lowercase hexadecimal`

现有 Graph Hash 的字段、排序和 NULL/空字符串历史语义全部保持原样。`WorkflowVersionContentHasher` 仅将 digest 委托给共享工具，Golden Test 固定并验证原 RC1 Graph Hash 输出。

RC2 Canonical 新增显式 NULL 表达：`-1:|`；空字符串为 `0:|`。字段按固定位置编码，无 JSON、Map、默认 Charset、Locale、时区、数据库 Collation 或 SQL 返回顺序依赖。数字使用十进制，Enum 使用 `name()`，长度使用 Java `String.length()`，最终字节编码固定 UTF-8。

## 3. Binding Canonical Contract

- `BINDING_CANONICAL_VERSION=VERSION_NODE_RESOLVER_BINDING_V1`
- `BINDING_HASH_IDENTITY_POLICY=SPECIFIC_VERSION_NODE_IDENTITY`

身份策略：

- `definitionId`：included，绑定发布证据必须明确属于哪个 Definition。
- `definitionVersionId`：included，不允许复制 Version 后沿用旧 Version 的绑定证据。
- `nodeId`：included，不允许将相同 Resolver 配置移动到其他 Node 后保持原绑定证据。
- Binding 行主键 `id`：excluded，它只是持久化标识。

Canonical 字段固定为：

1. `bindingSchemaVersion`
2. `definitionId`
3. `definitionVersionId`
4. `nodeId`
5. `bindingOrder`
6. `resolverCode`
7. `resolverVersion`
8. `resolverContractHash`
9. `strategyType`
10. `resolverMode`
11. `targetType`
12. `roleCode`
13. `organizationScopeType`
14. `organizationId`
15. `effectiveTimePolicy`

明确排除：`id`、传入的 `bindingHash`、`createdBy/createdTime`、`updatedBy/updatedTime`、`deleted/deleteToken`、`remark`、乐观锁 `version`。

V2.6.21 当前只允许 `ROLE_DIRECTORY / ROLE_DIRECTORY_V1 / ROLE / CANDIDATE_POOL / ROLE` 和 `NODE_ACTIVATED_AT`。未来枚举值在冻结 Schema 接纳前不会被 Hasher 静默处理，而是在 Domain 构造阶段 Fail Fast。

Golden Binding：

- Canonical：`32:VERSION_NODE_RESOLVER_BINDING_V1|2:10|2:20|2:30|1:1|14:ROLE_DIRECTORY|17:ROLE_DIRECTORY_V1|64:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa|4:ROLE|14:CANDIDATE_POOL|4:ROLE|13:ROLE_APPROVER|9:FIXED_ORG|2:99|17:NODE_ACTIVATED_AT|`
- Hash：`a7d5055376bcb4d39c47d312fd3fa6795b34e9ba37f263e8616416433f8b2949`

## 4. Manifest Canonical Contract

- `MANIFEST_CANONICAL_VERSION=VERSION_RESOLVER_BINDING_MANIFEST_V1`
- `MANIFEST_SORT_ORDER=nodeId ASC,bindingOrder ASC,bindingHash ASC`
- `MANIFEST_HASH_INPUT_MODEL=canonicalVersion + bindingCount + ordered computed binding hashes`

Manifest 不信任 Repository 输入顺序，也不信任 Binding 中已有的派生 `bindingHash`；它从稳定业务字段重新计算各 Binding Hash，再构造 Manifest Canonical。

以下情况 Fail Fast：

- 空 Binding 集合；
- 混合 Definition/Version 归属；
- 重复 `nodeId + bindingOrder`；
- 同一 Node 上仅 `bindingOrder` 不同的重复逻辑 Binding；
- NULL Binding。

不会自动去重、保留第一条或保留最后一条。Legacy 模式不生成空 Manifest，也不使用 `SHA256(empty)`。

Golden Manifest：

- Canonical：`36:VERSION_RESOLVER_BINDING_MANIFEST_V1|1:2|64:a7d5055376bcb4d39c47d312fd3fa6795b34e9ba37f263e8616416433f8b2949|64:85697c22a97898cc704a833ea915d59f422b1d243730ba802c62db7f6f9661cb|`
- Hash：`6dabc2572c7ef25fd81a4384b2817c76d4f5042ce21d95ea188327b85e26f45d`

## 5. Combined Content Hash Contract

- `COMBINED_CONTENT_CANONICAL_VERSION=WORKFLOW_COMBINED_CONTENT_V1`

`VERSION_RESOLVER_BINDING_CAPABLE` 固定输入为：

1. Combined Canonical Version
2. Resolver Binding Model
3. Graph Hash
4. Manifest Hash

Golden Combined：

- Graph Hash：`67c5bf892d3e5d57d3cb94f3c475096aee65aa41aaafda1dfa0dfc168e776567`
- Canonical：`28:WORKFLOW_COMBINED_CONTENT_V1|32:VERSION_RESOLVER_BINDING_CAPABLE|64:67c5bf892d3e5d57d3cb94f3c475096aee65aa41aaafda1dfa0dfc168e776567|64:6dabc2572c7ef25fd81a4384b2817c76d4f5042ce21d95ea188327b85e26f45d|`
- Content Hash：`d8b7f7b58e38a7776fbc8af3dec5e7c620227d59027ccb00d742989a5b96242b`

`LEGACY_USER_ONLY` 直接返回 Graph Hash，不增加 Canonical 包装、不再次 SHA-256，也不允许 Manifest。历史 Version 不会被重算为 RC2 Combined Hash。

## 6. 实现文件

### 新增主代码（7）

- `WorkflowCanonicalHashSupport`
- `VersionNodeResolverBindingCanonical`
- `VersionNodeResolverBindingComputation`
- `ResolverBindingManifestCanonical`
- `ResolverBindingManifestComputation`
- `WorkflowCombinedContentHasher`
- `WorkflowCombinedContentComputation`

### 新增测试代码（9）

- `VersionResolverBindingCanonicalFixtures`
- `VersionNodeResolverBindingCanonicalTest`
- `VersionNodeResolverBindingHashTest`
- `ResolverBindingManifestCanonicalTest`
- `ResolverBindingManifestDeterminismTest`
- `ResolverContractHashDriftTest`
- `WorkflowCombinedContentHasherTest`
- `LegacyWorkflowContentHashRegressionTest`
- `WorkflowCanonicalHashSupportTest`

本报告为第 17 个新增文件。

### 修改文件（2）

- `WorkflowVersionContentHasher.java`：只复用共享 UTF-8 SHA-256 digest，Graph Canonical 不变。
- `VersionResolverBindingDomainTest.java`：扩展 Domain 纯净性检查。

## 7. 测试矩阵

- Golden Canonical/Hash：PASS，Expected 为硬编码固定值，不是自证循环。
- Binding Identity Drift：definition/version/node/order 变化均改变 Hash。
- Binding Semantic Drift：role、organization scope/id、Resolver Contract 变化均改变 Hash。
- 当前 Schema 不支持的 resolver code/version/mode/strategy/target/schema 变化：Domain Fail Fast。
- Non-semantic Drift：Binding 行 ID、派生 Hash、乐观锁版本变化不改变 Hash；审计/删除/remark 字段不进入 Domain Canonical。
- Manifest Determinism：不同输入排列得到完全相同 Canonical/Hash。
- Manifest Membership/Order Drift：bindingOrder 或成员集合变化改变 Hash。
- Duplicate Binding：重复 slot 和重复逻辑 Binding 均拒绝。
- Resolver Contract Drift：Binding、Manifest、Combined 三层 Hash 均发生变化。
- Combined Drift：Graph、Manifest、Model 任一变化均不碰撞。
- Legacy Graph Golden：精确保持 `67c5bf892d3e5d57d3cb94f3c475096aee65aa41aaafda1dfa0dfc168e776567`。
- Hash 格式：NULL、空、错误长度、非小写 hex 均 Fail Fast；Draft 未计算值继续使用 NULL，而非空字符串。

## 8. 验证结果

- Java：21.0.12。
- Maven：3.9.9。
- Java 21 compile：PASS，841 个主源码文件。
- RC2-S3/S2 定向测试：30 项，0 失败、0 错误、0 跳过。
- Backend 全量测试：622 项，616 通过、0 失败、0 错误、6 跳过。
- Spring Boot Context：PASS。
- Legacy USER、Resolver、USER Assignment、Instance Binding：PASS。
- S2 Domain/Persistence/Mapping 测试继续 PASS。
- `NEW_RC2_S3_MAPPING_DRIFT_COUNT=0`。
- `PRE_EXISTING_MAPPING_DRIFT_COUNT=3`，仍为三个 Realtime Eligibility Evidence `remark` 旧漂移；本 Sprint 未修改。

## 9. Migration、Git 与运行安全

- V2.6.21 SHA-256：`cb9555ba5f81c292654286fe8c7396cdead4b71c9387c4b73d09139d4147296e`，无变化。
- Migration SHA：43/43 PASS。
- `HISTORICAL_MIGRATION_SHA_CHANGED=NO`。
- `RC1_VALIDATION_ASSETS_CHANGED=NO`。
- `git diff --check=PASS`。
- 未执行 Flyway、DDL、DML、Fixture 或真实数据库连接。
- 未触碰 `D:\mysql-rc1\data`。
- 未 commit、tag 或 push。
- `PUBLISH_INTEGRATION_IMPLEMENTED=NO`
- `INSTANCE_FREEZE_IMPLEMENTED=NO`
- `DIRECTORY_RUNTIME_BRIDGE_IMPLEMENTED=NO`
- `ROLE_RUNTIME=DISABLED`
- `CANARY=NOT_AUTHORIZED_NOT_ENABLED`
- `KILL_SWITCH=STOP_NEW_AND_CLAIM`

## 10. 工作树阶段边界

- RC2-S1：V2.6.21 Schema、Inventory/SHA/README、Migration Contract 与候选报告。
- RC2-S1.1：真实 MySQL 验收脚本、验收报告及资产晋级记录。
- RC2-S2：Version Binding Domain/Persistence、Version/Release 映射与测试。
- RC2-S3：本报告所列 17 个新增文件及 2 个修改文件。

## 11. 下一步边界

RC2-S4 可调用本 Sprint 的纯计算结果，在单一发布事务中写入 Binding Hash、Manifest、Version/Release Snapshot 与 Combined Content Hash。S4 必须继续保持 Hash Contract 不变，并单独验证发布事务回滚、并发与真实 MySQL 约束；不得借发布集成直接启用 ROLE Runtime 或 Canary。
