# Migration资产晋级与发布流程修正报告

## 1. 结论

Sprint 2-1.10 已完成生命周期V2 Migration资产晋级和发布流程修正。

- V2.1.0—V2.1.3 已原字节复制到MySQL正式扫描目录；
- 4个源文件与晋级文件的字节数、SHA-256完全一致；
- 晋级文件已通过 `SHA256SUMS` 冻结；
- Inventory已记录资产状态、自动扫描状态、执行状态、SHA-256和Flyway checksum；
- 发布流程已调整为“迁移前策略检查 → migrate → 迁移后严格validate”；
- 已从正式扫描目录在隔离MySQL 8.4.9环境重新执行全链；
- Flyway history、checksum和Schema指纹均与Sprint 2-1.9一致；
- 未修改业务代码、历史Migration内容或生产数据库。

## 2. 晋级资产列表

正式扫描目录：`database/migration/mysql`

| 版本 | 正式资产 | 字节数 | 资产状态 | 自动扫描 |
| --- | --- | ---: | --- | :---: |
| 2.1.0 | `V2.1.0__create_project_lifecycle_v2_structure.sql` | 14693 | PROMOTED_IMMUTABLE | 是 |
| 2.1.1 | `V2.1.1__link_project_stage_lifecycle_snapshot.sql` | 912 | PROMOTED_IMMUTABLE | 是 |
| 2.1.2 | `V2.1.2__backfill_legacy_project_lifecycle.sql` | 4946 | PROMOTED_IMMUTABLE | 是 |
| 2.1.3 | `V2.1.3__seed_project_lifecycle_templates.sql` | 6846 | PROMOTED_IMMUTABLE | 是 |

历史来源文件继续保留在 `database/mysql/migration`，但不再作为执行入口。`V2.0.0__legacy_to_v1.sql` 未晋级，因为它只适用于特定 `pm_*` 历史结构。

## 3. Checksum对比

### 3.1 文件SHA-256

| 版本 | 历史源文件SHA-256 | 正式文件SHA-256 | 结果 |
| --- | --- | --- | --- |
| 2.1.0 | `8794cb84f5a7e1857aadcaf7ee29c32ac10df04fad572ce1ef048004a37e5e4f` | `8794cb84f5a7e1857aadcaf7ee29c32ac10df04fad572ce1ef048004a37e5e4f` | 一致 |
| 2.1.1 | `6a8cd3095d16670d8f068f6be9ce22384be78c7babb296d88cc359044ecf01a5` | `6a8cd3095d16670d8f068f6be9ce22384be78c7babb296d88cc359044ecf01a5` | 一致 |
| 2.1.2 | `df4f4355f013d5dc7e0a5a1f93c4e329133049cc3395ee749ce8b7153180e65a` | `df4f4355f013d5dc7e0a5a1f93c4e329133049cc3395ee749ce8b7153180e65a` | 一致 |
| 2.1.3 | `8f6fb5778a8b8a566b8222ff029587e0ed9ccd00f0dccb132d1f552cff4f3e51` | `8f6fb5778a8b8a566b8222ff029587e0ed9ccd00f0dccb132d1f552cff4f3e51` | 一致 |

### 3.2 Flyway checksum

| 版本 | Sprint 2-1.9 checksum | 晋级后checksum | 结果 |
| --- | ---: | ---: | --- |
| 2.1.0 | -1798385258 | -1798385258 | 一致 |
| 2.1.1 | -491875563 | -491875563 | 一致 |
| 2.1.2 | 1777287545 | 1777287545 | 一致 |
| 2.1.3 | -1521439258 | -1521439258 | 一致 |

SHA-256用于仓库资产完整性；Flyway checksum用于历史表与解析后SQL一致性。两类摘要均已写入 `database/flyway/migration-inventory.yml`。

## 4. 发布流程变化

### 4.1 旧流程问题

旧的 `check-environment` 在 `migrate` 前直接执行严格 `validate`。Flyway 13.0.0会把合法pending Migration报告为validation failure，导致正常发布被阻断。

### 4.2 新流程

```text
迁移前策略检查
  ├─ flyway_schema_history存在
  ├─ 无failed历史记录
  ├─ 起始版本符合审批值
  ├─ 起始Schema指纹符合审批值
  ├─ SHA256SUMS覆盖全部正式SQL且哈希一致
  └─ Flyway info确认pending集合和顺序
        ↓
显式审批后执行migrate
  └─ validateOnMigrate=true
        ↓
迁移后严格验证
  ├─ Flyway validate，不忽略pending/missing/checksum异常
  ├─ Flyway info确认目标版本
  ├─ 目标Schema指纹核对
  └─ 业务验证与应用灰度
```

新增统一入口：

- PowerShell：`database/flyway/scripts/release.ps1`
- Linux/CI：`database/flyway/scripts/release.sh`

环境检查支持两个阶段：

- `check-environment.ps1 <env> pre` / `check-environment.sh <env> pre`；
- `check-environment.ps1 <env> post` / `check-environment.sh <env> post`。

预生产和生产应设置：

- `EXPECTED_SOURCE_DB_VERSION`；
- `EXPECTED_TARGET_DB_VERSION`；
- `EXPECTED_PRE_SCHEMA_FINGERPRINT`；
- `EXPECTED_POST_SCHEMA_FINGERPRINT`；
- `CHANGE_TICKET`和 `CONFIRM_FLYWAY_MUTATION=<env>:migrate`。

兼容期仍接受旧的 `EXPECTED_DB_VERSION` 和 `EXPECTED_SCHEMA_FINGERPRINT`，但正式流水线应使用分阶段变量，避免起始值和目标值混淆。

## 5. 正式目录验收结果

### 5.1 环境与执行链

| 项目 | 结果 |
| --- | --- |
| 数据库 | MySQL Community Server 8.4.9 |
| Flyway | 13.0.0 |
| Java | 21.0.12+8 |
| 正式扫描目录 | `database/migration/mysql` |
| 基础结构 | 120张表，生命周期V2表为0 |
| baseline | 2.0.0成功 |
| 迁移前info | 成功，2.1.0—2.1.3均为Pending |
| migrate | 成功，4条Migration全部执行 |
| 迁移后validate | 成功，严格校验5条历史/解析记录 |
| 迁移后info | 当前版本2.1.3 |
| 第二次migrate | 成功，无Migration需要执行 |

### 5.2 Flyway history

| rank | 版本 | 类型 | checksum | 执行时间 | 状态 |
| ---: | --- | --- | ---: | ---: | --- |
| 1 | 2.0.0 | BASELINE | — | 0 ms | 成功 |
| 2 | 2.1.0 | SQL | -1798385258 | 2125 ms | 成功 |
| 3 | 2.1.1 | SQL | -491875563 | 2294 ms | 成功 |
| 4 | 2.1.2 | SQL | 1777287545 | 10 ms | 成功 |
| 5 | 2.1.3 | SQL | -1521439258 | 135 ms | 成功 |

历史成功记录5条，失败记录0条。版本、脚本名、checksum和顺序与Sprint 2-1.9一致；执行耗时允许因环境波动而不同。

### 5.3 Schema指纹

晋级后指纹：

`b9e1d162ae88c5283ae6e6c30e9d49fa23399ab7d2dddff9902031a7a98d14b4`

与Sprint 2-1.9指纹完全一致。规范行数3740，业务表128张，生命周期表8张，ACTIVE模板2个，阶段模板16个。

## 6. 修改范围

- 晋级4个只读Migration资产；
- 新增 `SHA256SUMS`；
- 更新PowerShell/Linux环境检查脚本；
- 新增PowerShell/Linux发布编排脚本；
- 更新Inventory、MySQL扫描目录说明和Migration治理文档；
- 未修改业务代码和已验证Migration内容。

## 7. 剩余风险

1. 本机没有Docker命令，本次使用同版本Flyway Maven Plugin执行真实MySQL 8验收；Docker包装器端到端执行仍需在CI补测。
2. 当前只晋级MySQL实现。达梦和人大金仓目录仍无等价Migration，不能宣称国产数据库链已通过。
3. 历史来源和正式目录同时保留4个副本。正式目录是唯一执行来源，后续审查必须阻断两处摘要漂移；不得从历史目录运行Flyway。
4. 受管环境的实际执行状态仍未知，Inventory中的 `confirmed_applied` 保持为空；首次纳管必须逐库核验history和Schema指纹。
5. PowerShell脚本已通过语法解析，Linux脚本已通过Git Bash `bash -n` 静态语法检查；Docker容器编排的运行时验证仍需由CI执行。

## 8. 后续建议

1. 在Linux CI运行 `release.sh` 的临时MySQL 8容器测试，归档容器镜像摘要。
2. 为预生产制作包含起始/目标版本、双指纹、SHA256SUMS和变更单的签名发布制品。
3. 为达梦、人大金仓分别编写等价版本链并独立验收，不复制MySQL SQL直接宣称兼容。
4. 后续修正V2.1.x只能新增更高版本Migration，禁止修改已晋级文件。
