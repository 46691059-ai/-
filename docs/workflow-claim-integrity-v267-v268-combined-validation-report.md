# Workflow Claim Integrity V2.6.7 + V2.6.8 组合验收报告

## 1. 最终结论

Sprint 2-3.7-WF4.2.4 结论为 **PASS**。Fresh、Upgrade、Forward-Fix 三条真实 MySQL 路径全部通过；V2.6.8 关闭了 V2.6.7 独立验收发现的三项 P0 数据库完整性缺口。允许将 V2.6.8 晋级为 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`，并将 V2.6.7 标记为 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED_WITH_V268`。

V2.6.7 的独立验收失败记录继续保留。V2.6.7 不是可独立停留的最终完整性版本，任何部署必须在同一治理发布链中连续执行 V2.6.7、V2.6.8。

## 2. 环境版本

- MySQL Community Server 8.4.9，四个一次性实例均仅监听 `127.0.0.1`，使用独立端口和 data 目录；其中三个用于正式路径，一个用于脏数据 Guard。
- Flyway Community Edition 13.0.0。
- Eclipse Temurin OpenJDK 21.0.12+8。
- Apache Maven 3.9.9。
- Windows 10 amd64；未连接生产、托管、未知或既有 3306/3307 实例。
- 校验脚本：`database/flyway/scripts/validate-v268-combined.ps1`。

## 3. Fresh 结果

空库从治理基线迁移至 V2.6.8，31 条 Flyway history 均成功、0 失败；`info`、严格 `validate` 通过，第二次 `migrate` 为 no-op。V2.6.7 与 V2.6.8 在 history 中各出现一次。

## 4. Upgrade 结果

独立空库先迁移至 V2.6.6，再升级 V2.6.7、V2.6.8。两个增量各执行一次，严格 `validate` 通过，第二次 `migrate` 为 no-op，0 失败 history。

## 5. Repair / Forward-Fix 结果

独立空库先成功执行到 V2.6.7，保留其合法 Flyway history，不执行 `flyway repair`、不删除 history、不重跑 V2.6.7，再直接执行 V2.6.8。前向修复成功，严格 `validate` 与第二次 no-op 均通过。

## 6. V2.6.7 checksum

- Flyway checksum：`1689998435`
- SHA-256：`800e1a5b1af67e1680a3f0233e9d5400c0b42f39349d181f1777c7ce04ac6aed`

## 7. V2.6.8 checksum

- Flyway checksum：`1316060036`
- SHA-256：`4cfd089f4aa05f6e4fb9d9dc250159c0229b3d98eb400089e5e08a758cb4a207`

## 8. SHA-256 复核

`SHA256SUMS` 中 30 项资产全部复核通过，V2.6.7、V2.6.8 摘要与冻结值一致；未修改任何历史 Migration，也未创建 V2.6.9。

## 9. Schema Fingerprint

指纹基于实际 `information_schema`、`SHOW CREATE TABLE` 和 `SHOW TRIGGERS` 输出，包含列、索引、外键、CHECK 与 Trigger 定义。三条路径完全一致：

| 范围 | Fresh = Upgrade = Repair SHA-256 |
|---|---|
| Full Schema | `092e9ba6374d749ccd9f1b9427a386e0e7e4fe34a7a68bda20642141c1512c38` |
| Workflow Schema | `3dbaae27e7c11f3492b28b5ba53d398ba5394cb7ff0921be5bacdf2fa418ba58` |
| Claim Integrity Schema | `2ef7874f10c305944b1041fa4fe649d359ba8c5ed73ebc164c5188f7b5f9642a` |

## 10. Claim 结构验证

实际结构确认新增并生效：

- `workflow_task.uk_workflow_task_claim_owner`；
- `workflow_task_candidate_pool.uk_workflow_candidate_pool_claim_owner`；
- `workflow_task_claim.uk_workflow_task_claim_audit_owner`；
- `fk_workflow_task_claim_task_execution`；
- `fk_workflow_task_claim_node_execution`；
- `fk_workflow_task_claim_pool_execution`。

这些复合键将 Task、NodeExecution、CandidatePool、CandidateMember 与 Claim 约束在同一 Instance 聚合内。直接插入引用不存在 NodeExecution 的新 Claim 被 MySQL `1452 / 23000` 拒绝，残留为 0。

## 11. Audit 结构验证

实际结构确认 `fk_workflow_task_claim_audit_owner` 完整绑定 Claim 的业务归属字段，`chk_workflow_task_claim_audit_success_owner` 要求 SUCCESS Audit 具备完整 Claim 归属；UPDATE/DELETE Trigger 均存在并生效。正常 INSERT SUCCESS Audit 仍允许。

## 12. Migration Guard 验证

在 V2.6.7 库构造覆盖不存在 NodeExecution、跨 Instance/Task/Pool/Member、孤立或错配 SUCCESS Audit、重复活动 Claim 等历史违规组合。V2.6.8 在永久 DDL 前由临时 Guard CHECK 以 MySQL `3819 / HY000` 失败。违规 Claim/Audit 行数迁移前后保持 `1 / 3`，永久新增 UNIQUE/FK/CHECK/Trigger 数均为 0，证明无部分永久 DDL、无自动修正、删除、推断或补写。

## 13. 18 项数据库负向测试矩阵

| # | 场景 | 结果 | MySQL / SQLSTATE | 生效约束或 Trigger | 残留 Claim/Audit |
|---:|---|---|---|---|---|
| 1 | 不存在 NodeExecution | PASS | 1451/23000；另以新 Claim 验证为 1452/23000 | Claim/Audit 复合 FK | 1/1；新 Claim 0 |
| 2 | 其他 Instance NodeExecution | PASS | 1451/23000 | 复合归属 FK | 1/1 |
| 3 | Task 与 NodeExecution 不匹配 | PASS | 1451/23000 | Task/Execution FK | 1/1 |
| 4 | Pool 与 Task 不匹配 | PASS | 1451/23000 | Pool/Execution FK | 1/1 |
| 5 | Pool 与 NodeExecution 不匹配 | PASS | 1451/23000 | Task/Execution FK | 1/1 |
| 6 | Member 不属于 Pool | PASS | 1451/23000 | Member FK + Audit Owner FK | 1/1 |
| 7 | Claim 跨 Instance 拼接 | PASS | 1451/23000 | NodeExecution/Owner FK | 1/1 |
| 8 | 重复活动 Claim | PASS | 1062/23000 | `uk_workflow_task_claim_active` | 1/1 |
| 9 | SUCCESS 且 claim_id NULL | PASS | 3819/HY000 | SUCCESS Owner CHECK | 1/1 |
| 10 | SUCCESS 引用不存在 Claim | PASS | 1452/23000 | Audit Claim/Owner FK | 1/1 |
| 11 | SUCCESS 跨 Task | PASS | 1452/23000 | Audit Owner FK | 1/1 |
| 12 | SUCCESS 跨 Instance | PASS | 1452/23000 | Audit Owner FK | 1/1 |
| 13 | SUCCESS 跨 NodeExecution | PASS | 1452/23000 | Audit Owner FK | 1/1 |
| 14 | SUCCESS 跨 Pool | PASS | 1452/23000 | Audit Owner FK | 1/1 |
| 15 | SUCCESS 跨 Member | PASS | 1452/23000 | Audit Owner FK | 1/1 |
| 16 | UPDATE SUCCESS Audit | PASS | 1644/45000 | `trg_workflow_task_claim_audit_no_update` | 1/1 |
| 17 | DELETE SUCCESS Audit | PASS | 1644/45000 | `trg_workflow_task_claim_audit_no_delete` | 1/1 |
| 18 | 修改 SUCCESS Audit 脱离 Claim | PASS | 1644/45000 | no-update Trigger | 1/1 |

## 14. 原三项 P0 关闭结果

| P0 | 原缺口 | 数据库结果 |
|---|---|---|
| P0-1 | Claim 可引用不存在的 `node_execution_id` | PASS：MySQL 1452/23000 拒绝，残留 0 |
| P0-2 | SUCCESS Audit 可被修改为无关对象 | PASS：MySQL 1644/45000 Trigger 拒绝 |
| P0-3 | `claim_id = NULL` 的 SUCCESS Audit 可写入 | PASS：MySQL 3819/HY000 CHECK 拒绝 |

## 15. 合法 Claim 链路

真实合法链执行结果：Task assignee `701`，Task=`CLAIMED`，Pool=`CLAIMED`，NodeExecution=`ACTIVE`，Instance=`RUNNING`，活动 Claim=1，SUCCESS Audit=1。Claim 未完成 Task、NodeExecution 或 Instance，确认 `CLAIM != APPROVAL`。

## 16. Claim 后审批兼容

现有审批契约与全量测试通过：Claimant 仍需满足 assignee、RBAC、DataScope 与 SoD；非 Claimant 不可仅凭 `workflow:approve` 办理目标 Task。Claim 数据库增强未改变审批状态机。

## 17. 幂等验证

同一 Task、Candidate 与 idempotency key 重放返回单一事实；数据库活动 Claim 唯一约束拒绝第二条 Claim。最终活动 Claim=1、SUCCESS Audit=1，Task 与 Pool 未重复变更。应用幂等测试同步通过。

## 18. 双会话并发

两个独立 MySQL 会话按相同锁顺序竞争同一 Task：一个会话成功写入 Claim/Audit，另一会话未写入；最终 winner=`701`、Task/Pool=`CLAIMED`、活动 Claim=1、SUCCESS Audit=1。总等待约 2275.88ms。

## 19. 死锁检查

两会话 stderr 均为空，未出现 MySQL 1213 或 1205。测试未通过隐藏重试掩盖死锁。

## 20. 事务回滚

在 SUCCESS Audit 阶段故意提交非法 hash，MySQL 以 `3819 / HY000`、`chk_workflow_task_claim_audit_hash` 拒绝。事务后 Task=`PENDING`、Pool=`AVAILABLE`、Claim=0、Audit=0，无部分状态残留。

## 21. Append-only 验证

对受保护 SUCCESS Audit 直接执行 UPDATE 和 DELETE，分别由 BEFORE UPDATE/BEFORE DELETE Trigger 以 `1644 / 45000` 拒绝。Fresh、Upgrade、Repair 三路径 Trigger 定义指纹一致；正常 INSERT 成功。

## 22. DIRECT / Legacy 兼容

DIRECT 与 Legacy Task 均不创建 CandidatePool、CandidateMember 或 Claim，不执行历史回填，不改变既有语义。真实数据库检查为 Pool=0、Member=0、Claim=0。

## 23. 应用回归测试

- Java 21 Maven 全量测试：336 通过，0 失败，0 错误，0 跳过；包含 Spring Boot context。
- Workflow 专项：142 通过，0 失败，0 错误，0 跳过。
- Claim 正常、幂等、并发、DIRECT、Legacy、Claim 后审批、Audit 派生、Repository 不可变契约、事务回滚均覆盖。
- Migration contract tests 通过。
- `git diff --check` 通过。
- `SHA256SUMS` 30/30 通过。

## 24. Migration 资产状态

- V2.6.8：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。
- V2.6.7：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED_WITH_V268`。
- 以上仅代表一次性本地 MySQL 8 验收，不代表任何生产或托管环境已经执行。

## 25. V2.6.7 历史失败记录处理

原验收 run 与 `docs/workflow-task-claim-v267-validation-report.md` 未删除、未重写。治理语义明确为：V2.6.7 单独完整性验收失败；只有连续升级到 V2.6.8 后组合资产才满足完整性要求。

## 26. 临时环境清理

Fresh、Upgrade、Repair、Guard 与独立 DDL timing 实例均已停止；端口 34901—34905 无监听。验收完成后一次性数据目录已删除。未触碰任何未知 MySQL 实例。

## 27. 剩余风险

1. TaskClaim 的聚合归属由数据库复合 FK 保证，但在所有关联字段同时改为另一套合法聚合时，数据库本身允许更新；当前不可变性由 Repository/Application 无更新入口保证。若未来出现运维直写需求，应另行设计 Claim append-only 增量治理，不能改历史 Migration。
2. V2.6.8 在一次性小数据集上的执行耗时约 5.73 秒，完成后无等待 metadata lock；大表生产执行时长、锁窗口和磁盘放大仍需在预生产数据规模下评估。
3. 新增父表复合唯一键与 FK 支撑索引存在写放大成本，但它们承担精确复合归属约束，当前未发现可安全移除的明显冗余索引。
4. SoD 仍是平台基础规则，Investment 三重一大业务级职责分离不在本 Sprint 范围。

## 28. 下一步建议

在进入任何 ROLE/POSITION/ORG 或 Investment 集成前，先于预生产等量数据副本执行 V2.6.7→V2.6.8 发布演练，重点采集 metadata lock、DDL 时长、IO/redo、复制延迟与回滚手册；部署门禁必须禁止环境停留在 V2.6.7。
