# Workflow V1 RC2-S9 Retry #2 Controlled Canary Validation Report

## 1. 最终结论

`RC2_S9_RETRY_2=FAIL`。

本次在创建 MySQL 临时环境前执行 Fixture 资产预检，发现 S8.2 冻结资产与 Retry #2 的核心 Gate 不一致。根据“再次发现 Fixture Bug 直接 FAIL，不得边改边继续”的失败策略，本次未创建 MySQL/datadir/database，未执行 Baseline/Flyway/Guard/Seeder，未触碰 RC1 TEST 数据库。

## 2. 阻断根因

Retry #2 要求：

```text
DIRECTORY_EFFECTIVE_AT
= REVISION_HASH_EFFECTIVE_AT
= DIRECTORY_QUERY_EFFECTIVE_AT
```

当前冻结 Fixture 实际为：

- `FROM = 2026-01-01T00:00:00Z`；
- `DISCOVERY_AT = 2030-06-01T00:00:00Z`；
- Revision Result/Revision Head 使用 `FROM`；
- Directory verification 使用 `DISCOVERY_AT`；
- Seeder 显式断言两个 Hash 不相等；
- Dry Run 同样声明 `REVISION_HEAD_AT_2026-01-01;DISCOVERY_RESULT_AT_2030-06-01`；
- Fixture manifest 同时冻结 `from=2026-01-01` 与 `discoveryAt=2030-06-01`。

这与本次任务的“三者必须一致”是确定性冲突，不需运行数据库即可证明 Gate 必然失败。

## 3. 精确证据

| 资产 | 位置 | 证据 |
|---|---:|---|
| `Rc2ControlledCanaryFixtureSeederTest.java` | 72 | `FROM = 2026-01-01T00:00:00Z` |
| `Rc2ControlledCanaryFixtureSeederTest.java` | 74 | `DISCOVERY_AT = 2030-06-01T00:00:00Z` |
| `Rc2ControlledCanaryFixtureSeederTest.java` | 127-130 | 两次 Directory Query 分别使用 `FROM` 与 `DISCOVERY_AT` |
| `Rc2ControlledCanaryFixtureSeederTest.java` | 135 | 明确要求两个 result hash 不相等 |
| `04_rc2_canary_publish.ps1` | 32 | Dry Run 明确声明 2026 Revision / 2030 Discovery |
| `rc2-canary-fixture-manifest.json` | effectiveWindow | `from` 与 `discoveryAt` 不同 |

## 4. 执行状态

| 阶段 | 状态 | 说明 |
|---|---|---|
| Fixture asset preflight | FAIL | Directory effectiveAt contract 与 Retry #2 Gate 冲突 |
| Fresh MySQL/datadir | NOT_STARTED | Fail Closed |
| 17 Baseline / Flyway | NOT_STARTED | 无数据库目标 |
| Guard | NOT_STARTED | 无数据库目标 |
| Hybrid Seeder | NOT_STARTED | 禁止在已知 Gate 不合格时执行 |
| Candidate Discovery | NOT_STARTED | Fixture 未建立 |

## 5. 安全与资产治理

- 未修改 Fixture 业务资产、S9 bootstrap、生产代码或 Migration。
- 未创建或修改 V2.6.24。
- 未创建 MySQL 实例、端口、datadir 或 database。
- 未创建 Workflow Instance、Task、Candidate Pool、Claim、Admission 或 Realtime Eligibility Evidence。
- ROLE Runtime 仍为 `DISABLED`，Canary 仍为 `NOT_AUTHORIZED_NOT_ENABLED`，Kill Switch 仍为 `STOP_NEW_AND_CLAIM`。
- `S4_REAL_MYSQL_CONCURRENCY_DEBT=OPEN`。

## 6. 后续处理

必须回到独立 Fixture 资产 Sprint，在不改动生产 Canonical 语义的前提下，冻结唯一 effectiveAt，并同步 Seeder、Dry Run、manifest 与契约测试。资产修复并重新回归后，必须使用全新端口与 datadir 再次执行 RC2-S9；不得复用 38471/38472 或任何历史 S9 环境。
