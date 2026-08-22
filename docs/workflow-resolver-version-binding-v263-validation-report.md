# V2.6.3 Resolver Version Binding Migration 验收报告

## 1. 验收结论

结论：**FAIL / BLOCKED / NOT PROMOTED**。

V2.6.3 在 Fresh 与 V2.6.2 Upgrade 两条隔离路径均成功执行，Flyway history、严格 validate、二次 migrate no-op、双路径 Schema 指纹、历史线性实例回填及 Legacy 实例兼容均通过。但数据库负向测试发现生产阻断问题：`resolver_contract_hash` 继承 `utf8mb4_general_ci`，导致 `REGEXP '^[0-9a-f]{64}$'` 按大小写不敏感语义执行，64 位大写十六进制 Hash 被接受。

本 Sprint 禁止修改 V2.6.3 SQL，因此未晋级资产，也未修改任何历史 Migration、Workflow/Investment 业务代码或 ROLE/POSITION/ORG 能力。

## 2. 环境

| 项目 | 值 |
|---|---|
| MySQL | MySQL Community Server 8.4.9 |
| Flyway | Community Edition 13.0.0 |
| Fresh | 新建、仅绑定 `127.0.0.1` 的一次性实例 |
| Upgrade | 新建、仅绑定 `127.0.0.1` 的一次性实例，先迁移到 V2.6.2 |
| 基线 | V2.0.0 |
| Migration 扫描源 | `database/migration/mysql` 的只读字节一致副本 |
| 生产/托管数据库 | 未连接 |

一次性证据目录：`C:\Users\WUKONG\AppData\Local\Temp\enterprise-v263-validation-20260811-114709\evidence`。验收完成后两套 MySQL 进程均已停止。

## 3. Flyway结果

### Fresh

- V2.0.0 基线后执行 25 个版本化 Migration，最终版本 V2.6.3。
- `flyway_schema_history`：26 条成功记录（含 baseline），0 条失败。
- strict validate：通过，共验证 26 条记录。
- 第二次 migrate：`No migration necessary`。

### Upgrade

- 先迁移至 V2.6.2，再植入一条历史线性实例和一条 Legacy 实例。
- 升级时仅执行 V2.6.3 一次，最终 history 为 26 条成功、0 条失败。
- strict validate：通过。
- 第二次 migrate：no-op。

### 校验值

- Repository SHA-256：`35c4714708d51ba00f4cdc79b04bea4d897a7e6c72624ec595c9df147f1f41e6`
- Flyway checksum：`1481014267`
- SQL 内容未修改。

## 4. Schema验证

`workflow_instance` 最终结构统计：

- 字段：41
- 索引：13
- CHECK：17

新增字段：

| 字段 | 类型 | 可空 | 默认值 | 排序规则 |
|---|---|---:|---|---|
| `resolver_code` | `varchar(64)` | 是 | `NULL` | `utf8mb4_general_ci` |
| `resolver_version` | `varchar(64)` | 是 | `NULL` | `utf8mb4_general_ci` |
| `resolver_contract_hash` | `char(64)` | 是 | `NULL` | `utf8mb4_general_ci` |

索引 `idx_workflow_instance_resolver` 正确覆盖 `resolver_code,resolver_version,status,deleted`。三个新增 CHECK 均存在：绑定完整性、线性实例强制绑定、Hash 格式。

## 5. Schema Fingerprint

| 范围 | Fresh | Upgrade | 结论 |
|---|---|---|---|
| 完整 Schema | `3bb861a92a8919ef127512290eb66eabcbfb46182969795b06b4edb6856db6fd` | `3bb861a92a8919ef127512290eb66eabcbfb46182969795b06b4edb6856db6fd` | 一致 |
| Workflow Schema | `ffd0809897d0f33f9614f0b33d8379d1f1db34ffc2fb88997f11efa2177f933c` | `ffd0809897d0f33f9614f0b33d8379d1f1db34ffc2fb88997f11efa2177f933c` | 一致 |

## 6. 回填与业务契约验证

- 历史 `MULTI_NODE_LINEAR_V1` 实例正确回填：`EXPLICIT_USER / EXPLICIT_USER_V1 / 65873e...5b6d`。
- `SINGLE_NODE_LEGACY` 实例三个 Resolver 字段保持 `NULL`，兼容通过。
- Resolver 版本绑定、Registry 新版本不影响旧实例、Contract Hash 漂移拒绝、Resolver 缺失/非 ACTIVE 拒绝等现有契约测试：10/10 通过。
- 未执行 ROLE/POSITION/ORG，也未修改 Investment。

## 7. 负向测试

| 用例 | 预期 | 实际 | 结果 |
|---|---|---|---|
| 仅填写 `resolver_code` | 拒绝不完整绑定 | 拒绝 | PASS |
| 线性实例三个字段均空 | 拒绝 | 拒绝 | PASS |
| Hash 长度为 3 | 拒绝 | MySQL 3819 | PASS |
| 64 位大写十六进制 Hash | 拒绝 | 被接受 | **FAIL / BLOCKING** |

根因不是 Domain 行为，而是数据库防御约束的大小写语义：`char(64)` 使用大小写不敏感排序规则，当前 REGEXP 无法保证只接受小写 Hash。

## 8. 资产状态

- V2.6.3：`CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`
- 不标记 `CANONICAL_IMMUTABLE`。
- 不标记 `EPHEMERAL_MYSQL8_VALIDATED`。
- `SHA256SUMS` 未变，失败验收记录已保留在 Inventory 与本报告。

## 9. 剩余风险与后续建议

P0 风险：数据库可存入大写 Contract Hash，削弱 Resolver 契约值的规范化和跨环境一致性保障。

下一步应单独设计更高版本修复 Migration，在不改 V2.6.3 的前提下将 Hash 字段/表达式切换为大小写敏感或二进制比较，并补充 Fresh、V2.6.2 Upgrade、V2.6.3 Upgrade 三路径验收。修复通过前不得晋级 V2.6.3，也不得进入 ROLE/POSITION/ORG 实现。
