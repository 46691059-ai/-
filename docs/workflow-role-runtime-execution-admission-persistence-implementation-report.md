# Workflow ROLE Runtime Execution Admission Persistence Implementation Report

## 1. 修改文件清单

Sprint：`2-3.7-WF5.18`。

- Migration与治理：
  - `database/migration/mysql/V2.6.15__create_role_runtime_execution_admission_persistence.sql`
  - `database/flyway/migration-inventory.yml`
  - `database/migration/mysql/SHA256SUMS`
  - `database/migration/mysql/README.md`
- Domain：`domain/role/admission/persistence/`新增9个纯Java模型/策略文件。
- Repository Contract：新增Admission、Evidence、Event、Slot四个接口；Candidate Repository增加只读行锁查询。
- Infrastructure：新增4个Entity、4个MyBatis Mapper、4个Repository Adapter和1个Domain/Entity Mapper；Candidate Entity/Mapper只增加V2.6.15未来证据映射与锁查询。
- Application：新增内部`RoleRuntimeExecutionAdmissionPersistenceService`，无Controller/API。
- Test：新增V2.6.15 Migration Contract、Persistence Domain、Persistence Service和Production Isolation测试；更新WF5.16资产边界测试以识别唯一V2.6.15候选。

## 2. Current Canonical Baseline

- V2.6.14：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- SHA-256：`a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442`
- Flyway checksum：`-1734980808`
- 本Sprint复核后V2.6.14 SHA保持不变，资产状态未降级。
- `EXPLICIT_USER_V1`仍为`ACTIVE`；`ROLE_DIRECTORY_V1`仍为`PREPARED / NON_EXECUTABLE`；ROLE Runtime仍为`DISABLED`。

## 3. Existing Persistence Audit

V2.6.13提供Activation Request/RACI Approval/Evidence强归属；V2.6.14提供Promotion、Candidate Snapshot和Candidate Event。现有Candidate Owner Key为`(id,snapshot_id,promotion_id,activation_id,delete_token)`，但缺少Directory Result/Fence以及Definition Release/Version、Node和Graph证据。V2.6.15只增加未来行所需的可空字段，不从当前Definition、Node或Directory推断历史事实。

## 4. V2.6.15数据库变化

新增：

1. `workflow_role_runtime_execution_admission`
2. `workflow_role_runtime_execution_admission_evidence`
3. `workflow_role_runtime_execution_admission_event`
4. `workflow_role_runtime_execution_admission_slot`

Admission/Evidence/Event为append-only治理事实；Slot是可CAS更新的技术指针，不承载业务决策或审计证据。Migration状态为`CANDIDATE / NOT_EXECUTED`，未执行真实MySQL/Flyway。

## 5. Candidate Snapshot前向增强

新增可空字段：`directory_result_hash`、`directory_fence_token_hash`、`directory_fence_expires_at`、`enterprise_id`、`definition_release_id`、`definition_id`、`definition_version_id`、`node_id`、`node_binding_hash`、`graph_hash`。CHECK要求全空或全非空；历史行保持全空且永久不具备Admission资格。Definition Version、Node和Release使用FK锚定发布模型。

## 6. Slot模型

每个Candidate一行，以`candidate_snapshot_row_id`为主键；`VACANT/OCCUPIED`与活动指针必须一致。Application固定锁顺序为Candidate Snapshot再Admission Slot，并以`version`完成CAS。批准时只有一个事务可占用；撤销/过期释放必须先存在对应append-only Event。

## 7. Admission表

主体冻结请求身份、源Candidate复合归属、Activation/Promotion/Binding/Candidate Hash、Execution/Persistence Hash、Resolver Contract、Directory Fence、Definition/Node证据、稳定Decision、策略版本、有效期及审计主体。`VALIDATING`不持久化；主体只接受`ELIGIBLE / APPROVED_FOR_EXECUTION / BLOCKED / REJECTED`。

## 8. Evidence表

采用子表而非28列或不可约束JSON。唯一约束覆盖Admission内序号和Validator Code；序号限制1—28，PASS/FAIL与Block Reason一致。Capability、Feature Flag、Canary和Kill Switch统一保存类型化Code、Status、Provider/Policy Version、Scope与Hash，不保存密钥或目录明细。

## 9. Event表

支持`ADMISSION_CREATED / ELIGIBLE / APPROVED_FOR_EXECUTION / BLOCKED / REJECTED / REVOKED / EXPIRED`。Insert Guard校验严格序号、Previous Hash、前序状态和终态不可恢复；事件本身禁止UPDATE/DELETE。

## 10. Domain模型

新增`PersistentRoleRuntimeExecutionAdmission`、`RoleRuntimeExecutionAdmissionEvidenceRecord`、`RoleRuntimeExecutionAdmissionEvent`、`RoleRuntimeExecutionAdmissionSlot`、Persistence Bundle/Policy/Hash等纯Java对象。Domain不依赖Spring、MyBatis、Entity或Controller。

## 11. Execution/Persistence Hash分离

- WF5.16：`ROLE_RUNTIME_EXECUTION_ADMISSION_CANONICAL_V1`，保存为`execution_admission_hash`。
- WF5.18：`ROLE_RUNTIME_EXECUTION_ADMISSION_PERSISTENCE_CANONICAL_V1`，保存为`persistence_hash`。

Persistence Hash覆盖持久化身份、全部冻结归属、Directory Fence、Capability Root、Decision和Policy Version；实现和测试禁止两个Hash互换。

## 12. Capability Root Hash

Evidence按`sequenceNo -> validatorCode -> capabilityCode`稳定排序，以长度前缀Canonical串计算SHA-256。输入顺序变化不影响Root；任一Evidence内容变化都会改变Root并进一步改变Persistence Hash。

## 13. 强归属

数据库链路为Activation -> Promotion -> Candidate Snapshot -> Admission -> Evidence/Event。Admission对Candidate使用现有复合Owner Key；对Definition Version、Node和Release使用FK；Insert Guard逐字段比较Resolver、Contract、Directory、Scope、Hash、Definition和Node冻结事实，并要求Candidate最新事件为`ACTIVE`。

## 14. 幂等

`request_id`全局唯一，`(candidate_snapshot_row_id,idempotency_key)`唯一。相同请求且Persistence Hash一致返回既有结果；相同键但Payload Hash漂移返回`IDEMPOTENCY_PAYLOAD_MISMATCH`，不得插入重复Admission/Evidence/Event。

## 15. 并发Slot

短事务内按Candidate -> Slot固定顺序加锁。活动Slot已占用时拒绝第二个批准；CAS失败返回并发冲突并触发事务回滚。历史Admission不覆盖、不删除。

## 16. Repository边界

- Admission：`insert/findByRequestId/findByCandidateAndIdempotencyKey`
- Evidence：`appendAll/findByAdmissionId`
- Event：`append/findByAdmissionId`
- Slot：`ensure/lock/compareAndSetActive/compareAndSetVacant`

不存在`updateAdmission`、`delete`、`saveOrUpdate`或`replace`语义。

## 17. Application事务链

内部Service执行：幂等查询 -> Persistence Policy校验 -> 锁Candidate -> 确保并锁Slot -> Insert Admission -> Append Evidence -> Append Created/Decision Events -> 批准结果CAS占用Slot。方法标记`@Transactional`；Evidence、Event或CAS任一步失败均抛出并回滚。事务内不调用远程Directory、DataScope、SoD、Feature Flag或Kill Switch。

## 18. Append-only治理

Admission、Evidence、Event分别设置BEFORE UPDATE/DELETE Trigger和稳定`SQLSTATE 45000`错误消息。Slot只允许VACANT/OCCUPIED CAS，且对应批准、撤销或过期Event必须已在同一事务中存在。

## 19. Migration Guard

永久DDL前通过临时Guard检查V2.6.14依赖对象、Owner Key、源Candidate归属/Hash、目标命名空间和部分安装痕迹。Guard不执行UPDATE、DELETE、回填或推断。DDL隐式提交风险已显式保留给真实MySQL验收，不宣称静态测试具有真实数据库证明力。

## 20. 测试结果

- Java：21.0.12
- Maven：3.9.9
- Java 21 compile/test-compile：PASS
- Spring Boot Context：PASS
- 定向测试：PASS
- 后端全量测试：`474`通过，`0`失败，`0`错误，`0`跳过
- Migration Contract：PASS
- Domain纯净：PASS
- 幂等、Evidence/Event失败链、Slot CAS冲突：PASS
- `git diff --check`：PASS

本结果不包含真实MySQL/Flyway执行。

## 21. Production Dependency Scan

新Persistence Foundation未依赖Investment、真实Role Directory、真实DataScope/SoD、生产Feature Flag/Kill Switch、WorkflowInstance/Task/CandidatePool/Claim写服务或REST Controller。Controller/API新增为0。

## 22. V2.6.15 SHA

`405fb7fc3bc928fc84643bcbf5583cd9f9555dad8209ad6b4d962529ce969887`

治理清单37项逐文件复算：`37/37 PASS`。V2.6.14 SHA仍为`a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442`。

## 23. Migration状态

- V2.6.15：`CANDIDATE / NOT_EXECUTED`
- `flyway_checksum: null`
- 未提前晋级，未执行repair/baseline，未连接真实数据库。

## 24. 剩余风险

1. MySQL 8.4下ALTER/FK/Trigger语法、Metadata Lock和DDL隐式提交失败边界尚未真实验证。
2. Candidate Insert Guard、Event状态Guard和Slot Trigger需在真实MySQL上做负向矩阵与双会话单赢家验证。
3. Fresh与V2.6.14 Upgrade Schema Fingerprint尚未比对。
4. Evidence容量上限为28N，生产容量、归档和索引选择仍需发布前压测。
5. 本Sprint只冻结持久化事实；ROLE Runtime、真实Directory和运行对象创建仍明确关闭。

## 25. 下一步建议

下一Sprint仅执行V2.6.15真实MySQL/Flyway隔离验收：Fresh、V2.6.14 Upgrade、strict validate、二次migrate no-op、Guard零永久DDL、结构/FK/CHECK/Trigger、append-only、强归属负向矩阵、事务回滚、双会话Slot单赢家及Schema Fingerprint。不得进入ROLE Task、Candidate Pool Runtime、Claim Runtime或Investment集成。

最终状态：

- `ROLE_RUNTIME_EXECUTION_ADMISSION_PERSISTENCE_IMPLEMENTED`
- `V2.6.15 CANDIDATE / NOT_EXECUTED`
- `ROLE_RUNTIME_DISABLED`
