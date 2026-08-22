# V2.6.9–V2.6.11 ROLE Runtime Persistence Combined Validation Report

## 1. 最终结论

**FAIL / BLOCKED / NOT PROMOTED**。

三条正常迁移路径及绝大多数完整性测试通过，但V2.6.11未能拒绝小写历史
`resolver_version`。其 `REGEXP` 使用表字段的`utf8mb4_general_ci`比较语义，
大小写不敏感；测试值`lowercase`被接受，Migration成功并创建2个永久Trigger。
该P0违反“非法Resolver Version必须在永久DDL前失败”，因此所有资产保持Candidate。

## 2. 环境信息

| 项目 | 版本/说明 |
| --- | --- |
| MySQL | Community Server 8.4.9 |
| Flyway | 13.0.0 |
| Java | 21.0.12 |
| Maven | 3.9.9 |
| 隔离方式 | 7个全新、loopback-only、一次性MySQL实例 |
| 证据目录 | `D:\codex-validation-v2611-combined-final-20260813-1520\evidence` |
| 外部环境 | 未连接生产、托管或未知数据库 |

## 3. 三路径Migration结果

| 路径 | migrate | History | validate | 二次migrate |
| --- | --- | --- | --- | --- |
| Fresh：V2.0.0→V2.6.11 | PASS | 34成功、0失败 | PASS | no-op |
| Upgrade：V2.6.8→V2.6.11 | PASS；仅新增2.6.9/10/11 | 每项1次 | PASS | no-op |
| Forward Fix：V2.6.10→V2.6.11 | PASS；无需repair/history修改 | 仅新增2.6.11 | PASS | no-op |

## 4. Flyway Checksum

| Migration | Checksum |
| --- | ---: |
| V2.6.9 | 1523438049 |
| V2.6.10 | 936550308 |
| V2.6.11 | 1342469954 |

## 5. SHA-256

| Migration | SHA-256 |
| --- | --- |
| V2.6.9 | `fbe41e2a354e616ee4b369c57980009057c75ced46a2eb9e48d1eb8354beb0d2` |
| V2.6.10 | `2fad3f67a4a487568f5fc29f40cec73894d89fff65564212a75be98da186f7d6` |
| V2.6.11 | `ee16801af2f989449b1372ff82d76bd9850950a51ea7badff0125804c0815b06` |

Migration SHA清单33/33匹配，三个冻结SQL均未修改。

## 6. Schema Fingerprint

Fresh、Upgrade、Forward Fix三路径完全一致：

| 范围 | SHA-256 |
| --- | --- |
| Full | `b6c5061eae8d704dc76d82b30ffead959a63d3054dd344751a2f213229f8885b` |
| Workflow | `b5036e29809b3c90f31326b428bb7fb9ef8088ca6a28a22d04a04480f07f258d` |
| ROLE Runtime | `3b8e74374d55467e48066c4d0025e79872b330f5e51e45b0ba0d8dc06dff47d9` |

## 7. V2.6.11 Guard结果

| 场景 | 预期 | 实际 | 永久对象前→后 | 结果 |
| --- | --- | --- | --- | --- |
| 小写Resolver Version | 3819阻断 | Migration成功 | 0→2 | **FAIL** |
| Approval/Snapshot Contract不一致 | 3819阻断 | 3819 | 0→0 | PASS |
| 非法Binding Hash | 3819阻断 | 3819 | 0→0 | PASS |
| Canonical Hash漂移 | 3819阻断 | 3819 | 0→0 | PASS |
| 合法历史证据 | 成功 | 成功 | 0→2 | PASS |

根因是MySQL正则继承大小写不敏感排序规则。V2.6.11虽写明大写字符类，
`resolver_version NOT REGEXP '^[A-Z0-9][A-Z0-9_.-]{0,63}$'`仍接受小写字符。

## 8. Trigger结果

| 测试 | 错误 | 结果 |
| --- | --- | --- |
| Approval UPDATE | 1644 / `ROLE_RUNTIME_APPROVAL_APPEND_ONLY` | PASS |
| Approval DELETE | 1644 / `ROLE_RUNTIME_APPROVAL_APPEND_ONLY` | PASS |
| Snapshot UPDATE | 1644 / `ROLE_RUNTIME_SNAPSHOT_IMMUTABLE` | PASS |
| Snapshot DELETE | 1644 / `ROLE_RUNTIME_SNAPSHOT_IMMUTABLE` | PASS |
| 非法Snapshot INSERT | 1644 / `ROLE_RUNTIME_SNAPSHOT_CANONICAL_HASH_MISMATCH` | PASS |

## 9. P0缺口关闭复验

- 空`resolver_version`：由V2.6.10拒绝；
- Snapshot UPDATE/DELETE：拒绝；
- Approval UPDATE/DELETE：拒绝；
- PENDING Approval生成Snapshot：拒绝；
- Contract不一致：V2.6.11 Guard以3819拒绝；
- 非法Snapshot Hash：V2.6.11 Guard以3819拒绝；
- **Resolver Version大小写格式：未关闭，仍为P0。**

## 10. 负向测试矩阵

迁移后5/5 Trigger负向测试通过；历史Contract、Hash、Canonical三类Guard通过；
非法Resolver Version Guard失败。失败路径没有被其他通过项降级或忽略。

## 11. 并发结果

两个独立MySQL会话同时写同一Approval的Snapshot：退出码`0,1`，仅一个成功；
失败方为1062唯一键冲突。未出现1213死锁或1205锁等待超时。

## 12. 兼容性结果

- EXPLICIT_USER_V1、USER+DIRECT、Legacy Task未重新解析；
- 未生成ROLE Task、Candidate Pool Runtime或Claim Runtime；
- 合法`APPROVED Approval → FROZEN Snapshot → Binding Evidence`链通过；
- Approval/Snapshot Contract与存储/重算Binding Hash一致；
- 未访问真实Role Directory，未修改Investment；
- ROLE Runtime始终关闭。

Java 21后端全量回归394项通过，0失败、0错误、0跳过；Spring Boot测试上下文
正常启动。新增`V2611CombinedValidationContractTest`及历史Guard契约测试通过。

## 13. Migration资产状态

| 资产 | 状态 |
| --- | --- |
| V2.6.9 | `CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED` |
| V2.6.10 | `CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED` |
| V2.6.11 | `CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED` |

未标记任何`CANONICAL_IMMUTABLE`或组合验证成功状态。

## 14. 剩余风险

1. 小写或混合大小写Resolver Code/Version历史值可穿透V2.6.11并进入永久治理对象；
2. V2.6.11使用的`BINARY expr`在MySQL 8.4产生弃用警告，后续应改为明确CAST/COLLATE；
3. MySQL DDL自动提交，Guard漏检后不能依赖事务消除已创建Trigger；
4. 本报告仅代表隔离MySQL验收，不代表任何托管环境已执行。

## 15. 下一步建议

保持`ROLE_RUNTIME_DISABLED`。不得修改已验收失败的V2.6.11；应单独设计新的前向
Migration，使用显式二进制/大小写敏感比较（例如`REGEXP_LIKE(..., 'c')`或ASCII
binary CAST/COLLATE）扫描Resolver Code/Version，并在任何永久DDL前fail-closed。
重新组合验收通过前，不进入WF5.8、不启用ROLE Resolver、不接Directory或Investment。
