# V2.6.9 + V2.6.10 ROLE Runtime Persistence Combined Validation Report

## 1. 最终结论

**FAIL / BLOCKED / NOT PROMOTED**。

V2.6.9 与 V2.6.10 的三条正常迁移路径、Flyway 严格校验、二次迁移
no-op、Schema 收敛，以及迁移后的 15 项数据库完整性测试全部通过。
但是 V2.6.10 的永久 DDL 前 Guard 没有覆盖两类历史脏数据：

- Approval 与 Snapshot 的 Resolver Contract 不一致；
- Snapshot `binding_hash` 非法。

两类数据均未阻断 Migration，且执行后出现 6 个永久约束/Trigger，因此不满足
“Guard 在永久 DDL 前失败、无部分 DDL”的冻结标准。V2.6.9 与 V2.6.10 均不得晋级，
`ROLE_RUNTIME_DISABLED` 保持不变。

## 2. 环境信息

| 项目 | 版本/范围 |
| --- | --- |
| MySQL | Community Server 8.4.9 |
| Flyway | 13.0.0 |
| Java | 21.0.12 |
| Maven | 3.9.9 |
| 数据库 | 5 个全新、隔离、仅监听 loopback 的一次性实例 |
| 生产/未知数据库 | 未连接 |
| 证据目录 | `D:\codex-validation-v2610-combined-20260813-1210\evidence` |

## 3. 三路径结果

| 路径 | 结果 | History | strict validate | 二次 migrate |
| --- | --- | --- | --- | --- |
| Fresh：V2.0.0 → V2.6.9 → V2.6.10 | PASS | 33 成功、0 失败；2.6.9/2.6.10 各 1 次 | PASS | no-op |
| Upgrade：V2.6.8 → V2.6.9 → V2.6.10 | PASS | 新增 2 条，均仅执行 1 次 | PASS | no-op |
| Forward Fix：V2.6.9 → V2.6.10 | PASS | 32 → 33，未 repair、未改 history | PASS | no-op |

## 4. Checksum 与历史资产完整性

| Migration | Flyway checksum | SHA-256 | 结果 |
| --- | ---: | --- | --- |
| V2.6.9 | 1523438049 | `fbe41e2a354e616ee4b369c57980009057c75ced46a2eb9e48d1eb8354beb0d2` | 无漂移 |
| V2.6.10 | 936550308 | `2fad3f67a4a487568f5fc29f40cec73894d89fff65564212a75be98da186f7d6` | 无漂移 |

`SHA256SUMS` 共 32 项，32/32 匹配。未修改任何 Migration SQL。

## 5. Schema Fingerprint

Fresh、Upgrade、Forward Fix 三路径完全一致：

| 范围 | SHA-256 |
| --- | --- |
| Full Schema | `5e5293bdd366e155b5b30dcb0c8f551a236cdccaf174cf0701454497e5a11eb7` |
| Workflow Schema | `c69b3c784e0f3a50287bd7e2b8323f3eaa4d54f8c15b37cf428045b1b31f9f59` |
| ROLE Runtime Schema | `6cd8382a280d04426ceecceee6e770c7cb871c8eee781f73257fc73179b618ab` |

指纹包含表、字段、索引、外键、CHECK 与 Trigger 定义。

## 6. V2.6.9 P0 缺口关闭结果

| 验证项 | 数据库结果 | 判定 |
| --- | --- | --- |
| `resolver_version IS NULL` | 1048 / 23000 | PASS |
| 空 `resolver_version` | 3819 / HY000 | PASS |
| Snapshot UPDATE | 1644 / 45000 | PASS |
| Snapshot DELETE | 1644 / 45000 | PASS |
| Approval UPDATE | 1644 / 45000 | PASS |
| PENDING Approval 创建 Snapshot | 1644 / 45000 | PASS |

V2.6.10 在迁移完成后的治理约束关闭了上述五类原始 P0 缺口。

## 7. 负向测试矩阵

迁移完成后的 15/15 测试通过：NULL/空 Resolver Version、Snapshot
UPDATE/DELETE、Approval 的 status/approved_by/approved_at/hash 原位修改、
PENDING Approval 所有权、Resolver Code/Version/Contract 不一致，以及大写、
非 Hex、长度错误 Hash 均被数据库拒绝。

合法链路 `APPROVED Approval → Runtime Snapshot` 成功；未创建 ROLE Task、
ROLE Candidate Pool 或 Claim。

## 8. Guard 与 Trigger 验证

| Guard 脏数据场景 | Migration 退出码 | 永久对象前/后 | 结果 |
| --- | ---: | --- | --- |
| Approval/Snapshot Contract 不一致 | 0 | 0 → 6 | FAIL |
| 非法 Snapshot `binding_hash` | 0 | 0 → 6 | FAIL |

这两项证明 Guard 的扫描条件少于迁移后 INSERT Trigger/CHECK 的治理条件。
新数据会被阻断，但既有脏数据可在约束落地前穿透，无法证明 Upgrade 安全。

## 9. 兼容性验证

- `EXPLICIT_USER_V1` 绑定仍存在且行为未变；
- USER + DIRECT、Legacy Task 未被转换或重新解析；
- Candidate Pool、Claim 中无 ROLE 来源污染；
- 未调用 Approval Role Directory；
- 未修改 Investment；
- ROLE Runtime 始终未启用。

Java 21 后端全量回归：385 项通过，0 失败，0 错误，0 跳过；Spring Boot
测试上下文正常启动。新增 `V2610CombinedMigrationContractTest` 3/3 通过。

## 10. Migration 资产状态

| 资产 | 状态 |
| --- | --- |
| V2.6.9 | `CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED` |
| V2.6.10 | `CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED` |

此前 V2.6.9 单独失败历史保留；本次组合失败作为新增验收记录保留。未标记
`CANONICAL_IMMUTABLE`、`VALIDATED_WITH_V2610` 或
`EPHEMERAL_MYSQL8_VALIDATED`。

## 11. 剩余风险

1. 含 Contract 不一致或非法 Snapshot Hash 的真实升级库会完成 V2.6.10，
   留下与治理契约冲突的既有数据。
2. MySQL DDL 自动提交意味着 Guard 漏检后不能依赖事务回滚消除部分治理对象。
3. 本次是一次性隔离 MySQL 验收，不代表任何托管、预生产或生产环境已执行。

## 12. 下一步建议

保持 `ROLE_RUNTIME_DISABLED`。单独设计并实现新的前向修复 Migration：在任何
永久 DDL 前补充 Snapshot Hash 和 Approval/Snapshot Resolver Contract 的历史数据
扫描，发现异常立即失败且不自动修复；随后重新执行三路径与脏数据 Guard 组合验收。
在该验收通过前，不进入 WF5.8，不接真实 Directory，不创建 ROLE Task。
