# V2.6.16 ROLE Realtime Eligibility Evidence MySQL/Flyway 验收报告

## 1. 最终结论

**PASS**。V2.6.16 已达到 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。

运行状态保持：`ROLE_RUNTIME_DISABLED`。本报告没有声明真实 Directory 可用；所有 Directory 相关验收均为数据库约束与冻结契约验证。

## 2. 环境与隔离

- MySQL Community Server 8.4.9
- Flyway 13.0.0
- Java 21.0.12
- Maven 3.9.9
- 最终证据根：`D:\codex-validation-v2616-final-20260820-150430\evidence`
- 两个全新、loopback-only、一次性实例：Fresh 38620、Upgrade 38621
- 未连接生产、托管或未知数据库；未执行 clean、repair、baseline 绕过或 out-of-order。

## 3. Flyway结果

| 路径 | migrate | strict validate | 二次 migrate | V2.6.16次数 |
|---|---|---|---|---|
| Fresh 基线→2.6.16 | PASS | PASS | No migration necessary | 1 |
| Upgrade 2.6.15→2.6.16 | 仅执行2.6.16 | PASS | No migration necessary | 1 |

- V2.6.16 SHA-256：`d5d0b154eaea4c17dc8b11d7bebda0627c9f31fd96b13043545356a5a2286084`
- Flyway checksum：`-1570425240`
- Migration manifest：38/38，无摘要漂移

## 4. Schema Fingerprint

Fresh 与 Upgrade 完全一致：

- Full：`08c2002afa1e01f1de2b82cfb9adf36f1c0a8e0b788c9bacc03109c21888e5c0`
- Workflow：`b55ca8d904f920db2d10ea5f8eb24e3c4f040288d48f44d00688e80f8b97b6ea`
- Realtime Eligibility：`0e8bac7935499b6290276283b594e5ff8b22a5581beebf8f0e81e57573c93e3b`

结构统计：四表分别 52/16/19/19 字段，42 个约束、60 个索引记录、11 个 realtime 表 Trigger；连同 Claim 三个门禁 Trigger 共 14 个 V2.6.16 Trigger。

## 5. 正向链路

真实 MySQL 完成：Header→27 Validator Evidence→10 Capability Evidence→PREPARED→VERIFIED→Claim→CONSUMED→Claim Audit。两个 Root 与 Persistence Hash 由数据库重新计算并匹配，Evidence/Claim 冗余所有权一致。

一次故意令 Audit 失败的同事务探针证明 Event 随事务回滚，随后合法事务完整提交。

## 6. 负向矩阵

15/15 PASS：Header/Validator/Capability/Event UPDATE 与 DELETE；Validator、Capability、Event Hash 漂移；ROLE Claim 缺 Evidence；Evidence 重复消费；Claim Evidence 绑定修改；Claim Audit Persistence Hash 不一致，均被数据库拒绝。

首次候选额外发现并关闭生命周期前驱排序 P0。修订后：错误 Event Hash 返回 `ROLE_REALTIME_EVENT_HASH_MISMATCH`，合法序号链成功。

## 7. 并发、幂等和回滚

- 双 Session 同 eligibility request：1 成功、1 唯一键失败。
- 无 MySQL 1213 deadlock、无 1205 lock timeout。
- Claim Evidence 唯一键与活动 Claim 唯一键共同保证单次消费。
- 服务层双幂等键同 payload 返回原证据，payload 漂移拒绝。
- Evidence/Audit 失败事务无部分生命周期事实残留。

## 8. 应用回归

- 后端全量测试：530 PASS / 0 failure / 0 error / 0 skipped
- Spring Boot context：PASS
- Domain 无 Spring/MyBatis/Entity 依赖：PASS
- Legacy、USER、DIRECT 回归及隔离契约：PASS
- `git diff --check`：PASS

## 9. 资产与剩余风险

最终资产：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。首次失败候选及失败证据保留，未删除失败历史。

剩余风险仅为外部资源边界：真实 Approval Role Directory 仍未准入，因此未验证真实网络、认证、时钟偏差或生产目录语义。此项不影响 V2.6.16 数据库/契约验收，但在任何 ROLE Runtime 启用前必须单独完成资源准入与真实联调。
