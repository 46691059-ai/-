# Workflow V1 RC2-S8.2 Controlled Canary Fixture Execution Failure Root Cause & Repair

## 1. 结论

RC2-S9 Retry 的 Hybrid Application Seeder 已精确定位为 `STAGE_6_DIRECTORY_REVISION` 中的 Seeder 断言错误，不是生产代码缺陷。`approval_role_revision_head.current_result_hash` 对应 Revision 生效时点 `2026-01-01T00:00:00Z`，而原 Seeder 将其与另一次在 `2030-06-01T00:00:00Z` 执行的 Directory Query Result Hash 直接比较。`ApprovalRoleCanonical` 明确将 `effectiveAt` 纳入 Canonical Hash，因此两个 Hash 合法且必然不同。

最小修复是在 Seeder 中分别构造 Revision 生效时点结果与 Discovery 时点结果，各自校验 Canonical Hash，Revision Head 只与前者比较。没有修改 Canonical 规则、Directory 生产服务、Workflow 发布服务或数据库结构。

## 2. 失败证据

证据源：`D:\codex-rc2-s9-retry-canary-20260824-144249\evidence`。

- 失败类：`org.opentest4j.AssertionFailedError`
- 失败位置：`Rc2ControlledCanaryFixtureSeederTest.seed` 原第 133 行
- expected（Discovery at 2030）：`a9cb44f0a2e1c2bb8ed50dcb334ae9e32d84d172e0a10145985fcd531f5f61e2`
- actual（Revision head at 2026）：`2e1736fae83be972259ccee92b8d448d463e7c8a5483d49afcda7225a3279234`
- 回滚证据：14 类 Fixture/Runtime 对象计数全部为 0，无部分状态。
- Spring Context、Hikari 与 MySQL 连接已成功；写入执行已到 Directory Revision 阶段，因此临时账号的 `SELECT/INSERT/UPDATE` 权限充足。

## 3. 阶段定位

| 阶段 | 证据状态 | 说明 |
|---|---|---|
| STAGE_1_ENVIRONMENT | PASS | Guard 18/18，Spring Context、Hikari、MySQL 均已通过 |
| STAGE_2_TEST_ORGANIZATION | ROLLED_BACK | SQL 写入已执行，随外层事务回滚 |
| STAGE_3_TEST_USERS | ROLLED_BACK | 同上 |
| STAGE_4_APPROVAL_ROLE | ROLLED_BACK | Application Service 调用已执行 |
| STAGE_5_ROLE_ASSIGNMENTS | ROLLED_BACK | 两个不同 userId 在同一 effectiveAt 有效 |
| STAGE_6_DIRECTORY_REVISION | FAIL / ROLLED_BACK | Revision Head Hash 被错误与不同 effectiveAt 的 Query Hash 比较 |
| STAGE_7_WORKFLOW_DEFINITION | NOT_STARTED | 未到达 |
| STAGE_8_WORKFLOW_VERSION | NOT_STARTED | 未到达 |
| STAGE_9_WORKFLOW_NODE | NOT_STARTED | 未到达 |
| STAGE_10_VERSION_ROLE_BINDING | NOT_STARTED | 未到达 |
| STAGE_11_WORKFLOW_PUBLISH | NOT_STARTED | 未到达 |
| STAGE_12_FINAL_VERIFY | NOT_STARTED | 未到达 |

## 4. 事务与失败恢复

Seeder 测试入口使用一个 test-only `TransactionTemplate`，覆盖 Identity SQL、Approval Role Directory 与 Workflow Fixture 发布的 12 个阶段。这不是生产跨限界上下文事务模型，而是只对隔离、一次性 RC2 Fixture 生效的测试编排。本次证据证明该外层事务确实整体回滚。

治理边界为：当 Fixture 执行失败，依赖 test-only 单事务回滚，随后废弃 Fresh disposable database/datadir；不把该策略复制到生产 Application 事务边界。

## 5. Hybrid Seeder 架构审计

- Direct SQL：`sys_org`、`sys_user`，以及发布前对 DRAFT `workflow_version.resolver_binding_model` 的受控 CAS 更新。
- Application Service：`ApprovalRoleCommandService`、`ApprovalRoleDirectoryService`、`WorkflowDefinitionApplicationService`、`VersionResolverBindingApplicationService`。
- Canonical：`ApprovalRoleCanonical`、`VersionNodeResolverBindingCanonical`、`ResolverBindingManifestCanonical`、`WorkflowVersionContentHasher`、`WorkflowCombinedContentHasher`。
- ID：Fixture 使用 test-only `@Primary` 确定性 ID Generator 和 Identity SQL 显式稳定 ID；生产 ID 生成器未改变。
- Seeder 没有直接 SQL 写 `PUBLISHED`、Manifest Hash、Directory Hash 或 Release；这些仍由正式 Canonical/Application Service 产生。

## 6. 修复与契约

1. Seeder 分别查询 `FROM` 与 `DISCOVERY_AT`，两者均验证 revision=1。
2. Discovery 结果仍要求候选 userId 精确为 `990201,990202`，不输出 PII。
3. 两个 result hash 必须不同，且每个都必须等于其自身 Canonical Input 的重算值。
4. Revision Head 只与 `FROM` 时点的 Revision Result Hash 比较。
5. Dry Run 现在显式输出 planned stages/objects/application services/business keys/role binding/directory hash plan/publish plan，不执行 DML。

## 7. 验证结果

- PowerShell Parser：Guard、Publish 与 RC1 Fresh 脚本均 0 error。
- Guard Contract Matrix：14/14 PASS，未连接数据库。
- Fixture 定向契约：10/10 PASS（包含新增 effectiveAt Hash 回归）。
- Fixture Dry Run：PASS，`DATABASE_CONNECTION=NOT_ATTEMPTED`、`DATABASE_MODIFIED=NO`、`FIXTURE_EXECUTED=NO`。
- Backend full tests：690 run，0 failures，0 errors，8 skipped；Spring Context PASS。
- Migration SHA：45/45 PASS；V2.6.21=`cb9555ba5f81c292654286fe8c7396cdead4b71c9387c4b73d09139d4147296e`，V2.6.22=`658b4939a7e92295527ef3a5116d20814b0d17e4c34e46f3955e4c21c67dcc05`，V2.6.23=`874427e1df075042485c9098ad2f1b79dbc632200f01614f85529b30765c169d`。
- 本 Sprint 未运行 Seeder、未执行 Fixture DML、未创建 Fresh DB、未重跑 S9。

## 8. 资产与安全状态

- V2.6.21/V2.6.22/V2.6.23 及历史 Migration 不修改，不创建 V2.6.24。
- ROLE Runtime 仍为 `DISABLED`。
- Canary 仍为 `NOT_AUTHORIZED_NOT_ENABLED`。
- Kill Switch 仍为 `STOP_NEW_AND_CLAIM`。
- `S4_REAL_MYSQL_CONCURRENCY_DEBT=OPEN`，本 Sprint 不关闭。
- S8.2 只修复 Fixture Seeder/契约/Dry Run 资产；S9 bootstrap 和生产代码未改。

## 9. 剩余风险与下一步

本任务按约束不执行真实 Fixture，因此 Stage 7-12 仍需在下一次独立 S9 Retry 中获得真实 MySQL 运行证据。建议仅在重新建立可丢弃 RC2 TEST 环境并再次通过 18 项 Guard 后重试 S9；失败时仍整体废弃 datadir。
