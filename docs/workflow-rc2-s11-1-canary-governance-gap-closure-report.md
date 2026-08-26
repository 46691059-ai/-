# Workflow V1 RC2-S11.1 Canary Governance Model Gap Closure

## 1. 结论与边界

RC2-S11 发现的 Canary Scope 与授权/启用语义缺口已通过局部增量模型关闭。新权威对象为 `workflow_role_canary_scope_governance`，原通用表中的历史 `CANARY/ALLOW` 记录不会被自动解释为新授权。

- `APPROVAL_DECISION=PENDING_HUMAN_APPROVAL`
- `ROLE_RUNTIME=DISABLED`
- `CANARY_AUTHORIZED=NO`
- `CANARY_ENABLED=NO`
- `KILL_SWITCH=STOP_NEW_AND_CLAIM`
- `V2.6.24=CANDIDATE / NOT_EXECUTED`

本 Sprint 未执行 MySQL/Flyway，未触碰 RC1 TEST 数据库，未创建 commit/tag，也未启用 Canary 或 ROLE Runtime。

## 2. S11 缺口与修复映射

| S11 缺口 | S11.1 处理 |
| --- | --- |
| Canary key 仅包含 enterprise/definition/version/node | 建立 enterprise/organization/definition/version/node/roleCode 六维显式字段 |
| `CANARY/ALLOW` 同时表达授权与启用 | 独立状态机，`APPROVED_NOT_ENABLED` 与 `ENABLED` 分离 |
| organizationId/roleCode 越界无法由 Canary gate 阻断 | Runtime/Claim/Pool/commit-time gate 统一消费精确 `CanaryScope` |
| 旧 Canary 记录可能被宽泛继承 | 旧记录 `LEGACY_DISABLED / NOT_AUTHORIZED`，无 fallback |
| 管理员并发操作可能 last-write-wins | append-only revision + predecessor unique key + application expected revision |
| 授权证据可能在状态迁移中漂移 | insert trigger 以 binary 规则冻结 Directory/Binding/Manifest/Content/Release/Schema 证据 |

## 3. Scope 与状态机

逻辑唯一键为：

```text
enterpriseId + organizationId + definitionId + definitionVersionId + nodeId + roleCode
```

所有字段必填，禁止 `NULL`/`*`/`ALL` 和宽范围 fallback。状态迁移为：

```text
PROPOSED -> APPROVED_NOT_ENABLED -> ENABLED -> SUSPENDED -> ENABLED
     |                |                |            |
     +----------------+----------------+------------+-> REVOKED
```

`PROPOSED -> ENABLED` 和 `REVOKED -> ENABLED` 均被 Domain 与数据库 Trigger 拒绝。被撤销的 Scope 如需重新授权，必须建立新的治理链，不得原地复活。

## 4. 数据库变化

`V2.6.24__create_exact_canary_scope_governance.sql` 仅创建精确 Canary 治理台账：

- 六维 Scope 显式字段与大小写敏感 `role_code`；
- revision/predecessor 唯一约束；
- Directory、Binding、Manifest、Content、Release 与结构指纹证据；
- 审批、启用、暂停、撤销审计字段；
- 状态、Hash、时间窗口、不可变性 CHECK；
- insert 迁移/证据冻结 Trigger 与 UPDATE/DELETE 拒绝 Trigger。

SHA-256：`e549147abcf77168a1b1ef465c76ed642f7ce02f250117d0019bbeeb8d524822`。该资产仍为 `CANDIDATE / NOT_EXECUTED`，`flyway_checksum=null`，必须由 RC2-S11.2 执行真实 MySQL Fresh/Upgrade 验收后才能晋级。

## 5. Runtime Gate 变化

`ProductionCanaryRuntimeGate` 是唯一精确 Canary 决策入口。它只在以下条件同时满足时返回允许：

1. ROLE Runtime 独立开关已显式启用；
2. 精确六维 Scope 唯一最新记录为 `ENABLED`；
3. Global/Enterprise/Definition Feature Flag 均为 `ON`；
4. Global/Enterprise/Definition-Version Kill Switch 均为 `ALLOW`。

缺失、过期、读取异常、Scope 任一维不匹配、非 `ENABLED`、Runtime 关闭或 Kill Switch 非 `ALLOW` 均 fail closed。Claim、Realtime Eligibility、commit-time 与 Candidate Pool 冻结入口已统一使用该 Gate。

## 6. Domain/Repository/Application

- Domain：`CanaryScope`、`CanaryApprovalEvidence`、`CanaryGovernanceState`、`CanaryGovernanceRecord`、`CanaryRuntimeGate`；
- Repository：精确 Scope + effective time 查询最新 revision，insert-only；
- Application Service：创建 `PROPOSED` 与 expected-revision 迁移；
- Infrastructure：MyBatis Entity/Mapper/Adapter 及生产 Runtime Gate。

Domain 不依赖 Spring、MyBatis 或 Entity。Controller/API 未新增，因此本 Sprint 不提供授权或启用操作入口。

## 7. 兼容与安全结论

- `EXPLICIT_USER_V1` 和 Legacy USER/DIRECT 语义不变；
- Resolver Binding/Manifest/Content Hash 算法不变；
- RC2 Fixture 不会自动写入 V2.6.24 治理记录；
- 旧 `workflow_role_runtime_governance_control` 仍承载 Feature Flag/Kill Switch/SoD，但不再承载 Canary 授权；
- Approval 不生成 Instance、Task、Candidate Pool、Claim、Admission 或 Realtime Evidence。

## 8. 验证与剩余风险

静态契约测试覆盖六维字段、约束、Trigger、资产 SHA 与“不预置授权数据”。Domain/Gate 测试覆盖六维越界、状态迁移、Runtime disabled、Feature Flag/Kill Switch fail-closed 和 CAS 冲突。Java 21 编译与 Spring Context 通过；后端全量回归 `711` 项，`702` 通过，`0` 失败，`0` 错误，`9` 跳过。

剩余风险：

1. V2.6.24 尚未经 MySQL 8.4/Flyway 13 Fresh、Upgrade、validate、no-op 与并发负向验证；
2. 暂无对外授权/启用 API，后续需在人工审批边界下单独设计；
3. 本次仅建立治理候选资产，未形成任何真实 Canary 授权记录。

## 9. 修改文件清单

- Domain：`domain/canary/CanaryScope.java`、`CanaryApprovalEvidence.java`、`CanaryGovernanceState.java`、`CanaryGovernanceRecord.java`、`CanaryRuntimeGate.java`、`domain/repository/CanaryGovernanceRepository.java`；
- Application/Runtime：`CanaryGovernanceApplicationService.java`、`RoleCandidatePoolTransactionService.java`、`RoleClaimRuntimeTransactionService.java`、`RoleClaimCommitCapabilityGate.java`、`RoleClaimRuntimeGate.java`；
- Infrastructure：`ProductionCanaryRuntimeGate.java`、`ConfiguredRoleClaimRuntimeGate.java`、`ProductionRealtimeCapabilityAdapters.java`、`ProductionRoleClaimCommitCapabilityGate.java`、`RoleRuntimeProductionCapabilityFactory.java`；
- Persistence：`CanaryGovernanceEntity.java`、`CanaryGovernanceMapper.java`、`CanaryGovernanceEntityMapper.java`、`CanaryGovernanceRepositoryImpl.java`；
- Tests：`CanaryGovernanceStateMachineTest.java`、`CanaryGovernanceApplicationServiceTest.java`、`ProductionCanaryRuntimeGateTest.java`、`V2624CanaryGovernanceMigrationContractTest.java`、`ConfiguredRoleClaimRuntimeGateTest.java`、`RoleCandidatePoolTransactionServiceTest.java`、`PlatformSoDGovernanceTest.java`、`Rc2ControlledCanaryFixtureContractTest.java`、`V2623RealtimeEligibilityRemarkMigrationContractTest.java`；
- Migration/Governance：`V2.6.24__create_exact_canary_scope_governance.sql`、`migration-inventory.yml`、`SHA256SUMS`、`database/migration/mysql/README.md`；
- Report：`docs/workflow-rc2-s11-1-canary-governance-gap-closure-report.md`。

## 10. 后续边界

下一步仅允许进入 `RC2-S11.2 Real MySQL Validation`，对 V2.6.24 执行隔离 Fresh/Upgrade 验收。验收前审批包仍为 `NOT_READY`，不得进入人工 Canary Scope Approval，不得启用 Canary 或 ROLE Runtime。
