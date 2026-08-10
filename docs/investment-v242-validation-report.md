# V2.4.2 Investment Migration 真实 MySQL 验收报告

## 1. 验收结论

Sprint 2-2.4.1 已在隔离的 MySQL 8 环境完成 V2.4.2 真实执行验收。

| 验收项 | 结果 |
| --- | --- |
| 空业务库路径 | 通过 |
| V2.1.3 升级路径 | 通过 |
| 迁移前资产策略检查 | 通过，8 个 SQL 的 SHA-256 全部匹配 |
| `migrate` | 通过，最终版本 V2.4.2 |
| 迁移后严格 `validate` | 通过，9 条 Migration 校验成功 |
| 第二次 `migrate` | 通过，`No migration necessary` |
| Flyway 失败记录 | 0 |
| V2.4.2 Flyway checksum | `-2068189189` |
| V2.4.2 文件 SHA-256 | `c71ddc226a04155afe4552fd23bc826b87ba600967258675dc95b8a26d002d40` |
| Schema 指纹 | `27cffea8205e6bea95dd87e5300bf1c5a60d78593530290a83db993f3ebeb53f` |
| 综合结论 | **通过，附一项指纹跨补丁版本治理问题** |

本次没有连接受管、预生产或生产数据库，没有修改业务代码，也没有修改 V2.4.0、V2.4.1 或其他历史 Migration。

## 2. 环境

| 项目 | 验收值 |
| --- | --- |
| 验收日期 | 2026-08-04 |
| 操作系统 | Windows 本地隔离验收环境 |
| 数据库 | MySQL Community Server 8.4.9 |
| 监听范围 | `127.0.0.1:3307` |
| 验收 Schema | `enterprise_platform`，验收前确认不存在，验收后删除 |
| Flyway | Flyway Maven Plugin 13.0.0 |
| Java | OpenJDK 21.0.12+8 |
| Maven | Apache Maven 3.9.9 |
| JDBC Driver | MySQL Connector/J 9.4.0 |
| Migration 目录 | `database/migration/mysql` |
| 生产连接 | 无 |

本机没有 Docker 命令，因此使用本机 MySQL 8.4.9 二进制服务和验收专用 Maven Flyway 配置执行。Flyway SQL、JDBC、Schema History 和 checksum 均为真实执行结果；容器镜像级验证仍应在 CI 或预生产环境补做。

## 3. 验收路径

### 3.1 空业务库

“空库”是指包含基础平台结构但不包含投资业务数据的新环境，执行过程如下：

1. 初始化 `01_database.sql`—`16_sprint_1_log_center.sql`；
2. 初始化 `V1.1.0__investment_data_risk_bi.sql`；
3. 确认基础表 120 张，生命周期 V2 表 0 张；
4. 在 V2.0.0 建立 Flyway baseline；
5. 执行迁移前 SHA-256 与文件清单策略检查；
6. 执行 `migrate`，顺序应用 V2.1.0—V2.1.3、V2.2.0、V2.4.0—V2.4.2；
7. 执行迁移后严格 `validate`；
8. 再次执行 `migrate`，确认无待执行版本；
9. 核查 Flyway History、结构、权限和Schema指纹。

结果：8 个待执行 Migration 全部成功，Flyway SQL 执行时间 5,939 ms，外部墙钟时间 8,561 ms，最终版本 V2.4.2。

### 3.2 V2.1.3 升级库

1. 重建相同基础 Schema；
2. 在 V2.0.0 baseline；
3. 使用 Flyway target 将数据库准确迁移至 V2.1.3；
4. 确认起始版本 V2.1.3，成功历史记录 5 条；
5. 执行迁移前资产策略检查和 `info`；
6. 执行 V2.2.0、V2.4.0、V2.4.1、V2.4.2；
7. 执行严格 `validate` 和第二次 `migrate`；
8. 重复结构与权限核查。

结果：4 个待执行 Migration 全部成功，Flyway SQL 执行时间 3,363 ms，外部墙钟时间 5,793 ms，最终版本 V2.4.2。第二次 `migrate` 为 no-op。

## 4. Migration 结果

两条验收路径的最终 History 均为9条成功记录、0条失败记录。升级路径的最终记录如下：

| rank | 版本 | Flyway checksum | SQL执行时间 | 状态 |
| ---: | --- | ---: | ---: | --- |
| 1 | 2.0.0 | — | 0 ms | Baseline成功 |
| 2 | 2.1.0 | -1798385258 | 1,445 ms | 成功 |
| 3 | 2.1.1 | -491875563 | 1,078 ms | 成功 |
| 4 | 2.1.2 | 1777287545 | 10 ms | 成功 |
| 5 | 2.1.3 | -1521439258 | 19 ms | 成功 |
| 6 | 2.2.0 | 487347589 | 31 ms | 成功 |
| 7 | 2.4.0 | -717284868 | 3,292 ms | 成功 |
| 8 | 2.4.1 | -269537295 | 15 ms | 成功 |
| 9 | 2.4.2 | **-2068189189** | 25 ms | 成功 |

迁移顺序连续，`outOfOrder=false`，没有执行 `clean` 或 `repair`。V2.4.2 的Flyway checksum在空库与升级库中一致。

## 5. Schema 指纹与结构核查

使用 `database/mysql/verification/schema_fingerprint.sql` 对表、字段、索引、外键、CHECK约束和视图生成4,309行规范化数据，两条路径的SHA-256完全一致：

`27cffea8205e6bea95dd87e5300bf1c5a60d78593530290a83db993f3ebeb53f`

### 5.1 可研与尽调表

| 表 | 字段数 | 核查结果 |
| --- | ---: | --- |
| `investment_feasibility` | 25 | 存在，档案唯一、当前/冻结版本外键有效 |
| `investment_feasibility_version` | 46 | 存在，版本唯一及指标约束有效 |
| `investment_due_diligence_package` | 18 | 存在，包版本唯一及阻断计数约束有效 |
| `investment_due_diligence` | 31 | 存在，四类报告版本唯一及统计约束有效 |
| `investment_due_diligence_item` | 30 | 存在，问题编号唯一、证据与责任组织引用有效 |

五张目标表共核查：

- 28个索引，形成60条索引字段记录；
- 14个外键；
- 16个CHECK约束；
- 5个主键和5个业务唯一约束。

关键唯一索引包括：

- `uk_investment_feasibility_item`；
- `uk_inv_feasibility_version`；
- `uk_inv_dd_package_version`；
- `uk_inv_dd_report_version`；
- `uk_inv_dd_item_no`。

### 5.2 指纹跨版本说明

本次MySQL 8.4.9的指纹与历史V2.4.0验收报告中MySQL 8.4.10的`386857...`不同，但本轮空库和升级库在同一MySQL版本下完全一致，且所有Migration checksum、字段、索引和约束核查均通过。V2.4.1、V2.4.2只写入RBAC元数据，不执行DDL。

初步判断差异来自MySQL补丁版本对`information_schema`元数据文本的规范化表达，不能据此认定Migration结构漂移。进入预生产前应在目标MySQL精确版本重新生成并批准环境基准指纹，同时改进指纹工具的跨补丁版本归一化测试。

## 6. 权限验证

V2.4.2初始化的4项权限全部存在、启用且未逻辑删除：

| ID | 权限编码 | 结果 |
| ---: | --- | --- |
| 2721 | `investment:feasibility:view` | 通过 |
| 2722 | `investment:feasibility:edit` | 通过 |
| 2723 | `investment:due_diligence:view` | 通过 |
| 2724 | `investment:due_diligence:edit` | 通过 |

核查结果：

- 有效权限4条，编码无重复；
- `SUPER_ADMIN`有效授权4条；
- 升级路径和空库路径结果一致；
- 第二次Flyway `migrate`未重复写入数据。

## 7. 问题清单

1. **P2——Schema指纹跨MySQL补丁版本不稳定**：8.4.9与历史8.4.10指纹不同。发布校验应绑定目标数据库精确版本，并增加规范化差异定位工具；不应通过修改History或自动`repair`规避。
2. **P3——本机缺少Docker命令**：本次是原生MySQL/JDBC/Flyway真实验收，不包含`mysql:8.4`镜像级验证。CI或预生产仍需按正式容器运行一次。
3. **治理边界**：本报告只证明仓库Migration在隔离验收Schema可执行，不表示开发、测试、预生产或生产数据库已应用V2.4.2。

## 8. 资产登记

- `database/flyway/migration-inventory.yml`已登记本次验收记录；
- V2.4.2资产状态更新为`CANONICAL_IMMUTABLE`；
- V2.4.2执行状态更新为`EPHEMERAL_MYSQL8_VALIDATED`；
- V2.4.1在相同两条路径中同时获得真实验收，checksum登记为`-269537295`；
- 任何后续变更必须创建更高版本Migration，禁止修改V2.4.0—V2.4.2或使用`repair`掩盖checksum变化。
