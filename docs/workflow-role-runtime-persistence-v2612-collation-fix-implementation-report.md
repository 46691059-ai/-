# Workflow ROLE Runtime Persistence V2.6.12 Collation Forward Fix Implementation Report

## 1. 结论

Sprint 2-3.7-WF5.7.5 已完成前向修复候选实现。V2.6.12 只治理 ROLE
Runtime 持久化对象中 `resolver_version` 的大小写敏感性，不修改
V2.6.9—V2.6.11，不修复或推断历史证据，不启用 ROLE Runtime。

当前状态：

- `V2.6.12`: `CANDIDATE / NOT_EXECUTED`
- `ROLE_DIRECTORY_V1`: `PREPARED`
- `ROLE_RUNTIME_DISABLED`

## 2. 修改文件清单

- `database/migration/mysql/V2.6.12__fix_role_runtime_resolver_version_collation.sql`
- `database/migration/mysql/SHA256SUMS`
- `database/migration/mysql/README.md`
- `database/flyway/migration-inventory.yml`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/infrastructure/V2612ResolverVersionCollationContractTest.java`
- `docs/workflow-role-runtime-persistence-v2612-collation-fix-implementation-report.md`

未修改 V2.6.9、V2.6.10、V2.6.11、Workflow Runtime 业务代码及
Investment 模块。

## 3. Migration变化

新增 `V2.6.12__fix_role_runtime_resolver_version_collation.sql`，类型为
Forward Fix。执行顺序为：

1. 创建临时 Guard 对象。
2. 扫描两个 ROLE Runtime 表的历史 `resolver_version`。
3. 发现非法值时以 CHECK 失败终止，永久 DDL 尚未开始。
4. Guard 通过后删除临时对象。
5. 将两个目标列修改为显式 ASCII 二进制排序规则。
6. 使用原约束名重建大小写敏感 Resolver Version CHECK。

Migration 不包含 `UPDATE`、`DELETE`、大小写转换或默认值回填。

## 4. 字段Collation变化

| 表 | 修改前 | 修改后 |
|---|---|---|
| `role_runtime_binding_approval.resolver_version` | `VARCHAR(64)`，继承表级 `utf8mb4_general_ci` | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` |
| `workflow_role_runtime_binding_snapshot.resolver_version` | `VARCHAR(64)`，继承表级 `utf8mb4_general_ci` | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` |

`ascii_bin` 使 `ROLE_DIRECTORY_V1` 与 `role_directory_v1` 成为不同值，且
拒绝非 ASCII Resolver Version。

## 5. Guard规则

永久 DDL 前分别检查 Approval 与 Snapshot：

- 不允许 `NULL`。
- `TRIM` 后不能为空。
- 二进制比较必须与 `TRIM` 后值一致，禁止前后空格。
- `REGEXP_LIKE(value, '^[A-Z0-9][A-Z0-9_.-]{0,63}$', 'c')` 必须通过。
- 小写、混合大小写、非 ASCII 或格式非法值立即阻断。

Guard 不自动转换 `role_directory_v1`，也不猜测其正确业务版本。

## 6. CHECK规则

两个原 Resolver identity CHECK 名称保持不变，新增验证包括：

- Resolver Code 非空。
- Resolver Version 非空。
- Resolver Version 以显式大小写敏感模式匹配冻结格式。

预期矩阵：

| 输入 | 结果 |
|---|---|
| `ROLE_DIRECTORY_V1` | PASS |
| `role_directory_v1` | FAIL |
| `Role_Directory_V1` | FAIL |
| 空字符串 | FAIL |
| `NULL` | FAIL |

既有 Approval/Snapshot append-only Trigger 不被替换；非法 INSERT 由列定义和
CHECK 拒绝，UPDATE 继续由不可变 Trigger 拒绝。

## 7. Canonical Hash验证

`ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1` 已包含原始、未降级的
`resolverVersion`。契约测试验证大写、小写、混合大小写三种输入生成不同
SHA-256。V2.6.12 不改变 Domain Hash 算法。

## 8. 测试结果

新增 `V2612ResolverVersionCollationContractTest`，覆盖：

- Guard 必须先于永久 DDL。
- 两个目标列显式使用 `ASCII/ascii_bin`。
- CHECK 使用大小写敏感正则。
- 禁止历史数据修复 SQL。
- Canonical Hash 对 Resolver Version 大小写敏感。
- V2.6.12 保持 Candidate，ROLE Runtime 保持关闭。

执行结果：Java 21 编译及 Spring Boot 上下文通过；后端全量 404 项测试
通过，0 失败、0 错误、0 跳过；V2.6.12 契约测试 10 项通过；Migration
SHA 清单 34/34 匹配；`git diff --check` 通过。真实 MySQL/Flyway 三路径
组合验收不在本次实施范围，不能据此晋级资产。

## 9. SHA-256与资产状态

- SHA-256：`55513b47c278d2e7d8f5d7656115b1383c3c50494bd221d95f58c66f972ec67e`
- Flyway checksum：`null`
- 资产状态：`CANDIDATE / NOT_EXECUTED`
- 依赖：V2.6.11

## 10. 剩余风险与下一步

- 尚未在真实 MySQL 8 上验证 ALTER、CHECK 和既有 Trigger 的组合行为。
- 尚未验证含非法历史版本时 Guard 失败后无永久 DDL。
- 尚未验证 Fresh、Upgrade、Forward Fix 三路径 Schema Fingerprint 一致。
- V2.6.9—V2.6.11 仍为 Candidate，须与 V2.6.12 组合验收后统一判断。

下一步应单独执行 V2.6.9 → V2.6.12 真实 MySQL/Flyway 组合验收，包括
大小写负向测试、strict validate、二次 migrate no-op 和三路径 Schema
Fingerprint。不得进入 WF5.8 或启用 ROLE Runtime。
