# Sprint 2-3.7-WF5.23 ROLE Realtime Eligibility Evidence Persistence Governance Design

## 1. Current Baseline

| 项目 | 冻结值 |
|---|---|
| 数据库最高版本 | V2.6.15 |
| 资产状态 | `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED` |
| SHA-256 | `db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44` |
| Flyway checksum | `538981271` |
| Migration清单 | 37/37 |
| Realtime Eligibility | `FRAMEWORK_READY / NON_EXECUTABLE` |
| Directory | `DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED` |
| Resolver | `ROLE_DIRECTORY_V1_PREPARED_NON_EXECUTABLE` |
| ROLE Runtime | `DISABLED` |

WF5.22只在内存生成三态Eligibility、27步结果、TTL及Revision Fence；真实Claim尚未接入。本Sprint只冻结持久化治理契约，不创建V2.6.16、不修改代码、不产生真实审计证据。

## 2. Existing Claim Persistence Audit

### 2.1 `workflow_task_claim`

已保存：Claim编号、Task/Pool/Candidate Member/Instance/NodeExecution、Candidate/Operator User、Claim状态与时间、冻结资格摘要Hash、扁平Realtime/DataScope/SoD/RBAC文本、Task/Pool状态前后值、Task版本前后值、幂等键、trace及活动Token。

V2.6.8通过复合外键把Claim强归属到Task、NodeExecution、Pool和Member，并通过唯一活动Token保证一个Task只有一个活动Claim。

缺口：没有Eligibility Evidence业务ID、双Directory Revision、TTL、Runtime Binding ID/Hash、27步Validator、分项Capability、Root Hash、Persistence Hash及消费状态。

### 2.2 `workflow_task_claim_audit`

已保存：成功/拒绝/冲突/失败事件、冗余归属字段、原因、冻结资格Hash、扁平资格文本、状态前后值、trace/幂等键及前序/当前事件Hash。V2.6.8要求成功事件完整关联Claim，并使用Trigger禁止UPDATE/DELETE。

缺口：审计表是Claim行为日志，不是资格判断聚合；无法结构化复核27步和10类Capability，也不能独立表达“ELIGIBLE但Claim未提交”。

### 2.3 Candidate与Binding

- `workflow_task_candidate_pool`冻结Task/Instance/Version/Node/NodeExecution、Resolver Binding、Assignment Snapshot、Resolver contract/rule、Pool Hash和有效时间。
- `workflow_task_candidate_member`冻结Pool/Task/Instance/User、来源、组织/岗位/角色引用、Eligibility Snapshot/Hash和状态。
- V2.6.5的Binding Set、Resolver Binding、Node Resolver Binding Snapshot提供Instance/Version/Node/Resolver/Contract/Rule强归属。

这些对象可作为Evidence的父事实，但不得被Eligibility写回或刷新。

### 2.4 V2.6.15可复用经验

V2.6.15已采用主事实、结构化Evidence、事件Hash链、占用槽、Root Hash、Persistence Hash、复合FK、Guard和append-only Trigger。其治理模式可借鉴，但Execution Admission与Realtime Eligibility语义不同，不得复用同一业务表或混合Canonical版本。

## 3. Persistence Gap Matrix

| 问题 | 当前能力 | 缺口 | 冻结结论 |
|---|---|---|---|
| Claim身份/归属 | Claim复合FK较完整 | 无Binding/Evidence引用 | 新Evidence复合归属；Claim单向引用Evidence |
| Claim Audit | 行为事件Hash链 | 资格步骤不可查询 | 保持Claim Audit；不扩成Evidence仓库 |
| Realtime Evidence位置 | 扁平TEXT和单Hash | 无结构化证据 | 新独立Aggregate |
| 27步Validator | 无 | 无顺序/完整性/Root | 子表，每步一行 |
| Capability | 4个扁平摘要 | 无provider/policy/hash | 子表，每Capability一行 |
| Directory | Candidate历史事实 | 无Claim Revision/complete/contract | 主Evidence保存最小事实 |
| TTL | 无 | 过期仍可能提交 | 主Evidence时间字段+Claim INSERT Trigger |
| Claim绑定 | 无 | 无法证明Claim用了哪个Evidence | Claim增加单向Evidence引用；事件记录消费 |
| 失败Evidence | Audit摘要 | ELIGIBLE/CAS失败不可表达 | 聚合保留+`CLAIM_NOT_COMMITTED`事件 |
| 不可变 | Claim Audit已有Trigger | Eligibility无对象 | 全Evidence/Event append-only |
| 幂等 | Claim按Task+key | Prepare可能重复 | Eligibility request唯一键+Claim原幂等 |
| Legacy | 历史Claim允许无新证据 | 不能强制全量回填 | INSERT门禁只约束未来ROLE Claim |

结论：现有表均不足以承载完整Realtime Eligibility Evidence；扩展Claim Audit会混淆“资格判断”与“处理权取得”。

## 4. 方案比较

| 方案 | 不可变/审计 | 容量/查询 | 失败Evidence | FK/事务 | 扩展性/Legacy | 结论 |
|---|---|---|---|---|---|---|
| A 扩展Claim | 差；资格与Claim耦合 | 宽表、稀疏字段 | Claim失败时无父记录 | 简单但无法Claim前保存 | Capability扩展困难 | 不选 |
| B 扩展Claim Audit | 中；事件与事实混杂 | 大TEXT或重复列 | 可保存 | 成功/失败语义复杂 | 破坏既有Audit契约 | 不选 |
| C Evidence主表+Validator/Capability | 好；结构化 | 行数可控、易按Task/User查询 | 可保存 | 需复合FK/Root校验 | Legacy隔离好 | 可行 |
| D Aggregate+Validator/Capability+Lifecycle | 最佳；判断与消费均可追溯 | 多一张Event表 | 完整表达 | Migration与Trigger最复杂 | 最适合未来Capability | 推荐 |

推荐D。Evidence内容只插入一次；状态、验证、消费、过期和Claim结果通过append-only Event表达。Claim自身保持处理权事实。

## 5. 推荐Aggregate

`RoleRealtimeEligibilityEvidenceAggregate`由以下对象组成：

1. `workflow_role_realtime_eligibility_evidence`：一次资格判断的主事实和Root Hash。
2. `workflow_role_realtime_eligibility_validator_evidence`：有序Validator结果。
3. `workflow_role_realtime_eligibility_capability_evidence`：去重后的Capability结果。
4. `workflow_role_realtime_eligibility_event`：生命周期、Claim提交结果和Hash链。
5. `workflow_task_claim.eligibility_evidence_id`：未来ROLE Claim对Evidence的单向强引用。

不新增独立ROLE Candidate Pool，不复制完整Directory，不把Validator事实再次写入Claim Audit。Claim Audit只保存Evidence ID/Persistence Hash引用与Claim行为结果。

## 6. Evidence Identity

主表建议字段：

| 字段 | 语义/约束 |
|---|---|
| `id` / `eligibility_evidence_id` | 数据库主键/稳定业务ID，均唯一 |
| `eligibility_request_id` | Prepare请求ID；精确重试幂等 |
| `claim_request_id` / `claim_idempotency_key` | Claim意图与既有Claim幂等关联 |
| `attempt_no` | 同一Claim意图重新Prepare的递增尝试号 |
| `correlation_id` | 跨阶段追踪，不作为授权 |
| `instance_id/task_id/node_execution_id` | Workflow强归属 |
| `candidate_pool_id/candidate_member_id/candidate_user_id` | Frozen Candidate强归属 |
| `resolver_binding_id/node_resolver_binding_id` | 精确Runtime Binding来源 |
| `claim_id` | 不放在主Evidence，避免循环FK；通过Claim及Event关联 |
| `claim_at/verified_at/expires_at` | 可信服务器时间；`verified_at <= claim_at < expires_at` |

唯一键建议：`eligibility_request_id`；`(task_id,candidate_user_id,claim_request_id,attempt_no)`；`persistence_hash`。相同Eligibility Request重试返回原Aggregate，不重复生成子记录。

## 7. Result Model

资格Decision固定为`ELIGIBLE / INELIGIBLE / INDETERMINATE`。Claim结果独立为：

- `CLAIM_COMMITTED`
- `CLAIM_NOT_COMMITTED`

不得从Eligibility Decision推导Claim结果。`ELIGIBLE + CLAIM_NOT_COMMITTED`是合法且必须可查询的组合；`INELIGIBLE/INDETERMINATE + CLAIM_COMMITTED`必须由数据库拒绝。

## 8. Validator Evidence

建议每个已执行Validator一条结构化记录：`evidence_id, validator_code, validator_order, status, reason_code, evidence_hash, checked_at, canonical_version`。

约束：

- 唯一`(evidence_id, validator_order)`和`(evidence_id, validator_code)`。
- `validator_order BETWEEN 1 AND 27`。
- `status IN (PASS, FAIL, INDETERMINATE)`。
- ELIGIBLE必须恰有27条、顺序1..27连续、全部PASS。
- INELIGIBLE/INDETERMINATE可因fail-fast少于27条，但必须从1连续到terminal order，最后一条状态与主Decision一致，前序均PASS。
- 子记录INSERT-only，不允许用大JSON替代。

## 9. Capability Evidence

建议字段：`evidence_id, capability_code, validator_code, status, decision, provider_version, policy_version, evidence_hash, checked_at, valid_until, canonical_version`。

固定Capability代码：`ROLE_MEMBERSHIP, USER_STATUS, ORGANIZATION_MEMBERSHIP, DATA_SCOPE, PLATFORM_SOD, BUSINESS_SOD, AUDIT, FEATURE_FLAG, KILL_SWITCH, CANARY`。

ELIGIBLE必须具备上述10项且全部PASS。Fail-fast拒绝只要求已执行Capability连续符合Validator轨迹；不得伪造未执行Capability为PASS。

## 10. Directory Evidence

主Evidence保存：`candidate_directory_revision, claim_directory_revision, directory_result_hash, directory_contract_hash, directory_complete, effective_at, directory_checked_at`。

只证明candidateUserId的单人实时资格。禁止成员列表、HR档案、手机号、身份证、地址、薪资或Secret进入数据库。

## 11. Candidate Frozen Facts

Evidence通过复合FK引用Pool和Member，同时冗余冻结`candidate_pool_hash, candidate_user_id, frozen_role_code, frozen_organization_id, candidate_directory_revision`用于审计和Hash。

冗余值必须由INSERT Trigger与父表核对；不得以Evidence修改Pool/Member，也不得从实时Directory补Candidate。

## 12. Revision Fence

Claim Prepare保存Candidate Revision与Claim Revision。Claim提交时复核：

- Pool Hash、Runtime Binding Hash、Task Version未漂移。
- claim revision/result/contract与Evidence一致。
- Evidence仍在TTL内。

R10到R15的正常升级本身不失败。数据库只能验证已持久化Fence及本地事实，不能在锁内查询外部Directory；远端Revision有效性必须在Prepare时完成并受TTL约束。

## 13. TTL

必须存`verified_at`与`expires_at`，且`expires_at > verified_at`。Claim INSERT Trigger要求：

```text
claim.claim_time >= evidence.verified_at
claim.claim_time <  evidence.expires_at
CURRENT_TIMESTAMP(3) < evidence.expires_at
```

`expires_at`一经插入不可修改。过期Evidence只能追加EXPIRED事件并重新Prepare，不能延长或覆盖。

## 14. Eligibility Canonical

继续使用`ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1`证明一次资格判断输入与结果。该Hash由WF5.22生成，数据库只校验格式、引用与其在Persistence Canonical中的固定位置，不重新定义执行语义。

## 15. Persistence Canonical

新增并独立冻结`ROLE_REALTIME_ELIGIBILITY_PERSISTENCE_CANONICAL_V1`，固定UTF-8、UTC毫秒、长度前缀、稳定字段顺序和小写SHA-256。

至少覆盖：Eligibility Hash、Pool/Binding Hash、Candidate User、Role/Org、双Revision、Directory Result/Contract、Validator Root、Capability Root、verified/expires、Decision、Policy/Canonical Version、Task/Pool/Member/Instance/Node归属与attempt no。排除数据库ID、显示名、写入时间和日志文本。

## 16. Validator Root

`validator_evidence_root_hash`按`validator_order`升序，对每项稳定编码后计算SHA-256。任何code/order/status/reasonCode/evidenceHash/checkedAt变化均改变Root。

数据库Claim门禁重新计算ELIGIBLE的27项Root并与主表比较；缺项、重复、乱序或Root漂移均拒绝。

## 17. Capability Root

`realtime_capability_evidence_root_hash`按`capability_code`ASCII升序计算。任何status/decision/provider/policy/evidenceHash/checkedAt变化均改变Root。

ELIGIBLE Claim门禁要求10个固定Capability代码各一条，并重算Root；不得依赖JSON顺序或数据库默认Collation。

## 18. Claim Success Gate

未来`workflow_task_claim`新增nullable `eligibility_evidence_id`和`eligibility_contract_version`，并对`eligibility_evidence_id`建立唯一键及复合FK：

```text
(eligibility_evidence_id, task_id, instance_id, node_execution_id,
 candidate_pool_id, candidate_member_id, candidate_user_id)
→ Evidence同名强归属键
```

Claim BEFORE INSERT Trigger根据Pool的`strategy_type/resolver_code`识别未来`ROLE_DIRECTORY_V1`路径，而不是信任调用方声明。ROLE Claim必须同时满足17项门禁：Evidence存在且ELIGIBLE、TTL、User/Member/Task/Pool/Node/Binding/Pool Hash、27步完整、10 Capability完整、双Root、Persistence Hash、Claimant、Evidence唯一消费、Claim幂等。否则SQLSTATE 45000拒绝。

Legacy历史行允许Evidence ID为空；Trigger只约束Migration生效后的新INSERT，不回填历史。

## 19. Evidence Consumption

一个Evidence只能消费一次，只能用于其固定Task和Candidate User。`workflow_task_claim.eligibility_evidence_id`唯一键是数据库单赢家门禁；成功Claim再追加CONSUMED事件。

Evidence不可跨Task、跨Candidate、跨Pool或跨attempt复用。Claim失败不算消费，可在TTL内用同一Claim Request精确重试，但一旦已有Claim引用即不可再用。

## 20. Failed Claim Evidence

- INELIGIBLE/INDETERMINATE：保留主Evidence、实际执行的Validator/Capability及REJECTED事件；不创建Claim。
- ELIGIBLE但CAS/并发/审计失败：保留Evidence，追加`CLAIM_NOT_COMMITTED`事件和稳定原因；不得写成功Claim Audit。
- Fake Evidence：只能存在于测试Schema/回滚事务或明确`evidence_source=FAKE_TEST`的非生产隔离环境；生产Claim Trigger必须拒绝FAKE_TEST。

## 21. Immutability

主Evidence、Validator、Capability、Event均INSERT-only，数据库Trigger禁止UPDATE/DELETE。主表`deleted=0, delete_token=0, version=0`固定；归档不得通过逻辑删除伪装，应采用受治理的只读归档副本和Hash清单。

生命周期不原位更新主Evidence，由Event Hash链表达。

## 22. Claim Binding

| 方案 | 评价 |
|---|---|
| Claim增加Evidence ID | 写Claim时即可强门禁；单向FK，无环；推荐 |
| Evidence增加Claim ID | Evidence先生成时Claim不存在，需UPDATE，违反不可变；拒绝 |
| 独立Binding表 | 审计好，但Claim可能在Binding前短暂存在，单独无法保证P0；不单独采用 |

推荐“Claim单向Evidence FK + Evidence Event”。Claim行本身是权威绑定，CONSUMED Event提供append-only生命周期证据，不创建循环FK。

## 23. Transaction Order

未来执行分两类事务：

```text
事务A（Prepare后）
Insert Evidence → Validator → Capability → PREPARED/REJECTED Event
→ 重算双Root/Persistence Hash → Commit

事务B（短Claim事务）
固定锁Task→Pool→Member→NodeExecution→Instance
→ Verify本地事实/TTL/Hash/幂等
→ CAS Task/Pool
→ Insert TaskClaim(带Evidence ID，DB Trigger最终门禁)
→ Append Claim Audit + CONSUMED Event
→ Commit
```

远程Directory只允许在事务A之前的Prepare阶段。Claim成功行、成功Audit和CONSUMED Event必须同事务提交。

## 24. Rollback Strategy

推荐保留Attempt Evidence，而不是把事务A与Claim事务合并：

- 审计可解释明确拒绝、CAS竞争及系统失败。
- 避免长事务持锁等待Directory。
- Claim事务B任一步失败整体回滚，不留下Claim/成功Audit/CONSUMED事件。
- 回滚后用独立可靠事务追加`CLAIM_NOT_COMMITTED`事件；若该事件写入失败，进入可靠审计Outbox/告警，不能伪造成成功。

保留Evidence不表示Claim成功，最终结果只能以Claim行和Event判定。

## 25. Concurrency

同Task同Candidate两个Session可各自拥有不同Evidence。双方按固定锁顺序进入事务B：

- 仅一个通过Task/Pool CAS并插入Claim。
- 赢家Evidence被唯一Claim引用并追加CONSUMED。
- 输家事务回滚，Evidence保留并追加CLAIM_NOT_COMMITTED/CONCURRENT_CLAIM。
- 两个Evidence都可审计，但只有一个`CLAIM_COMMITTED`。

同一Evidence并发使用由Claim上`UNIQUE(eligibility_evidence_id)`保证单赢家；不得依赖应用内锁。

## 26. Idempotency

- `eligibility_request_id`：相同Prepare请求只生成一个Aggregate。
- `claim_request_id`：一次业务Claim意图，可因Evidence过期产生新的attempt no。
- `idempotency_key`：沿用Claim的`(task_id, idempotency_key)`唯一契约。

相同request+attempt重试查询原Evidence；相同Claim幂等键若已成功返回原Claim；若Evidence过期，必须显式创建新attempt并保留旧EXPIRED事件，不能覆盖原Aggregate。

## 27. Lifecycle

事件状态冻结：`PREPARED → VERIFIED → CONSUMED`；终态/旁路为`EXPIRED / REJECTED`，并以`CLAIM_COMMITTED / CLAIM_NOT_COMMITTED`表达Claim结果。

事件包含sequence no、event type、reason、claim id可空、event time、previous/event hash。状态机由INSERT Trigger校验；禁止自动升级、降级或重新激活过期Evidence。

## 28. Legacy/DIRECT

- USER+DIRECT、SINGLE_NODE_LEGACY、历史USER Multi-Node不创建ROLE Eligibility Evidence。
- 历史Claim不回填、不补造、不推断Evidence。
- 新增Claim列保持nullable；数据库门禁基于未来ROLE_DIRECTORY_V1 Pool/Binding事实触发。
- DIRECT回归必须证明新表和Trigger不改变现有行为。

## 29. Runtime Boundary

Persistence Ready不等于Runtime Ready。V2.6.16即使未来通过，也只表示可存证；不得据此启用ROLE_DIRECTORY_V1、创建ROLE Task/Pool或执行Claim。当前继续`ROLE_REALTIME_ELIGIBILITY_NON_EXECUTABLE / ROLE_RUNTIME_DISABLED`。

## 30. Directory Resource Boundary

`DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED`继续有效。V2.6.16可使用Fake契约验证DDL，但Fake记录必须隔离且不能作为生产审计。真实Directory Evidence验收必须等待WF5.20.2资源到位。

## 31. V2.6.16 Plan

需要新增`V2.6.16__create_role_realtime_eligibility_evidence.sql`，本Sprint只冻结规划，不创建SQL。

1. Guard：校验V2.6.15、Claim/Candidate/Binding父结构和历史兼容。
2. 创建主Eligibility Evidence。
3. 创建Validator Evidence。
4. 创建Capability Evidence。
5. 创建Lifecycle Event。
6. Claim新增nullable Evidence引用/Contract Version。
7. 建立唯一键、复合FK、CHECK。
8. 建立append-only与Claim最终门禁Trigger。
9. Final Assertion校验对象数量、约束和Trigger。

Migration不得回填历史Evidence，不得repair历史Claim，不得修改V2.6.15。

## 32. Guard

永久DDL前检查：

- 目标表/列/Trigger不存在，避免半实施重跑。
- V2.6.7/V2.6.8 Claim父键、Candidate复合Owner键存在。
- V2.6.5 Binding Set/Resolver/Node Binding结构存在。
- V2.6.15四类Admission对象及关键Hash字段存在。
- 所有父Hash字段为ascii/ascii_bin或显式二进制比较。
- 历史Claim满足既有约束；允许其Evidence ID为空。

发现异常立即失败；禁止自动补数据、删除、推断或repair。

## 33. Strong Ownership

强归属链：

```text
WorkflowInstance
  → NodeExecution
  → Task
  → CandidatePool
  → CandidateMember
  → RealtimeEligibilityEvidence
  → TaskClaim
```

主Evidence建立Pool/Member/Task/Node/Instance复合FK和Binding复合FK。Claim再通过包含相同冗余Owner字段的复合FK引用Evidence。任何跨Instance、Task、Pool、Member、User、Node或Binding拼接均由数据库拒绝。

## 34. Capacity

设每日Eligibility Attempt为D：主表D；Validator最多27D；Capability最多10D；事件通常2D至4D，总量约40D至42D行/日，ELIGIBLE路径写放大最高。

索引优先服务`task_id/candidate_user_id/verified_at`、`claim_request_id`、`decision/expires_at`、Evidence子表Owner、Claim Evidence唯一键；避免为reason文本建索引。按真实D、审计法规、查询P95及存储成本制定分区/归档，本文不预设生产保留期限。

归档必须保持主/子/Event/Claim引用整体、Root Hash与校验清单，不能只删子表。

## 35. Audit Queries

设计可回答：

1. 通过主Decision与27步解释为何可Claim或被拒绝。
2. 通过Member/Pool解释Candidate历史资格。
3. 通过Capability/Directory字段解释Claim时实时资格。
4. 对比candidate/claim Revision。
5. 查询DataScope、双SoD、Flag/Canary/KillSwitch结果。
6. 查询verified/expires及提交时是否有效。
7. 通过Claim Evidence ID和Event判断Claim是否成功。
8. 通过Claim/Task Action Log回答最终审批人；Eligibility不承担审批行为记录。

## 36. Failure Matrix

| 场景 | 数据库/事务结果 | 审计结果 |
|---|---|---|
| Evidence缺失 | ROLE Claim INSERT拒绝 | FAILED/NOT_COMMITTED |
| 27步不完整 | ELIGIBLE Claim拒绝 | Validator incomplete |
| INELIGIBLE/INDETERMINATE | 不允许Claim | REJECTED |
| TTL过期 | Trigger/Verify拒绝 | EXPIRED |
| Candidate/Task/Pool不一致 | 复合FK/Trigger拒绝 | OWNERSHIP_MISMATCH |
| Binding/Revision漂移 | Verify/Trigger拒绝 | HASH/FENCE_MISMATCH |
| Validator/Capability Root错误 | Trigger拒绝 | ROOT_MISMATCH |
| Persistence Hash错误 | Trigger拒绝 | PERSISTENCE_HASH_MISMATCH |
| Evidence重复消费 | Claim唯一键拒绝 | ALREADY_CONSUMED |
| Claim CAS失败 | Claim事务回滚 | CLAIM_NOT_COMMITTED |
| 双Session | 一成功一失败 | 两Evidence保留，仅一CONSUMED |
| Evidence写入失败 | 事务A整体回滚 | 无伪造部分Aggregate |
| Claim Audit失败 | 事务B整体回滚 | 无Claim成功事实 |
| DIRECT/Legacy | 不要求Evidence | 原链路不变 |

## 37. Acceptance Matrix

| 类别 | 后续必须验证 |
|---|---|
| A Domain | Aggregate、Decision、Event状态机、Canonical |
| B Persistence Contract | insert/query-only、完整映射、事务边界 |
| C Migration Contract | 文件名、Guard顺序、对象数量、历史SHA |
| D/E Fresh/Upgrade | Fresh至V2.6.16、V2.6.15升级、双路径Fingerprint |
| F Guard | 脏父结构/半对象失败且无永久残留 |
| G/H FK/CHECK | 归属、状态、时间、Hash、计数负向测试 |
| I Append-only | 主/子/Event UPDATE/DELETE拒绝 |
| J TTL | 边界毫秒、过期、不可延长 |
| K/L Hash/Root | Canonical稳定、任一字段漂移、27/10完整性 |
| M Claim Binding | ROLE必绑、Legacy可空、Evidence单消费 |
| N/O Transaction/Concurrency | 回滚无成功残留、双Session单赢家 |
| P Idempotency | Prepare与Claim重复请求均返回原事实 |
| Q/R DIRECT/Legacy | 零调用、零回填、原行为不变 |
| S Dependency Scan | 无真实Directory/Secret/Investment/Runtime Enable依赖 |

## 38. NO-GO

附件列出的12项P0均已冻结：Claim采用单向Evidence引用；失败Evidence保留；TTL由Verify+INSERT Trigger；ELIGIBLE要求27步和10 Capability完整；双Root算法稳定排序；Claim最终门禁由数据库Trigger/复合FK/唯一消费联合保证；并发单赢家；Legacy只读兼容；强归属采用复合FK；V2.6.16确定需要。

若实施时无法以MySQL 8.4验证Root重算、Trigger原子性或Legacy识别，必须`NO-GO`，不得用应用校验替代数据库P0门禁。

## 39. Implementation Gate

V2.6.16实施前必须同时具备：

- 本文41项契约冻结且评审通过。
- SQL字符集/Collation显式、Canonical测试向量冻结。
- Claim ROLE识别规则以Pool/Binding数据库事实为准。
- 事务A/B及失败事件可靠写入方案可测试。
- Fresh/Upgrade/Guard/双Session脚本准备完成。
- V2.6.15及37项历史SHA再次确认。

当前结论：`V2.6.16_IMPLEMENTATION_READY`仅表示设计输入完备，不授权创建SQL、接入Claim或启用Runtime。

## 40. Risks

- MySQL Trigger重算27+10 Root会增加Claim INSERT成本，必须以真实容量压测。
- 事务B失败后的NOT_COMMITTED事件是独立可靠写，存在审计补偿窗口，需要Outbox/告警设计。
- 数据库无法主动感知外部Directory在TTL内发布的新Revision；需Directory契约和保守TTL共同治理。
- Claim表增加复合FK/Trigger会影响高并发路径，必须验证锁顺序、1213/1205和执行计划。
- Legacy与未来ROLE路径识别若仅依赖调用参数可能被绕过，必须由Pool/Binding事实驱动。
- 归档会跨五类对象和Claim引用，实施前需单独制定不可破坏Hash链的归档方案。
- Fake测试只能证明结构契约，不能证明真实Directory Evidence可信。

## 41. Next Step

下一步仅允许在独立Sprint依据本文实现V2.6.16候选及Persistence Contract测试；创建后仍应保持`CANDIDATE / NOT_EXECUTED`，再单独执行真实MySQL/Flyway验收。

在Directory资源未交付前，也可选择等待并重新执行WF5.20.2。不得自动进入真实ROLE Claim Runtime、Investment集成或Runtime启用。

## 冻结结论

- `ROLE_REALTIME_ELIGIBILITY_EVIDENCE_PERSISTENCE_DESIGN_READY`
- `V2.6.16_IMPLEMENTATION_READY`
- `ROLE_REALTIME_ELIGIBILITY_NON_EXECUTABLE`
- `DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED`
- `ROLE_DIRECTORY_V1_PREPARED_NON_EXECUTABLE`
- `ROLE_RUNTIME_DISABLED`
