# Workflow Version Release V2.5.5 真实 MySQL 验收报告

## 1. 验收结论

- Sprint：2-3.7-WF2.7.1
- 结论：`PASS`
- 资产状态：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- 验收范围仅包含数据库验收、Migration 资产治理和报告；未修改业务代码、V2.5.0—V2.5.5 SQL、Investment，也未创建 V2.5.6。

## 2. 环境信息

| 项目 | 值 |
|---|---|
| 数据库 | MySQL Community Server 8.4.9 |
| Migration 工具 | Flyway Community Edition 13.0.0 |
| Fresh | 新初始化、仅监听 `127.0.0.1` 的一次性实例，端口 33855 |
| Upgrade | 新初始化、仅监听 `127.0.0.1` 的一次性实例，端口 33856 |
| Flyway baseline | 2.0.0 |
| Migration 来源 | 当前仓库 SQL 的逐字节一致副本 |

未连接生产数据库、未知数据库或历史临时 Schema；两个数据目录均在本次验收中重新初始化。

## 3. Fresh 验收

- 基础 Schema 初始化后，从 V2.5.0 顺序执行到 V2.5.5：通过。
- Flyway history：22 条成功记录，0 条失败记录。
- V2.5.5 执行一次，执行时间 535 ms。
- `validate`：通过。
- 第二次 `migrate`：`No migration necessary`。
- V2.5.5 Flyway checksum：`1152171452`。

## 4. Upgrade 验收

- 先迁移至 V2.5.4，再加入逐字节一致的 V2.5.5：通过。
- 仅新增一条 V2.5.5 history，未重复执行旧版本。
- Flyway history：22 条成功记录，0 条失败记录。
- V2.5.5 执行时间 601 ms。
- `validate`：通过。
- 第二次 `migrate`：no-op。

## 5. Checksum 与历史完整性

| 类型 | 值 |
|---|---|
| V2.5.5 SHA-256 | `50efcb230b2cba2e7a7600fdfc3e229f05a9e85877194cf9f1eed0875d52605e` |
| V2.5.5 Flyway checksum | `1152171452` |
| SHA256SUMS 清单 | 21/21 通过，0 漂移 |
| V2.4.0—V2.5.5 | 16/16 通过，0 漂移 |

V2.5.0—V2.5.5 SQL 内容均未修改，失败历史记录继续保留。

## 6. 双路径 Schema fingerprint

指纹覆盖列、索引、表约束、键关系和 CHECK 表达式；排除数据内容及自动增长计数器。

| 范围 | Fresh | Upgrade | 结果 |
|---|---|---|---|
| 完整 Schema（6337 条规范化结构记录） | `75f0f4b40a939d1cec2db71845da8c6a529d5ba23002a87d004cbb0611f2fdc3` | `75f0f4b40a939d1cec2db71845da8c6a529d5ba23002a87d004cbb0611f2fdc3` | 一致 |
| Workflow Schema（496 条规范化结构记录） | `291ec48598f72f6d837785de657a368bfecca1bca8fc3ab48b673085c380282c` | `291ec48598f72f6d837785de657a368bfecca1bca8fc3ab48b673085c380282c` | 一致 |

## 7. workflow_version_release 结构

- 表共 19 个字段。
- 业务字段覆盖 definition、前后版本、版本号、内容哈希、操作人/组织、发布时间、trace 和校验摘要。
- 审计字段：`created_by`、`created_time`、`updated_by`、`updated_time`。
- 逻辑删除：`deleted`、`delete_token`。
- 乐观锁：`version`。
- 索引共 5 个：主键、发布版本唯一键、前版本查询索引、发布时间索引和 trace 查询索引。
- 外键覆盖流程定义、前版本和发布版本；复合外键同时约束版本必须归属同一 Definition。
- CHECK 覆盖版本号、64 位小写 SHA-256 和发布记录不可变规则。

约束负向结果：重复发布记录 `1062`、非法哈希 `3819`、不存在版本外键 `1452`、修改发布记录 `3819`。

## 8. workflow_instance 兼容性

- 新字段 `definition_content_hash_snapshot` 为 `VARCHAR(128) NOT NULL`。
- Upgrade 路径在 V2.5.4 状态预置真实历史实例；迁移后该字段按其绑定版本哈希正确回填。
- 历史实例的 `version_id`、`definition_version_no` 与 `definition_content_hash_snapshot` 一致。
- 新实例按已发布版本绑定，版本 ID、版本号和内容哈希快照来自同一冻结版本。

## 9. 发布状态机验证

1. 创建 Definition 和 DRAFT Version 1，发布后 Version 1 为 `PUBLISHED`。
2. 创建 DRAFT Version 2 并发布，Version 1 转为 `RETIRED`，Version 2 转为 `PUBLISHED`。
3. Definition 的 `current_version_id` 指向 Version 2。
4. 同一 Definition 最终仅有一个 `PUBLISHED` 版本。
5. 每次发布均写入不可变 `workflow_version_release` 证据。

并发测试使用两个独立 MySQL 客户端同时发布同一 DRAFT 版本：一个影响 1 行并成功，另一个影响 0 行；仅一个管理员成为发布成功者。

## 10. 业务负向测试

| 场景 | 结果 |
|---|---|
| 重复发布 PUBLISHED 版本 | 拒绝 |
| 发布不存在 Version | 拒绝 |
| 发布其他 Definition 的 Version | 拒绝 |
| 修改 PUBLISHED 版本节点 | 拒绝，节点未变化 |
| RETIRED 版本重新发布 | 拒绝 |
| 两管理员并发发布同一版本 | 仅一个成功 |

这些测试以与应用层相同的状态、归属和乐观锁条件执行；数据库约束另行验证，不以 RBAC 代替领域规则。

## 11. 内容 Hash 验收

- 同一流程配置重复计算得到相同 SHA-256：`87ccc58b91d7b0fc919315cc877c727d04a0300a99200a7e6de602508fe953c6`。
- 修改节点顺序得到：`12cc81df3dbc073568fd6f338831dd6c534e4fcff4f3534603029e2a7b412af3`。
- 修改节点配置得到：`80b571057ade0b81780d893e84a0d2e668dc1d129dd2e6b9c298efe2f1d553cc`。
- 规范化哈希输入不包含数据库 ID、创建/更新时间及审计字段。

## 12. 资产状态与剩余风险

`migration-inventory.yml` 已记录本次 Fresh/Upgrade 验收、Flyway checksum、双路径指纹与负向结果；README 已同步晋级说明。V2.5.5 正式晋级为 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。

剩余风险：本次为隔离的一次性 MySQL 验收，不代表生产 Migration 授权；真实生产发布仍需备份、变更窗口、容量评估和回滚演练。并发测试验证了数据库乐观锁的单赢家行为，但未替代应用层端到端并发与权限测试。下一步仅建议进行 V2.5.5 发布前环境准入，不进入 WF3 多节点开发。
