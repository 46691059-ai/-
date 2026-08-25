# Workflow V1 RC2-S8.1 Canary Fixture Guard Failure Root Cause & Repair

## 1. 结论

S9 的拒绝属于 `GUARD_BUG`。数据库 Schema、Migration、Fixture ID 与安全状态没有触发有效安全拒绝。

精确失败点：`SCHEMA_FINGERPRINT`。Guard 的 MySQL `Invoke-Query` 未传递 `--default-character-set=utf8mb4`，导致中文 Schema 注释按错误客户端字符集解码；同一数据库被 Guard 计算为 `b5dbb351ccb11f9fb28134f4c25b8bc398119534be8a9ae36d182e00d6be53bd`，而受治理 UTF-8 采集结果为冻结值 `ffcd3b9c031c6a8521fe4cbdb1d3c248e49c50e2ccfa4860ce9aea04b5caacd0`。

修复仅涉及 Fixture Guard、Fixture Contract Test、RC2 TEST environment marker 模板和本报告。未修改 Fixture 业务数据、生产 Workflow/Directory 代码或 Migration，未连接数据库，未执行 Fixture。

## 2. S9 Evidence

证据目录：`D:\codex-rc2-s9-canary-20260824-141542\evidence`。

- `summary.json`：失败阶段为 `FIXTURE_GUARD`，Fixture 未执行。
- `migrate.log`：45 项 Migration 成功，latest 2.6.23。
- `validate.log`：strict validate 成功。
- `second-migrate.log`：No migration necessary。
- `schema.txt`：按受治理 UTF-8 规则采集，SHA-256 为冻结 fingerprint。
- `fixture-guard.log`：`REFUSE_TO_EXECUTE: Schema fingerprint mismatch`，exit 2。

## 3. Guard 判定逐项重放

| CHECK | EXPECTED | ACTUAL | 结果 | SOURCE |
| --- | --- | --- | --- | --- |
| Host | `127.0.0.1` | `127.0.0.1` | PASS | S9 `my.ini` /连接身份 |
| Port | 非 34061 | 38471 | PASS | S9 `my.ini` |
| Datadir | 非 `D:\mysql-rc1\data` 且 RC2 标识 | `D:\codex-rc2-s9-canary-20260824-141542\mysql\data` | PASS | S9 `my.ini` |
| MySQL | 8.4.x | 8.4.9 | PASS | S9 query/evidence |
| Database | `enterprise_platform` | `enterprise_platform` | PASS | S9 query/evidence |
| Latest Migration | 2.6.23 | 2.6.23 | PASS | Flyway history |
| Failed migrations | 0 | 0 | PASS | Flyway history |
| Schema fingerprint | `ffcd3b…acd0` | Guard=`b5dbb3…53bd`; UTF-8 governed=`ffcd3b…acd0` | **FAIL** | Guard stdout + schema evidence |
| Migration SHA | 45/45 | 45/45 | PASS | `SHA256SUMS` 重算 |
| ROLE Runtime | DISABLED | DISABLED | PASS | Fixture safety contract / Seeder override |
| Canary | NOT_AUTHORIZED_NOT_ENABLED | governance control absent | PASS | Fresh Schema + Guard control query语义 |
| Kill Switch | STOP_NEW_AND_CLAIM | STOP_NEW_AND_CLAIM | PASS | Fixture safety contract / Seeder override |
| Production Directory | DISABLED | DISABLED | PASS | Fixture safety contract / Seeder override |
| Fixture IDs | 不存在 | Baseline/Migration 0 命中，Fixture 未执行 | PASS | Fresh 构建证据 + 静态资产扫描 |
| Fixture business keys | 不存在 | Baseline/Migration 0 命中，Fixture 未执行 | PASS | Fresh 构建证据 + 静态资产扫描 |
| Partial Fixture | `NONE_PRESENT` | `NONE_PRESENT` | PASS | Fixture 在 Guard 前无写入 |
| RC2 TEST identity | 明确、可审计 | 原实现仅调用参数+目录名，证据不足 | 设计缺口 | 原 Guard 源码审计 |

`FIXTURE_ID_CONFLICT=NO`，`FIXTURE_BUSINESS_KEY_CONFLICT=NO`。

## 4. 最小修复

1. `Invoke-Query` 固定加入 `--default-character-set=utf8mb4`，与冻结的 V2.6.23 fingerprint 采集实现保持一致。
2. 新增派生路径 marker：`<mysql-root>/rc2-test-environment.json`。Guard 不接受调用参数或目录名作为唯一 RC2 身份证据。
3. Marker 必须由验证/bootstrap 流程创建并精确绑定 host、port、绝对 datadir、database、release 2.6.23、fingerprint、45 项 SHA 和 Runtime 安全态。
4. Guard 同时支持 `EPHEMERAL_RC2_TEST` 和未来 `PERSISTENT_RC2_TEST`，但继续拒绝 34061、RC1 datadir、生产身份和缺失 marker。
5. 缺少 governance control 记录继续按源码 Fail Closed 语义解释为 Canary 未授权/未启用；不要求插入“OFF”记录。
6. Fixture 状态明确区分 `NONE_PRESENT`、`ALL_PRESENT`、`PARTIAL_PRESENT`；Preflight 只接受 `NONE_PRESENT`。

## 5. Ephemeral RC2 TEST Identity Contract

`EPHEMERAL_RC2_TEST_IDENTITY_CONTRACT=EPHEMERAL_RC2_TEST_IDENTITY_V1`：

- host 必须为 `127.0.0.1`；
- port 必须为有效高位非受保护端口，且不得为 34061；
- datadir 必须为绝对 RC2 路径，禁止 `D:\mysql-rc1`；
- database 必须为 `enterprise_platform`；
- MySQL 必须为 8.4.x；
- release/latest Migration 必须为 2.6.23，failed=0；
- Schema fingerprint 必须为 `ffcd3b…acd0`；
- Migration SHA 必须 45/45；
- marker 必须由 validation/bootstrap 创建并与实际端口/datadir完全一致；
- ROLE Runtime=DISABLED、Canary=NOT_AUTHORIZED_NOT_ENABLED、Kill Switch=STOP_NEW_AND_CLAIM、production Directory provider=DISABLED；
- Fixture ID/业务键无冲突且状态为 `NONE_PRESENT`。

模板：`database/test-fixtures/rc2/rc2-test-environment-marker.template.json`。模板本身不是授权 marker；下一次 S9 必须在全新环境构建完成并校验 fingerprint 后生成实际 marker。

## 6. Contract 与 Dry Run

Guard 内置纯策略 `ContractTest`，不连接数据库：

- 合法 Ephemeral RC2 TEST：PASS；
- RC1 34061、RC1 datadir、错误 fingerprint、错误 latest Migration、错误 SHA、ROLE Runtime enabled、Canary authorized、非安全 Kill Switch、partial fixture、Fixture ID conflict、Fixture business-key conflict、缺少 RC2 identity、production identity：全部按预期拒绝。

结果：14/14 PASS。

Dry Run 对合法模拟 Ephemeral RC2 TEST 输出 17 项 `CHECK_*=PASS`，并输出 `GUARD_DRY_RUN=PASS`、`DATABASE_CONNECTION=NOT_ATTEMPTED`、`DATABASE_MODIFIED=NO`、`FIXTURE_EXECUTED=NO`。

Java Fixture Contract Test：9/9 PASS。后端全量测试：689 run，681 pass，0 failure，0 error，8 skipped；Spring Context PASS。

## 7. 资产保护

- Migration SHA：45/45 PASS。
- V2.6.21：`cb9555ba5f81c292654286fe8c7396cdead4b71c9387c4b73d09139d4147296e`。
- V2.6.22：`658b4939a7e92295527ef3a5116d20814b0d17e4c34e46f3955e4c21c67dcc05`。
- V2.6.23：`874427e1df075042485c9098ad2f1b79dbc632200f01614f85529b30765c169d`。
- V2.6.24：未创建。
- 历史 Migration 漂移：NO。

## 8. 状态与下一步

- `DATABASE_MODIFIED=NO`
- `FIXTURE_EXECUTED=NO`
- `RC1_TEST_DATABASE_TOUCHED=NO`
- `ROLE_RUNTIME=DISABLED`
- `CANARY=NOT_AUTHORIZED_NOT_ENABLED`
- `KILL_SWITCH=STOP_NEW_AND_CLAIM`
- `S4_REAL_MYSQL_CONCURRENCY_DEBT=OPEN`

S8.1 修复完成后可以从全新 datadir 重试 RC2-S9；不得复用已丢弃的 S9 环境，也不得直接进入 Canary Scope Change Approval、RC2-S10 或 Runtime Activation。
