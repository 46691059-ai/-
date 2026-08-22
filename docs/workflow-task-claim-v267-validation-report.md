# V2.6.7 Workflow Task Claim Runtime 真实MySQL/Flyway验收报告

## 1. 验收环境

- 日期：2026-08-12
- MySQL：Community Server 8.4.9
- Flyway：13.0.0 Community Edition
- Java：21.0.12
- Maven：3.9.9
- 环境：两个全新、隔离、仅绑定 `127.0.0.1` 的一次性MySQL数据目录
- 路径：Fresh；V2.6.6 → V2.6.7 Upgrade
- 安全边界：未连接生产、托管、未知本机或历史遗留Schema

最终结论：`FAIL / BLOCKED / NOT_PROMOTED`。

## 2. Fresh结果

- 基础Schema初始化成功。
- Flyway baseline为V2.0.0，29个版本化Migration成功执行至V2.6.7。
- `flyway_schema_history`成功行30条（含baseline），失败行0条。
- V2.6.7仅执行一次。
- strict validate通过。
- 第二次migrate返回 `No migration necessary`。

## 3. Upgrade结果

- 使用不包含V2.6.7的只读Migration副本建立V2.6.6状态。
- 切换到完整只读副本后仅执行V2.6.7一次。
- strict validate通过，第二次migrate为no-op。
- history成功30条、失败0条。
- Fresh/Upgrade的Flyway checksum及最终Schema完全一致。

## 4. V2.6.7 checksum

- Flyway checksum：`1689998435`
- Repository SHA-256：`800e1a5b1af67e1680a3f0233e9d5400c0b42f39349d181f1777c7ce04ac6aed`

## 5. Schema结构

字段数：

- `workflow_task_claim`：33
- `workflow_task_claim_audit`：35
- `workflow_task`：28
- `workflow_task_candidate_pool`：32
- `workflow_task_candidate_member`：24

`workflow_task_claim`具有主键、3个唯一约束、3个普通索引、2个复合外键和10个ENFORCED CHECK；`workflow_task_claim_audit`具有主键、2个唯一约束、3个普通索引、1个外键和4个ENFORCED CHECK。审计、逻辑删除、乐观锁、幂等键和TraceId字段均存在。

## 6. Claim合法数据链

真实数据库构造链路：Instance → NodeExecution → Task → AssignmentSnapshot → CandidatePool → 2个CandidateMember → TaskClaim → ClaimAudit。

合法Claim后数据库状态：

`assignee=701 / Task=CLAIMED / Pool=CLAIMED / NodeExecution=ACTIVE / Instance=RUNNING / active Claim=1 / SUCCESS Audit=1 / AssignmentSnapshot=1 / CandidateMember=2`

AssignmentSnapshot未覆盖、Candidate Pool未重新解析、Candidate Member集合未变化，证明 `Claim != Approve`。

## 7. Claim准入门禁

Java 21 Claim专项18项测试全部通过，分别覆盖RBAC、Task状态、NodeExecution状态、Instance状态、Pool状态、冻结Candidate资格、Candidate Member状态、实时人员资格、DataScope和SoD。门禁失败不会进入Task/Pool CAS。后端全量回归共332项，0失败、0错误、0跳过，Spring Boot上下文启动通过。

数据库验收同时确认Candidate/Pool/Task的冻结关系和约束；数据库不能替代RBAC、DataScope及SoD应用门禁。

## 8. 实时人员资格

专项测试通过：用户停用、employment无效均fail-closed；冻结时有效但Claim时失效会被拒绝。新用户即使当前满足角色或组织条件，只要不在冻结Candidate Pool中仍被拒绝，且不会重跑Resolver。

## 9. DataScope

拥有 `workflow:approve`、属于Candidate且实时有效，但目标流程发起组织不在当前数据范围时，Claim被拒绝。RBAC未替代DataScope。

## 10. SoD

平台基础SoD规则验证通过：流程发起人不能领取同一流程任务。本次未引入Investment专属规则。

## 11. DIRECT兼容

真实Schema中DIRECT Task未产生Candidate Pool、Candidate Member或TaskClaim。应用专项测试确认调用Claim返回 `DIRECT_ASSIGNMENT_NOT_CLAIMABLE`，原assignee及Task状态不变。

## 12. Legacy兼容

SINGLE_NODE_LEGACY、旧USER+DIRECT及线性DIRECT Task均未被回填或转换；没有伪造Candidate Pool或历史Claim。

## 13. 幂等

- 数据库拒绝同一Task的第二条活动Claim：MySQL 1062。
- 数据库拒绝同一Task重复 `idempotency_key`：MySQL 1062。
- 应用测试确认相同Task、用户和幂等键返回原Claim，不重复更新Task/Pool或写成功审计。
- 其他用户复用该幂等键会被应用层拒绝。

## 14. 并发单赢家

两个独立MySQL会话按Task → Pool → Member → NodeExecution → Instance固定顺序竞争同一Task：

- Candidate 701：Task CAS=1、Pool CAS=1。
- Candidate 702：Task CAS=0、Pool CAS=0。
- 最终assignee=701，活动Claim=1，成功Audit=1，Pool=CLAIMED。
- 总耗时约2371ms，证明第二会话发生锁等待后安全退出。

## 15. 死锁

双会话日志不存在MySQL 1213；固定锁顺序未产生稳定死锁。第二会话通过CAS判负退出，没有部分提交。

## 16. Claim唯一性

`uk_workflow_task_claim_active(task_id, active_token)`真实生效，同一Task不能存在两条活动Claim。

## 17. Audit

合法成功审计包含Task、Claim、Claimant、Operator、Pool、Member、冻结资格Hash、实时资格、RBAC、DataScope、SoD、状态前后、Claim时间、TraceId和幂等键。

发现两个P0阻断：

1. SUCCESS Audit现有Task/Instance/NodeExecution/Pool/Member字段可被更新为不相关ID，MySQL接受。
2. `claim_id=NULL`的孤立SUCCESS Claim Audit可插入，MySQL接受。

因此“append-only”和成功审计归属目前仅靠应用约定，未由数据库约束冻结。

## 18. 事务回滚

真实事务在TaskClaim写入、Task/Pool更新后，故意写入非法Audit Hash触发MySQL 3819并回滚。最终状态：Task=PENDING、assignee=NULL、Pool=AVAILABLE、Claim=0、Audit=0，没有部分残留。

## 19. 外键负向测试

Pool、Member、Instance归属及Audit不存在Claim均被MySQL 1452拒绝；不存在的Task通过Pool复合归属被拒绝。

P0失败：`workflow_task_claim.node_execution_id`没有直接或等价复合外键，改为不存在的NodeExecution时MySQL接受。应用层所有权检查不能替代本次要求的数据库结构完整性。

## 20. CHECK负向测试

非法Claim状态、Claimant/Operator不一致、非法Hash、空幂等键、空TraceId、非法delete_token、负version、非法版本转换均被ENFORCED CHECK拒绝。数据库约束负向合计17项：14通过，3失败（NodeExecution归属、Audit交叉归属、孤立SUCCESS Audit）。

## 21. Claim后审批链

Claimed Task可由Claimant继续进入既有审批/complete语义；真实数据库允许 `CLAIMED → APPROVED` 合法状态组合，测试事务随后回滚。非Claimant仍由原任务处理权限检查拒绝。Claim没有改变NodeExecution推进或Instance完成规则。

## 22. Schema Fingerprint

- 完整Schema：`6bedb919b239b3fee9aeda80e4341fd310fac669ade663e2fe2106e1bc9aeed3`
- Workflow Schema：`0b65989672fa92ee94f08581953ec71e2a5f4a01aea0c796414ca9672e7184a9`
- Claim结构：`377e99dcf24dc25eb42a1ce0fcdfd04afb3706540b4e143ca6f25cac492a441c`

Fresh与Upgrade三类指纹完全一致。

## 23. 历史Migration摘要

V2.5.0—V2.6.7仓库SHA-256全部与`SHA256SUMS`一致；V2.6.7 SHA未漂移。V2.5.0—V2.6.6及V2.6.7 SQL均未修改，也未创建V2.6.8。

## 24. 临时环境清理

所有验收MySQL进程均已停止；端口不再监听。三个隔离验收数据目录已按预先核验的显式绝对路径清理，无法恢复。未修改任何生产、托管或未知数据库。

## 25. 最终PASS/FAIL

`FAIL / BLOCKED / NOT_PROMOTED`

Flyway执行本身通过，但数据库未强制Claim的NodeExecution归属，也未强制SUCCESS Claim Audit的归属与不可变性。这些属于验收明确要求的结构完整性P0项，不能用应用校验替代。

V2.6.7保持 `CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`。后续需另立治理Sprint设计增量修复；本Sprint禁止修改V2.6.7或创建V2.6.8，因此到此停止。
