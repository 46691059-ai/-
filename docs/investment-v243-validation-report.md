# V2.4.3 Investment Scheme Migration真实MySQL验收报告

## 1. 环境信息

| 项目 | 验收值 |
| --- | --- |
| 验收日期 | 2026-08-08 |
| 操作系统 | Windows本地隔离验收环境 |
| 数据库 | MySQL Community Server 8.4.9 |
| 监听地址 | `127.0.0.1:3307` |
| 验收Schema | `enterprise_platform`，验收前确认不存在，验收后删除 |
| Flyway | Flyway Maven Plugin 13.0.0 |
| Java | OpenJDK 21.0.12+8 |
| Maven | Apache Maven 3.9.9 |
| JDBC Driver | MySQL Connector/J 9.4.0 |
| Migration扫描目录 | `database/migration/mysql` |
| 生产连接 | 无 |

本机没有Docker命令，因此使用本机MySQL 8.4.9与验收专用Flyway Maven配置完成真实JDBC执行。未连接开发、测试、预生产或生产数据库。

## 2. Migration结果

### 2.1 空业务库路径

执行过程：

1. 执行`01_database.sql`—`16_sprint_1_log_center.sql`和`V1.1.0__investment_data_risk_bi.sql`；
2. 确认基础表120张，V2.4投资方案三表尚不存在；
3. 在V2.0.0建立Flyway baseline；
4. 执行迁移资产SHA-256、文件清单和版本顺序策略检查；
5. 执行`migrate`；
6. 执行迁移后严格`validate`；
7. 第二次执行`migrate`确认no-op。

结果：V2.1.0—V2.1.3、V2.2.0、V2.4.0—V2.4.3共9个待执行Migration全部成功，最终版本V2.4.3。

| 指标 | 结果 |
| --- | --- |
| 资产策略检查 | 9个SQL的SHA-256全部匹配 |
| Flyway内部迁移时间 | 4,709 ms |
| migrate墙钟时间 | 7,050 ms |
| post严格validate | 成功，校验10条Migration记录 |
| 第二次migrate | `No migration necessary` |
| 最终History | 10条成功、0条失败 |
| V2.4.3执行时间 | 10 ms |

### 2.2 V2.4.2升级路径

先将隔离Schema准确迁移至V2.4.2，确认：

- 起始版本为V2.4.2；
- History成功记录9条；
- 投资方案权限数据为0条。

随后执行固定发布流程：

```text
pre资产策略检查
  -> Flyway info
  -> migrate V2.4.3
  -> post严格validate
  -> 第二次migrate
```

结果：只执行V2.4.3一个Migration，Flyway内部执行时间23 ms，外部墙钟时间2,183 ms；严格validate成功，第二次migrate为no-op。

## 3. Checksum与Flyway History

V2.4.3资产摘要：

| 类型 | 值 |
| --- | --- |
| 文件 | `V2.4.3__add_investment_scheme_permissions.sql` |
| SHA-256 | `0918ae589df84e9c401aa695cb1b57b83937fccaad85f1c2d0507d4260bae368` |
| Flyway checksum | `-657690293` |
| 版本 | `2.4.3` |
| 状态 | Success |

升级路径最终History：

| rank | 版本 | Flyway checksum | SQL执行时间 | 状态 |
| ---: | --- | ---: | ---: | --- |
| 1 | 2.0.0 | — | 0 ms | Baseline成功 |
| 2 | 2.1.0 | -1798385258 | 928 ms | 成功 |
| 3 | 2.1.1 | -491875563 | 867 ms | 成功 |
| 4 | 2.1.2 | 1777287545 | 10 ms | 成功 |
| 5 | 2.1.3 | -1521439258 | 19 ms | 成功 |
| 6 | 2.2.0 | 487347589 | 15 ms | 成功 |
| 7 | 2.4.0 | -717284868 | 3,506 ms | 成功 |
| 8 | 2.4.1 | -269537295 | 13 ms | 成功 |
| 9 | 2.4.2 | -2068189189 | 12 ms | 成功 |
| 10 | 2.4.3 | **-657690293** | 23 ms | 成功 |

History版本顺序连续，失败记录为0；`outOfOrder=false`，未执行`clean`或`repair`。

## 4. 权限验证

V2.4.3初始化结果：

| ID | 权限编码 | 状态 | 逻辑删除 | 结果 |
| ---: | --- | ---: | ---: | --- |
| 2731 | `investment:scheme:view` | 1 | 0 | 通过 |
| 2732 | `investment:scheme:edit` | 1 | 0 | 通过 |

关联核查：

- 有效权限2条，无重复编码；
- `SUPER_ADMIN`有效权限授权2条；
- 投资方案页面及按钮菜单3条；
- `SUPER_ADMIN`有效菜单授权3条；
- V2.4.2升级前权限为0条，升级后准确新增2条；
- 第二次Flyway migrate不重复写入。

## 5. Schema变化

V2.4.3是纯RBAC元数据Migration，不包含`CREATE TABLE`、`ALTER TABLE`或其他DDL，因此不改变业务Schema。

空库路径和V2.4.2升级路径的最终Schema指纹完全一致：

`27cffea8205e6bea95dd87e5300bf1c5a60d78593530290a83db993f3ebeb53f`

规范化指纹数据为4,309行，与同版本MySQL 8.4.9上的V2.4.2验收指纹一致。

投资方案业务结构仍由V2.4.0提供，本次复核结果为：

| 表 | 字段数 |
| --- | ---: |
| `investment_scheme` | 13 |
| `investment_scheme_version` | 39 |
| `investment_scheme_funding` | 17 |

三表合计包含33条索引字段记录、11个外键和8个CHECK约束。V2.4.3没有修改这些结构。

## 6. 剩余风险

1. **容器镜像验收未覆盖**：本机无Docker命令，本次为原生MySQL/JDBC/Flyway真实验收；CI或预生产仍需使用正式MySQL镜像复验。
2. **受管环境状态未知**：本报告只证明隔离环境可执行，不表示开发、测试、预生产或生产数据库已应用V2.4.3。
3. **固定种子ID冲突风险**：V2.4.3使用固定权限和菜单ID。在受管环境发布前必须检查这些ID是否被非目标数据占用；禁止通过修改已验证Migration或Flyway History规避冲突。
4. **指纹跨补丁版本问题仍存在**：MySQL 8.4.9与历史8.4.10的`information_schema`规范化指纹不同，目标环境应按精确数据库版本维护基准指纹。
5. V2.4.3现已视为不可变资产，后续修正必须使用更高版本Migration，不得修改V2.4.0—V2.4.3或自动执行`repair`。

## 7. 资产登记

- `database/flyway/migration-inventory.yml`已增加本次两路径验收记录；
- V2.4.3资产状态更新为`CANONICAL_IMMUTABLE`；
- V2.4.3执行状态更新为`EPHEMERAL_MYSQL8_VALIDATED`；
- Flyway checksum登记为`-657690293`；
- 受管环境`confirmed_applied`仍保持空集合。
