# Workflow ROLE Runtime Execution Admission Persistence Governance Design

## 1. Current Canonical Baseline

- Sprint：`2-3.7-WF5.17`
- V2.6.14：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- V2.6.14 SHA-256：`a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442`
- V2.6.14 Flyway checksum：`-1734980808`
- Framework：`ROLE_RUNTIME_EXECUTION_ADMISSION_FRAMEWORK_READY`
- `EXPLICIT_USER_V1`：`ACTIVE`
- `ROLE_DIRECTORY_V1`：`PREPARED / NON_EXECUTABLE`
- ROLE Runtime：`DISABLED`
- WF5.16 Persistence Decision：`PERSISTENCE_REQUIRED`

本Sprint只冻结持久化契约。未重新执行WF5.14.2、WF5.14.3、WF5.15或WF5.16；不创建SQL，不修改任何代码、Registry、历史Migration或运行状态。

## 2. Existing Persistence Audit

### 2.1 V2.6.9—V2.6.12

- `role_runtime_binding_approval`保存Proposal/Eligibility/Resolver Contract审批事实。
- `workflow_role_runtime_binding_snapshot`保存已经有Instance、Binding Set、Node Binding之后的ROLE Runtime快照。
- V2.6.10—V2.6.12补强Resolver Version、Approval关联、Canonical、大小写敏感和append-only治理。

`workflow_role_runtime_binding_snapshot`属于“实例创建后”对象，依赖`workflow_instance`、`workflow_instance_resolver_binding_set`和`workflow_node_resolver_binding_snapshot`，不能作为“实例创建前”的Execution Admission存储。

### 2.2 V2.6.13

- `role_runtime_activation_request`冻结Resolver、Contract、Binding、Candidate、Directory Contract/Revision、Business Scope、EffectiveAt和Activation Hash。
- `role_runtime_activation_approval`冻结三类RACI审批决定。
- `role_runtime_activation_evidence`冻结Activation、Resolver Contract、Binding、Candidate和Directory证据。
- 三表已通过复合FK、CHECK、Insert Guard和UPDATE/DELETE Trigger实现强归属与append-only。

### 2.3 V2.6.14

- `workflow_role_binding_promotion`冻结Promotion、Activation归属、Resolver、Binding、Candidate、Directory Contract/Revision、有效期及证据Hash。
- `workflow_role_binding_candidate_snapshot`冻结Promotion引用、Activation、Resolver、Binding、Candidate、Directory Revision、有效期和Evidence Set。
- `workflow_role_binding_snapshot_event`以Hash链记录Candidate Snapshot生命周期。
- Candidate Snapshot通过复合FK连接Promotion和Activation，核心数据不可UPDATE/DELETE。

### 2.4 可复用与缺口矩阵

| 事实 | 当前来源 | Admission处理 |
|---|---|---|
| Activation与RACI证据 | V2.6.13 | 只引用和比对Hash，不复制完整审批内容 |
| Promotion及其证据 | V2.6.14 Promotion | 复合FK归属并比对Hash |
| Runtime Binding Candidate | V2.6.14 Candidate Snapshot | 作为Admission唯一源对象 |
| Candidate生命周期 | V2.6.14 Event | 要求最新有效状态为`ACTIVE`，不修改原状态 |
| 实例后ROLE Binding | V2.6.9 Snapshot | Admission阶段不使用 |
| requestId/idempotency | 未持久化 | V2.6.15新增 |
| 28项Validator结果 | 未持久化 | V2.6.15新增Evidence子表 |
| Capability/Flag/Canary/Kill Switch | 未持久化 | V2.6.15新增冻结证据 |
| Definition Version/Node | V2.6.14 Candidate缺失 | V2.6.15前向扩展未来Candidate行并在Admission中冻结 |
| nodeBindingHash/graphHash | 未持久化 | V2.6.15新增 |
| directoryResultHash/Revision Fence | Candidate仅有Revision和Directory Contract Hash | V2.6.15前向扩展未来Candidate行并新增Fence证据 |
| Admission决策、撤销、过期 | 未持久化 | V2.6.15新增Admission/Event |

## 3. Existing Asset Decisions

1. 复用V2.6.13的Activation证据、V2.6.14的Promotion和Candidate强归属链。
2. 不复制完整Activation Approval、Promotion Evidence或Directory成员明细；Admission只保存引用、比较Hash和本次校验结果。
3. Admission不得直接UPDATE Candidate Snapshot状态，也不得向V2.6.14 Event写入Admission状态。
4. Admission必须成为独立聚合，因为其生命周期、撤销、过期、Capability和发布控制语义均不同于Candidate。
5. Admission撤销禁止UPDATE原Admission，必须追加Event。
6. Feature Flag、Canary和Kill Switch保存当时的冻结证据与Policy Version，不复制生产配置对象。
7. Capability保存最终Decision实际执行过的逐项证据；不保存不可约束的大段原始响应。
8. Candidate与Admission以复合FK、源Candidate扩展字段和Insert Guard建立数据库可证明归属。
9. 历史Candidate不补造Admission、不推断缺失字段，也不重新解析。

## 4. Aggregate方案比较

| 维度 | A：扩展Candidate Snapshot | B：单Admission表 | C：Admission + Evidence + Event |
|---|---|---|---|
| 不可变性 | Admission变化污染Candidate职责 | 主体可不可变，但证据易堆叠 | 三类事实分别append-only，最清晰 |
| 审计能力 | 难记录28项和多次状态变化 | 依赖宽表或JSON | 逐项Evidence与事件Hash链完整 |
| 撤销/过期 | 需要UPDATE Candidate | 容易UPDATE状态 | 追加REVOKED/EXPIRED Event |
| Hash治理 | Candidate Hash与Admission Hash混用 | 单表Hash语义复杂 | 主体、Evidence Root、Event Hash分离 |
| 并发控制 | 影响Candidate现有Trigger | 可实现但需额外控制 | 可使用独立活动槽和事件序列 |
| 查询复杂度 | 表面简单、语义复杂 | 读取简单、审计复杂 | 常用索引+审计Join可控 |
| FK复杂度 | 低，但破坏限界 | 中 | 较高，但数据库可证明完整归属 |
| 数据量 | 宽Candidate持续膨胀 | Admission N | Admission N、Evidence最多28N、Event约3—6N |
| DDL风险 | 高，改变Canonical对象职责 | 中 | 中；仅需受控前向扩展源归属字段 |
| Runtime扩展 | Candidate被多职责锁死 | 有限 | 可独立关联未来Instance对象 |
| Legacy隔离 | 容易误触历史Candidate | 可隔离 | 历史Candidate通过NULL扩展字段明确不可准入 |

结论：采用方案C，并增加一个只负责并发协调、不承载审计事实的`Admission Slot`技术表。Slot的CAS更新必须由不可变Event完整留痕。

## 5. Persistence Decision

冻结：`PERSISTENCE_REQUIRED / OPTION_C_SELECTED`。

原因：跨进程恢复、撤销、Canary/Kill Switch审计、生产追溯和故障恢复都不能依赖内存对象；V2.6.13/V2.6.14没有Admission语义，直接复用会造成Hash和职责混用。

## 6. 推荐Persistence Domain模型

### RoleRuntimeExecutionAdmission

一次Admission请求的不可变主体和稳定决策，保存身份、源对象引用、全部冻结业务Hash、最终稳定Decision、Policy Version和Persistence Hash。

### RoleRuntimeExecutionAdmissionEvidence

保存固定Validator中实际执行的每一步结果。每行只表达一个序号/检查码的证据，不表达Admission生命周期。

### RoleRuntimeExecutionAdmissionEvent

保存Admission生命周期变化、操作者、原因、前序Event Hash和Event Hash。撤销与过期只能通过Event表达。

### RoleRuntimeExecutionAdmissionSlot

每个Candidate一行的技术性当前活动指针，用于行锁、CAS和单活动Admission约束；不是审计事实，不能替代Event。

## 7. Admission身份、业务键和逻辑删除

- `admission_id`：全局稳定业务ID，ASCII大小写敏感。
- `request_id`：调用方请求ID，全局唯一，用于结果查询。
- `idempotency_key`：同一Candidate内唯一；相同键不同Request Hash必须拒绝。
- `candidate_snapshot_row_id + snapshot_id + promotion_id + activation_id`：源Candidate复合身份。
- `enterprise_id + definition_id + definition_version_id + node_id`：执行归属。
- Resolver Code/Version/Contract Hash必须同时冻结。

一个Runtime Binding Candidate最多只能有一个“当前有效”的`APPROVED_FOR_EXECUTION` Admission，但可以保留任意历史Admission。重新Admission永远INSERT新Admission；任何关键事实变化必须使用新`request_id`、新`admission_id`和新Persistence Hash。

Admission、Evidence、Event的`deleted`和`delete_token`固定为`0`，禁止逻辑删除和物理删除。Slot也不得删除，只允许受控CAS更新当前指针。

## 8. 状态持久化Decision

持久化稳定Decision：

- `ELIGIBLE`
- `APPROVED_FOR_EXECUTION`
- `BLOCKED`
- `REJECTED`

后续有效状态通过最新Event派生：

- `REVOKED`
- `EXPIRED`

`VALIDATING`是单次Application调用中的瞬时状态，不写Admission主体，避免进程崩溃留下无法解释的半状态。`CREATED`以同一事务中的首个`ADMISSION_CREATED` Event留痕；最终Admission、Evidence和Event必须原子提交。

若未来需要异步长时验证，应另行设计Workflow Job，不得把本设计的`VALIDATING`当作任务队列状态。

## 9. Append-only治理

- Admission：INSERT/query only；禁止UPDATE/DELETE。
- Evidence：INSERT/query only；禁止UPDATE/DELETE。
- Event：append/query only；禁止UPDATE/DELETE。
- 状态变化：追加`ADMISSION_CREATED`、`VALIDATION_PASSED`、`APPROVED_FOR_EXECUTION`、`BLOCKED`、`REJECTED`、`REVOKED`或`EXPIRED` Event。
- 三张核心表均需`BEFORE UPDATE`和`BEFORE DELETE` Trigger返回`SQLSTATE 45000`稳定错误码。
- Event Insert Guard校验合法状态转换、严格递增sequence、previousEventHash和主体归属。
- Slot可CAS更新，但每次更新必须在同一事务中已有对应Event，且Trigger校验Event引用。

## 10. 28项Evidence方案比较与决策

| 方案 | 约束 | 查询/审计 | 演进 | 决策 |
|---|---|---|---|---|
| 28列 | 易做NOT NULL | 单行查询简单 | 新检查必须改宽表，Capability字段难表达 | 不采用 |
| JSON | 只能做有限JSON CHECK | 临时灵活，审计查询和索引差 | 容易跨语言漂移 | 不采用 |
| Evidence子表 | 序号、代码、类型、结果均可约束 | 可逐项查询和索引 | 可按Canonical Version扩展 | 采用 |

批准结果必须存在且仅存在sequence 1—28的28行Evidence。阻断结果保存从1到首个失败步骤的连续Evidence，主体记录`executed_check_count`和`last_check_sequence`。禁止跳号、重复序号或保存失败步骤之后的伪检查结果。

## 11. Capability Evidence

步骤21—28全部使用统一Evidence结构，至少保存：

- `capability_code`
- `capability_status`：`READY/NOT_READY/DEGRADED/BLOCKED`
- `checked_at`
- `evidence_hash`
- `provider_version`
- `policy_version`
- `result_code`
- `scope_hash`

所有最终Decision实际使用的Capability结果均持久化：批准保存全部READY；阻断保存已执行的READY结果和首个失败结果。原始Token、密钥、完整人员目录、DataScope明细和SoD敏感规则不得落库，只保存不可逆Evidence Hash和必要业务摘要。

## 12. Feature Flag Evidence

Evidence类型`FEATURE_FLAG`必须冻结：

- scopeType；
- enterpriseId；
- definitionVersionId；
- flagPolicyVersion；
- flagResult；
- checkedAt；
- evidenceHash。

只保存`enabled=true`不合格。Evidence必须能证明哪个企业、哪个Definition Version、依据哪个Policy获得准入。

## 13. Canary Evidence

Evidence类型`CANARY_SCOPE`冻结enterpriseId、definitionId、definitionVersionId、nodeId、canaryPolicyVersion、canaryResult、checkedAt和evidenceHash。

Canary范围变化不得更新历史Admission；必须创建新Admission。Canary Evidence与Admission主体范围必须由Insert Guard逐字段比对。

## 14. Kill Switch Evidence

冻结语义：`OPEN`允许继续校验，`CLOSED`阻止新Execution Admission。

Evidence必须保存observedStatus、policyVersion、checkedAt和evidenceHash。Kill Switch后续变化不修改历史Admission；`CLOSED`后的新请求写入BLOCKED Admission与失败Evidence，便于证明拒绝原因。

## 15. Directory Revision Fence

采用`Prepare -> Verify -> Commit`：

1. Prepare在数据库事务外解析Directory并冻结Revision、Result Hash和不可伪造Fence Token。
2. Verify在不持有Candidate高争用行锁时验证Fence Token、有效期和Provider签名。
3. Commit开启短事务，锁定Candidate与Admission Slot，只校验冻结Token Hash、Revision、Result Hash和过期时间，不调用远程Directory。
4. 任一漂移返回`DIRECTORY_REVISION_DRIFT`并整体回滚。

V2.6.14 Candidate当前没有`directory_result_hash`，因此V2.6.15必须以前向ALTER为未来Candidate行增加：

- `directory_result_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL`
- `directory_fence_token_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL`
- `directory_fence_expires_at DATETIME(3) NULL`

历史行保持NULL且永不补值；Admission Insert Guard只接受三个字段完整的V2.6.15后Candidate。不得从`candidate_hash`猜测Directory Result Hash。

## 16. Definition Version / Node证据

V2.6.15必须在Admission主体保存：

- `definition_id BIGINT NOT NULL`
- `definition_version_id BIGINT NOT NULL`
- `node_id BIGINT NOT NULL`
- `node_binding_hash VARCHAR(64) ASCII/ascii_bin NOT NULL`
- `graph_hash VARCHAR(64) ASCII/ascii_bin NOT NULL`
- `definition_release_id BIGINT NOT NULL`

同时以前向ALTER为未来V2.6.14 Candidate行增加enterpriseId、上述Definition/Node/Hash字段。历史Candidate保持NULL，不backfill。新字段形成全NULL或全非NULL的CHECK；Admission只接受全非NULLCandidate。

数据库关系：

- `(definition_id, definition_version_id)` FK到`workflow_version(definition_id,id)`；
- `(definition_version_id,node_id)` FK到`workflow_node(version_id,id)`；
- `definition_release_id` FK到`workflow_version_release.id`；
- Insert Guard验证Release的definition/publishedVersion/contentHash与Admission的Definition/Version/graphHash一致；
- Insert Guard验证Admission的Node、nodeBindingHash与源Candidate扩展事实一致。

这不是修改V2.6.14历史SQL，而是V2.6.15候选中的前向、兼容、无推断扩展。

## 17. Canonical Hash治理

冻结持久化Canonical：`ROLE_RUNTIME_EXECUTION_ADMISSION_PERSISTENCE_CANONICAL_V1`。

至少覆盖：admissionId、requestId、idempotencyKey、activationHash、promotionHash、bindingHash、candidateHash、resolverCode、resolverVersion、resolverContractHash、directoryRevision、directoryResultHash、directoryFenceTokenHash、enterpriseId、businessScope、definitionId、definitionVersionId、nodeId、nodeBindingHash、graphHash、effectiveAt、featureFlagEvidenceHash、canaryEvidenceHash、killSwitchEvidenceHash、capabilityEvidenceRootHash、decision、policyVersion。

编码要求：UTF-8、固定字段名和顺序、长度前缀、集合稳定排序、UTC时间、64字符小写SHA-256、ASCII/ascii_bin存储。

与WF5.16 Hash的关系：

- `ROLE_RUNTIME_EXECUTION_ADMISSION_CANONICAL_V1`证明内存Admission请求事实；字段为`execution_admission_hash`。
- `ROLE_RUNTIME_EXECUTION_ADMISSION_PERSISTENCE_CANONICAL_V1`证明持久化身份、Decision和完整Evidence集合；字段为`persistence_hash`。

两者必须同时保存且禁止互换、覆盖或使用同一列表示。

## 18. Evidence Root Hash

`capability_evidence_root_hash`采用Canonical Aggregate Hash，不要求真正Merkle Tree：

1. 每条Evidence先按其Canonical Version计算`evidence_hash`；
2. 按`sequence_no`、`check_code`、`capability_code`稳定排序；
3. 对`sequence|checkCode|result|evidenceHash`长度前缀串计算SHA-256；
4. Root Hash写入Admission主体和最终Decision Event；
5. Event Insert Guard核对两处相等及Evidence条数/连续性。

任何Evidence变化都会产生不同Root Hash和Persistence Hash，旧Admission不能复用。

## 19. Admission与V2.6.14强归属

强归属链：

```text
role_runtime_activation_request
  -> workflow_role_binding_promotion
  -> workflow_role_binding_candidate_snapshot
  -> workflow_role_runtime_execution_admission
  -> admission_evidence / admission_event
```

Admission使用现有Candidate Owner Key `(id,snapshot_id,promotion_id,activation_id,delete_token)`建立复合FK。Candidate本身已通过复合FK指向Promotion和Activation。

V2.6.15的Admission Insert Guard还必须从源Candidate逐字段比较Activation/Promotion/Binding/Candidate/Resolver/Contract/Directory/Scope/EffectiveAt及新增Definition/Node事实。定义与节点再通过独立FK锚定发布模型。禁止只凭Application拼接ID或Hash。

## 20. Admission表设计

候选表：`workflow_role_runtime_execution_admission`。

核心字段：

- 身份：id、admission_id、request_id、idempotency_key；
- 源归属：candidate_snapshot_row_id、snapshot_id、promotion_id、activation_id、source_delete_token；
- Hash：activation_hash、promotion_hash、binding_hash、candidate_hash、execution_admission_hash、capability_evidence_root_hash、persistence_hash；
- Resolver：resolver_code、resolver_version、resolver_contract_hash；
- Directory：directory_revision、directory_result_hash、directory_fence_token_hash、directory_fence_expires_at、directory_verified_at；
- 业务归属：enterprise_id、business_scope、definition_release_id、definition_id、definition_version_id、node_id、node_binding_hash、graph_hash；
- Decision：decision、policy_version、effective_at、admission_expires_at、executed_check_count、last_check_sequence；
- 审计：requested_by、decided_by、decided_at、created_by/created_time、updated_by/updated_time、deleted、delete_token、version。

唯一键：admission_id、request_id、persistence_hash、`(candidate_snapshot_row_id,idempotency_key)`。Owner Key至少包含`(id,admission_id,candidate_snapshot_row_id,delete_token)`供子表FK使用。

CHECK：Hash格式、Resolver大小写、Decision集合、Evidence计数1—28、时间关系、固定`deleted=0/delete_token=0/version=0`。索引：Candidate+Decision、Enterprise+Definition+Node+Decision、Resolver+Version、decidedAt。

## 21. Evidence表设计

候选表：`workflow_role_runtime_execution_admission_evidence`。

核心字段：id、admission_row_id、admission_id、sequence_no、check_code、evidence_type、result、block_reason、capability_code/status、provider_version、policy_version、observed_value_code、scope_type、scope_enterprise_id、scope_definition_id/version_id、scope_node_id、checked_at、subject_hash、evidence_hash、canonical_version及标准审计字段。

唯一键：`(admission_row_id,sequence_no,delete_token)`、`(admission_row_id,check_code,delete_token)`。复合FK连接Admission Owner Key。

CHECK：sequence 1—28、PASS/FAIL与blockReason一致、Capability状态集合、类型化Evidence必填字段、Hash格式、Canonical Version、固定append-only字段。索引：checkCode+result、capabilityCode+status、checkedAt。

## 22. Event表设计

候选表：`workflow_role_runtime_execution_admission_event`。

核心字段：id、admission_row_id、admission_id、candidate_snapshot_row_id、sequence_no、event_type、from_status、to_status、reason_code、source_evidence_root_hash、previous_event_hash、event_hash、occurred_at、operator_id、operator_role、idempotency_key、canonical_version及标准审计字段。

唯一键：`(admission_row_id,sequence_no,delete_token)`、event_hash、`(admission_row_id,idempotency_key,delete_token)`。复合FK连接Admission Owner Key。

CHECK：合法状态/Event集合、sequence>0、Hash格式、RACI角色、固定append-only字段。Insert Guard验证前序状态、previousEventHash、Evidence Root及撤销/过期权限。

## 23. Admission Slot设计

候选技术表：`workflow_role_runtime_execution_admission_slot`。

- 主键/FK：candidate_snapshot_row_id，另保存snapshot_id与source delete_token用于强归属；
- 当前指针：active_admission_row_id、active_admission_id，可为空；
- 控制字段：slot_status、version、updated_by、updated_time；
- UNIQUE(active_admission_row_id)，复合FK到Admission Owner Key。

Slot不保存审计事实，不使用逻辑删除。批准时锁定Slot并以version CAS占用；撤销/过期时追加Event后CAS释放。若Event写入失败，Slot更新必须回滚。`active_token`放在不可变Admission上无法在撤销后释放，`delete_token`不得用于业务状态，故不采用这两种方案。

## 24. 撤销模型

- 可撤销主体：Release Approver或Security/Audit治理角色；Workflow Administrator无最终撤销权。
- 请求人、批准人和撤销人受SoD约束，单人不得完成完整闭环。
- 撤销追加`REVOKED` Event，不UPDATE Admission、Evidence或历史Event。
- 同一事务锁Slot、验证当前Admission、追加Event并CAS清空活动指针。
- 撤销后禁止基于该Admission创建新的WorkflowInstance或其他运行对象。
- 已经存在的历史Instance/Task不删除、不回写；处置策略留给后续Runtime治理。

## 25. 过期模型

过期来源包括Policy TTL、Directory Fence/Evidence TTL、Canary窗口和Admission有效期。检测到过期后追加`EXPIRED` Event并释放Slot。

`EXPIRED`不得自动恢复、续期或修改时间字段；必须重新Prepare、Verify并创建新Admission。

## 26. 幂等设计

- `request_id`全局唯一，查询未知提交结果时以其返回既有决定。
- `(candidate_snapshot_row_id,idempotency_key)`唯一。
- Event的`(admission_row_id,idempotency_key)`唯一。
- 相同requestId/idempotencyKey且Request Hash相同，返回既有聚合；Hash不同，返回`IDEMPOTENCY_PAYLOAD_MISMATCH`。
- Evidence不单独接受外部重试；只在Admission事务中按固定序号批量写入。
- 事务提交结果未知时禁止换新key盲重试。

## 27. 并发设计

两个请求同时Admission同一Candidate时：

1. 确保Slot行存在；
2. `SELECT ... FOR UPDATE`锁Candidate，再锁Slot；
3. 若Slot已有未撤销/未过期Admission，则一个请求返回既有结果或`ACTIVE_ADMISSION_EXISTS`；
4. 若为空，按idempotency唯一键插入Admission/Evidence/Event；
5. CAS设置Slot的active pointer；
6. 只有一个事务提交成功。

固定锁顺序：Candidate Snapshot -> Admission Slot -> Admission -> Evidence/Event。禁止逆序。核心证据表不使用version更新；Slot使用version CAS。历史Admission永久保留。

## 28. 事务边界

### Prepare（事务外）

获取/构造Directory冻结证据、Capability、Feature Flag、Canary、Kill Switch及Canonical Hash；不得写库。

### Verify（事务外或无高争用锁阶段）

验证Fence Token、Registry Descriptor和Provider Contract。远程调用必须在持有Candidate行锁之前结束。

### Commit（单一短事务）

锁Candidate和Slot；重新验证Activation、Promotion、Candidate、最新Candidate Event、全部Hash、Fence Token有效期、Definition Release、Node和Registry Descriptor快照；随后insert Admission、批量insert Evidence、append Event并CAS Slot。

任一步失败全部回滚。禁止在事务内调用远程Directory、DataScope、SoD、Flag或Kill Switch服务。

## 29. Database Guard

V2.6.15永久DDL前必须用临时Guard对象和`CHECK(violation_count=0)`验证：

- Flyway History中的V2.6.13/V2.6.14版本与checksum；
- V2.6.14三表、Owner Key、FK、CHECK和append-only Trigger结构；
- Activation、Promotion、Candidate及Candidate Event现有归属完整；
- Resolver Code/Version/Contract和全部Hash格式合法；
- 无目标表、索引、Trigger的部分安装；
- Definition Version/Node/Release基础Owner Key存在；
- 无重复目标业务键或活动Slot。

Guard失败必须发生在永久DDL前，不得自动修复、删除、回填或推断历史数据。历史Candidate缺少新增扩展字段是兼容状态，不是脏数据；它们保持NULL且永久不具备Admission资格。

## 30. V2.6.15 Migration规划

候选文件名：`V2.6.15__create_role_runtime_execution_admission_persistence.sql`。本Sprint不创建该文件。

DDL阶段：

1. Phase 1：Pre-DDL Guard；
2. Phase 2：以前向ALTER为Candidate添加nullable归属/Fence字段，准备Definition/Node/Release Owner Key与CHECK；
3. Phase 3：创建Admission表；
4. Phase 4：创建Evidence表；
5. Phase 5：创建Lifecycle Event表；
6. Phase 6：创建Admission Slot并添加复合FK/CHECK；
7. Phase 7：创建Insert Guard、状态转换及append-only Trigger；
8. Phase 8：最终Information Schema结构验证。

风险评估：

- Candidate ALTER存在Metadata Lock，需先做锁等待预检并使用MySQL 8.4支持的在线DDL能力；
- Evidence最多28N，索引仅保留审计和高频查询所需组合，避免写放大；
- 复合FK增加写成本，但换取数据库可证明归属，不得降为弱关联；
- Trigger必须保持常量级查询并命中Owner/状态索引；
- 容量按Admission N、Evidence最多28N、Event约3—6N估算，禁止删除审计数据；归档策略需另行设计只读冷存储。

## 31. Legacy / Explicit User兼容

- `EXPLICIT_USER_V1`、`USER + DIRECT`、`SINGLE_NODE_LEGACY`和历史Multi-Node USER流程不进入本聚合。
- 历史ROLE Candidate不backfill、不补造Admission、不刷新、不重新Resolve。
- Candidate新增nullable扩展字段不改变历史行语义；Admission Guard只允许V2.6.15后完整行。
- 不迁移历史Instance、Task、Pool或Claim。

## 32. 权限与RACI

只规划、不初始化：

- `workflow:role-runtime:view`
- `workflow:role-runtime:request`
- `workflow:role-runtime:approve`
- `workflow:role-runtime:revoke`

RACI：Business Owner确认业务范围；Security/Audit Owner确认SoD、DataScope和证据；Workflow Owner维护技术契约但不能单独批准；Release Approver执行最终批准或撤销。

Workflow Administrator不能单独完成request、approve、revoke闭环。RBAC权限也不能替代Activation、Promotion、Binding、Admission、Flag和Canary事实资格。

## 33. Audit查询能力

未来查询模型必须回答：请求人、Activation、Promotion、Candidate、Resolver Version/Contract Hash、Directory Revision/Result Hash、28项结果、全部Capability、Feature Flag/Canary/Kill Switch依据、批准人与撤销人、Definition Version、Node、Execution/Persistence Hash及完整Event链。

未来Runtime实现应在WorkflowInstance Resolver Binding中保存`admission_id`和`persistence_hash`强引用，从而回答Admission创建了哪些运行对象；V2.6.15不创建运行对象。

## 34. Failure Matrix

| 场景 | 结果 | 持久化规则 |
|---|---|---|
| Candidate不存在/非ACTIVE | BLOCKED | 保存执行到失败步骤的Evidence |
| Activation/Promotion证据缺失 | BLOCKED | 禁止拼接或补齐 |
| Resolver/Binding/Candidate Hash漂移 | BLOCKED | Fail Closed |
| Directory Revision/Result Hash漂移 | BLOCKED | `DIRECTORY_REVISION_DRIFT` |
| Definition Version/Node Binding漂移 | BLOCKED | FK/Guard拒绝 |
| Capability NOT_READY/DEGRADED | BLOCKED | 保存首个失败Capability证据 |
| Flag关闭/Canary不匹配/Kill Switch CLOSED | BLOCKED | 保存Policy Version和Evidence Hash |
| SoD/DataScope/Audit失败 | BLOCKED | 不保存敏感明细 |
| 重复requestId、未知提交结果 | 幂等返回 | Request Hash不同则拒绝 |
| 并发Admission | 单赢家 | Candidate+Slot锁、唯一键、CAS |
| Evidence/Event插入失败 | 整体失败 | Admission和Slot全部回滚 |
| Admission/Evidence Hash非法 | 数据库拒绝 | 无自动修复 |
| REVOKED/EXPIRED后继续使用 | 拒绝 | 必须创建新Admission |

## 35. Acceptance Matrix

WF5.18及后续验收至少覆盖：

A. Domain Contract；B. Persistence Contract；C. Migration Contract；D. MySQL Fresh；E. V2.6.14 Upgrade；F. Guard Negative；G. FK Negative；H. CHECK Negative；I. Append-only；J. Transaction Rollback；K. Concurrent Admission；L. Idempotency；M. Hash Stability；N. Hash Drift；O. Explicit User Regression；P. Legacy Regression；Q. Production Dependency Scan。

额外必须验证：历史Candidate NULL扩展字段不能Admission、未来Candidate完整字段可以Admission、Evidence 1—28连续性、Slot释放与Event原子性、Directory Fence过期、三路径Schema Fingerprint一致、二次migrate no-op。

## 36. NO-GO

以下任一存在即NO-GO：

1. Admission可以覆盖旧记录；
2. Evidence可以UPDATE；
3. Event可以UPDATE/DELETE；
4. Directory Revision/Result/Fence无法证明；
5. Candidate归属只能靠Application；
6. Definition Version证据缺失；
7. Node或Node Binding证据缺失；
8. Resolver Contract无法冻结；
9. Capability结果不可追溯；
10. Feature Flag结果不可追溯；
11. Canary结果不可追溯；
12. Kill Switch结果不可追溯；
13. Execution或Persistence Hash不稳定/语义混用；
14. 并发可产生多个当前有效Admission；
15. 撤销需要原位UPDATE核心证据；
16. EXPIRED可自动恢复；
17. Guard会自动修复历史数据；
18. 实现要求修改V2.6.14历史SQL；
19. Persistence要求先把`ROLE_DIRECTORY_V1`改为ACTIVE；
20. Persistence要求先接入Investment。

## 37. V2.6.15 Implementation Gate

本设计已冻结：聚合职责、源Candidate前向扩展、表字段、强归属、双Hash、Evidence Root、状态/Event、Slot并发、事务、Guard、Migration阶段和验收矩阵。

结论：`V2.6.15_IMPLEMENTATION_READY`。

该状态只允许下一Sprint创建候选Migration、Domain Persistence Model、insert/query Repository和契约测试；不代表Migration已执行、资产已晋级、Resolver可执行或ROLE Runtime已启用。

## 38. 剩余风险

- Candidate表前向ALTER的Metadata Lock和在线DDL行为必须在隔离MySQL 8.4实测；
- Directory Provider尚未证明可提供不可伪造、可过期的Revision Fence Token；
- MySQL Trigger对Slot/Event原子约束的最终SQL写法需Migration Contract验证；
- Evidence 28N和Event长期增长需要真实容量模型与冷归档方案；
- graphHash/nodeBindingHash跨语言Canonical需要Golden Vector；
- enterpriseId当前按稳定字符串契约设计，落地前必须与平台租户标识映射测试一致；
- 当前所有Capability仍为Fake，Persistence实现不能因此标记Production Ready；
- 后续Runtime对象对admission_id的强引用尚未设计Migration；
- ROLE Runtime仍未完成真实Directory、SoD、DataScope、Flag、Canary和Kill Switch接入。

## 39. 下一步建议

下一Sprint仅按本契约实施V2.6.15候选和Persistence基础代码，初始状态必须为`CANDIDATE / NOT_EXECUTED`。先完成静态Contract、H2/Repository和负向测试，再单独执行真实MySQL Fresh/Upgrade验收。

最终状态：

- `ROLE_RUNTIME_EXECUTION_ADMISSION_PERSISTENCE_DESIGN_READY`
- `V2.6.15_IMPLEMENTATION_READY`
- `ROLE_RUNTIME_DISABLED`

禁止进入真实ROLE Runtime、ROLE Task、Candidate Pool Runtime、Claim Runtime或Investment Integration。
