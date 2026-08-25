# Workflow V1 RC2-S8 Controlled Canary Fixture Assets 报告

## 1. 结论

RC2-S8 已形成一套 `TEST_ONLY / NON_PRODUCTION / DETERMINISTIC / AUDITABLE / FAIL_CLOSED` 的受控 Fixture 资产。资产只为后续 S9 在独立 RC2 TEST 环境生成一个可只读发现的 ROLE-bound Published Workflow Version；本 Sprint 未连接或修改数据库，未执行 Fixture，未创建 Runtime 对象，也未授权 Canary。

最终状态：

- `FIXTURE_ASSETS_READY`
- `FIXTURE_NOT_EXECUTED`
- `ROLE_RUNTIME=DISABLED`
- `CANARY=NOT_AUTHORIZED_NOT_ENABLED`
- `KILL_SWITCH=STOP_NEW_AND_CLAIM`

## 2. Schema 与代码审计

重新审计了 `sys_org`、`sys_user`、四张 Approval Role Directory 表、Workflow Definition/Version/Node/Transition、Version Release、Version Resolver Binding 与 Manifest 的真实字段、约束、Trigger 和 Application Service。

结论：

- 预留 ID 990001、990101、990201、990202、990401、990402、990404 在 Baseline、Migration seed 和现有 Fixture 中没有持久化占用；源码测试向量中的相似数字不属于目标数据库资产。
- 主键策略为应用雪花 ID，但数据库允许显式正 BIGINT；Fixture 通过 test-only `@Primary` 确定性 ID Generator 注入固定 ID，不修改生产 Generator。
- 当前合法最小图是一个启用的 `APPROVAL + SINGLE` 节点，不存在 START/END NodeType，也未用命名伪造类型。
- Version 使用 `schemaVersion=1.0` 与 `SINGLE_NODE_LEGACY` 执行引擎，但 Resolver 模型为 `VERSION_RESOLVER_BINDING_CAPABLE`；这不创建 Legacy USER binding。
- 当前 Application API 尚无“把 DRAFT Version 切换为 binding-capable”的命令，因此 Seeder 在发布前对该单一 DRAFT 配置字段执行带状态/version CAS 的受控更新；Node、Binding、Manifest、Combined Content Hash 和 Release 发布仍全部走正式 Application/Canonical 链路。

## 3. Fixture 资产

| 文件 | 职责 |
| --- | --- |
| `database/test-fixtures/rc2/rc2-canary-fixture-manifest.json` | 唯一 ID、业务键、时间窗、Runtime 安全态和预期 Scope |
| `database/test-fixtures/rc2/00_rc2_canary_guard.ps1` | 只读环境、Schema、SHA、冲突、部分残留、RC1/生产保护 Gate |
| `database/test-fixtures/rc2/01_rc2_canary_identity.sql` | 一个独立测试组织和两个无真实 PII、锁定登录的候选用户 |
| `database/test-fixtures/rc2/04_rc2_canary_publish.ps1` | Dry Run 或经明确授权后调用 test-only Application Seeder |
| `database/test-fixtures/rc2/05_rc2_canary_verify.sql` | 只读聚合、发布事实及 Runtime 零对象验证 |
| `database/test-fixtures/rc2/99_rc2_canary_decommission-plan.sql` | Governed State Transition 边界与历史证据保留规则 |
| `Rc2ControlledCanaryFixtureSeederTest` | opt-in、外层事务、正式 Directory/Workflow Application 与 Canonical 调用 |
| `Rc2ControlledCanaryFixtureContractTest` | 资产命名、Guard、PII/Secret、DML、Hash、Topology、Decommission 静态契约 |

## 4. 执行策略与事务

`FIXTURE_EXECUTION_STRATEGY=HYBRID_APPLICATION_SEEDER`。

1. Guard 先执行只读 Gate：限定 `127.0.0.1`、拒绝 34061 与 `D:/mysql-rc1`、要求显式 `RC2_TEST` 标识、MySQL 8.4、最新 2.6.23、失败 Migration 为 0、固定 Schema fingerprint、45/45 SHA、安全 Runtime 状态、ID/业务键完全不存在且无部分残留。
2. Identity SQL 只插入测试组织和两个测试用户；用户 phone/email/employeeId 均为 NULL，密码列使用非编码的不可登录标记并永久锁定，不赋予真实登录能力。
3. `ApprovalRoleCommandService.createRole/assignUsers` 在同一外层事务生成 Role、两项 Assignment、Revision 1 和 CAS Head；Hash 由 `ApprovalRoleCanonical` 与 Directory Service 生成。
4. Workflow Definition、Version、单 APPROVAL Node 由正式 Application Service 创建；Version Binding 由当前 Registry Descriptor 和 `VersionNodeResolverBindingCanonical` 计算。
5. `WorkflowDefinitionApplicationService.publishVersion` 负责 Binding Coverage、Manifest、Graph/Combined Content Hash、Version PUBLISHED、Definition ACTIVE 与 Release Snapshot 的原子发布。
6. 任一步失败，外层事务整体回滚；重复执行不执行 upsert，完整存在或部分存在都 Fail Closed。

Seeder 需要同时满足 PowerShell Guard、精确 `RC2_TEST_FIXTURE_EXECUTION` 确认、环境授权和 opt-in JUnit system property。普通测试与生产启动均不可达。

## 5. Hash 来源

| 事实 | 权威来源 |
| --- | --- |
| Directory source/assignment/result hash | `ApprovalRoleCanonical` + `ApprovalRoleCommandService` + `ApprovalRoleDirectoryService` |
| Graph hash | `WorkflowVersionContentHasher` |
| Version binding hash | `VersionNodeResolverBindingCanonical` |
| Manifest hash | `ResolverBindingManifestCanonical` |
| Combined content hash | `WorkflowCombinedContentHasher` |
| Release snapshot | `WorkflowDefinitionApplicationService.publishVersion` |

SQL 中没有复制 Canonical 算法，没有随机填充 64 字符 Hash，也没有硬编码 Resolver Contract Hash；Contract Hash 在执行时从当前 `ResolverRegistry` 的 `ROLE_DIRECTORY / ROLE_DIRECTORY_V1` PREPARED Descriptor 读取并校验。Registry 状态不会被修改。

## 6. Fixture Scope

```text
enterpriseId=990001
organizationId=990101
definitionId=990401
definitionVersionId=990402
nodeId=990404
roleCode=RC1_TEST_CANARY_APPROVER
candidateUserIds=990201,990202
status=PROPOSED_NOT_ENABLED
```

预期执行后：一个 ACTIVE Definition、一个 PUBLISHED Version、一个 Version Release、一个 ROLE-bound Node、一个 Manifest、一个 ACTIVE Approval Role、Revision 1 和两个有效候选。预期 Runtime Instance、Task、Candidate Pool、Claim、Admission 与 Governance Control 均为 0。

## 7. Decommission

`FIXTURE_DECOMMISSION_MODE=GOVERNED_STATE_TRANSITION`。

- 两项 Assignment 通过 `ApprovalRoleCommandService.endAssignment` 结束并各自追加 Revision。
- Approval Role 通过 `deactivateRole` 停用并追加 Revision。
- Published Version 以 CAS 状态转为 RETIRED，Definition 转为 ARCHIVED。
- 测试用户和组织停用，用户 token version 前移。
- Version Release、Manifest、Directory Revision 及审计证据永久保留。
- 不执行物理 DELETE、TRUNCATE、REPLACE、INSERT IGNORE 或 upsert。

## 8. 验证结果

- PowerShell 5.1 Parser：两个脚本均 0 errors。
- Dry Run：`PASS`；明确输出 `DATABASE_CONNECTION=NOT_ATTEMPTED`、`DATABASE_MODIFIED=NO`、`FIXTURE_EXECUTED=NO`。
- Fixture Contract Test：8/8 PASS。
- Java 21 compile/test-compile：PASS。
- Spring Context：PASS。
- Backend full test：688 run，680 pass，0 failure，0 error，8 skipped。
- 新增 opt-in Seeder 在未授权全量测试中按设计 skipped；没有连接数据库。
- Migration SHA：45/45 PASS；V2.6.21、V2.6.22、V2.6.23 均未变化。
- 新 Migration：0。

## 9. 风险与下一步

- S9 首次人工执行前必须准备一个非 34061、datadir 带 RC2 标识、Schema fingerprint 精确匹配的独立持久 RC2 TEST 实例。
- Application 当前缺少 DRAFT Version Resolver Binding Model 的公开命令；Fixture 使用受控 CAS 补足该配置，不应被推广为生产管理 API。
- Seeder 尚未在数据库执行，因此真实生成的数据和 S9 Discovery 结果仍不得宣称存在。
- `S4_REAL_MYSQL_CONCURRENCY_DEBT=OPEN`，本 Sprint 未关闭。
- 即使 S9 Fixture 与只读 Discovery 成功，也只能形成 `PROPOSED_NOT_ENABLED` Scope，不代表 ROLE Runtime 或 Canary 获得激活授权。
