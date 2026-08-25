# Workflow V1 RC2-S9 Retry #3 Controlled Canary Validation Report

## 1. 最终结论

`RC2_S9_RETRY_3=FAIL`。

Pre-execution Asset Gate、全新 MySQL 8.4 临时环境、17 项 Baseline、Flyway 迁移至 V2.6.23、strict validate、二次 migrate no-op、Schema Fingerprint 与 45/45 Migration SHA 均通过。真实 Guard 未通过，因此按 Fail Closed 规则停止：未执行 Hybrid Application Seeder，未创建 Fixture，未执行 Candidate Discovery，未提出 Canary Scope。

失败阶段为 `FIXTURE_GUARD`。冻结 Guard 在 Windows PowerShell 5.1 DryRun 中通过，但在本次 PowerShell 7 实际调用中，`ConvertFrom-Json` 将 manifest 的 ISO-8601 `directoryEffectiveAt` 自动转换为 `DateTime`；后续强制转换为字符串时不再保留 `2026-01-01T00:00:00Z` 原始 Canonical 格式，导致 Guard 报告：

```text
REFUSE_TO_EXECUTE: fixture directoryEffectiveAt must be canonical UTC seconds
```

首次通过 Windows PowerShell 5.1 子进程调用时还出现 SecureString 无法跨进程按对象传递的问题，Guard 报告 `Guard policy failed: DATABASE`。该次调用未执行 Fixture；随后直接调用冻结 Guard 才定位到上述 JSON 日期自动转换问题。本轮没有现场修改 Guard、Manifest、Seeder、Publish Script 或 Verify SQL。

## 2. Pre-execution Asset Gate

| 检查项 | 结果 |
|---|---|
| Authoritative EffectiveAt 唯一 | PASS |
| Directory/Revision/Query EffectiveAt 一致 | PASS |
| 可执行资产中的 2030 旧字面量 | 0 |
| Fixture Contract / Guard 资产 | PASS |
| Migration SHA | 45/45 PASS |
| Fixture DryRun | PASS |

冻结时间契约均为 `2026-01-01T00:00:00Z`。此结果证明资产文本契约一致，但不替代 PowerShell 7 下的真实 Guard 可执行性。

## 3. 临时数据库环境

| 项目 | 值 |
|---|---|
| MySQL | Community Server 8.4.9 |
| Port | 38573 |
| Database | `enterprise_platform` |
| 临时根目录 | `D:\codex-rc2-s9-retry3-canary-20260824-151900` |
| Datadir | `D:\codex-rc2-s9-retry3-canary-20260824-151900\mysql\data`（已销毁） |
| RC1 TEST 数据库 | 未触碰 |
| Environment Marker | 已创建并复制到 evidence |

该环境未复用 34061、38471、38472 或历史 S9 datadir；仅监听 `127.0.0.1`。失败后 MySQL 已停止，38573 无监听，专用 datadir 已销毁，证据目录保留。

## 4. Baseline、Flyway 与 Schema

| Gate | 结果 |
|---|---|
| 17 项 Baseline | PASS |
| Explicit Flyway baseline 2.0.0 | PASS |
| Migrate through V2.6.23 | PASS，45 migrations applied |
| Flyway history | Latest 2.6.23，failed 0，46 history rows including baseline |
| Strict validate | PASS，46 migrations validated |
| Second migrate | NO_OP |
| Schema Fingerprint | `ffcd3b9c031c6a8521fe4cbdb1d3c248e49c50e2ccfa4860ce9aea04b5caacd0` |
| Migration SHA | 45/45 PASS |

## 5. Guard与失败边界

真实 Guard 结果为 `FAIL`。实际 Guard 未完成完整检查矩阵，因此不得沿用 DryRun 的 18/18 结果冒充真实 Guard：

- `GUARD_CHECKS_PASS=NOT_COMPLETED`；
- `GUARD_CHECKS_FAIL=1`；
- 阻断原因：PowerShell 7 JSON 日期自动转换破坏 Canonical UTC seconds 字符串；
- Fixture 执行：未开始；
- Partial Fixture：无；
- Candidate Discovery：未执行。

这是受冻结 Fixture/Guard 执行资产的跨 PowerShell 运行时兼容缺口。依据本 Sprint 规则，需要回到独立资产修复任务处理；不得在本次运行中边修边跑。

## 6. Fixture、Hash与Runtime状态

由于真实 Guard 未通过，以下对象均未创建或未执行验证：

| 范围 | 结果 |
|---|---|
| TEST Organization / Users | 0 / 0 |
| Approval Role / Assignments | 0 / 0 |
| Directory Revision / Candidates | NOT_CREATED / 0 |
| Workflow Definition / Published Version | 0 / 0 |
| ROLE Bound Node / Version Binding | 0 / 0 |
| Manifest / Version Release | 0 / 0 |
| Directory/Binding/Manifest/Content Hash validation | NOT_EXECUTED |
| Runtime objects | 0 |
| Candidate Pool / Claim / Admission / Realtime Evidence | 0 / 0 / 0 / 0 |

`ROLE_RUNTIME=DISABLED`，`CANARY=NOT_AUTHORIZED_NOT_ENABLED`，`KILL_SWITCH=STOP_NEW_AND_CLAIM`。未创建 Workflow Instance、NodeExecution、Task、CandidatePool、Claim、Admission 或 Realtime Eligibility Evidence。

## 7. Candidate Discovery与Canary结论

`CANDIDATE_DISCOVERY_SOURCE=CURRENT_RC2_S9_RETRY_3_TEST_DATABASE`，但 Discovery 因 Guard 失败而未执行。推荐候选数为 0，`PROPOSED_CANARY_SCOPE_AVAILABLE=NO`。

本次不授权 Canary、不启用 Canary、不启用 ROLE Runtime。`READY_FOR_CANARY_SCOPE_CHANGE_APPROVAL=NO`，`READY_FOR_RC2_S10=NO`，`READY_FOR_ROLE_RUNTIME_CANARY_ACTIVATION=NO`。

## 8. Evidence与后续处理

Evidence 根目录：`D:\codex-rc2-s9-retry3-canary-20260824-151900\evidence`。

已保留：Pre-execution Gate、environment marker、Baseline、Flyway baseline/migrate/validate/no-op、Schema、Guard、summary 与 MySQL 配置副本。未生成 Fixture Stage、Directory Hash、Workflow Publish、Runtime Zero-state 或 Candidate Discovery 成功证据，因为相关阶段未执行。

下一步必须在独立 Fixture 资产修复任务中统一 Guard 的 JSON 读取语义，使 Windows PowerShell 5.1 与 PowerShell 7+ 都以原始 Canonical 字符串读取 `directoryEffectiveAt`，并修正 SecureString 子进程调用方式。完成资产回归后，应使用另一套全新端口和 datadir 重跑 RC2-S9；不得复用本次已销毁环境。

`S4_REAL_MYSQL_CONCURRENCY_DEBT=OPEN`。
