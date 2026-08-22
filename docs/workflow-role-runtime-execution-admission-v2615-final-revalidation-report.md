# V2.6.15 Final Approval Integrity 真实 MySQL/Flyway 重验报告

## 1. 最终结论

**PASS**。修订后的 V2.6.15 关闭本轮十项 P0，晋级为
`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。ROLE Runtime 仍为
`DISABLED`，`ROLE_DIRECTORY_V1` 仍为 `PREPARED / NON_EXECUTABLE`。

## 2. 环境版本

- MySQL Community Server 8.4.9，回环地址、无密码、一次性数据目录
- Flyway Community Edition 13.0.0
- Java 21.0.12
- Maven 3.9.9
- 未连接生产、共享、托管或未知数据库

## 3. Fresh

完整基线至 V2.6.15 成功；history 失败记录 0；V2.6.15 成功记录 1；strict
validate PASS；第二次 migrate 输出 `No migration necessary`。

## 4. Upgrade

V2.6.14 至 V2.6.15 仅执行 V2.6.15 一次；strict validate PASS；第二次
migrate no-op。历史 Candidate 的10个未来证据字段保持 NULL，未回填或推断。

## 5. Flyway checksum

V2.6.15 observed checksum：`538981271`。

## 6. SHA-256

- V2.6.15：`db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44`
- V2.6.14：`a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442`
- Migration 清单：37/37 匹配
- 失败候选 `405fb7fc…`、`7887bfb1…` 继续保留，未覆盖或删除

## 7. Schema Fingerprint

Fresh 与 Upgrade 完全一致：

| 范围 | SHA-256 |
|---|---|
| Full | `ac2d1ea143f8628b4f2b49db8c46bee07706eb47127d5d6d5a63e538dd682db1` |
| Workflow | `1bc5d05940fdb7450d8562bda6f14380a721aa4080fcd1f6e79053a4527ff87e` |
| Execution Admission | `4cc55c932c4440605a2c1f49d2ac2cfa687d49875149732653fd8205d6be9530` |

指纹覆盖 Table、Column、Index/Unique、FK、CHECK 和 Trigger。

## 8. 28项 Evidence

0/28、27/28 均被 `1644 / 45000 / ROLE_ADMISSION_EVIDENCE_INCOMPLETE`
拒绝；27 PASS + 1 FAIL 被 `ROLE_ADMISSION_VALIDATOR_CONTRACT_MISMATCH`
拒绝。精确1..28序号及冻结 validator code 由 CHECK、两个 UNIQUE 和 Final
Event Guard 共同强制；29、重复序号或重复代码不能形成合法集合。

## 9. Capability 8/8

固定集合为 DIRECTORY、REALTIME_ELIGIBILITY、DATA_SCOPE、SOD、AUDIT、
FEATURE_FLAG、KILL_SWITCH、CANARY_SCOPE。合法8/8 PASS+READY通过。

## 10. Capability负向矩阵

DIRECTORY NOT_READY、REALTIME_ELIGIBILITY DEGRADED、DATA_SCOPE BLOCKED、SOD
NOT_READY、AUDIT DEGRADED、FEATURE_FLAG BLOCKED、KILL_SWITCH NOT_READY、
CANARY_SCOPE DEGRADED 全部被
`1644 / 45000 / ROLE_ADMISSION_CAPABILITY_NOT_READY`拒绝。

## 11. DB Root

MySQL 从实际8项 Evidence 按 capability code 稳定排序重算：
`9f73f6861c4969751365f82d540715ec272f336b193d0f95a41de903b9f1e5cb`。
Trigger 使用固定8槽，不依赖 `GROUP_CONCAT` 会话上限。

## 12. Java/DB Canonical

Java固定向量与MySQL实际重算值完全一致。逆序输入保持同Root；status、
evidenceHash、providerVersion或policyVersion变化均改变Root。首次验收查询曾因
默认 `group_concat_max_len=1024` 截断产生假阴性，已保留证据并修正验收查询；
Migration与业务代码未因此修改。

## 13. Root负向

Event提交错误Root，以及实际Evidence变化后继续提交旧Root，均被
`ROLE_ADMISSION_CAPABILITY_ROOT_MISMATCH`拒绝；缺少Capability先在完整性门禁拒绝。

## 14. Persistence Hash

Event的 `source_persistence_hash` 必须等于Admission冻结值。Root正确但
Persistence错误被 `ROLE_ADMISSION_PERSISTENCE_HASH_MISMATCH`拒绝。Execution、
Capability Root、Persistence三类Hash使用独立字段。

## 15. Final Event Guard

实测确认Admission归属、事件链、稳定Decision、未过期、28/28合同、全部PASS、
8/8 PASS+READY、Evidence格式、实际Root、Persistence引用、Slot归属、
token/version以及Decision/Closure终态共同Fail Closed。

## 16. Slot DELETE

`trg_role_admission_slot_no_delete`以
`1644 / 45000 / ROLE_RUNTIME_ADMISSION_SLOT_DELETE_FORBIDDEN`拒绝直接DELETE。

## 17. Slot CAS

合法占用成功；version跳跃、snapshot/ownership修改、token不变或绕过状态转换均由
`trg_role_admission_slot_update_guard`拒绝。双Session竞争时最终仅一个OCCUPIED、
version=1。

## 18. Slot Release

APPROVED后追加REVOKED，合法释放将active指针置NULL、token变化、version+1且保留
Slot行。不存在DELETE释放。

## 19. Terminal

Decision终态与Closure终态生成列唯一索引均存在。APPROVED后REJECTED冲突、
REVOKED后EXPIRED冲突均被状态链/唯一终态治理拒绝。

## 20. 双Session批准

两个独立Session同时提交同一Admission最终批准：1成功、1返回
`ROLE_ADMISSION_EVENT_CHAIN_INVALID`；最终APPROVED事件数为1；无1213/1205。

## 21. 并发

- Slot CAS：单赢家，另一会话更新0行，无死锁/锁超时。
- 相同Evidence序号/validator：单赢家，另一会话1062，无重复Evidence。

## 22. 幂等

Admission ID、request、Candidate+idempotency key以及Event idempotency均受唯一键保护；
重复请求不会生成第二Admission、第二套Evidence或第二批准Event。

## 23. Partial Install

当前候选真实目标对象碰撞Guard在永久DDL前失败且零新增。此前冻结的10类部分安装
矩阵继续由同一Pre-DDL Guard覆盖；本轮Migration Contract Test再次验证Candidate
部分字段、四表、索引、FK、CHECK和Trigger均纳入检测，禁止自动续装或修复。

## 24. Guard零新增

真实Guard失败前后Candidate新增字段数不变，Evidence/Event/Slot新增表为0；无新增
Index、FK、CHECK或Trigger，无repair、baseline绕过或history修改。

## 25. 事务回滚

- Admission写入后Evidence非法Hash失败：Admission计数恢复为0。
- Slot CAS成功后最终批准因27/28 Evidence失败：Slot恢复VACANT/version=2，Event为0。
- 应用Persistence Service继续由同一`@Transactional`边界覆盖。

## 26. 合法链路

Candidate Snapshot → Admission → 28 PASS Evidence → 8 Capability PASS+READY →
CREATED → ELIGIBLE → Slot CAS → APPROVED_FOR_EXECUTION 全部成功；随后REVOKED和
Slot Release成功。未创建任何运行对象。

## 27. Revision / Definition / Node

Directory revision/result/fence、Definition、published Version、Release、Node、
node binding和graph hash继续由Candidate/Admission复合FK及Insert Guard冻结；跨归属
负向规则未退化。

## 28. FK回归

11/11指定Ownership FK在`information_schema.referential_constraints`真实存在；
Slot父唯一键仍为`(id,snapshot_id,delete_token)`精确匹配。

## 29. Append-only

Admission、Evidence、Event的UPDATE/DELETE全部返回1644/45000；Slot DELETE拒绝，
仅合法token/version CAS UPDATE允许。

## 30. Legacy / USER

运行对象查询为0/0/0/0。EXPLICIT_USER_V1仍ACTIVE；ROLE_DIRECTORY_V1仍
PREPARED/NON_EXECUTABLE；USER+DIRECT及Legacy不进入Admission Persistence；历史
Candidate不补造Admission。

## 31. 应用测试

- Java 21 compile：PASS
- Spring Boot Context：PASS
- 后端全量：481通过、0失败、0错误、0跳过
- Migration Contract、DDL Dependency、Capability Root Canonical、Admission专项：PASS
- Domain纯净、Production Dependency Scan：PASS
- Migration SHA：37/37
- `git diff --check`：PASS

## 32. Migration资产状态

```text
V2.6.15
asset_status: CANONICAL_IMMUTABLE
execution_status: EPHEMERAL_MYSQL8_VALIDATED
sha256: db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44
flyway_checksum: 538981271
```

## 33. 临时环境清理

验收结束后已关闭37711、37712及相关回环MySQL进程；一次性数据库目录在报告和
资产证据固化后删除。未保留运行实例或密钥。

## 34. 剩余风险

1. 本次结论仅代表一次性MySQL 8.4.9验收，不代表生产环境已执行。
2. Slot高并发已验证单Candidate竞争，生产容量、长事务和监控阈值仍需发布演练。
3. ROLE Runtime仍禁用；本报告不授权Task、CandidatePool Runtime、Claim或Investment集成。

## 35. 下一步建议

保持ROLE Runtime禁用。下一任务应先进行独立发布准备评审；未经明确Sprint不得进入
WF5.19、创建V2.6.16或启用ROLE执行链。

## 36. 修改范围

- 新增最终重验脚本：`database/flyway/scripts/validate-v2615-final-runtime.ps1`
- 更新Migration Inventory和MySQL Migration README
- 新增本报告
- V2.6.15 SQL、V2.6.14及历史Migration、Workflow/Investment业务代码均未修改
