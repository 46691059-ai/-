# Workflow V1 RC2-S8.4 Runtime Fixture Guard Failure Root Cause & Repair

## 1. 结论

`RC2_S8_4=PASS`，`READY_TO_RETRY_RC2_S9=YES`。

Retry #3 的数据库、Migration、Schema、marker 与安全状态本身均符合 RC2 TEST 要求。真实阻断来自 Guard 对 manifest 时间字段的读取语义：PowerShell 7.6.4 的 `ConvertFrom-Json` 将 ISO-8601 JSON string 自动转换为 `System.DateTime`，原代码随后执行 `[string]` 转换，得到 `01/01/2026 00:00:00`；按本机时区再次解析后对应 `2025-12-31T16:00:00Z`，不再等于冻结的 `2026-01-01T00:00:00Z`。这是 `GUARD_STATE_SEMANTICS_BUG`，不是合法安全拒绝。

首次证据 `guard.log` 中的 `Guard policy failed: DATABASE` 来自外部验收编排把 `SecureString` 作为子进程参数传递，属于调用身份/参数错误；仓库冻结发布脚本本身使用同一 PowerShell 进程调用 Guard。改为正确直接调用后，唯一真实 Guard 资产失败项是 Canonical EffectiveAt 解析。`guard-direct.log` 未捕获 stderr，这是 evidence capture 缺口；`summary.json` 记录了该根因，且 Guard 源码中失败断言可精确定位。

## 2. 失败项

```text
GUARD_FAILED_CHECK=FIXTURE_DIRECTORY_EFFECTIVE_AT_CANONICAL_UTC_SECONDS
GUARD_FAILED_EXPECTED=2026-01-01T00:00:00Z (raw JSON string)
GUARD_FAILED_ACTUAL=System.DateTime -> 01/01/2026 00:00:00 -> 2025-12-31T16:00:00Z
GUARD_FAILED_SOURCE=summary.json + 00_rc2_canary_guard.ps1 original manifest conversion/assertion
```

修复后 Guard 从 manifest 原始文本中提取唯一、无转义的 JSON string，再进行严格 UTC seconds 校验。它不依赖 PowerShell 的 JSON 日期自动转换行为，Windows PowerShell 5.1 与 PowerShell 7+ 得到相同值。

## 3. Retry #3 真实18项Runtime Guard证据重建

Retry #3 的 Guard 没有在失败前逐项输出结果，因此本表由保留的 `guard.log`、`summary.json`、`environment-marker.json`、`my.ini`、`environment-state.json`、Flyway日志和Schema证据逐项重建。首次调用只报告 `DATABASE`，意味着其余策略检查没有产生失败；数据库名称则由 marker、state 与 Flyway连接证据独立证明为正确。不得把 DryRun 18/18 当成真实运行结果。

| # | Check | Expected | Actual | Result | Source |
|---:|---|---|---|---|---|
| 1 | Host / loopback | `127.0.0.1` | `127.0.0.1` | PASS | `my.ini`, marker |
| 2 | Port policy | high port, not 34061 | `38573` | PASS | `my.ini`, state |
| 3 | Datadir identity | new RC2 path, not RC1 | `D:\codex-rc2-s9-retry3-canary-20260824-151900\mysql\data` | PASS | marker, state, `my.ini` |
| 4 | Database | `enterprise_platform` | `enterprise_platform` | PASS | marker, state, Flyway logs |
| 5 | MySQL version | `8.4.x` | `8.4.9` | PASS | state, Flyway logs |
| 6 | Latest migration | `2.6.23` | `2.6.23` | PASS | state, Flyway no-op |
| 7 | Failed migration count | `0` | `0` | PASS | state |
| 8 | Schema fingerprint | frozen RC2 fingerprint | `ffcd3b9c031c6a8521fe4cbdb1d3c248e49c50e2ccfa4860ce9aea04b5caacd0` | PASS | state, schema evidence |
| 9 | Migration SHA | `45/45` | `45/45` | PASS | summary, repository recalculation |
| 10 | Environment marker | valid V1 marker | `EPHEMERAL_RC2_TEST_IDENTITY_V1` | PASS | marker |
| 11 | Marker datadir | equals actual/expected | exact match after Windows normalization | PASS | marker, state, `my.ini` |
| 12 | Marker port | requested = actual = marker | `38573 = 38573 = 38573` | PASS | marker, state, `my.ini` |
| 13 | ROLE Runtime | `DISABLED` | `DISABLED` | PASS | marker, manifest, summary |
| 14 | Canary | `NOT_AUTHORIZED_NOT_ENABLED` | same | PASS | marker, manifest, summary |
| 15 | Kill Switch | `STOP_NEW_AND_CLAIM` | same | PASS | marker, manifest, summary |
| 16 | Production Directory provider | `DISABLED` | `DISABLED` | PASS | marker, manifest |
| 17 | Fixture IDs/business keys | none present | none present | PASS | Guard query outcome, summary |
| 18 | Partial fixture | `NONE_PRESENT` | no Fixture object created | PASS | Guard query outcome, summary |

以上 18 项 Runtime 状态均通过；唯一失败发生在进入 Runtime 查询前的 manifest Canonical EffectiveAt prerequisite。

## 4. 修复内容

- `00_rc2_canary_guard.ps1`
  - 新增原始 JSON string 提取，拒绝重复、缺失或带转义的时间属性；
  - Windows PowerShell 5.1 / PowerShell 7+ 使用同一 Canonical 时间语义；
  - datadir 使用规范化绝对路径，并按 Windows OrdinalIgnoreCase 进行精确比较；
  - 新增 requested port 与 mysqld actual port 一致性检查；
  - DryRun 明确输出18项检查、`RUNTIME_GUARD_DRY_RUN=PASS` 和 18/0 计数；
  - Contract Matrix 扩展本次日期自动转换、actual port mismatch、wrong database、wrong MySQL version、failed migration、malformed marker、production provider 和 runtime governance control 场景。
- `04_rc2_canary_publish.ps1`
  - 同步以原始 JSON string 读取 `directoryEffectiveAt`；
  - 保持 SecureString 在同一进程调用 Guard，不增加明文参数、force、skip 或 ignore 开关。
- `Rc2ControlledCanaryFixtureContractTest.java`
  - 增加跨 PowerShell JSON 时间读取契约和新增 Fail Closed 场景的静态契约断言。

未修改 Fixture ID、业务键、Directory EffectiveAt、Workflow topology、Hash算法、生产代码或Migration。

## 5. 安全语义确认

- Runtime安全状态来自 manifest 与强制 environment marker；Guard没有要求数据库必须存在 governance control 记录。
- `RuntimeControlCount=0` 继续表示未创建控制记录，符合 Fail Closed；非零仍拒绝。
- Test Seeder Application Service capability 与 production Directory provider 严格分离；provider 必须保持 `DISABLED`。
- `34061`、`D:\mysql-rc1\data`、marker缺失/畸形、错误fingerprint、错误Migration/SHA、Runtime开启、Canary授权、不安全Kill Switch、Fixture冲突和partial fixture均继续拒绝。
- 所有 MySQL metadata 查询仍包含 `--default-character-set=utf8mb4`。

## 6. 测试结果

| 测试 | 结果 |
|---|---|
| PowerShell 7.6.4 parser | 0 errors |
| Windows PowerShell 5.1 parser | 0 errors |
| PowerShell 7 ContractTest | 26/26 PASS |
| Windows PowerShell 5.1 ContractTest | 26/26 PASS |
| PowerShell 7 Runtime Guard DryRun | 18/18 PASS |
| Windows PowerShell 5.1 Runtime Guard DryRun | 18/18 PASS |
| Fixture Java contract tests | 22 run, 0 failures, 0 errors, 0 skipped |
| Migration SHA | 45/45 PASS |

本任务未创建或修改数据库，未执行 Fixture，未执行 Candidate Discovery，也未重跑 S9。下一次 S9 必须创建新的 MySQL 8.4 datadir、动态端口、数据库、marker 与 evidence，不能复用 Retry #3 已销毁环境。

`ROLE_RUNTIME=DISABLED`，`CANARY=NOT_AUTHORIZED_NOT_ENABLED`，`KILL_SWITCH=STOP_NEW_AND_CLAIM`，`S4_REAL_MYSQL_CONCURRENCY_DEBT=OPEN`。
