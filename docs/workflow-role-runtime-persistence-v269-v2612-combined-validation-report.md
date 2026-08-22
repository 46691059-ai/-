# Workflow ROLE Runtime Persistence V2.6.9—V2.6.12 Combined Validation Report

## 1. 最终结论

**PASS**。

V2.6.9、V2.6.10、V2.6.11、V2.6.12 在真实、隔离、一次性 MySQL 8
环境中的四路径组合验收全部通过。历史失败由前向 Migration 链关闭，未
修改任何历史 SQL。ROLE Runtime 仍为关闭状态。

## 2. 环境信息

| 组件 | 版本/约束 |
|---|---|
| MySQL | Community Server 8.4.9 |
| Flyway | 13.0.0 |
| Java | 21.0.12 |
| Maven | 3.9.9 |
| 数据库 | 4 个新建、隔离、仅绑定 `127.0.0.1` 的临时实例 |
| 验收证据 | `D:\codex-validation-v2612-final-20260813-1530\evidence` |

未连接生产、托管、未知或既有数据库。

## 3. 四路径结果

| 路径 | 起点与执行 | 结果 |
|---|---|---|
| A Fresh | 空基线 → V2.6.12 | PASS；35 条成功 history，0 失败；strict validate；二次 migrate no-op |
| B Upgrade | V2.6.8 → V2.6.9—V2.6.12 | PASS；4 个版本各执行一次；validate；no-op |
| C Forward Fix | V2.6.11 → V2.6.12 | PASS；只执行 V2.6.12；无需 repair/history 修改；validate；no-op |
| D Failed Guard Recovery | V2.6.11 成功后注入非法版本、Contract 漂移、Binding Hash 漂移 | PASS；V2.6.12 以 3819 失败；目标 DDL 签名前后一致 |

## 4. Flyway Checksum

| Migration | Flyway checksum |
|---|---:|
| V2.6.9 | `1523438049` |
| V2.6.10 | `936550308` |
| V2.6.11 | `1342469954` |
| V2.6.12 | `-1172180638` |

## 5. 四版本SHA-256

| Migration | SHA-256 |
|---|---|
| V2.6.9 | `fbe41e2a354e616ee4b369c57980009057c75ced46a2eb9e48d1eb8354beb0d2` |
| V2.6.10 | `2fad3f67a4a487568f5fc29f40cec73894d89fff65564212a75be98da186f7d6` |
| V2.6.11 | `ee16801af2f989449b1372ff82d76bd9850950a51ea7badff0125804c0815b06` |
| V2.6.12 | `55513b47c278d2e7d8f5d7656115b1383c3c50494bd221d95f58c66f972ec67e` |

治理清单：34/34 匹配，无摘要漂移。

## 6. Schema Fingerprint

Fresh、Upgrade、Forward Fix 完全一致：

| 范围 | SHA-256 |
|---|---|
| Full Schema | `4f5082397adc177cde53e9a0f689c003b1eed499e8f47894e3fdbac4ecdfd2db` |
| Workflow Schema | `1b2d40c88d4d60bab786aeeb542d5e30a94fa2e62fb0320326f6cf3186f85ba8` |
| ROLE Runtime Schema | `40344f50f488a5b7ef107fe382bbc3546b0f17089596ad26422c1ac3b22f87e3` |

## 7. V2.6.12字段治理结果

两个字段均通过 `information_schema.columns` 核查：

| 字段 | 修改前 | 修改后 |
|---|---|---|
| `role_runtime_binding_approval.resolver_version` | `varchar(64) / utf8mb4 / utf8mb4_general_ci / NOT NULL` | `varchar(64) / ascii / ascii_bin / NOT NULL` |
| `workflow_role_runtime_binding_snapshot.resolver_version` | `varchar(64) / utf8mb4 / utf8mb4_general_ci / NOT NULL` | `varchar(64) / ascii / ascii_bin / NOT NULL` |

Resolver identity CHECK 使用显式大小写敏感 `REGEXP_LIKE(..., 'c')`。

## 8. Guard结果

Guard 覆盖 Approval/Snapshot 的 Resolver Code、Resolver Version、Contract
Hash、Binding Hash、关联一致性与 Canonical Hash。Failed Guard Recovery 同时
注入：

- `role_directory_v1`
- Approval/Snapshot Contract Hash 不一致
- 非法大写 Binding Hash

V2.6.12 在临时 Guard INSERT 处以 MySQL `3819` 失败。V2.6.12 目标列、CHECK、
Trigger 的验收签名前后一致，无部分 ALTER、CHECK 或 Trigger 残留。

## 9. Trigger结果

| 操作 | 结果 | MySQL |
|---|---|---|
| Approval UPDATE | 拒绝 | `1644 / 45000 / ROLE_RUNTIME_APPROVAL_APPEND_ONLY` |
| Approval DELETE | 拒绝 | `1644 / 45000 / ROLE_RUNTIME_APPROVAL_APPEND_ONLY` |
| Snapshot UPDATE | 拒绝 | `1644 / 45000 / ROLE_RUNTIME_SNAPSHOT_IMMUTABLE` |
| Snapshot DELETE | 拒绝 | `1644 / 45000 / ROLE_RUNTIME_SNAPSHOT_IMMUTABLE` |

## 10. 负向测试矩阵

| 输入 | 结果 | 数据库证据 |
|---|---|---|
| `ROLE_DIRECTORY_V1` | PASS | 合法 INSERT |
| `role_directory_v1` | PASS（拒绝） | 3819 CHECK |
| `Role_Directory_V1` | PASS（拒绝） | 3819 CHECK |
| `ROLE_directory_V1` | PASS（拒绝） | 3819 CHECK |
| 空字符串 | PASS（拒绝） | 3819 CHECK |
| NULL | PASS（拒绝） | 1048 NOT NULL |

## 11. Canonical Hash

`ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1` 验证通过：Resolver Version 大小写、
Contract Hash、Binding 相关组织输入任一变化均产生不同 SHA-256。未进行
大小写归一化或历史 Hash 重算。

## 12. 并发结果

两个独立会话同时对相同 Approval 和 Snapshot 执行一致性读取：均成功，
退出码 `0,0`，无 `1213` 死锁、无 `1205` 锁等待超时，读取到的 Resolver
Version、Contract Hash 和 Binding Hash 一致。

## 13. 兼容性结果

- Java 21 编译、Spring Boot 上下文及后端全量测试通过：407 项通过，0 失败、
  0 错误、0 跳过。
- EXPLICIT_USER_V1 与 USER + DIRECT 后端回归通过。
- Legacy Task 与历史 Candidate Pool 回归通过。
- Migration 前后运行对象计数一致：`DIRECT=0, CandidatePool=0, Claim=0,
  ROLE Snapshot=1`（隔离验收 Fixture）。
- 未生成 ROLE Task、ROLE Candidate Pool 或 Claim。
- 未重新解析历史 Task/Candidate Pool。
- 未调用真实 Role Directory，未修改 Investment。

## 14. 资产状态

| 资产 | 状态 |
|---|---|
| V2.6.9 | `CANONICAL_IMMUTABLE / VALIDATED_WITH_V2610_V2611_V2612` |
| V2.6.10 | `CANONICAL_IMMUTABLE / VALIDATED_WITH_V2611_V2612` |
| V2.6.11 | `CANONICAL_IMMUTABLE / VALIDATED_WITH_V2612` |
| V2.6.12 | `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED` |

`ROLE_DIRECTORY_V1` 保持 `PREPARED`，`ROLE_RUNTIME_DISABLED`。

## 15. 剩余风险与下一步建议

- 本报告证明隔离 MySQL 8.4.9 的结构与约束行为，不代表生产发布完成。
- 正式发布仍需备份、变更窗口、容量/锁时长评估及发布后 validate。
- Approval/Snapshot 并发验收为只读竞争；ROLE Runtime 未启用，因此未验证
  真实 ROLE 任务、候选池或 Claim 并发。

下一步只能在新的明确 Sprint 中规划预生产发布或 ROLE Runtime 启用门禁。
本阶段不进入 WF5.8，不实现 ROLE Task、Candidate Pool Runtime、Claim
Runtime 或 Investment Integration。
