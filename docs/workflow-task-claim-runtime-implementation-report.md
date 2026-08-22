# Workflow Candidate Pool Claim Runtime 实施报告

## 1. 修改文件清单

本次改动限定在 Workflow Claim 运行时、V2.6.7 候选资产和测试。主要文件如下：

- 启动扫描：`backend/src/main/java/cn/gov/enterprise/EnterprisePlatformApplication.java`
- API/应用层：`ClaimWorkflowTaskCommand.java`、`TaskClaimApplicationService.java`、`TaskClaimTransactionService.java`、`WorkflowTaskController.java`
- Domain：`TaskClaim.java`、`TaskClaimStatus.java`、`TaskClaimContext.java`、`TaskClaimResult.java`、`RealtimeEligibilityChecker.java`、`SegregationOfDutiesPolicy.java`
- 既有模型增量：`WorkflowTask.java`、`CandidatePool.java`
- Repository Port：`TaskClaimRepository.java`、`TaskClaimAuditRepository.java`，以及 Candidate Pool、Candidate Member、Task、NodeExecution 的锁定/CAS能力
- Infrastructure：Claim Entity/Mapper/Adapter、Candidate/Task/NodeExecution锁定Mapper、`WorkflowClaimEligibilityMapper.java`、`DatabaseRealtimeEligibilityChecker.java`、`DefaultWorkflowSoDPolicy.java`
- Migration治理：`V2.6.7__create_workflow_task_claim.sql`、`migration-inventory.yml`、`SHA256SUMS`、Migration `README.md`
- 测试：Claim Domain、事务/并发、RBAC、安全边界、Migration契约测试；同步修正V2.6.3/V2.6.5/V2.6.6历史验收测试的资产状态断言，使其与已冻结基线一致

未修改 Investment 代码，未修改 V2.5.0—V2.6.6 历史 Migration。

## 2. 现状审计

- `workflow_task` 已有 `PENDING`、`CLAIMED` 状态，但此前 Candidate Pool Task 没有 Claim 应用入口。
- Candidate Pool 已预留 `AVAILABLE`、`CLAIMED`；Candidate Member 的 `INCLUDED` 表示冻结候选资格。
- Claim 前 Candidate Pool Task 的 `assignee_user_id` 为空；DIRECT Task 已有明确处理人。
- `workflow:approve` 已是任务审批基础权限，本次复用，不新增 `workflow:claim`。
- 既有 DataScope 信息由 `SecurityPrincipal` 提供；人员、组织、岗位实时状态此前没有 Workflow Claim 专用事实端口。
- 平台此前没有通用 Claim SoD Port，本次仅建立平台最小规则，不嵌入 Investment 规则。

## 3. Claim Domain

`TaskClaim` 是一次成功领取的不可变业务事实，包含 Task、Pool、Member、Instance、NodeExecution、Claimant/Operator、时间、幂等键、TraceId、冻结资格Hash、实时资格结果、DataScope、SoD、RBAC和状态前后快照。

`TaskClaimContext` 是资格与SoD判断的最小事实上下文；`TaskClaimResult` 是API结果；`TaskClaimStatus` 预留 `CLAIMED/RELEASED/CANCELLED`，本Sprint只产生 `CLAIMED`。

Domain不依赖Spring、MyBatis、Entity或HR内部模型。

## 4. Claim状态机

- Task：`PENDING + CANDIDATE_POOL + assignee=null` → `CLAIMED + assignee=claimant`。
- Candidate Pool：`AVAILABLE` → `CLAIMED`。
- TaskClaim：本Sprint只创建 `CLAIMED`。
- Claim不完成审批；Claim后Task仍须走原有 `approve/reject/complete` 动作。
- 非PENDING Task、非AVAILABLE Pool、非ACTIVE NodeExecution、非RUNNING Instance全部拒绝。

## 5. 实时资格接口

`RealtimeEligibilityChecker` 仅暴露 Workflow 所需事实：用户启用且未锁定、在职、组织有效、岗位有效。

基础设施适配器通过窄查询端口读取 `sys_user/hr_employee/sys_org/hr_position`，不向Domain泄漏HR Entity。任一事实缺失或失效时采用 fail-closed 拒绝策略。

## 6. SoD接口

`SegregationOfDutiesPolicy` 返回 allow/deny、策略编码、版本和原因。`DefaultWorkflowSoDPolicy` 只执行平台最低规则：流程发起人不能领取同一流程任务。Investment三重一大职责分离规则仍由Investment提供，未在本Sprint写死。

## 7. Claim事务

`TaskClaimTransactionService.claim()` 使用单一 `@Transactional` 边界，固定锁顺序为：

1. Task
2. Candidate Pool
3. Candidate Member
4. NodeExecution
5. WorkflowInstance
6. 冻结候选、实时资格、DataScope、SoD校验
7. 写入TaskClaim
8. CAS更新Task
9. CAS更新Candidate Pool
10. 写入成功Claim Audit

任一步异常都会回滚整个事务。

## 8. 并发控制

- Task、Pool、Member、NodeExecution、Instance采用行锁读取。
- Task与Pool更新均带旧版本和旧状态条件执行CAS。
- `workflow_task_claim`以Task活动Claim唯一键保证单赢家。
- Candidate Member新增稳定复合唯一键，供Claim所有权外键引用。
- 固定锁顺序降低死锁风险；CAS或唯一约束冲突均明确返回并发失败。

## 9. 幂等规则

同一Task和同一 `idempotencyKey` 返回原Claim；若该幂等键属于另一Claimant则拒绝。不同用户/不同幂等键并发领取同一Task时，Task/Pool CAS和活动Claim唯一键共同保证只产生一个成功结果。

## 10. DIRECT兼容

DIRECT Task调用Claim接口固定返回 `DIRECT_ASSIGNMENT_NOT_CLAIMABLE`。`EXPLICIT_USER_V1 + USER + DIRECT`原行为不变，不创建Candidate Pool或Claim记录。

## 11. Legacy兼容

Legacy Task不允许Claim，不自动补建Candidate Pool，不自动转换assignment mode，不做历史数据推断或回填。

## 12. API

- `POST /workflow/tasks/{taskId}/claim`
- 权限：`workflow:approve`
- 请求：`idempotencyKey`必填；`remark`、`traceId`可选
- 返回：`taskId`、`claimId`、`assigneeUserId`、`claimTime`、`taskStatus`、`poolStatus`、`idempotentReplay`
- 保留既有只读接口：`GET /workflow/tasks/{taskId}/candidates`

未开放release、transfer、delegate或管理员修改Claim状态接口。为避免新增无DataScope保护的读入口，本Sprint未开放可选的Claim详情接口。

## 13. Migration

V2.6.7新增：

- `workflow_task_claim`：成功Claim事实、活动Claim唯一性、幂等唯一性、所有权外键、状态/Hash/审计/逻辑删除/乐观锁约束。
- `workflow_task_claim_audit`：Claim事件的追加式审计结构。
- `workflow_task` Candidate Pool Claim前后assignment约束。
- `workflow_task_candidate_member` Claim所有权复合唯一键。

Migration没有临时表，不依赖默认临时表字符集；Hash和幂等键使用显式ASCII二进制排序规则。未执行真实MySQL/Flyway。

## 14. 测试结果

- Java：21.0.12
- Claim专项测试：18项通过，0失败（含候选资产状态与SHA契约）
- 后端全量测试：332项通过，0失败，0错误，0跳过
- Spring Boot上下文：通过
- Java 21主代码编译：500个源文件通过
- Claim测试覆盖：成功、非候选、离职、停用、DataScope、SoD、RBAC、DIRECT、Legacy、assignee、Claim不审批、Pool状态、幂等、双候选并发单赢家、CAS回滚、非法Task/NodeExecution/Instance状态、Domain纯净和Migration契约
- `git diff --check`：通过
- `SHA256SUMS`：29项仓库资产全部匹配

本Sprint未执行真实MySQL Migration，因此没有Flyway checksum或Schema fingerprint。

## 15. SHA-256

`V2.6.7__create_workflow_task_claim.sql`：

`800e1a5b1af67e1680a3f0233e9d5400c0b42f39349d181f1777c7ce04ac6aed`

## 16. 风险

- V2.6.7仅完成静态约束与Java测试，仍需Fresh和V2.6.6 Upgrade真实MySQL/Flyway验收。
- 当前实时资格适配器依赖现有HR状态编码和岗位数据完整性；缺失岗位会按fail-closed拒绝。
- 当前SoD仅为平台基础规则，尚未承载业务模块的三重一大职责矩阵。
- 数据库死锁与不同隔离级别下的行为仍需真实MySQL双会话验收。
- 拒绝事件使用现有平台安全审计；成功Claim另写不可变Claim Audit。若未来要求数据库内完整拒绝审计，需要单独设计事务外可靠审计通道。
- Release、Transfer、Delegate、Timeout及其终态约束仅预留，未实现。

## 17. 下一步建议

进入独立的V2.6.7真实MySQL/Flyway验收Sprint：执行Fresh和V2.6.6 Upgrade、strict validate、二次migrate no-op、结构/约束负向测试、双会话单赢家与Schema fingerprint对比。验收通过后才能将资产晋级为 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。

不要在该验收前进入ROLE/POSITION/ORG Resolver或Investment集成。
