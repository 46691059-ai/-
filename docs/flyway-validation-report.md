# Flyway 真实环境验收报告

## 1. 验收结论

Sprint 2-1.9 已在隔离的 MySQL 8 空库环境完成真实执行验证。

- 综合结论：**通过，但存在两个上线前治理项**。
- 数据库 Migration 结果：`2.0.0 baseline -> 2.1.0 -> 2.1.1 -> 2.1.2 -> 2.1.3` 全部成功。
- 迁移后 `validate`：通过，checksum 一致。
- 幂等复跑：通过，第二次 `migrate` 无新增历史记录，Schema 指纹保持不变。
- 生产环境：未连接、未执行任何 Migration。
- 业务代码及历史 SQL：未修改。
- 验收后：临时 MySQL 进程已停止，临时数据目录及下载残留已删除。

上线前治理项：

1. 当前权威扫描目录 `database/migration/mysql` 尚无正式 SQL，验收使用的是已登记但未自动扫描的 `database/mysql/migration` 资产。正式发布前必须完成资产晋级审批，不能直接扩大生产扫描范围。
2. Flyway 13.0.0 的独立 `validate` 会把 pending Migration 判为失败。现有发布流程不能无条件执行“严格 validate -> migrate”，需要区分迁移前 pending 检查与迁移后 checksum 严格校验。

## 2. 环境信息

| 项目 | 验收值 |
| --- | --- |
| 验收日期 | 2026-08-03 |
| 操作系统 | Windows 本地隔离验收环境 |
| 数据库 | MySQL Community Server 8.4.9, Win64 x86_64 |
| 数据目录 | 新建临时空数据目录，未注册 Windows 服务 |
| 网络范围 | 仅绑定 `127.0.0.1` 临时端口 |
| Schema | `enterprise_platform` |
| Flyway | Flyway Maven Plugin 13.0.0 |
| Java | OpenJDK 21.0.12+8 |
| Maven | Apache Maven 3.9.11 |
| Migration 来源 | `database/mysql/migration`（验收专用显式位置） |
| 生产连接 | 无 |

本机未安装 Docker，因此采用本机 MySQL 8.4.9 二进制启动临时实例。该差异不影响 SQL、JDBC、Flyway 历史表和 Schema 指纹验证，但容器镜像级验收仍应在 CI 或预生产补做。

## 3. 空库准备

### 3.1 基础结构

在全新 MySQL 数据目录中依次执行：

1. `01_database.sql` 至 `16_sprint_1_log_center.sql`；
2. `V1.1.0__investment_data_risk_bi.sql`；
3. 不执行 `database/mysql/init/00_enterprise_platform.sql`，因为该聚合入口已经包含生命周期 V2 SQL，会使本次 Migration 失去验证意义；
4. 不执行 `V2.0.0__legacy_to_v1.sql`，该脚本仅适用于特定 `pm_*` 历史结构；
5. 使用 Flyway 在 `2.0.0` 建立 baseline，再执行生命周期 V2 Migration。

基础脚本结果：17 个脚本最终全部成功。Migration 前共有 120 张表，`project_lifecycle_%` 表数量为 0。

注意：`V1.1.0__investment_data_risk_bi.sql` 未自行声明默认 Schema。逐文件执行时必须显式指定 `enterprise_platform`；Docker 聚合入口通过同一客户端会话继承 `USE enterprise_platform`，因此不会触发该问题。

## 4. Flyway 执行结果

| 顺序 | 操作 | 结果 | 外部耗时 | 说明 |
| ---: | --- | --- | ---: | --- |
| 1 | `baseline` | 成功 | — | 建立 `2.0.0` 基线；不执行 legacy 脚本 |
| 2 | `info` | 成功 | 2480 ms | 识别 2.1.0—2.1.3 为 pending |
| 3 | 迁移前 `validate` | 预期失败 | 2332 ms | Flyway 13 将 pending 版本判为 validation failure；无 checksum 错误 |
| 4 | `migrate` | 成功 | 8307 ms | Flyway 内部 Migration 执行耗时 5099 ms |
| 5 | 迁移后 `validate` | 成功 | 2304 ms | Flyway 校验耗时 34 ms，成功校验 6 个解析记录 |
| 6 | `info` | 成功 | 2287 ms | 当前版本 2.1.3，4 条 SQL 均为 Success |
| 7 | 第二次 `migrate` | 成功 | 2892 ms | 无新增 Migration，历史成功记录仍为 5 条 |

### 4.1 版本和 checksum

| rank | 版本 | 类型 | Flyway checksum | SQL 执行时间 | 状态 |
| ---: | --- | --- | ---: | ---: | --- |
| 1 | 2.0.0 | BASELINE | — | 0 ms | 成功 |
| 2 | 2.1.0 | SQL | -1798385258 | 2426 ms | 成功 |
| 3 | 2.1.1 | SQL | -491875563 | 2528 ms | 成功 |
| 4 | 2.1.2 | SQL | 1777287545 | 16 ms | 成功 |
| 5 | 2.1.3 | SQL | -1521439258 | 129 ms | 成功 |

版本顺序与文件命名一致。`V2.0.0__legacy_to_v1.sql` 在 `info` 中显示为 `Ignored (Baseline)`，未被执行。

### 4.2 文件资产 SHA-256

| 文件 | SHA-256 |
| --- | --- |
| `V2.1.0__create_project_lifecycle_v2_structure.sql` | `8794cb84f5a7e1857aadcaf7ee29c32ac10df04fad572ce1ef048004a37e5e4f` |
| `V2.1.1__link_project_stage_lifecycle_snapshot.sql` | `6a8cd3095d16670d8f068f6be9ce22384be78c7babb296d88cc359044ecf01a5` |
| `V2.1.2__backfill_legacy_project_lifecycle.sql` | `df4f4355f013d5dc7e0a5a1f93c4e329133049cc3395ee749ce8b7153180e65a` |
| `V2.1.3__seed_project_lifecycle_templates.sql` | `8f6fb5778a8b8a566b8222ff029587e0ed9ccd00f0dccb132d1f552cff4f3e51` |

上述哈希与 `database/flyway/migration-inventory.yml` 一致。Flyway checksum 是 SQL 规范化后的 CRC32 整数，不能与文件 SHA-256 直接比较；二者分别用于运行历史校验和仓库资产完整性校验。

## 5. Migration 规则验证

### 5.1 禁止 clean

- PowerShell 执行包装器仅允许 `info`、`validate`、`migrate`、`baseline`。
- 传入 `clean` 时参数校验立即失败，退出码为 1。
- 包装器固定传递 `cleanDisabled=true`，形成第二层防护。

结论：通过。

### 5.2 禁止 repair

- `repair` 不在包装器允许命令集合中。
- 传入 `repair` 时参数校验立即失败，退出码为 1。
- 未执行任何自动 repair。

结论：通过。

### 5.3 版本顺序

`flyway_schema_history.installed_rank` 为 1—5 连续递增，版本依次为 2.0.0、2.1.0、2.1.1、2.1.2、2.1.3；全部 `success=1`。

结论：通过。

## 6. Schema 一致性与结构指纹

使用 `database/mysql/verification/schema_fingerprint.sql`，在一致性只读事务中对表、字段、索引、外键、检查约束和视图生成规范化数据流，并计算 UTF-8 SHA-256。

| 指标 | 结果 |
| --- | ---: |
| 业务表数（排除 Flyway/发布审计表） | 128 |
| 字段数 | 2145 |
| 索引记录数 | 1104 |
| 外键数 | 187 |
| CHECK 约束数 | 172 |
| 指纹规范行数 | 3740 |
| 生命周期 V2 表数 | 8 |
| `project_stage` 关联字段数 | 2 |
| ACTIVE 模板数 | 2 |
| 阶段模板数 | 16 |

Schema 指纹：

`b9e1d162ae88c5283ae6e6c30e9d49fa23399ab7d2dddff9902031a7a98d14b4`

第二次 `migrate` 后重新计算，指纹完全一致，证明本次 Migration 链在该空库基线上可重复验证且无额外结构漂移。

## 7. Inventory 一致性

| 核查项 | 结果 |
| --- | --- |
| Inventory 文件路径 | `database/flyway/migration-inventory.yml` |
| 2.1.0—2.1.3 文件清单 | 与实际执行记录一致 |
| 资产 SHA-256 | 全部一致 |
| Flyway 历史版本 | 2.1.0—2.1.3 全部 Success |
| 2.0.0 legacy 脚本 | 按 Inventory 规则未自动执行 |
| 生产/受管环境 applied 状态 | 仍未确认，保持空集合 |
| 本次隔离验收记录 | 已登记为 `sprint-2-1-9-20260803` |

Inventory 与本次隔离验收实际记录一致。该结论只证明资产在本次临时 MySQL 8 环境执行成功，不代表开发、测试、预生产或生产数据库已经应用。

## 8. 问题清单

### P1：正式扫描目录尚无可执行 Migration

当前治理目录为 `database/migration/mysql`，但 2.1.0—2.1.3 仍位于 `database/mysql/migration`，且 Inventory 标记 `automatic_scan: false`。本次通过显式验收位置执行，不能直接等价为生产发布路径已就绪。

建议：下一 Sprint 对 2.1.x 资产完成审批、不可变性确认和晋级方案；生产执行器只扫描治理目录，不应临时改扫历史资产目录。

### P1：迁移前严格 validate 与 pending 版本冲突

Flyway 13.0.0 的独立 `validate` 在存在 pending Migration 时返回失败。若 CI 固定先严格 validate 再 migrate，新版本发布会被误阻断。

建议将流水线拆分为：

1. 迁移前：`info` + pending/顺序/资产哈希策略检查；如必须调用 validate，仅在该阶段显式允许 `*:pending`，禁止忽略 missing、failed 或 checksum mismatch；
2. 迁移执行：`migrate` 保持 `validateOnMigrate=true`；
3. 迁移后：执行不带忽略规则的严格 `validate` 和 Schema 指纹检查。

### P2：V1.1 初始化脚本依赖默认 Schema

该脚本在逐文件独立连接时需要 `--database=enterprise_platform`。建议在初始化运行说明中固化默认 Schema 参数；本 Sprint 不修改历史 SQL。

### P2：容器级验收尚未完成

本机 Docker 命令不可用，本次使用 MySQL 8.4.9 本地临时进程。仍需在 CI/预生产用锁定的 MySQL 8 镜像和 Flyway 13.0.0 容器复现一次。

## 9. 后续建议

1. 先解决治理目录晋级和 pre-validate 策略两个 P1 问题，再批准预生产执行。
2. 在 CI 增加“一次 migrate + 严格 validate + 第二次 migrate + 指纹稳定”验收任务。
3. 为每个数据库版本保存预期指纹，并与 `flyway_schema_history` 一并归档。
4. 预生产必须使用只具备目标 Schema DDL/DML 权限的专用迁移账号；禁止使用应用账号或 root。
5. 生产继续保持应用启动时不自动 Migration，由发布流水线显式审批执行。

## 10. 资料依据

- [Redgate Flyway Command-line 13.0.0](https://documentation.red-gate.com/flyway/reference/usage/command-line)
- [Redgate Flyway Maven Goal](https://documentation.red-gate.com/fd/maven-goal-277579365.html)
- [Redgate Flyway MySQL 支持](https://documentation.red-gate.com/fd/mysql-277579322.html)
