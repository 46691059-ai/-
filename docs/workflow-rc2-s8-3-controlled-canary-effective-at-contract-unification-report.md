# Workflow V1 RC2-S8.3 Controlled Canary Fixture EffectiveAt Contract Unification

## 1. 结论

RC2 Controlled Canary Fixture 已收敛为唯一 Directory EffectiveAt Contract：

```text
RC2_CANARY_DIRECTORY_EFFECTIVE_AT=2026-01-01T00:00:00Z
```

唯一可执行字面量位于 `rc2-canary-fixture-manifest.json` 的 `effectiveWindow.directoryEffectiveAt`。Java Seeder、Assignment 窗口、Revision/Revision Head Hash 校验、Directory Query、PowerShell Dry Run、SQL Verify 与契约测试均由此派生。生产 `NODE_ACTIVATED_AT` 语义未改动。

## 2. 根因

S8.2 将不同时点的 Hash 分别校验为合法，但仍保留了两个可独立修改的 Fixture 时间源：Revision/Assignment 使用 2026 时点，Discovery/Verify 使用 2030 时点。Retry #2 的 Pre-execution Gate 正确地在创建数据库前拒绝了该分叉。

## 3. 精确搜索与分类

### 3.1 修复前 STALE_LITERAL

| 文件 | 旧字面量/用途 | 处理 |
|---|---|---|
| `Rc2ControlledCanaryFixtureSeederTest.java` | `FROM=2026` + `DISCOVERY_AT=2030` | 改为读取集中 Contract |
| `Rc2DirectoryEffectiveAtHashContractTest.java` | 独立 2026/2030 常量 | 改为读取集中 Contract |
| `04_rc2_canary_publish.ps1` | Dry Run 硬编码 2026/2030 | 改为读取 manifest |
| `05_rc2_canary_verify.sql` | Assignment Verify 硬编码 2030 | 改为从已冻结 Revision 事实派生 |
| `rc2-canary-fixture-manifest.json` | `from` + `discoveryAt` 双时点 | 收敛为 `directoryEffectiveAt` |

### 3.2 AUTHORITATIVE

| 文件 | 位置 | 说明 |
|---|---|---|
| `database/test-fixtures/rc2/rc2-canary-fixture-manifest.json` | `effectiveWindow.directoryEffectiveAt` | 唯一权威可执行字面量 |

### 3.3 DERIVED

| 文件 | 派生方式 |
|---|---|
| `Rc2ControlledCanaryFixtureContract.java` | 读取 manifest 并解析 `Instant` |
| `Rc2ControlledCanaryFixtureSeederTest.java` | 使用 Contract 字段创建 Assignment/Directory Query |
| `00_rc2_canary_guard.ps1` | 读取 manifest，执行唯一性/残留字面量 Gate |
| `04_rc2_canary_publish.ps1` | 读取 manifest，输出三个同值证据 |
| `05_rc2_canary_verify.sql` | 从由 manifest 创建的 Revision `effective_from` 派生会话变量 |
| `Rc2ControlledCanaryFixtureContractTest.java` | 静态资产契约 |
| `Rc2DirectoryEffectiveAtHashContractTest.java` | Assignment/Hash/Gate 契约 |

### 3.4 DOCUMENTATION_HISTORICAL_EVIDENCE

下列文档保留旧 2026/2030 差异作为失败证据，不会被脚本、Seeder 或测试读取：

- `docs/workflow-rc2-s8-2-controlled-canary-fixture-execution-repair-report.md`
- `docs/workflow-rc2-s9-retry-controlled-canary-validation-report.md`
- `docs/workflow-rc2-s9-retry-2-controlled-canary-validation-report.md`

`database/flyway/scripts` 内其他 Approval Role/Migration 隔离验收脚本中的 2026 时点属于其各自独立测试向量，不被 RC2 Fixture/S9 读取，不属于本 Fixture Contract 的重复权威源。

## 4. Assignment 与 Hash 契约

- U1 `990201` 和 U2 `990202` 均使用 `[directoryEffectiveAt, assignmentEffectiveTo)`。
- 权威时点是包含式起点，两条 Assignment 同时有效，候选数精确为 2。
- Revision 生成、Revision Head 与 Directory Result 使用相同 `effectiveAt` 和相同 Canonical Input，Hash 必须相等。
- SQL Verify 从 `approval_role_revision(id=990305).effective_from` 派生只读会话变量，不使用当前时间、2030 或 Node Activation 模拟值。

## 5. Pre-execution Gate

Guard 在任何数据库连接前：

1. 校验 manifest EffectiveAt 是规范 UTC 秒精度值；
2. 校验 Assignment 结束时点晚于权威时点；
3. 对 Fixture Java/PowerShell/SQL/manifest 执行可执行旧 2030 字面量扫描；
4. 要求权威 2026 字面量在可执行资产中恰好出现 1 次；
5. 要求 Seeder 读取中央 Contract，Verify SQL 读取派生会话 Contract；
6. 对 Revision/Query mismatch 和 stale literal 合成场景 Fail Closed。

## 6. 测试与静态验证

- PowerShell Parser：2/2 脚本 0 parse errors。
- Guard Contract Matrix：18/18 PASS（包含 4 项 EffectiveAt Gate）。
- EffectiveAt Contract Test：12/12 PASS。
- Fixture 定向组合：21/21 PASS。
- Backend Full Tests：701 run，0 failures，0 errors，8 skipped；Spring Context PASS。
- Fixture Dry Run：PASS。
- Pre-execution Asset Gate Dry Run：PASS。
- `EXECUTABLE_STALE_2030_LITERAL_COUNT=0`。
- `EXECUTABLE_AUTHORITATIVE_EFFECTIVE_AT_LITERAL_COUNT=1`。
- 未创建/修改数据库，未执行 Seeder/Fixture，未重跑 S9。

## 7. 保护边界

- 未修改 Workflow/Approval Role 生产代码、Canonical/Hash 算法、Migration 或 Runtime Gate。
- `effectiveTimePolicy=NODE_ACTIVATED_AT` 仍是生产 Runtime 语义；Fixture 证据时点不改写该策略。
- V2.6.21/V2.6.22/V2.6.23 不变，不创建 V2.6.24。
- ROLE Runtime=`DISABLED`，Canary=`NOT_AUTHORIZED_NOT_ENABLED`，Kill Switch=`STOP_NEW_AND_CLAIM`。
- `S4_REAL_MYSQL_CONCURRENCY_DEBT=OPEN`。

## 8. 下一步

S8.3 资产已满足重试 RC2-S9 的前置条件。下一次 S9 必须使用新的 MySQL 8.4 datadir 与动态高位端口，不得复用 38471/38472；仍需依次通过 Fresh/Flyway/Guard 后才能执行 Fixture。
