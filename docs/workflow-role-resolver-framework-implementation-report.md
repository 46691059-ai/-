# Workflow ROLE Resolver Framework 实施报告

Sprint：2-3.7-WF5.1
状态：`ROLE_RESOLVER_FRAMEWORK_READY / ROLE_RUNTIME_DISABLED`
日期：2026-08-13

本 Sprint 仅实现 ROLE Resolver 的纯领域契约、Fail Closed 校验、测试 Fake Adapter 和静态 Registry 的非 ACTIVE 准备能力。未接入真实 Approval Role Directory，未创建数据库 Migration，未修改 Investment，未改变 `EXPLICIT_USER_V1 + USER + DIRECT` 的运行行为。

## 1. 修改文件清单

生产代码：

- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/assignment/ResolverStatus.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/assignment/ResolverRegistry.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/config/WorkflowAssignmentResolverConfiguration.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/RoleDirectoryErrorCode.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/RoleDirectoryException.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/RoleDirectorySourceType.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/RoleDirectoryQuery.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/RoleDirectoryMember.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/RoleDirectoryResult.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/RoleDirectoryPort.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/RoleResolverContext.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/RoleResolverResult.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/RoleDirectoryRetryPolicy.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/RoleDirectoryCanonical.java`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/role/RoleDirectoryResolver.java`

测试代码：

- `backend/src/test/java/cn/gov/enterprise/modules/workflow/support/FakeRoleDirectoryAdapter.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/domain/WorkflowRoleDirectoryDomainTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/domain/WorkflowRoleDirectoryResolverTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/domain/WorkflowRoleResolverContractTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/domain/WorkflowRoleDomainPurityTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/infrastructure/WorkflowTaskClaimMigrationContractTest.java`：仅修正已过期的 V2.6.7 资产状态断言，使其与已冻结的 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED_WITH_V268` 一致；未改 Migration。

文档：

- `docs/workflow-role-resolver-framework-implementation-report.md`

## 2. Domain 模型

新增的 `domain.role` 是纯领域包，不依赖 Spring、MyBatis、Entity 或 Infrastructure 类型：

- `RoleDirectoryQuery`：冻结 enterprise、业务组织、审批角色、节点激活时刻、Port 契约版本和 TraceId。
- `RoleDirectoryMember`：表达带半开有效期 `[effectiveFrom, effectiveTo)`、来源及 Revision 的角色成员事实。
- `RoleDirectoryResult`：表达一次完整、原子、带 Revision、Result Hash 和 Contract Hash 的目录响应。
- `RoleResolverContext`：绑定实例、节点执行、冻结 Resolver Binding、roleCode、businessOrgId 和 effectiveAt。
- `RoleResolverResult`：仅返回去重排序后的候选用户及目录证据；本 Sprint 不写 Candidate Pool。
- `RoleDirectoryErrorCode` / `RoleDirectoryException`：提供固定、可审计的 Fail Closed 错误语义。
- `RoleDirectoryRetryPolicy`：仅允许 `DIRECTORY_TIMEOUT`、`DIRECTORY_UNAVAILABLE`，最多两次尝试（首次加一次重试）。

## 3. RoleDirectoryPort

`RoleDirectoryPort.resolve(RoleDirectoryQuery)` 是 Workflow 对 Organization/Governance Directory 的 required-side port。生产代码中没有数据库实现、HTTP 实现、缓存实现或 fallback；真实目录仍不存在，因此 ROLE Runtime 保持关闭。

Resolver 校验顺序为：

1. Instance Resolver Binding 的 code/version/contract hash；
2. Query 的 contractVersion 与作用域；
3. Result 必须 `complete=true`；
4. roleCode、organizationId、effectiveAt 必须与 Query 一致；
5. Contract Hash 与 Result Hash 必须一致；
6. 成员必须处于有效期、同角色、同业务组织；
7. 无有效成员时 Fail Closed。

## 4. Resolver Registry 变化

新增固定描述符：

| 字段 | 值 |
|---|---|
| resolverCode | `ROLE_DIRECTORY` |
| resolverVersion | `ROLE_DIRECTORY_V1` |
| strategyType | `ROLE` |
| mode | `CANDIDATE_POOL` |
| portContract | `ROLE_DIRECTORY_PORT_V1` |
| status | `PREPARED` |
| enabled | `false` |

Registry 现在允许登记“无可执行实现、`PREPARED + disabled`”的描述符，用于启动时契约发现和校验。任何运行时 `require` 都会拒绝该描述符；Spring 配置还显式阻断 ROLE 描述符被改为非 PREPARED 状态。既有 ACTIVE Resolver 仍必须拥有完全匹配的实现。

## 5. Fake Adapter

`FakeRoleDirectoryAdapter` 仅位于测试源码，支持脚本化返回或异常，可覆盖：

- 单成员、多成员、无成员；
- Revision 和 Result Hash 变化；
- Source Conflict；
- Partial Result；
- Timeout / Unavailable 及一次受限重试。

生产包没有 Fake Bean，也没有可访问 Directory 的 Controller。

## 6. Contract Hash 与 Canonical

固定契约：

- Contract：`ROLE_DIRECTORY_PORT_V1`
- Canonical：`ROLE_CANONICAL_JSON_V1`
- Contract Hash：`5e8f9870effc7f886671f3cc5077a7bf0419cd9a6698dd6ca01e9de0c01f708d`

`RoleDirectoryCanonical` 使用显式字段顺序、UTF-8、UTC 毫秒时间、明确 JSON 转义、成员按 `userId + assignmentId` 排序及 SHA-256 小写 Hex；不依赖 Jackson 默认字段顺序、Map 迭代顺序、数据库返回顺序或系统时区。

测试确认：成员输入顺序变化 Hash 不变；Revision、effectiveAt、成员集合变化 Hash 必须变化；固定 Contract Canonical 的计算结果与冻结 Hash 完全一致。

## 7. API

本 Sprint 新增 Controller API：`0`。

未暴露 Directory 查询、Resolver 注册、Resolver 启停、ROLE 任务创建或 Candidate Pool 写入接口。

## 8. 测试结果

环境：Java `21.0.12`、Maven `3.9.9`、Spring Boot `3.5.9`。

- 定向测试：19 项通过，0 失败，0 错误，0 跳过；
- 后端全量测试：348 项通过，0 失败，0 错误，0 跳过；
- Spring Boot 上下文：通过；
- Java 21 编译：通过，共编译 512 个生产源码；
- Domain 纯净检查：通过；
- `EXPLICIT_USER_V1` 回归：通过；
- `git diff --check`：通过。

全量测试首次发现一条既有 V2.6.7 测试仍断言历史失败状态。该断言已按当前 Inventory 的组合晋级事实更新；V2.6.7 SQL、SHA、Flyway checksum 和失败历史记录均未改动。

## 9. Migration 状态

- 新增 Migration：`0`；
- V2.6.9：不存在；
- `SHA256SUMS`：30 项全部校验通过；
- V2.5.0—V2.6.8：未修改；
- V2.6.8 SHA-256 保持 `4cfd089f4aa05f6e4fb9d9dc250159c0229b3d98eb400089e5e08a758cb4a207`。

## 10. 剩余风险

1. 真实 Approval Role Directory、Owner/RACI 落地和数据质量尚未实施。
2. ROLE 描述符仅 PREPARED，不可用于实例启动或 Task 创建。
3. 当前框架尚未持久化 ROLE 的结构化快照、Directory Revision 与 Result Hash；后续如需持久化必须单独设计 V2.6.9 候选。
4. 固定 Canonical 尚需独立第二语言实现进行交叉验证。
5. Candidate 数量上限仍待压测后由平台治理冻结。
6. 真实目录延迟、冲突、Correction 和跨组织泄漏仍需集成环境验证。
7. WF4.2.6 仍为 `PREPROD_RESOURCE_NOT_READY`，本结果不代表预生产或生产发布就绪。

## 11. 下一步建议

下一 Sprint 应保持 ROLE Runtime 关闭，先完成 WF5.2 的真实目录适配与安全评审设计，明确 Directory 数据源、Revision 原子读取、Owner、SLA、跨语言 Canonical 验证及 V2.6.9 是否必要。未完成真实目录验收和增量 Migration 验收前，不得把 `ROLE_DIRECTORY` 切换为 ACTIVE，也不得接入 Investment。

最终状态：`ROLE_RESOLVER_FRAMEWORK_READY / ROLE_RUNTIME_DISABLED`。
