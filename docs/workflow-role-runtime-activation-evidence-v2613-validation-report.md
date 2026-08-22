# V2.6.13 ROLE Runtime Activation Evidence 真实 MySQL/Flyway 验收报告

## 1. 最终结论

**FAIL / BLOCKED / NOT PROMOTED**

V2.6.13 在 Fresh、V2.6.12 Upgrade、V2.6.8 Forward 三条隔离路径均于同一 DDL
位置失败。MySQL 返回错误 `6125`：

> `fk_role_activation_approval_request` 引用父表 `activation_id`，但父表不存在仅由
> `activation_id` 构成的唯一键。

父表实际唯一键为 `(activation_id, delete_token)`，子表外键只引用
`activation_id`，不满足 MySQL 8.4.9 的外键父键要求。

按 Sprint 禁止项，本次没有修改 V2.6.13、没有创建 V2.6.14，也没有执行 repair。
最终保持：

- `V2.6.13 = CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`
- `ROLE_RUNTIME_DISABLED`

## 2. 环境信息

| 项目 | 版本/边界 |
|---|---|
| 数据库 | MySQL Community Server 8.4.9 |
| Flyway | 13.0.0 |
| Java | 21.0.12 |
| Maven | 3.9.11 |
| 网络 | `127.0.0.1` 独立端口 |
| 数据目录 | 三条业务路径及 Guard 路径分别使用新建一次性目录 |
| 生产/已知业务库 | 未连接 |

验收证据目录：`D:\codex-validation-v2613-20260813-1734\evidence`。

## 3. Fresh 结果

路径：基础初始化 + Flyway baseline `2.0.0` → `2.6.13`。

- V2.6.12 及之前 Migration 执行成功；
- V2.6.13 执行失败；
- SQL State：`HY000`；
- MySQL Error Code：`6125`；
- 失败语句位置：V2.6.13 第 74 行，创建
  `role_runtime_activation_approval` 及其 Request 外键；
- Flyway history：V2.6.13 `success = 0`；
- strict validate：不通过/不具备通过条件；
- 二次 migrate no-op：不成立。

## 4. Upgrade 结果

路径：先建立 V2.6.12 完成库，再执行 V2.6.13。

- 基线库建立成功；
- 仅 V2.6.13 尝试执行；
- 与 Fresh 相同，MySQL `6125` 失败；
- Flyway history：V2.6.13 `success = 0`；
- strict validate 与 no-op 不成立。

## 5. Forward 结果

路径：先建立 V2.6.8，再依次执行 V2.6.9 → V2.6.13。

- V2.6.9、V2.6.10、V2.6.11、V2.6.12 执行成功；
- V2.6.13 以相同 MySQL `6125` 失败；
- Flyway history：V2.6.13 `success = 0`；
- strict validate 与 no-op 不成立。

## 6. Checksum 与 SHA-256

- V2.6.13 Flyway checksum：`1627294290`
- V2.6.13 Repository SHA-256：
  `7ec6ec6441006dc7f114b92adfc60efdfb2cc8f66bda3a6a7a52d9fcbf3168e6`
- 候选 SQL 验收前后 SHA-256：一致
- V2.6.9–V2.6.12：未修改
- 当前正式扫描目录实际包含 `35` 个 Migration SQL，SHA 清单为 `35/35` 匹配，
  `0` 漂移。任务要求中的 `36/36` 与仓库实际资产数不一致；本次没有通过创建禁止的
  V2.6.14 或伪造清单条目来凑数。

## 7. Schema 与 Fingerprint

三条失败路径均观察到相同的非事务 DDL 残留：

- 已创建：`role_runtime_activation_request`
- 未创建：`role_runtime_activation_approval`
- 未创建：`role_runtime_activation_evidence`
- V2.6.13 Activation Trigger：`0`
- Flyway history：V2.6.13 失败行存在

Request 残留表包含 23 个字段、主键、两个唯一索引、两个普通索引及 7 个 CHECK。
由于最终三表 Schema 未形成，Full、Workflow、ROLE Runtime 的“验收通过态”Fingerprint
不存在，不能以部分失败 Schema 指纹替代最终指纹。

## 8. Guard 结果

结论：**不通过**。

- V2.6.13 的前置 Guard 能检查 V2.6.12 Resolver Version collation 和目标表是否已存在；
- 但本次正常 Fresh 路径在永久创建 Request 表后才因 Approval 外键失败；
- MySQL DDL 自动提交导致失败后留下部分永久对象；
- 因此未满足“失败不得创建部分永久对象”的验收要求。

任务列出的缺失 Request、Hash、状态、delete token 等属于目标表创建后的约束负向矩阵，
不是当前 SQL 前置 Guard 可扫描的历史对象。主 Migration 未完成，后续矩阵不能被判定为通过。

## 9. Trigger 与 Append-only 结果

**未达到可验收状态。** Migration 在创建第二张表时失败，六个 append-only Trigger 尚未创建。
因此 Approval/Evidence UPDATE、DELETE 拒绝测试不能执行，不能沿用静态契约测试作为真实
MySQL Trigger 验收结果。

## 10. 合法链路结果

**未执行/阻断。** Approval 与 Evidence 表不存在，无法建立：

`Activation Request → Approval Decision → Evidence Record`

同时确认本次验收没有创建 Runtime Binding、Task、Candidate Pool 或 Claim，也没有启用
ROLE Resolver Runtime。

## 11. 事务结果

**未执行/阻断。** 三表结构不完整，无法验证 Evidence 写入失败后的四类对象整体回滚。

另需关注：MySQL DDL 本身不在应用事务回滚边界内，本次失败已证明 Migration 失败会留下
Request 表，必须在后续前向治理或候选修复设计中显式关闭该风险。

## 12. 并发结果

**未执行/阻断。** 无完整 Schema，无法进行同一 Activation 双 Session 单赢家测试。
未报告 1213/1205 并不等于并发测试通过。

## 13. 应用回归结果

- Java 21 compile：通过
- Spring Boot Context：通过
- 后端全量测试：`432` 通过，`0` 失败，`0` 错误，`0` 跳过
- ROLE Runtime Active Bean：未引入
- 真实 Directory：未依赖
- `git diff --check`：通过
- Migration SHA：实际 `35/35` 匹配

应用测试通过不能覆盖真实 MySQL Migration 失败，资产不得晋级。

## 14. Migration 资产状态

| 属性 | 状态 |
|---|---|
| asset_status | `CANDIDATE` |
| execution_status | `EPHEMERAL_MYSQL8_VALIDATION_FAILED` |
| repository SHA-256 | `7ec6ec6441006dc7f114b92adfc60efdfb2cc8f66bda3a6a7a52d9fcbf3168e6` |
| observed Flyway checksum | `1627294290` |
| promoted | 否 |
| ROLE Runtime | `DISABLED` |

失败历史已保留在本报告、Inventory、README 和隔离 evidence 中，没有删除或改写。

## 15. 剩余风险

除当前外键阻断外，静态结构审计还发现后续必须单独验证的风险：

1. Evidence 表没有 `approval_id`，数据库无法证明某条 Evidence 属于具体 Approval；
2. Approval 表没有冻结 Resolver Contract 字段，无法在数据库层验证 Request/Approval Contract 一致；
3. Binding/Candidate/Activation Hash 仅校验格式，数据库没有 Canonical 重算或交叉一致性保护；
4. 三方 Approval 完整性目前主要依赖 Domain/Application，数据库无法阻止只写部分 Decision/Evidence；
5. Migration 失败后的部分 DDL 清理/恢复流程尚未冻结。

这些项没有因应用测试通过而关闭。

## 16. 下一步建议

保持 NO-GO，不进入 WF5.11。建议下一 Sprint 仅处理 V2.6.13 失败治理：

1. 决定候选 SQL 是否允许在未晋级状态下修正，或采用新的前向版本；
2. 统一外键与逻辑删除唯一键设计，例如让子表携带并引用完整稳定父键；
3. 增加 Approval→Evidence 强关联及 Contract/Canonical 一致性保护；
4. 设计失败后部分 DDL 的可识别恢复路径；
5. 修复后重新执行完整 Fresh、Upgrade、Forward、Guard、Trigger、事务、并发和 Fingerprint 验收。

在上述验收通过前，保持 `ROLE_RUNTIME_DISABLED`。
