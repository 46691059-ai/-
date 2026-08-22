# Workflow Claim 数据库完整性治理设计

## 1. 背景及P0问题

本文是 Sprint 2-3.7-WF4.2.2 的冻结设计，基线为 V2.6.7。V2.6.7 保持 `CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`，本阶段不修改历史 Migration、不创建 V2.6.8 SQL、不修改 Workflow 或 Investment 代码。

真实 MySQL 验收暴露三项 P0：

1. `workflow_task_claim.node_execution_id`没有数据库级归属约束，合法 Task 可以与不存在或不相关的 NodeExecution 拼接为伪 Claim。
2. `workflow_task_claim_audit`仅以可空`claim_id`单列关联 Claim，冗余的 Task、Instance、NodeExecution、Pool、Member 字段可以被独立改写。
3. 数据库允许`result='SUCCESS'`且`claim_id IS NULL`的孤立成功审计。

治理目标是让数据库拒绝跨 Task、跨 Instance、跨 NodeExecution、跨 Candidate Pool、跨 Candidate Member 的 Claim 与 SUCCESS Audit。应用校验仍保留，但不得作为数据库完整性的替代。

## 2. Claim权威归属链

冻结唯一权威链：

```text
WorkflowInstance
  -> NodeExecution
    -> Task
      -> CandidatePool
        -> CandidateMember
          -> TaskClaim
            -> ClaimAudit
```

| 层级 | 权威标识/外键 | 冗余查询字段 | 审计快照 | 允许更新 | 永久不可变 |
| --- | --- | --- | --- | --- | --- |
| Instance | `workflow_instance.id` | `version_id` | 流程版本、业务关联由Instance冻结 | 仅状态机字段和乐观锁版本 | ID、业务关联、冻结版本 |
| NodeExecution | `(instance_id,id)`，并关联Version/Node | `node_code_snapshot` | 节点名称、编码、访问次数 | 仅节点状态机及完成信息 | Instance、Version、Node归属 |
| Task | `(id,instance_id,node_execution_id)`，底层仍保留Version/Node强关联 | `assignee_user_id` | Task创建时分配快照另表保存 | Task状态、受控Assignee/Claim时间、乐观锁版本 | Instance、Version、Node、NodeExecution归属 |
| CandidatePool | `(id,task_id,instance_id,node_execution_id)` | Resolver、策略、状态 | Pool/Rule/Contract Hash | 仅Pool状态和乐观锁版本 | Task、Instance、NodeExecution及冻结候选规则 |
| CandidateMember | `(id,pool_id,task_id,instance_id,candidate_user_id)` | 组织/岗位/角色快照 | Eligibility Snapshot/Hash | 仅受控资格状态 | Pool、Task、Instance、Candidate User和资格快照 |
| TaskClaim | Claim自身是经办权领取事实；必须复合引用上游 | 上游ID仅为查询加速，不是第二事实来源 | 实时资格、RBAC、DataScope、SoD、状态前后 | 未来仅状态CAS、active token和乐观锁版本 | 全部业务归属、身份、幂等和证据字段 |
| ClaimAudit | `claim_id`及与Claim完全一致的复合归属 | 保留冗余字段用于独立审计检索 | 事件Hash、TraceId、幂等键、发生时间 | 无 | 全行；通过补偿事件纠错 |

任何冗余字段必须由同一条复合外键或不可变机制与权威事实绑定；不能只依赖“写入时碰巧一致”。

## 3. TaskClaim复合外键设计

### 3.1 方案比较

当前结构事实：`workflow_node_execution`已有`UNIQUE(instance_id,id)`及`UNIQUE(instance_id,version_id,node_id,id)`；`workflow_task`已有外键`(instance_id,version_id,node_id,node_execution_id)`指向NodeExecution，并已有覆盖Task/Instance/Version/Node/NodeExecution的候选键。Claim尚未复用该权威关系。

| 方案 | 完整性 | 冗余与索引成本 | 查询/写入性能 | MySQL/达梦/金仓 | 结论 |
| --- | --- | --- | --- | --- | --- |
| A：`claim.node_execution_id -> execution.id` | 只证明存在，无法证明同Instance/Task | 最低 | 最轻 | 均支持 | 不可单独采用 |
| B：`(instance_id,node_execution_id)`复合FK | 可证明Instance归属 | 一个窄复合索引 | 开销较低 | 均支持 | 必须作为显式防线 |
| C：`(task_id,instance_id,node_execution_id)`引用Task候选键 | 直接复用Task与Execution权威关系 | Task和Claim各增加/复用候选键 | Claim写入增加一次索引校验，查询可被覆盖索引加速 | 均支持 | 核心推荐 |
| D：仅经Member/Pool间接闭合 | 链完整时可传递证明，但Claim自身NodeExecution字段仍可能漂移 | 少一个直接FK | 写入略轻、诊断更复杂 | 均支持 | 只能作为辅助，不能单独采用 |

冻结模型：以C为核心、B为显式存在性防线、D作为Pool/Member归属补强；A不单独使用。

### 3.2 父级候选键

推荐在未来增量 Migration 中建立以下父级候选键和子级外键，具体约束名在实施阶段冻结：

1. `workflow_task`增加唯一候选键`(id, instance_id, node_execution_id)`。
2. `workflow_task_candidate_pool`增加唯一候选键`(id, task_id, instance_id, node_execution_id)`。
3. 复用V2.6.7的Candidate Member唯一键`(id, pool_id, task_id, instance_id, candidate_user_id)`。
4. `workflow_task_claim`增加供Audit引用的唯一候选键：

   `(id, task_id, instance_id, node_execution_id, candidate_pool_id, candidate_member_id, candidate_user_id, operator_user_id)`。

### 3.3 Claim强关联

`workflow_task_claim`至少建立：

- `(task_id, instance_id, node_execution_id)`引用`workflow_task(id, instance_id, node_execution_id)`；
- `(candidate_pool_id, task_id, instance_id, node_execution_id)`引用Candidate Pool同序候选键；
- 继续使用`(candidate_member_id, candidate_pool_id, task_id, instance_id, candidate_user_id)`引用Candidate Member；
- `(instance_id, node_execution_id)`直接引用`workflow_node_execution(instance_id,id)`，作为NodeExecution存在性和Instance归属的显式防线。

Pool已经复合引用Task的Version、Node和NodeExecution，因此上述约束闭合后，Claim无法用“合法Task + 非法NodeExecution”“合法Pool + 其他Task/Instance”“合法Member + 其他Pool”拼接伪事实。

不建议给Claim再复制`version_id`和`node_id`。这些字段可经Task/Pool权威链获得；继续复制会扩大可漂移面。若后续性能数据证明Join成本不可接受，应先通过覆盖索引或只读物化视图优化，而不是新增事实字段。

### 3.4 CandidatePool归属

Candidate Pool的权威父对象是Task。V2.6.6已用`(task_id,instance_id,version_id,node_id,node_execution_id)`复合外键绑定Task；未来Claim必须使用`(candidate_pool_id,task_id,instance_id,node_execution_id)`引用Pool候选键。这样合法Task A不能与Pool B组合，Pool也不能在Claim中被替换为同Instance的其他节点Pool。

### 3.5 CandidateMember归属与身份边界

复用V2.6.7的`(candidate_member_id,candidate_pool_id,task_id,instance_id,candidate_user_id)`复合关系。该约束同时证明Member属于同一Pool、Task、Instance且冻结候选用户一致。

字段语义冻结：数据库中的`candidate_user_id`是冻结候选成员身份，领域层等价于`claimant_user_id`；`operator_user_id`是实际发起Claim操作的用户。当前不支持代领，因此三者必须相等。未来若允许管理员代领，必须新增明确的actor/beneficiary语义和独立Migration，不能放宽现有CHECK。

## 4. NodeExecution关联方案

| 方案 | 完整性 | 冗余/性能 | MySQL及国产数据库 | 结论 |
| --- | --- | --- | --- | --- |
| A：仅`node_execution_id -> id` | 只能证明存在，不能证明属于同一Instance/Task | 最少字段，索引简单 | 普遍支持 | 不足 |
| B：`(instance_id,node_execution_id)`复合FK | 同时证明存在和Instance归属 | 复用现有字段，索引成本低 | MySQL、达梦、金仓均支持 | 必须采用 |
| C：Task、Pool与NodeExecution复合闭环 | 同时约束Task、Pool、Instance、NodeExecution | 多个窄候选键，写入多一次索引维护 | 均为标准复合UNIQUE/FK | 推荐主方案 |

冻结结论：采用C，并包含B作为显式存在性防线。A不能单独使用。约束统一`ON DELETE RESTRICT ON UPDATE RESTRICT`，禁止通过级联改变审计归属。

## 5. ClaimAudit事件模型

保留现有双轴语义：

- `event_type`表示动作类型，当前Claim闭环只实现`CLAIM`；`RELEASE/CANCEL/TRANSFER/DELEGATE`仍是未实现的保留动作。
- `result`表示结果，`SUCCESS`是已形成TaskClaim的业务事实；`DENIED/CONFLICT/FAILED`是未形成Claim的尝试结果。

冻结规则：

1. 当前`workflow_task_claim_audit`持久化链只写`CLAIM + SUCCESS`；失败尝试继续进入平台安全审计，不写伪TaskClaim，也不伪装为SUCCESS。
2. 若未来将拒绝或系统事件纳入本表，必须另行Migration明确`event_type/result`组合、可空字段、幂等键和保留期限；本次治理不提前开启。
3. `SUCCESS`必须绑定有效Claim并具有完整一致的业务归属；`REJECTED/SYSTEM`语义不能复用SUCCESS约束或Hash算法。

## 6. SUCCESS Audit强关联

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| A：Audit仅保存`claim_id`，其他归属通过Claim查询 | 单一事实源、篡改面最小、约束简单 | 改变既有表和审计查询契约；Claim表可用性成为审计查询前提 | 长期目标，当前不做破坏性迁移 |
| B：保留Task/Instance/Execution/Pool/Member并以复合FK绑定Claim | 保留独立审计检索与证据导出；兼容既有结构 | 复合索引较宽，写入和DDL成本更高 | V2.6.8推荐方案 |

推荐B。继续保存Audit冗余归属字段，原因是审计检索、证据导出和跨系统留痕不能完全依赖多表Join；但所有冗余字段必须通过一条复合外键绑定到同一个Claim：

```text
Audit(
  claim_id, task_id, instance_id, node_execution_id,
  candidate_pool_id, candidate_member_id,
  claimant_user_id, operator_user_id
)
  -> TaskClaim(
  id, task_id, instance_id, node_execution_id,
  candidate_pool_id, candidate_member_id,
  candidate_user_id, operator_user_id
)
```

同时新增或替换CHECK，冻结为：

- `event_type='CLAIM' AND result='SUCCESS'`时，以上所有关联字段均`NOT NULL`；
- `claimant_user_id = operator_user_id`，并由复合FK再次与Claim一致；
- SUCCESS的`frozen_eligibility_hash`、状态前后、RBAC、DataScope、SoD证据均非空；
- 不允许`SUCCESS + claim_id NULL`；
- 当前版本不接受非SUCCESS行进入ClaimAudit，失败尝试走平台安全审计。

不采用“只保存claim_id并删除其他字段”的激进方案：它会破坏既有审计查询契约和证据独立性。复合FK将冗余字段降级为受约束的审计投影，而不是第二事实来源。

## 7. Audit不可变性

SUCCESS Audit是append-only事实：业务生命周期内不允许UPDATE、逻辑删除或物理删除。现有`deleted=0/delete_token=0/version=0` CHECK只能约束值，不能阻止把一组合法值更新成另一组合法值，因此不足以证明不可变。

采用四层防护：

1. **Domain/Application**：只暴露记录成功事件和查询，不提供修改、逻辑删除、物理删除命令。
2. **Repository**：`TaskClaimAuditRepository`只保留`appendSuccess(...)`/`find...`；Mapper不得被Controller或Application直接注入。
3. **数据库权限**：应用运行账号对Audit表仅授予`SELECT, INSERT`，明确撤销`UPDATE, DELETE`；Flyway/DBA使用独立账号。该层是跨MySQL、达梦、金仓的主要运行时边界。
4. **数据库防御**：按数据库方言提供UPDATE/DELETE阻断Trigger或等价规则，防止误授高权限后篡改。Trigger只负责不可变性，不承担归属校验；归属仍由标准FK/CHECK保证。

Break-glass修复只能由独立DBA账号在变更单、双人复核、备份和外部审计开启后执行，不属于应用能力。任何修复采用补偿事件优先，不覆写原事件。

## 8. Claim不可变字段

TaskClaim创建成功后以下字段永久不可变：

- `id/claim_no`；
- `task_id/instance_id/node_execution_id`；
- `candidate_pool_id/candidate_member_id`；
- `candidate_user_id/operator_user_id`；
- `claim_time/idempotency_key/trace_id`；
- Eligibility Hash及实时资格、RBAC、DataScope、SoD证据；
- Task/Pool状态前后和版本前后；
- `created_by/created_time`。

未来仅允许由明确状态机CAS修改`status`、`active_token`、`updated_by/updated_time`和`version`。Claim事实不进行逻辑删除；终态使用`RELEASED/CANCELLED`等状态表达。Release/Transfer/Delegate不在本阶段实现。

实施时Repository应拆分为`insertClaim`、`findActive...`和专用`compareAndSetStatus`，禁止通用`updateById`修改整个Entity。数据库权限可使用列级UPDATE授权；方言Trigger对不可变列做`OLD/NEW`比较作为纵深防御。

## 9. V2.6.8 Migration规划

本节仅规划，不创建SQL。建议未来候选文件名冻结为`V2.6.8__strengthen_workflow_claim_integrity.sql`，按以下顺序执行：

1. **Phase A—临时预检**：创建仅用于统计/阻断的临时对象或直接执行断言；任一异常计数非零立即失败。
2. **Phase B—辅助唯一索引**：给Task、Candidate Pool、TaskClaim增加所需复合唯一候选键。
3. **Phase C—复合外键**：给TaskClaim增加Task、NodeExecution强关联；将Pool外键升级为包含`node_execution_id`的复合外键；保留Member复合外键；给Audit增加对TaskClaim完整归属元组的复合外键。
4. **Phase D—CHECK治理**：收紧Audit CHECK，阻止SUCCESS空Claim、空归属和不完整证据。
5. **Phase E—结构验证**：校验约束均为ENFORCED、外键列序正确、负向探针被拒绝，并记录Flyway checksum及Schema fingerprint。
6. **Phase F—清理临时对象**：显式清理所有预检临时对象；永久候选键和约束不得清理。
7. **部署后权限**：配置应用账号append-only权限；各数据库方言单独提供不可变Trigger脚本，不把MySQL Trigger作为唯一方案。

MySQL DDL存在隐式提交，故所有数据预检必须先于第一条永久DDL。若DDL中途失败，不执行自动repair或回写历史Migration；保留失败证据，按已完成DDL状态编写受控前向修复。V2.6.7摘要不得改变。

## 10. 历史数据预检

未来Migration必须在DDL前检查并报告具体主键，不自动删除、纠正或猜测：

1. Claim引用不存在的NodeExecution；
2. Claim的Instance与NodeExecution不一致；
3. Claim与Task的Instance/NodeExecution不一致；
4. Claim与Candidate Pool的Task/Instance/NodeExecution不一致；
5. Claim与Candidate Member的Pool/Task/Instance/User不一致；
6. SUCCESS Audit的`claim_id`为空或Claim不存在；
7. Audit与Claim的Task/Instance/NodeExecution/Pool/Member/User/Operator任一不一致；
8. 同一Task存在重复活动Claim或重复活动token；
9. 非法`event_type/result`组合、空证据、非法Hash或伪SUCCESS；
10. 将来要创建的候选键存在重复值或NULL值。

预检失败状态为`DATA_REMEDIATION_REQUIRED`。数据治理人员必须提供确认人、确认依据、修复前后证据和审批单；修复脚本独立版本化，不与结构Migration混写。

## 11. Fresh / Upgrade / Repair验收方案

### A. Fresh

从V2.0.0完整执行至未来V2.6.8；执行pre-check、migrate、strict validate、二次migrate no-op，验证所有负向约束并生成完整/Workflow/Claim三类Schema fingerprint。

### B. Upgrade

建立V2.6.6状态，依次执行V2.6.7、V2.6.8。必须证明V2.6.7只执行一次、V2.6.8只执行一次，合法Claim和既有DIRECT/Legacy路径不受影响。

### C. Repair环境

“Repair”指已经成功执行V2.6.7但验收标记失败的已知隔离环境正常前向升级到V2.6.8，不是执行Flyway `repair`。禁止改checksum、删除history或把失败验收伪装为成功。先确认V2.6.7 SHA/checksum与仓库一致，再执行相同预检和V2.6.8。

三条路径最终Schema fingerprint必须完全一致；Fresh与Upgrade还要执行同一套合法链、负向约束、并发单赢家、回滚和审计不可变测试。

V2.6.7原文件SHA-256固定为`800e1a5b1af67e1680a3f0233e9d5400c0b42f39349d181f1777c7ce04ac6aed`，历史状态继续保留`CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`且不得删除原FAIL记录。只有V2.6.7与V2.6.8组合完成三路径真实验收后，才可评估将V2.6.7标记为`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED_WITH_V268`；该状态不代表V2.6.7单独通过。

## 12. 负向验收矩阵

| # | 场景 | 数据库预期 |
| --- | --- | --- |
| 1 | Claim引用不存在NodeExecution | FK拒绝 |
| 2 | Claim引用其他Instance的NodeExecution | 复合FK拒绝 |
| 3 | Claim的Task与Instance/NodeExecution不一致 | Task复合FK拒绝 |
| 4 | Claim跨Candidate Pool | Pool复合FK拒绝 |
| 5 | Claim跨Candidate Member或Candidate User | Member复合FK拒绝 |
| 6 | SUCCESS Audit的`claim_id=NULL` | CHECK拒绝 |
| 7 | SUCCESS Audit引用其他Task | Audit→Claim复合FK拒绝 |
| 8 | SUCCESS Audit引用其他Instance | Audit→Claim复合FK拒绝 |
| 9 | SUCCESS Audit引用其他NodeExecution | Audit→Claim复合FK拒绝 |
| 10 | SUCCESS Audit引用其他Pool | Audit→Claim复合FK拒绝 |
| 11 | SUCCESS Audit引用其他Member/User/Operator | Audit→Claim复合FK拒绝 |
| 12 | UPDATE SUCCESS Audit的Task归属 | 运行账号权限及不可变Trigger拒绝 |
| 13 | UPDATE SUCCESS Audit的Instance归属 | 运行账号权限及不可变Trigger拒绝 |
| 14 | UPDATE SUCCESS Audit的NodeExecution归属 | 运行账号权限及不可变Trigger拒绝 |
| 15 | UPDATE SUCCESS Audit的Pool归属 | 运行账号权限及不可变Trigger拒绝 |
| 16 | UPDATE SUCCESS Audit的Member归属 | 运行账号权限及不可变Trigger拒绝 |
| 17 | DELETE或逻辑删除SUCCESS Audit，或修改TaskClaim不可变字段 | 权限、CHECK/Trigger拒绝 |
| 18 | 重复活动Claim | 既有唯一约束拒绝 |

合法正向链另行验证：Claim、Audit及后续Approve成功，且Claim本身不推进NodeExecution。

每项都必须在真实MySQL双路径执行；达梦、金仓适配验收使用相同语义矩阵，不以“SQL可解析”代替约束行为验证。

## 13. Application/Repository影响

后续实施预计影响但本Sprint不修改：

- `TaskClaimEntity`和`WorkflowTaskClaimAuditEntity`：映射约束字段；禁止生成通用可变模型语义。
- `TaskClaimRepository`：保留查询和insert，未来状态变化只开放带expectedVersion的专用CAS方法。
- `TaskClaimAuditRepository`：重命名或明确为`appendSuccess`，只提供insert和find，不提供update/delete。
- Mapper：仅基础设施Adapter可见；Audit Mapper不得向Controller/Application暴露。
- `TaskClaimTransactionService`：继续在同一事务内完成Claim、Task/Pool CAS和Audit append；新增约束异常必须映射为稳定冲突码，不能吞掉数据库完整性异常。
- 测试：增加架构测试，阻止Audit update/delete方法和Controller直接Mapper依赖。

Application构造规则同时冻结：创建Claim时一次性写入完整归属；SUCCESS Audit必须在Claim持久化后由已持久化Claim对象派生；外部调用方不得提交Audit的Task/Instance/Execution/Pool/Member ID；Audit Adapter不得接受可自由组合的业务ID参数。

数据库约束是最终防线，Repository不可变API是正常开发路径，两者缺一不可。

## 14. Claim、审批与Investment边界

- Claim只确定Task经办人，`Claim != Approve`；不改变Task Action、NodeExecution推进和Instance审批状态机。
- Workflow负责Candidate资格、Claim完整性、Task治理和流程留痕。
- Investment继续负责三重一大业务语义、投资决策结果和业务职责分离规则。
- 本治理不修改Investment、不增加Investment外键，也不允许Workflow直接写Investment状态。

## 15. 国产数据库兼容性

| 能力 | MySQL 8 | 达梦 | 人大金仓 | 治理要求 |
| --- | --- | --- | --- | --- |
| 复合UNIQUE/FK | 支持 | 支持 | 支持 | 列类型、顺序、长度完全一致；父键显式UNIQUE |
| CHECK | 8.x强制执行 | 支持，语法需适配 | 支持 | 避免MySQL专属表达式；Hash正则按方言测试 |
| RESTRICT FK | 支持 | 支持/需核对语法 | 支持 | 禁止CASCADE修改审计归属 |
| 事务DDL | 多数DDL隐式提交 | 行为不同 | PostgreSQL系通常事务化 | 预检先行，失败采用前向修复 |
| 列级权限 | 支持 | 支持 | 支持 | 应用与Migration账号分离 |
| Trigger | 支持 | 支持，语法不同 | 支持，语法不同 | 仅作不可变纵深防御，按vendor脚本维护 |

核心归属完整性只使用标准UNIQUE、FK、NOT NULL和CHECK；Trigger不承担跨表关系判断。Flyway目录应按数据库方言隔离不可变Trigger及正则差异，保持同一领域约束和验收矩阵。

## 16. 风险清单

1. **历史脏数据P0**：任何孤立或跨归属记录都会阻止新增FK，必须人工治理，禁止自动猜测。
2. **MySQL非事务DDL P0**：中途失败可能留下部分索引/约束，只能基于实际Schema前向修复。
3. **外键环P1**：Instance当前Execution、Execution归属和Task/Pool链已较复杂；新增约束必须保持RESTRICT且避免新增反向级联路径。
4. **复合索引过宽P1**：Audit→Claim键字段较多，增加存储、Buffer Pool和写放大；实施前核算索引字节并用真实数据压测。
5. **DDL锁P1**：大表增加UNIQUE/FK可能阻塞Claim写入；需评估在线DDL能力、维护窗口和锁等待终止阈值。
6. **Claim写性能P1**：每次Claim需多组FK/唯一键校验；需验证P95/P99和并发单赢家吞吐。
7. **不可变性跨库差异P1**：Trigger/权限语法不同，需要三套vendor验收，不能只验证MySQL。
8. **高权限账号与Audit不可变性P1**：DBA可能绕过运行账号权限，必须以break-glass、双人审批和外部审计治理。
9. **未来Release状态变化P1**：Release/Transfer/Delegate启用前必须重新定义Claim可变状态及Audit事件组合，不能放宽原始归属。
10. **Claim历史查询性能P2**：append-only Audit持续增长，需要按Task/Trace/时间的覆盖索引、归档和容量基线。
11. **逻辑删除与唯一键P2**：Claim/Audit事实原则上不删除；父表逻辑删除及`delete_token`组合不能导致活动事实失联或业务键复用歧义。
12. **查询契约P2**：Repository收紧可能影响测试夹具和运维脚本，实施前需完整引用扫描。

## 17. WF4.2.3实施准入门禁

只有以下条件全部满足，才能进入实施Sprint：

1. 本文评审通过并标记`DESIGN_FROZEN`；
2. V2.6.7 SHA-256及Flyway checksum再次确认无漂移；
3. 生产/测试环境历史数据预检SQL完成同行评审，并能输出具体问题主键；
4. Task、Pool、Member、Claim、Audit候选键及外键列序冻结；
5. SUCCESS事件组合和失败事件去向冻结；
6. MySQL、达梦、金仓的权限与不可变Trigger策略分别确认；
7. Application运行账号与Flyway账号权限边界确认；
8. Fresh/Upgrade/Repair脚本及16项负向矩阵准备完成；
9. 明确MySQL DDL部分成功后的前向修复Runbook；
10. 确认实施范围不包含ROLE/POSITION/ORG、Investment、Release/Transfer/Delegate。

任一门禁未满足，状态保持`BLOCKED`，不得创建或执行修复Migration。

## 18. 冻结结论

- V2.6.7状态保持：`CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`。
- 本文状态：`DESIGN_FROZEN / IMPLEMENTATION_NOT_STARTED`。
- 本阶段没有修改V2.6.7、没有创建V2.6.8 SQL、没有修改Workflow或Investment业务代码。
- 后续只能在独立实施Sprint通过新增Migration前向治理，禁止修改历史资产或使用Flyway repair掩盖验收失败。
