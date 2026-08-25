# Workflow V1 RC2-S9 Retry Controlled Canary Validation Report

## 1. 最终结论

`RC2_S9_RETRY=FAIL`。

全新隔离 RC2 TEST 环境成功完成数据库 Bootstrap、Flyway、UTF-8 Schema fingerprint、环境 marker 和修复后 Guard。Guard 18 项检查全部通过。Hybrid Application Seeder 随后在真实 Directory Application/Canonical 路径中触发 Fixture 断言缺陷，外层事务完整回滚；按失败策略停止，未续跑、未补 SQL、未修改 Fixture 资产。

临时 MySQL 已停止，datadir 与加密临时凭据已丢弃，完整 Evidence 保留。

## 2. Fresh 环境

- 根目录：`D:\codex-rc2-s9-retry-canary-20260824-144249`
- MySQL：8.4.9
- 端口：38472
- 数据库：`enterprise_platform`
- datadir：`D:\codex-rc2-s9-retry-canary-20260824-144249\mysql\data`（失败后已丢弃）
- Evidence：`D:\codex-rc2-s9-retry-canary-20260824-144249\evidence`
- 环境身份：`EPHEMERAL_RC2_TEST_IDENTITY_V1`，marker 与实际端口/datadir绑定
- RC1 34061 / RC1 datadir：未访问

## 3. Database 与 Flyway

- 17 项 Baseline：PASS
- explicit Flyway baseline 2.0.0：PASS
- 45 项 Migration 至 2.6.23：PASS
- latest Migration：2.6.23
- failed migrations：0
- history：46 行（baseline + 45 migrations）
- strict validate：PASS
- second migrate：NO_OP
- Migration SHA：45/45 PASS
- UTF-8 Schema fingerprint：`ffcd3b9c031c6a8521fe4cbdb1d3c248e49c50e2ccfa4860ce9aea04b5caacd0`

## 4. Guard

修复后的 `00_rc2_canary_guard.ps1` 真实执行成功：

```text
GUARD=PASS
RC1_PROTECTION_GUARD=PASS
PRODUCTION_ENVIRONMENT_GUARD=PASS
FIXTURE_ID_CONFLICT=NO
FIXTURE_BUSINESS_KEY_CONFLICT=NO
DATABASE_MODIFIED=NO
```

补充只读明细覆盖 18 项：RC2 identity、loopback、非 34061、非 RC1 datadir、MySQL 8.4、database、latest/failed Migration、fingerprint、45/45 SHA、ROLE Runtime、Canary、Kill Switch、production Directory、Fixture IDs、business keys、partial state 和 governance control，结果 18 PASS / 0 FAIL。

## 5. Fixture 失败

Seeder 失败位置：`Rc2ControlledCanaryFixtureSeederTest.seed` 第 133 行。

```text
expected: a9cb44f0a2e1c2bb8ed50dcb334ae9e32d84d172e0a10145985fcd531f5f61e2
 but was: 2e1736fae83be972259ccee92b8d448d463e7c8a5483d49afcda7225a3279234
```

精确原因：

1. `ApprovalRoleCommandService.publish` 在 Assignment 批次发布时以最早 `effectiveFrom=2026-01-01T00:00:00Z` 调用 `directory.resolveAt`，并把该结果 hash 写入 Revision 与 Revision Head。
2. Fixture 随后以 `DISCOVERY_AT=2030-06-01T00:00:00Z` 调用 `ApprovalRoleDirectoryService.resolve`。
3. `ApprovalRoleCanonical.resultHash` 包含查询的 `effectiveAt`；即使候选成员相同，不同 effectiveAt 的 canonical hash 按设计也必须不同。
4. Fixture 错误地断言 Revision Head hash 必须等于 2030 查询结果 hash，因此真实链路必然失败。

该问题属于 S8 Fixture 断言/验证时点契约错误，不是 Directory 业务代码、数据库约束、Guard 或 Schema 问题。S9 Retry 未修改该资产。

## 6. 回滚与安全边界

失败后立即只读检查以下 14 类对象：Organization、Users、Approval Role、Assignments、Revision Head、Revision、Definition、Version、Node、Version Binding、Manifest、Version Release、Instance、Governance Control。

结果：全部计数为 0。外层事务完整回滚，`FIXTURE_PARTIAL_STATE_DETECTED=NO`。

因此未形成 Directory Revision、Workflow Published Version、Candidate Discovery 或 Proposed Canary Scope；未创建 Instance、Task、Candidate Pool、Claim、Admission、Realtime Eligibility Evidence 或 Governance Control。

## 7. Evidence

保留：

- `baseline.log`
- `migrate.log`
- `validate.log`
- `second-migrate.log`
- `schema.txt`
- `environment-marker.json`
- `fixture-guard.log`
- `guard-checks.log`
- `fixture-execution.log`
- `fixture-rollback-verification.log`
- `summary.json`

Evidence 不包含数据库明文密码；加密临时凭据已删除。

## 8. 状态与下一步

- `ROLE_RUNTIME=DISABLED`
- `CANARY=NOT_AUTHORIZED_NOT_ENABLED`
- `KILL_SWITCH=STOP_NEW_AND_CLAIM`
- `RC1_TEST_DATABASE_TOUCHED=NO`
- `S4_REAL_MYSQL_CONCURRENCY_DEBT=OPEN`

下一步必须返回 S8 Fixture 资产修复：Revision Head hash 应与同一 revision publication effectiveAt 的 canonical result 比较；2030 只读 discovery hash 应独立重算并验证候选与自身 canonical hash，不得与历史发布时点 hash直接相等。修复并回归后，必须再次使用全新 datadir/端口重跑 S9，不得进入 Canary Scope Change Approval 或 RC2-S10。
