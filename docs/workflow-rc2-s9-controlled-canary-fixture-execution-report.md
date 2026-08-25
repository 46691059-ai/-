# Workflow V1 RC2-S9 Controlled Canary Fixture Execution 报告

## 1. 最终结论

`RC2_S9=FAIL`。

独立 RC2 TEST MySQL 8.4.9 Fresh 环境成功完成 17 项 Baseline、显式 Flyway baseline 2.0.0、迁移至 2.6.23、strict validate、二次 migrate no-op 和受治理 UTF-8 Schema fingerprint 校验。Controlled Canary Fixture Guard 随后按 Fail Closed 原则拒绝授权，Fixture 未执行。

阻断原因是 S8 Guard 的 MySQL 查询参数缺少 `--default-character-set=utf8mb4`。Schema 中的中文表/列注释被客户端错误解码，导致 Guard 内部计算出伪 fingerprint 漂移。使用与 V2.6.23 真实验收脚本一致的 UTF-8 客户端参数重新只读采集后，fingerprint 精确为冻结值 `ffcd3b9c031c6a8521fe4cbdb1d3c248e49c50e2ccfa4860ce9aea04b5caacd0`，证明数据库结构本身没有漂移。

依据 S9 失败处理规则，本 Sprint 未修改 S8 Fixture 资产、未绕过 Guard、未在半成品库补 SQL。临时 MySQL 已停止，datadir 已丢弃，Evidence 保留。

## 2. 隔离环境

- MySQL：8.4.9
- 端口：38471
- 数据库：`enterprise_platform`
- 临时根目录：`D:\codex-rc2-s9-canary-20260824-141542`
- datadir：`D:\codex-rc2-s9-canary-20260824-141542\mysql\data`（失败后已丢弃）
- Evidence：`D:\codex-rc2-s9-canary-20260824-141542\evidence`（已保留）
- RC1 端口 34061：未使用
- RC1 datadir：未访问

初始化阶段曾有一次 PowerShell 编排变量命名冲突，发生在 MySQL 初始化前；对应空临时目录未进入任何数据库或 Fixture 操作，不属于 Fixture 执行结果。

## 3. Database 与 Flyway 结果

- 17 项 Baseline：PASS
- Flyway baseline 2.0.0：PASS
- migrate 2.0.0 → 2.6.23：PASS，45 项 Migration 全部执行成功
- Flyway history：46 行（1 baseline + 45 migrations）
- failed migrations：0
- strict validate：PASS，验证 46 项
- second migrate：NO_OP
- Migration SHA：45/45 PASS
- Schema fingerprint：`ffcd3b9c031c6a8521fe4cbdb1d3c248e49c50e2ccfa4860ce9aea04b5caacd0`

## 4. Fixture Guard 失败

Guard 真实执行结果：

```text
REFUSE_TO_EXECUTE: Schema fingerprint mismatch
FIXTURE_GUARD_EXIT=2
```

根因位于 `database/test-fixtures/rc2/00_rc2_canary_guard.ps1` 的 MySQL `Invoke-Query` 参数：未显式设置 UTF-8 客户端字符集。冻结的 V2.6.23 验收实现显式使用 `--default-character-set=utf8mb4`；S8 Guard 未复用这一必要参数。

该问题必须返回 S8 Fixture 资产治理修复，修复后从新的 Fresh RC2-S9 环境重新执行。不得把 Guard 的 expected fingerprint 改为错误解码产生的值。

## 5. 未执行边界

由于 Guard 未通过，以下操作全部未发生：

- Identity/Organization Fixture INSERT
- Approval Role、Assignment、Revision、Revision Head 创建
- Workflow Definition、Version、Node、Version Binding、Manifest、Release 创建
- Candidate Discovery
- Workflow Instance、NodeExecution、Task、Candidate Pool、Claim、Admission 或 Realtime Eligibility 创建
- Governance Control 写入
- Canary 授权或启用
- ROLE Runtime 启用
- Governed Decommission

因此不存在 Fixture 半状态；数据库在失败后随 datadir 一并丢弃。

## 6. Evidence

保留文件：

- `baseline.log`
- `migrate.log`
- `validate.log`
- `second-migrate.log`
- `schema.txt`
- `fixture-guard.log`
- `summary.json`

Evidence 中不包含数据库明文密码。临时应用凭据未完成创建，且 datadir 已销毁。

## 7. 安全状态

- `ROLE_RUNTIME=DISABLED`
- `CANARY=NOT_AUTHORIZED_NOT_ENABLED`
- `KILL_SWITCH=STOP_NEW_AND_CLAIM`
- `RC1_TEST_DATABASE_TOUCHED=NO`
- `FIXTURE_EXECUTED=NO`
- `S4_REAL_MYSQL_CONCURRENCY_DEBT=OPEN`

## 8. 下一步

返回 S8 资产修复：只为 Guard 的所有 MySQL 只读查询显式增加 `--default-character-set=utf8mb4`，补充包含中文注释的 fingerprint 契约测试，并重新执行 S8 静态治理。随后必须使用全新隔离 datadir 从头重跑 RC2-S9。

当前不得进入 Canary Scope Change Approval、RC2-S10 或 ROLE Runtime Canary Activation。
