# Workflow V1 RC2-S9A.1 Structural Fingerprint Baseline Reconciliation

## 1. 结论

本次对账结论为 `PASS`。唯一根因归类为 `EXPECTED_BASELINE_GENERATED_WITH_OLD_ALGORITHM`：S9A 的 `154c19d...` 是未版本化 mock 基线，仓库及历史验收证据中均不存在可重建该值的 canonical 文本；真实 RC2 Fresh 证据按旧 Collector V1 算法可精确重建 `73eae9...`。本次没有把真实值直接替换为 expected，而是建立 `RC2_SCHEMA_STRUCTURAL_CANONICAL_V2`，经双 Fresh 与 Upgrade 三个独立 MySQL 8.4.9 数据目录验证后，将权威基线晋级为 `92ea9f...`。

未执行 Fixture，未触碰 RC1 TEST 数据库 `127.0.0.1:34061` 或 `D:\mysql-rc1\data`，未修改 Migration，未创建 V2.6.24，ROLE Runtime、Canary 与 Kill Switch 状态均未改变。

## 2. Expected 与 Current 来源审计

| 项目 | 值 | 来源 |
|---|---|---|
| 旧 Expected | `154c19d739393a00b2dd84631be3753c8adfe4c9557059616331e142716f77c2` | S9A mock verifier/collector 常量及其架构报告；未保存 canonical 文本，算法未版本化 |
| S9B Current | `73eae9b42a61f87288230d09c01c56017ef9492ebe272478c86609fd9f02daeb` | S9B 真实 Fresh 2.6.23 的 V1 Collector 输出；由保留的 `schema.txt` 按 V1 查询、前缀、注释排除及 LF 编码规则精确重建 |
| 新权威 Expected | `92ea9f4d006233f672b22c3c335663c04121dbb652ce657aac07d7b04518171d` | `rc2-schema-fingerprint-baseline.json`，canonical 版本 `RC2_SCHEMA_STRUCTURAL_CANONICAL_V2` |

旧 expected 没有可追溯 canonical 内容，因此 `structural-canonical-expected.txt` 保存的是从真实历史 Fresh Schema 重建的 V1 canonical，而不是声称能够逆向还原 `154c19d...`。该文件的 SHA-256 精确为 `73eae9...`，此限制已在差异证据中显式记录。

## 3. Canonical V2 规则

Structural Fingerprint 只覆盖运行语义结构：TABLE、COLUMN、类型、NULLABLE、DEFAULT、COLLATION、主键/唯一键/普通索引、外键、CHECK、TRIGGER 与 generated column。COMMENT、表/Schema 创建时间、AUTO_INCREMENT 当前值、文件路径、server UUID 和内部临时元数据均排除；Metadata Fingerprint 继续独立作为 evidence-only 信号。

所有集合均显式 `ORDER BY`，字符串以 UTF-8、LF、无 BOM 写出。表达式保留数据库返回语义并用 UTF-8 hex 固定边界，避免 CLI/JDBC 转义差异；不剥离括号，也不进行可能改变 SQL 语义的解析重写。

- TABLE：表名、引擎、字符集与 collation。
- COLUMN：序号、名称、column type、nullable、稳定 default marker、字符集/collation、`EXTRA`、`GENERATION_EXPRESSION`。
- DEFAULT：`<DEFAULT_NULL>`、`<NO_DEFAULT>`、`<DEFAULT_VALUE_HEX>` 与 `<GENERATED_NO_DEFAULT>` 分离；字符串 `NULL` 作为实际值编码，不与 SQL NULL 碰撞。MySQL `information_schema` 无法恢复 nullable 列在源 DDL 中“省略 default”和“显式 DEFAULT NULL”的文本差别，V2 固定其等价运行语义，不伪造不可恢复的源文本差异。
- INDEX：唯一性、列序、列或表达式、排序、前缀长度、nullable、索引类型、可见性。
- FOREIGN KEY：精确 source/reference 列、ordinal、UPDATE/DELETE rule。
- CHECK：名称、enforced 标志、完整 clause；只固定编码与边界，不随意剥离 MySQL 8.4 规范化括号。
- TRIGGER：event、table、timing、orientation、condition 与完整 action statement；三次独立实例结果稳定，因此保留 action statement。
- GENERATED COLUMN：`EXTRA` 与 generation expression 均参与指纹。

## 4. Canonical 差异证据

一次性证据根目录：`D:\codex-rc2-s9a1-20260824-172352`。

| 文件 | 内容 |
|---|---|
| `structural-canonical-expected.txt` | 从历史真实 Fresh Schema 精确重建的 V1 canonical |
| `structural-canonical-current.txt` | Fresh A 的 V2 canonical |
| `structural-canonical-diff.txt` | 差异总数与前 50 条 EXPECTED/ACTUAL/OBJECT_TYPE/OBJECT_NAME/FIELD |
| `fingerprint-reconciliation-summary.json` | 对账机器可读摘要 |

语义 keyed diff 共 `7551` 条，来源是 V1 到 V2 的字段表达和证据覆盖升级，而非 Migration/真实 Schema 漂移。V2 canonical 共 7,397 条：TABLE 192、COLUMN 3,807、INDEX 2,262、TRIGGER 81、FK 492、CHECK 563；Fresh A、Fresh B 与 Upgrade 的 canonical 字节完全一致。

## 5. 三路径真实 MySQL 验证

环境为 MySQL Community Server 8.4.9。三个独立 datadir 均使用 17 项 Baseline、显式 Flyway baseline 2.0.0、`baselineOnMigrate=false`、migrate、strict validate 与二次 migrate no-op。

| 路径 | Structural fingerprint | Metadata fingerprint | 结果 |
|---|---|---|---|
| Fresh A | `92ea9f4d006233f672b22c3c335663c04121dbb652ce657aac07d7b04518171d` | `2d01817ef3853231cc32f4275577984138c67a9461664fbf28d98c8a6e5e0c03` | PASS |
| Fresh B | `92ea9f4d006233f672b22c3c335663c04121dbb652ce657aac07d7b04518171d` | `2d01817ef3853231cc32f4275577984138c67a9461664fbf28d98c8a6e5e0c03` | PASS |
| V2.6.22 → V2.6.23 Upgrade | `92ea9f4d006233f672b22c3c335663c04121dbb652ce657aac07d7b04518171d` | `2d01817ef3853231cc32f4275577984138c67a9461664fbf28d98c8a6e5e0c03` | PASS |

`DOUBLE_FRESH_MATCH=YES`，`FRESH_UPGRADE_STRUCTURAL_MATCH=YES`。这三个 datadir 均与 RC1 TEST 环境隔离。

## 6. Attestation 与负向矩阵

真实 Fresh A evidence 经 V2 Collector/Verifier 验证通过，attestation ID 为 `41c9f9dc5e4a4917f8bc95b84ae683dd66b778557c6d61a021c0debbe61f072d`。

| 场景 | 预期 | 结果 |
|---|---|---|
| 合法真实 Fresh evidence | PASS | PASS |
| 删除 Column | FAIL | PASS |
| 修改 Column type | FAIL | PASS |
| 删除 FK | FAIL | PASS |
| 删除 Trigger | FAIL | PASS |
| 修改 CHECK | FAIL | PASS |
| 仅修改 comment | Structural PASS，Metadata 改变 | PASS |

PowerShell 契约测试在 PowerShell 7 与 Windows PowerShell 5.1 均为 32/32 PASS；Java `Rc2ControlledCanaryFixtureContractTest` 为 10/10 PASS。三个修改的 PowerShell 脚本均为 0 parser error，JSON 资产解析通过。

## 7. Migration 与资产保护

Migration SHA 清单为 `45/45 PASS`。V2.6.21、V2.6.22、V2.6.23 分别保持：

- `cb9555ba5f81c292654286fe8c7396cdead4b71c9387c4b73d09139d4147296e`
- `658b4939a7e92295527ef3a5116d20814b0d17e4c34e46f3955e4c21c67dcc05`
- `874427e1df075042485c9098ad2f1b79dbc632200f01614f85529b30765c169d`

未修改任何历史 Migration，未创建 V2.6.24。旧 S9A/S9B 报告中的失败值作为历史证据原样保留；当前执行资产统一从单一 JSON 基线读取 expected 值，避免 Collector 与 Verifier 常量再次漂移。

## 8. 修改文件

- `database/test-fixtures/rc2/rc2-schema-fingerprint-baseline.json`
- `database/test-fixtures/rc2/collect-rc2-environment-evidence.ps1`
- `database/test-fixtures/rc2/verify-rc2-environment-attestation.ps1`
- `database/test-fixtures/rc2/test-rc2-environment-attestation.ps1`
- `database/test-fixtures/rc2/rc2-canary-fixture-manifest.json`
- `database/test-fixtures/rc2/rc2-test-environment-marker.template.json`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/fixture/Rc2ControlledCanaryFixtureContractTest.java`
- `docs/workflow-rc2-s9a1-structural-fingerprint-baseline-reconciliation-report.md`

## 9. 剩余风险与门禁

- `154c19d...` 没有保留 canonical 原文，无法做密码学意义上的旧 expected 内容重建；本报告明确保留该治理缺口，没有把推断包装为证据。
- Canonical V2 以 MySQL 8.4.9 的 `information_schema` 语义为冻结边界；MySQL 跨大版本升级必须重新做双 Fresh/Upgrade 稳定性审计，不能静默沿用。
- S4 真实 MySQL 并发债务仍为 `OPEN`。
- 本次只恢复重试 RC2-S9B 的前置条件；不授权 Canary scope 变化、RC2-S10 或 ROLE Runtime Canary activation。

最终状态：`RC2_S9A_1=PASS`，`READY_TO_RETRY_RC2_S9B=YES`，`ROLE_RUNTIME=DISABLED`，`CANARY=NOT_AUTHORIZED_NOT_ENABLED`，`KILL_SWITCH=STOP_NEW_AND_CLAIM`。
