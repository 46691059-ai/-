# V2.6.13 ROLE Runtime Activation Evidence 最终验收报告

## 1. 最终结论

`PASS`。重新设计且内容冻结的 V2.6.13 已通过隔离 MySQL 8/Flyway 最终验收，状态晋级为 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。

最终业务状态为：

- `ROLE_RUNTIME_ACTIVATION_EVIDENCE_PERSISTED`
- `ROLE_RUNTIME_DISABLED`

本次没有创建 V2.6.14，没有执行 `repair`，没有改写 Flyway history，没有启用 ROLE Runtime，也未进入 WF5.11。

## 2. 环境版本

| 项目 | 版本/说明 |
| --- | --- |
| MySQL | MySQL Community Server 8.4.9 |
| Flyway | 13.0.0 |
| Java | 21.0.12 |
| 验收实例 | 新建、隔离、仅监听 loopback 的一次性实例 |
| 生产连接 | 未连接 |

## 3. Fresh 结果

Fresh 路径从项目基础版本执行至 V2.6.13，全部 Migration 成功，Flyway history 无失败，strict validate 通过，第二次 migrate 返回 no-op。V2.6.13 仅执行一次。

## 4. Upgrade 结果

V2.6.12 至 V2.6.13 升级成功，仅新增执行 V2.6.13；strict validate 通过，第二次 migrate 为 no-op。

## 5. Forward 结果

V2.6.9 → V2.6.10 → V2.6.11 → V2.6.12 → V2.6.13 前向路径成功；无需 `repair`、history 修改或版本绕过，strict validate 与第二次 migrate no-op 均通过。

## 6. Flyway checksum

V2.6.13 Flyway checksum：`1882937893`，与冻结基线一致。

## 7. SHA-256

- 当前 V2.6.13：`da80b7d3483ba3f3ee6b4f938515e94730695e28fc9842b9e488eb8daa68fd17`
- 已归档失败候选：`7ec6ec6441006dc7f114b92adfc60efdfb2cc8f66bda3a6a7a52d9fcbf3168e6`
- 治理清单：35/35 匹配

验收前后当前 Migration 文件摘要无漂移；失败候选继续保留为 `FAILED_CANDIDATE_ARCHIVED`。

## 8. Schema Fingerprint

| 指纹范围 | SHA-256 |
| --- | --- |
| Full Schema | `f62b80c19ddf928bb9ed6db65c5792091dfbff56cc74d229c187927b3e5ffb1b` |
| Workflow Schema | `2b31cdbc9c7d9863893b2dc0d357c792cdfbc51185fce9961bd3602a354c1385` |
| ROLE Runtime Schema | `2dd606e66d8eca899f2cb96e60f649ba480ba1fe4df2f0c9909ebabf2a197573` |

Fresh、Upgrade、Forward 三条路径的三个指纹逐项相同。V2.6.13 对象统计为 3 张表、60 个字段、16 个索引、3 个外键、17 个 CHECK、8 个 Trigger。

## 9. Guard 结果

不存在 Activation Request、孤立 Approval/Evidence、重复 activation_id、非法 Hash、非法状态和非法 delete_token 均被数据库拒绝。Guard 失败后新增目标表数为 0、新增 Trigger 数为 0，未产生本 Migration 的部分永久对象，也未自动修复数据。

`resolver_version` 的非空、格式与大小写规则沿用已冻结治理链，并在三路径中保持一致。

## 10. FK 结果

Request → Approval → Evidence 强关联验证通过：

- Approval 通过 `(activation_id, delete_token)` 归属于 Request；
- Evidence 通过 `(activation_id, delete_token)` 归属于 Request；
- Evidence 通过 `(approval_id, activation_id, delete_token)` 关联唯一 Approval；
- 无 Approval 的 Evidence 写入被错误 1644/SQLSTATE 45000 拒绝。

## 11. Hash 结果

Activation、Contract、Binding Hash 在 Request、Approval、Evidence 链路中一致。长度错误、大写或非规范 Hash、Contract Hash 不一致、Binding Hash 漂移均被 CHECK 或 INSERT Trigger 拒绝。

## 12. Trigger 结果

Approval 和 Evidence 的 append-only 规则通过：

| 操作 | 结果 | 数据库错误 |
| --- | --- | --- |
| Approval UPDATE/DELETE | 拒绝 | 1644 / 45000 `ROLE_RUNTIME_ACTIVATION_APPROVAL_APPEND_ONLY` |
| Evidence UPDATE/DELETE | 拒绝 | 1644 / 45000 `ROLE_RUNTIME_ACTIVATION_EVIDENCE_APPEND_ONLY` |

## 13. 事务结果

模拟 Evidence 写入失败后，Request、Approval、Evidence 整体回滚，残留记录数为 0。

## 14. 并发结果

两个 Session 同时创建同一 Activation：1 个成功，1 个由唯一约束以错误 1062 拒绝；未出现 1213 死锁或 1205 锁等待超时。

## 15. 应用测试结果

- Java 21 编译：PASS
- Spring Boot context：PASS
- 后端全量测试：434 通过，0 失败，0 错误，0 跳过
- Migration SHA：35/35
- `git diff --check`：PASS
- ROLE Directory Resolver：保持 `PREPARED / NON_EXECUTABLE`
- ROLE Runtime Bean：未启用

## 16. Migration 资产状态

V2.6.13 已更新为：

- `asset_status: CANONICAL_IMMUTABLE`
- `execution_status: EPHEMERAL_MYSQL8_VALIDATED`
- `flyway_checksum: 1882937893`

失败候选 SQL 和失败验收报告继续保留，不覆盖、不删除。

## 17. 剩余风险

- 本次证据来自一次性隔离 MySQL 实例，不代表生产发布授权；生产前仍需执行备份、容量、权限和变更窗口检查。
- 数据库约束已验证证据链完整性，但 ROLE Directory 的真实性、业务职责分离和人员实时资格尚未接入。
- ROLE Runtime 仍被明确禁用，因此本报告不证明 ROLE Task、Candidate Pool Runtime 或 Claim Runtime 可生产启用。

## 18. 下一步建议

保持 V2.6.13 不可变并继续维持 `ROLE_RUNTIME_DISABLED`。后续任务应单独审批，先验证真实 Directory 契约、业务 SoD 和生产发布门禁；未经授权不得进入 WF5.11、ROLE Task Runtime、Candidate Pool Runtime、Claim Runtime 或 Investment Integration。
