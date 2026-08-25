# Workflow V1 RC2-S7 V2.6.23 Realtime Eligibility Remark 验收报告

## 1. 最终结论

`V2.6.23__add_workflow_realtime_eligibility_remark.sql` 已在隔离的 MySQL Community Server 8.4.9 / Flyway 13.0.0 环境完成 Fresh 与 V2.6.22 Upgrade 双路径验收。三张目标表的 `remark` 持久化契约、历史 NULL 兼容、真实 MyBatis Mapper 空值及非空值写入回读均通过。

最终状态：

- `RC2_S7=PASS`
- `V2.6.23=CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- `WORKFLOW_MAPPING_DRIFT_COUNT=0`（指三张 RC2-S7 目标表的可持久化 Entity/Schema 契约）
- `ROLE_RUNTIME=DISABLED`
- `CANARY=NOT_AUTHORIZED_NOT_ENABLED`
- `KILL_SWITCH=STOP_NEW_AND_CLAIM`

## 2. Preflight 契约审计

三个 Entity 均通过 `WorkflowAuditedEntity` 继承 `remark`，没有在子 Entity 重复声明，也没有通过 `exist=false` 规避持久化。现有 Workflow 表的统一契约为：

| 项目 | 冻结值 |
| --- | --- |
| 列名 | `remark` |
| SQL 类型 | `VARCHAR(500)` |
| 可空 | `YES` |
| 默认值 | `NULL`（未声明 DEFAULT） |
| 字符集 | `utf8mb4` |
| Collation | `utf8mb4_0900_ai_ci` |
| 顺序 | `delete_token` 后、`version` 前 |

V2.6.23 仅执行三个显式 `ALTER TABLE ... ADD COLUMN`，未使用 `IF NOT EXISTS`，未改动索引、外键、Trigger、Hash、`version`、`deleted` 或 `delete_token`。

## 3. Migration 资产

| 项目 | 结果 |
| --- | --- |
| 文件 | `database/migration/mysql/V2.6.23__add_workflow_realtime_eligibility_remark.sql` |
| SHA-256 | `874427e1df075042485c9098ad2f1b79dbc632200f01614f85529b30765c169d` |
| Flyway checksum | `445023774` |
| 目标表 | 3 |
| 新增列 | 3 |
| Migration SHA | `45/45 PASS` |
| V2.6.21 SHA | `cb9555ba5f81c292654286fe8c7396cdead4b71c9387c4b73d09139d4147296e`，保持不变 |
| V2.6.22 SHA | `658b4939a7e92295527ef3a5116d20814b0d17e4c34e46f3955e4c21c67dcc05`，保持不变 |

目标表：

1. `workflow_role_realtime_eligibility_capability_evidence`
2. `workflow_role_realtime_eligibility_event`
3. `workflow_role_realtime_eligibility_validator_evidence`

## 4. 真实 MySQL/Flyway 验收

证据目录：`D:/codex-rc2-v2623-20260824-111106/evidence`。

### Fresh 路径

- 17 项 Baseline 完整导入。
- 显式 Flyway baseline `2.0.0`。
- 完整迁移至 `2.6.23`。
- Flyway history 失败数：`0`。
- strict validate：`PASS`。
- 第二次 migrate：`NO_OP`。
- Schema fingerprint：`ffcd3b9c031c6a8521fe4cbdb1d3c248e49c50e2ccfa4860ce9aea04b5caacd0`。

### Upgrade 路径

- 来源版本：`2.6.22`。
- 目标版本：`2.6.23`。
- 仅执行 V2.6.23 一次。
- Flyway history 失败数：`0`。
- strict validate：`PASS`。
- 第二次 migrate：`NO_OP`。
- Schema fingerprint：`ffcd3b9c031c6a8521fe4cbdb1d3c248e49c50e2ccfa4860ce9aea04b5caacd0`。
- 升级前构造的历史行在升级后 `remark IS NULL`：`PASS`。

Fresh 与 Upgrade 最终 Schema fingerprint 完全一致。

## 5. 真实 Mapper 与 Mapping Checker

`V2623RealtimeEligibilityRemarkMysqlMappingTest` 在临时 MySQL 2.6.23 Schema 上实际启动 Spring Context，并通过三个真实 MyBatis Mapper 分别验证：

- `remark=null` 插入：`PASS`。
- `remark='RC2_TEST_REMARK'` 插入：`PASS`。
- 三张表 SELECT readback：`PASS`。
- 测试结果：`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
- 测试事务回滚，临时实例随后销毁。

三张 RC2-S7 目标表的 Entity/Schema drift 已全部关闭：

- `CAPABILITY_EVIDENCE_REMARK_MAPPING=PASS`
- `REALTIME_ELIGIBILITY_EVENT_REMARK_MAPPING=PASS`
- `VALIDATOR_EVIDENCE_REMARK_MAPPING=PASS`
- `NEW_RC2_S7_MAPPING_DRIFT_COUNT=0`
- `WORKFLOW_MAPPING_DRIFT_COUNT=0`

全库 Checker 仍报告既有 DB-superset：五个 Investment 映射报告，以及 `workflow_role_runtime_execution_admission_event` 的两个数据库生成治理列。它们不是本次三个 `remark` 持久化缺口，也未被本 Sprint 改写或重新归类。

## 6. 应用回归

Java 21.0.12 / Maven 3.9.9 全量测试：

| 指标 | 结果 |
| --- | --- |
| Tests run | 679 |
| Passed | 672 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 7（既有外部环境条件测试及默认关闭的真实 MySQL opt-in 测试） |
| Java 21 compile | PASS |
| Spring Context | PASS |
| S3 Golden Hash | unchanged / PASS |
| S4 Release regression | PASS |
| S5 Instance Freeze regression | PASS |
| S6 Directory Bridge regression | PASS |
| Legacy USER regression | PASS |

真实 MySQL opt-in Mapper 测试另行执行且 `Skipped=0`，不以默认全量测试中的条件跳过替代真实验收。

## 7. 安全、隔离与清理

- Fresh/Upgrade 使用独立一次性 datadir 与端口 `38241/38242`。
- 未使用或修改 RC1 TEST 端口 `34061` 与 `D:/mysql-rc1/data`。
- 验收结束后两个临时 MySQL 均已停止，datadir 已删除。
- 未创建 Task、Candidate Pool、Claim，未启用 ROLE Runtime 或 Canary。
- 未执行 commit、tag 或 push。
- `git diff --check=PASS`。

## 8. 剩余风险与下一步

- `S4_REAL_MYSQL_CONCURRENCY_DEBT=OPEN`，本 Sprint 未顺带关闭。
- 全库 Mapping Checker 的既有 DB-superset 报告仍应在后续独立治理中明确 allowlist/语义，避免与 Entity 缺列型 drift 混淆。
- RC2-S7 已满足进入下一独立 Sprint 的条件，但仍不具备 ROLE Runtime Canary 激活授权。

建议下一步：进入 RC2-S8 前继续保持 `ROLE_RUNTIME=DISABLED`、Canary 关闭及 Kill Switch 安全态，并单独规划 S4 的真实 MySQL 并发债务验收。
