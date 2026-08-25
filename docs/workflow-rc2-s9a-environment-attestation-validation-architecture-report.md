# Workflow V1 RC2-S9A Environment Attestation Validation Architecture

## 1. 结论

RC2-S9A 已建立 `ONE EVIDENCE SOURCE / ONE DECISION ENGINE / ONE FIXTURE CONTRACT`：

```text
Fresh Bootstrap
  -> Environment Evidence Collector
  -> environment-evidence.json
  -> Environment Attestation Verifier
  -> RC2_ENVIRONMENT_ATTESTATION_ID
  +  Fixture Contract Verifier
  -> READY_TO_EXECUTE_FIXTURE
  -> test-only Seeder consumes passed Attestation ID
```

本任务只使用 Mock Evidence、静态契约与历史 Fresh Schema 证据，没有创建或修改数据库，没有执行 Fixture、Candidate Discovery、Canary 或Runtime。

持久DryRun证据：`D:\codex-rc2-s9a-attestation-20260824-162204`。

## 2. 旧链路审计

| 关注点 | 原实现位置 | 重复/耦合问题 | 新归属 |
|---|---|---|---|
| Pre-execution Gate | `00_rc2_canary_guard.ps1`, `04_rc2_canary_publish.ps1` | 静态契约、环境身份与Runtime策略混合 | Fixture Contract Verifier + Attestation Verifier |
| Runtime Guard | `00_rc2_canary_guard.ps1` | 同时查询MySQL、读marker、算fingerprint并判定 | Collector采集；Verifier唯一判定 |
| S9 Bootstrap | 外部验收编排、Flyway脚本 | 端口、路径、数量和SHA重复判断 | Bootstrap只建库；Collector统一形成事实 |
| Migration Snapshot | 外部编排、`validate-rc2-v2623-real-mysql.ps1` | 宽泛枚举与错误的 literal wildcard 风险 | Collector按SHA256SUMS逐项复制与复验 |
| Fingerprint | Flyway验收脚本、旧Guard | 一个Hash同时承载结构与注释元数据 | Structural硬Gate + Metadata证据/告警 |
| Marker | Bootstrap、旧Guard | parser和字段比较重复 | Collector读取一次，raw嵌入Evidence；Verifier只读Evidence |
| Fixture Contract | manifest、Guard、Seeder、Java契约测试 | EffectiveAt与业务契约散落 | Fixture Contract Verifier |
| Runtime Safety | manifest、marker、Guard、Seeder配置 | 多套状态解释 | Collector采事实；Attestation统一Fail Closed |

`LEGACY_DUPLICATED_CHECKS=HOST,PORT,DATADIR,DATABASE,MYSQL_VERSION,MIGRATION_HISTORY,MIGRATION_SHA,SCHEMA_FINGERPRINT,MARKER,RUNTIME_SAFETY,FIXTURE_CONFLICT,EFFECTIVE_AT`。

## 3. Environment Evidence Collector

新增 `collect-rc2-environment-evidence.ps1`。Collector只输出事实，不产生环境PASS/FAIL或ELIGIBLE结论：

- MySQL identity、requested/actual port、database和datadir；
- marker path/version/raw；
- latest/failed/count与Migration SHA事实；
- structural/metadata两个Schema fingerprint；
- ROLE Runtime、Canary、Kill Switch和Production Directory Provider事实；
- Fixture ID、业务键和partial状态；
- raw Fixture EffectiveAt string；
- Migration Snapshot source/destination count与逐文件SHA结果。

所有MySQL查询固定 `--default-character-set=utf8mb4`。PowerShell 7使用 `System.Text.Json` 按token读取，PowerShell 5.1使用 `JavaScriptSerializer`；ISO值进入Evidence前显式保持 `System.String`。

Migration Snapshot 的权威输入是 `SHA256SUMS` 中45个明确文件名。实现先枚举清单，再对每个 source 执行 `Copy-Item -LiteralPath $source`，复制后重新验证45个destination SHA。不存在 `Copy-Item -LiteralPath "...V*.sql"`。

## 4. Environment Attestation Verifier

新增 `verify-rc2-environment-attestation.ps1`。Verifier只读取 `environment-evidence.json` 与其中嵌入的 `environmentMarkerRaw`：

- 不调用MySQL；
- 不调用端口探测；
- 不读取marker源文件；
- 不扫描Migration目录；
- 不重新计算Schema fingerprint；
- 一次计算全部断言并输出完整 `failedAssertions[]`。

Metadata fingerprint漂移只进入 `warnings[]`；Structural fingerprint漂移阻断。合法Evidence生成：

```text
RC2_ENVIRONMENT_ATTESTATION_ID=
fbb1ec22d2244b50a19935ad6d48fcd94e8adf11a8995e3876626925b4ccb0ae
```

ID使用 `RC2_ENVIRONMENT_ATTESTATION_CANONICAL_V1` 的显式长度前缀Canonical SHA-256，绑定host、requested/actual port、requested/actual datadir、database、marker identity、latest Migration、Structural Fingerprint、Migration SHA摘要和Runtime Safety状态。timestamp不参与Hash。

## 5. Structural与Metadata Fingerprint

权威V2.6.23基准由已保留的Fresh Schema证据推导：

- `SCHEMA_STRUCTURAL_FINGERPRINT=154c19d739393a00b2dd84631be3753c8adfe4c9557059616331e142716f77c2`；
- `SCHEMA_METADATA_FINGERPRINT=ea9c481e5ef23ea37366f97178f7f5b9a8b30357d5e342c41740e558d63ad47a`。

Structural包括table、column/type/null/default/collation/generated-extra、index/PK/unique、FK、CHECK和trigger，不包含table/column comment。Metadata只包含table/column comment。中文metadata变化测试保持Structural不变且只产生warning。

## 6. Fixture Contract Verifier

新增 `verify-rc2-fixture-contract.ps1`，仅验证：

- fixture contract与RC2 TEST身份；
- raw authoritative EffectiveAt和assignment有效窗口；
- U1/U2、expected candidate count=2；
- enterprise、organization、roleCode与保留ID；
- SINGLE_APPROVAL_NODE / SINGLE_NODE_LEGACY；
- VERSION_RESOLVER_BINDING_CAPABLE；
- ROLE_DIRECTORY / ROLE_DIRECTORY_V1 / FIXED_ORG / NODE_ACTIVATED_AT；
- Seeder继续消费中央EffectiveAt契约且不启用Runtime。

它不检查MySQL、端口、datadir、Migration、Schema fingerprint或Runtime Safety。DryRun为16/16 PASS。

## 7. 旧Guard与Seeder边界

`00_rc2_canary_guard.ps1` 采用 `DEPRECATED_COMPATIBILITY_WRAPPER_TO_ATTESTATION_VERIFIER`：

- 不再采集或实现环境策略；
- DryRun仅编排Mock Collector、唯一Attestation Verifier和Fixture Contract Verifier；
- Preflight兼容入口只接受Collector Evidence并委托唯一Verifier；
- 不再作为第二权威Gate。

`04_rc2_canary_publish.ps1` 在执行前消费 `environment-evidence.json` 与通过的attestation result，并只做执行目标与已证明Evidence的精确绑定。test-only Seeder新增强制消费 `RC2_ENVIRONMENT_ATTESTATION_ID` 和attestation result；它不重新运行Guard或重新解析候选。

## 8. Contract Tests与兼容性

Attestation矩阵覆盖26项：合法Evidence、全部失败聚合、MySQL/port/RC1 port/RC1 datadir/datadir mismatch、Migration/failed/count/SHA、Structural与Metadata、Runtime/Canary/Kill Switch/provider、Fixture冲突/partial、marker、snapshot zero、literal wildcard、PS5/PS7 ISO string、中文metadata以及完整失败集合。

结果：

- PowerShell 7.6.4：26/26 PASS；
- Windows PowerShell 5.1：26/26 PASS；
- 两端Attestation DryRun：PASS；
- 两端Fixture Contract DryRun：16/16 PASS；
- 两端Attestation ID完全一致；
- Java Fixture Contract：22 tests，0 failures，0 errors，0 skipped；
- Migration Snapshot：45 source / 45 destination / 45/45 SHA PASS。

## 9. 后续S9唯一流程

下一次RC2-S9必须执行：静态Fixture Contract -> Fresh MySQL -> 按SHA清单构建Migration Snapshot -> Bootstrap V2.6.23 -> Collector -> Evidence JSON -> Attestation Verifier -> Fixture Contract Verifier -> Seeder -> Post-Fixture Read-only Verify -> Candidate Discovery。

不得再次把旧Guard作为第二套判断，不得使用宽泛wildcard作为权威Migration清单。`ROLE_RUNTIME=DISABLED`，`CANARY=NOT_AUTHORIZED_NOT_ENABLED`，`KILL_SWITCH=STOP_NEW_AND_CLAIM`，`S4_REAL_MYSQL_CONCURRENCY_DEBT=OPEN`。
