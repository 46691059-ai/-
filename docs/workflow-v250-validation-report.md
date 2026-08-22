# Workflow Center V2.5.0 真实 MySQL 验收报告

项目：《县域国企数字化运营治理平台》
Sprint：2-3.7-WF2.1.1
验收日期：2026-08-10
结论：**PASS**

## 1. 验收边界

本次只验收 `V2.5.0__create_workflow_definition_domain.sql` 的真实 MySQL 8 执行结果，没有修改该 SQL，没有修改 V2.4.0—V2.4.9，没有修改 Investment 代码，也没有连接生产或未知数据库。

验收前后 V2.5.0 文件 SHA-256 保持：

`5b41f9b787bbf4bdf0927b5f30dc487c6d88085d81aedf19e784d44474c02926`

## 2. 环境信息

| 项目 | Fresh路径 | Upgrade路径 |
| --- | --- | --- |
| 环境性质 | 本机一次性隔离实例 | 本机一次性隔离实例 |
| MySQL | MySQL Community Server 8.4.9 | MySQL Community Server 8.4.9 |
| Flyway | Community Edition 13.0.0 | Community Edition 13.0.0 |
| 地址 | `127.0.0.1:33750` | `127.0.0.1:33751` |
| 数据目录 | 独立临时目录 `fresh-data` | 独立临时目录 `upgrade-data` |
| 网络 | 只监听回环地址 | 只监听回环地址 |
| 基础平台表 | Migration前120张 | Migration前120张 |
| Flyway baseline | V2.0.0 | V2.0.0 |
| Migration制品 | 正式目录的字节一致只读副本 | 同一只读副本 |
| 生产连接 | 无 | 无 |
| Workflow业务服务 | 未启动 | 未启动 |

两个实例均由本次验收新建，不复用既有业务数据库。验收目录为一次性临时目录，MySQL仅绑定`127.0.0.1`。

## 3. Fresh验收结果

Fresh路径先执行平台基础SQL并建立V2.0.0 baseline，然后从正式受管链执行16个版本化Migration：

```text
V2.1.0 -> V2.1.1 -> V2.1.2 -> V2.1.3
-> V2.2.0
-> V2.4.0 -> ... -> V2.4.9
-> V2.5.0
```

| 检查项 | 结果 |
| --- | --- |
| pre `info` | V2.1.0—V2.5.0均为Pending |
| `migrate` | 成功执行16个Migration |
| 最终版本 | V2.5.0 |
| V2.5.0执行时间 | 468 ms（Flyway history） |
| history成功/失败 | 17 / 0，含1条baseline |
| 严格`validate` | 成功验证17条记录，36 ms |
| 第二次`migrate` | `No migration necessary` |
| 最终表数量 | 149 |

Fresh完整迁移链总SQL执行时间为8.292秒；外部进程墙钟时间约11.9秒。

## 4. V2.4.9升级验收结果

Upgrade路径先迁移至V2.4.9，确认V2.5.0为唯一Pending版本，再单独执行升级。

| 检查项 | 结果 |
| --- | --- |
| V2.4.9起始链 | 成功执行15个Migration |
| 升级前版本 | V2.4.9 |
| 升级前V2.5.0状态 | Pending |
| V2.5.0升级 | 成功执行1个Migration |
| V2.5.0执行时间 | 810 ms（Flyway history） |
| 最终版本 | V2.5.0 |
| history成功/失败 | 17 / 0，含1条baseline |
| 严格`validate` | 成功验证17条记录，34 ms |
| 第二次`migrate` | `No migration necessary` |
| 最终表数量 | 149 |

升级过程中未执行`clean`或`repair`。

## 5. Checksum与History

两条路径的V2.5.0记录完全一致：

| 字段 | 值 |
| --- | --- |
| version | `2.5.0` |
| description | `create workflow definition domain` |
| type | `SQL` |
| Flyway checksum | `-785031784` |
| success | `1` |
| 文件SHA-256 | `5b41f9b787bbf4bdf0927b5f30dc487c6d88085d81aedf19e784d44474c02926` |

文件SHA-256用于仓库资产完整性，Flyway checksum用于`flyway_schema_history`一致性，两者不能互相替代。

## 6. Schema结构检查

两条路径结果一致：

| 结构项 | 数量 |
| --- | ---: |
| 新增Workflow表 | 3 |
| Workflow字段 | 61 |
| 独立索引（含主键） | 18 |
| 外键 | 4 |
| CHECK | 22 |
| 已启用CHECK | 22 |

### 6.1 表和字段

| 表 | 字段数 | 必备治理字段 |
| --- | ---: | --- |
| `workflow_definition` | 17 | 8/8齐全 |
| `workflow_version` | 20 | 8/8齐全 |
| `workflow_node` | 24 | 8/8齐全 |

必备治理字段为：

- `created_by`
- `created_time`
- `updated_by`
- `updated_time`
- `deleted`
- `delete_token`
- `remark`
- `version`

字段类型、NULL约束、默认值、字符集和注释均与V2.5.0设计一致。

### 6.2 唯一约束与索引

确认存在：

- Definition：企业定义编码唯一、当前版本归属、业务状态、归属组织和当前版本索引；
- Version：定义内版本号唯一、内容哈希唯一、定义归属、状态和来源版本索引；
- Node：版本内节点编码唯一、顺序唯一、版本归属、类型和治理节点索引。

所有业务唯一索引均包含`delete_token`。用于复合外键的归属唯一索引已正确建立。

### 6.3 外键

| 外键 | 关系 | 更新/删除 |
| --- | --- | --- |
| `fk_workflow_definition_current_version` | Definition当前版本必须属于本Definition | RESTRICT/RESTRICT |
| `fk_workflow_version_definition` | Version归属Definition | RESTRICT/RESTRICT |
| `fk_workflow_version_source` | 来源Version必须属于同一Definition | RESTRICT/RESTRICT |
| `fk_workflow_node_version` | Node归属Version | RESTRICT/RESTRICT |

未建立到Investment、用户、组织或其他限界上下文的跨模块物理外键。

### 6.4 CHECK约束

22项CHECK全部显示`ENFORCED=YES`，覆盖：

- 定义、版本和节点状态白名单；
- 版本号、乐观锁和生效时间范围；
- 发布证据完整性；
- 节点类型、治理类型、审批模式和分配规则类型；
- 会签阈值、节点顺序、超时和布尔标识；
- 逻辑删除与`delete_token`一致性。

## 7. 双路径Schema指纹

使用`database/mysql/verification/schema_fingerprint.sql`对表、字段、索引、外键、CHECK和视图生成规范化只读数据流。

| 指纹 | Fresh | Upgrade |
| --- | --- | --- |
| 完整Schema行数 | 4,882 | 4,882 |
| 完整Schema SHA-256 | `edb9693a057cdffd1c0baccbf220ec9acabd15d527905e55ff116fa102b46912` | 相同 |
| Workflow结构行数 | 378 | 378 |
| Workflow SHA-256 | `fdde51b07e28a1261ec5c956a18f3ab2cbfd004266d3e553f9ec3ce89686d2cd` | 相同 |

结论：Fresh与V2.4.9 Upgrade最终结构完全一致。

## 8. 负向约束测试

三类测试在两个实例分别执行并通过，测试数据随后清理为0条。

| 测试 | Fresh | Upgrade | MySQL错误 |
| --- | --- | --- | --- |
| 重复企业定义编码 | PASS | PASS | 1062 Duplicate entry |
| 当前版本引用其他定义版本 | PASS | PASS | 1452 Foreign key constraint |
| 非法定义状态`INVALID` | PASS | PASS | 3819 Check constraint violated |

约束没有仅停留在DDL元数据层，MySQL 8已实际强制执行。

## 9. 资产状态更新

`database/flyway/migration-inventory.yml`已更新V2.5.0：

- `asset_status: CANONICAL_IMMUTABLE`
- `execution_status: EPHEMERAL_MYSQL8_VALIDATED`
- `flyway_checksum: -785031784`
- 记录Fresh和V2.4.9 Upgrade双路径、指纹和负向测试结果。

`database/migration/mysql/README.md`同步登记验收结论。该状态只证明一次性隔离MySQL 8验收通过，不表示开发、测试、预生产或生产数据库已经应用V2.5.0。

## 10. 未变更项

- V2.5.0 SQL内容：未修改；
- V2.4.0—V2.4.9历史Migration：未修改；
- V2.4.0—V2.4.9 SHA-256：10/10一致；
- Investment代码：未修改；
- Workflow业务接口：未开发、未启动；
- 生产数据库：未连接、未执行。

## 11. 剩余风险

1. 本次仅验证MySQL 8.4.9；达梦、人大金仓的CHECK、复合外键和时间默认值仍需独立方言验收。
2. “发布后不可变”不能完全由当前DDL表达，后续仍需Domain状态机、Repository写入限制和审计测试。
3. 规则配置使用TEXT保存规范化JSON，JSON Schema校验属于后续应用层职责。
4. 本次使用空业务数据，未覆盖大量流程定义下的索引选择性和查询性能。
5. `created_* / updated_*`与现有BaseEntity命名不同，后续Entity必须使用Workflow专用映射，不得回改已冻结Migration。
6. 本报告不授权生产执行；生产仍需备份、变更审批、发布窗口、迁移后validate和Schema指纹比对。

## 12. 最终结论

V2.5.0已在MySQL Community Server 8.4.9上通过Fresh完整迁移、V2.4.9单版本升级、Flyway history/checksum、迁移后严格validate、二次migrate no-op、三表字段/索引/外键/CHECK检查、双路径Schema指纹一致性以及唯一/外键/状态负向测试。

验收结果：**PASS**。资产状态可晋级为`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`，但不代表任何生产或受管环境已执行。
