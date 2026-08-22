# Workflow ROLE Runtime Binding Persistence Governance Design

> Sprint：2-3.7-WF5.13
> 设计状态：`ROLE_RUNTIME_BINDING_PERSISTENCE_DESIGN_READY`
> 运行状态：`ROLE_RUNTIME_DISABLED`
> 数据库基线：V2.6.13 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`

## 1. 对象模型

### 1.1 设计目标

本设计只定义以下治理转换的持久化边界：

```text
RuntimeBindingCandidate
        ↓
RuntimeBindingSnapshot
```

该转换保存“某个经批准的 ROLE Binding 候选已经形成不可变、可追溯的持久化事实”，不表示 ROLE Runtime 已启用，也不产生任何流程执行对象。

核心不变量：

1. `RuntimeBindingSnapshot` 不是 `WorkflowInstance`。
2. `RuntimeBindingSnapshot` 不是 `NodeExecution` 或 `Task`。
3. `RuntimeBindingSnapshot` 不是 `CandidatePool`。
4. `RuntimeBindingSnapshot` 不是 `Claim`。
5. `ACTIVE Snapshot != ROLE_RUNTIME_ENABLED`。
6. Snapshot 不包含候选成员，不调用 Role Directory，不持有真实用户目录明细。

### 1.2 RuntimeBindingSnapshot

职责：冻结一个 `PROMOTED` RuntimeBindingCandidate 的持久化事实，供未来独立的 Runtime Materialization Gate 读取。

建议字段：

| 字段 | 含义 |
| --- | --- |
| `snapshotId` | 稳定业务键，不使用数据库 ID 作为外部契约 |
| `activationId` | V2.6.13 Activation 稳定业务键 |
| `promotionId` | WF5.12 Promotion 稳定业务键 |
| `promotionHash` | `ROLE_RUNTIME_BINDING_PROMOTION_CANONICAL_V1` 结果 |
| `resolverCode` | 冻结 Resolver Code |
| `resolverVersion` | 冻结 Resolver Version |
| `resolverContractHash` | 冻结 Resolver Contract Hash |
| `bindingHash` | 获准 Binding Hash |
| `candidateHash` | 获准 Candidate Rule Hash，不是成员 Hash |
| `directoryContractHash` | 获准 Directory Contract Hash |
| `directoryRevision` | 获准目录水位 |
| `businessScope` | 获准业务范围 |
| `effectiveFrom/effectiveUntil` | Snapshot 使用窗口 |
| `activationEvidenceHash` | Activation Evidence 权威摘要 |
| `promotionEvidenceHash` | Promotion Evidence 权威摘要 |
| `snapshotHash` | 本 Snapshot 的 canonical hash |
| `canonicalVersion` | `ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1` |
| `createdAt/createdBy` | 审计信息，不进入 Snapshot Hash |

Snapshot 是 insert-only 对象。构造后所有业务字段、Hash、范围和时间窗口均不可修改。

### 1.3 RuntimeBindingEvidence

职责：保存 Snapshot 的最小证据引用，不复制审批正文、组织目录或人员明细。

建议内容：

- Activation Request 引用：`activationId`、`activationRequestId`；
- Approval 引用：按 approver type 排序的 `approvalId + decisionHash`；
- Activation Evidence 引用：按 evidence type 排序的 `evidenceId + evidenceHash`；
- Promotion 引用：`promotionId + promotionHash + promotionEvidenceHash`；
- Resolver、Binding、Candidate、Directory Hash；
- `evidenceSetHash` 与 canonical version。

Evidence 只能追加，不能更新或删除。数据库只保存稳定 ID 和 Hash，不保存敏感意见正文、Token、用户列表或 Directory 完整响应。

### 1.4 RuntimeBindingPromotionReference

职责：把 Snapshot 精确关联到唯一 Promotion Decision，而不是根据 Hash 模糊查找。

建议字段：

- `promotionId`；
- `promotionDecisionId`；
- `promotionStatus = PROMOTED`；
- `promotionHash`；
- `promotionEvidenceHash`；
- `activationId`、`activationHash`；
- `requestedBy`、`promotedAt`；
- `idempotencyKey`、`requestHash`。

Promotion Reference 必须能证明：相同 Promotion 只产生一个 Snapshot；相同幂等键但不同 Request Hash 必须拒绝。

### 1.5 RuntimeBindingLifecycle

职责：以 append-only 事件表达 Snapshot 生命周期，禁止通过 UPDATE 修改当前状态。

建议字段：

- `snapshotId`；
- `sequenceNo`；
- `fromStatus`、`toStatus`；
- `reasonCode`；
- `sourceEvidenceHash`；
- `occurredAt`、`operatorId`；
- `eventHash`、`previousEventHash`；
- `canonicalVersion`。

当前状态由最大连续 `sequenceNo` 的有效事件派生，不在 Snapshot 主记录原位维护。

### 1.6 RuntimeBindingIntegrityPolicy

纯 Domain Policy，输入 Candidate、Activation Evidence、Promotion Decision/Reference、Evidence Set、Lifecycle 和评估时间，输出：

- `VALID`：允许追加 `VALIDATED` 或 `ACTIVE` 事件；
- `BLOCKED`：证据缺失、Hash 漂移、时间无效或来源不一致；
- `REVOKED`：上游批准已被追加撤销事实。

Policy 禁止访问 Spring、MyBatis、Entity、当前 Directory、Task 或 Candidate Pool。Application 层负责在同一只读快照中装载权威事实，Policy 负责确定性校验。

## 2. 持久化边界

### 2.1 必须持久化

| 对象 | 原因 | 写入策略 |
| --- | --- | --- |
| Promotion Decision/Reference | WF5.12 当前内存结果无法跨进程恢复，是 Snapshot 来源权威 | insert-only |
| RuntimeBindingSnapshot | 冻结 Candidate 的持久化事实 | insert-only |
| RuntimeBindingEvidence | 支持来源追溯及 Hash 复核 | append-only |
| Lifecycle Event | 表达 CREATED/VALIDATED/ACTIVE/BLOCKED/REVOKED，不更新主表 | append-only |

### 2.2 不持久化

- Role Directory 完整响应；
- 角色成员、人员、岗位、组织目录副本；
- Candidate Pool 或 Candidate Member；
- Task assignee、Claim、审批动作；
- Resolver 可执行实例或 Registry 状态；
- Investment 业务内容；
- 推断出的历史 ROLE 归属。

### 2.3 来源可追溯

Snapshot 创建事务必须同时证明：

```text
V2.6.13 Activation Request(PERSISTED)
  └─ Approval + Evidence
       └─ Promotion Decision(PROMOTED)
            └─ RuntimeBindingCandidate
                 └─ RuntimeBindingSnapshot(CREATED)
```

不得只凭 Promotion Hash 创建 Snapshot。必须使用稳定外键或完整复合所有权键关联 Activation 和 Promotion，并在 INSERT Guard 中比较 Activation、Contract、Binding、Candidate、Directory Revision 与 Evidence Hash。

### 2.4 不可变与历史隔离

- Snapshot、Promotion Reference、Evidence、Lifecycle Event 全部禁止 UPDATE/DELETE；
- Role、组织、人员或 Directory Revision 后续变化不修改历史 Snapshot；
- Snapshot 失效通过追加 `REVOKED` 事件表达，不删除、不覆盖；
- 规则变更必须产生新 Activation、新 Promotion 和新 Snapshot；
- 禁止自动刷新、自动升级 Resolver、自动降级或 EXPLICIT_USER fallback；
- 禁止把新 Snapshot 回填到已存在 Workflow Instance。

## 3. 生命周期

### 3.1 状态机

```text
CREATED → VALIDATED → ACTIVE
    |          |         |
    └──────→ BLOCKED     └──────→ REVOKED
               |
               └───────────────（终止；修复后创建新Snapshot）
```

规则：

- `CREATED`：Candidate、Promotion Reference 和初始 Evidence 已原子写入，但尚未完成完整复核。
- `VALIDATED`：`RuntimeBindingIntegrityPolicy` 的全部检查通过。
- `ACTIVE`：该 Snapshot 成为某个 Promotion 的唯一权威持久化快照，可供未来物化门禁查询。
- `BLOCKED`：完整性、契约、时间或权限证据失败；禁止原位修复，必须产生新的 Promotion/Snapshot。
- `REVOKED`：Activation、Promotion 或安全批准被追加撤销事实；历史仍保留，未来消费者必须停止使用。

`ACTIVE` 只表示“当前权威 Snapshot”，不表示：

- `ROLE_DIRECTORY_V1 = ACTIVE`；
- ROLE Runtime 已启用；
- 允许创建 Instance、Task、Candidate Pool 或 Claim。

### 3.2 状态转换约束

- 每个 Snapshot 的事件序号从 1 连续递增；
- `CREATED` 必须是第一条事件，`previous_event_hash` 为空；
- 后续事件必须引用上一事件 Hash；
- 同一 `(snapshot_id, sequence_no)` 唯一；
- 同一 Snapshot 只能出现一次 ACTIVE；
- BLOCKED/REVOKED 后禁止追加非终态事件；
- 所有状态转换由 Domain Policy 和数据库 INSERT Trigger 双重校验；
- 不提供管理员直接修改状态接口。

## 4. 完整性约束

固定校验顺序：

1. **Activation 归属**：Activation Request 存在、状态 PERSISTED、未撤销。
2. **Promotion 归属**：Promotion Decision 存在、状态 PROMOTED、未撤销。
3. **Activation Hash**：Snapshot、Promotion、V2.6.13 Request/Approval/Evidence 一致。
4. **Promotion Hash**：按 `ROLE_RUNTIME_BINDING_PROMOTION_CANONICAL_V1` 重算一致。
5. **Resolver Contract Hash**：Code、Version、Contract Hash 在 Activation、Promotion、Candidate、Snapshot 间一致。
6. **Binding Hash**：Activation、Promotion、Candidate、Snapshot 一致。
7. **Candidate Hash**：Activation、Promotion、Candidate、Snapshot 一致；不解析成员。
8. **Directory Revision**：Revision、Directory Contract Hash 与冻结 Evidence 一致。
9. **Effective Scope**：Business Scope、effectiveFrom/effectiveUntil 一致且处于有效窗口。
10. **Canonical Hash**：重算 `ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1` 与 stored snapshotHash 一致。
11. **Lifecycle**：事件链连续、Hash 链完整、状态转换合法。
12. **Runtime 隔离**：Resolver 仍为 PREPARED/non-executable 或 Runtime 开关为 DISABLED 时，禁止任何物化消费者继续运行。

任一步失败均 fail closed。禁止自动补齐外键、修正 Hash、更新 Directory Revision 或猜测历史来源。

## 5. Hash 治理

### 5.1 Canonical 版本

新增设计版本：`ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1`。

### 5.2 Snapshot Hash 输入

必须包含：

- snapshot stable key；
- activationId、activationHash、activationEvidenceHash；
- promotionId、promotionHash、promotionEvidenceHash；
- resolverCode、resolverVersion、resolverContractHash；
- bindingHash、candidateHash；
- directoryContractHash、directoryRevision；
- businessScope、effectiveFrom、effectiveUntil；
- evidenceSetHash；
- canonicalVersion。

### 5.3 排除字段

禁止包含：

- 数据库自增 ID；
- created_time、updated_time、created_by、updated_by；
- optimistic-lock version、deleted、delete_token；
- 当前 Directory 数据或成员顺序；
- 当前 Resolver Registry 状态；
- 当前系统时间、线程、Trace、连接信息；
- Task、Candidate Pool、Claim 或 Investment 数据。

### 5.4 规范

- UTF-8 固定字段顺序和长度前缀；
- 时间使用 UTC、ISO-8601、毫秒精度；
- 集合按稳定业务键排序；
- Hash 使用 SHA-256、64 位小写 hex、`CHARACTER SET ascii COLLATE ascii_bin`；
- Resolver Code/Version 使用 case-sensitive 存储和比较；
- 任一关键字段变化必须得到新 Snapshot Hash，不能覆盖旧 Snapshot。

### 5.5 Evidence/Event Hash

- `evidenceSetHash`：对按 `evidence_type + stable_reference` 排序后的证据 Hash 集合计算；
- `eventHash`：覆盖 snapshotId、sequenceNo、from/to status、reason、sourceEvidenceHash、previousEventHash、occurredAt；
- Event Hash 链用于检测缺失、插入、乱序或替换的生命周期事件。

## 6. Repository 设计

### 6.1 RuntimeBindingSnapshotRepository

只允许：

```text
insert(snapshot)
findBySnapshotId(snapshotId)
findActiveByPromotionId(promotionId)
findByActivationId(activationId)
```

禁止 update、delete、save-or-update 和自动 upsert。

### 6.2 RuntimeBindingEvidenceRepository

只允许：

```text
insertAll(snapshotId, evidence[])
findBySnapshotId(snapshotId)
```

同一 evidence type/stable reference 不得重复。

### 6.3 RuntimeBindingLifecycleRepository

只允许：

```text
append(event, expectedPreviousSequence, expectedPreviousHash)
findLatest(snapshotId)
list(snapshotId)
```

并发追加使用固定锁顺序、当前事件行锁、序号唯一约束和 previous hash CAS。一个事务内完成 Snapshot、Evidence 与 CREATED Event 的首次写入；完整性校验后另一个事务只追加 VALIDATED/ACTIVE 或 BLOCKED 事件。

### 6.4 Promotion Reference 查询

Promotion Reference 必须通过稳定 promotionId 和 promotionHash 精确查询，不允许“取最新 Promotion”。同一 activationId 可以有多次 Promotion，但只有明确引用的 PROMOTED Decision 能产生对应 Snapshot。

### 6.5 审计边界

Audit append-only，至少记录：

- 谁请求持久化；
- 使用哪个 Activation/Promotion；
- 每项完整性检查结果；
- 当前 Canonical Version；
- 失败 Reason Code；
- 生命周期事件 Hash 链。

审计权限不等于 Snapshot 激活权，更不等于 Runtime Enable 或任务审批权。

## 7. 数据库评估

### 7.1 现有表复用结论

现有 `workflow_role_runtime_binding_snapshot` 不能用于本阶段的 Candidate 持久化：

- 它强制要求 `instance_id`、`binding_set_id`、`resolver_binding_id`、`node_resolver_binding_id`；
- 外键要求 Workflow Instance、Resolver Binding Set 和 Node Binding 已存在；
- 它表达的是“实例创建后的节点运行证据”，不是“实例创建前的 Promotion Candidate 快照”；
- 为复用该表而提前创建或伪造 Instance/Binding 会违反本 Sprint 禁止范围及来源真实性原则。

因此保留既有表和 V2.6.9–V2.6.12 完全不变。未来 Runtime 物化成功后，可由独立 Migration 在后置运行快照上增加对前置 Snapshot 的引用，但不得在 WF5.13 实施。

### 7.2 V2.6.14 决策

**需要 V2.6.14** 才能把 WF5.12 的内存 Candidate 安全地变为跨进程、可恢复的持久化事实。本 Sprint 只冻结方案，不创建 SQL、不执行 Migration。

建议文件名：

`V2.6.14__persist_role_runtime_binding_promotion_snapshot.sql`

建议新增三张前置治理表：

1. `workflow_role_binding_promotion`
2. `workflow_role_binding_candidate_snapshot`
3. `workflow_role_binding_snapshot_event`

Evidence 最小引用优先内嵌于 Snapshot；若单个 Activation 的证据引用数量不是固定集合，则拆分第四张 `workflow_role_binding_snapshot_evidence`。最终 SQL 设计前必须以 V2.6.13 实际 Evidence 类型数量与未来扩展需求做决策，禁止用 JSON 绕过强关联。

### 7.3 表结构建议

#### workflow_role_binding_promotion

| 字段组 | 建议字段 |
| --- | --- |
| 身份 | `id`, `promotion_id`, `idempotency_key`, `request_hash` |
| Activation | `activation_id`, `activation_hash`, `activation_evidence_hash` |
| Resolver | `resolver_code`, `resolver_version`, `resolver_contract_hash` |
| 规则 | `binding_hash`, `candidate_hash`, `directory_contract_hash`, `directory_revision` |
| 范围 | `business_scope`, `effective_from`, `effective_until` |
| Promotion | `promotion_hash`, `promotion_evidence_hash`, `status`, `promoted_at`, `promoted_by` |
| 治理 | `canonical_version`, 标准审计字段、`deleted`, `delete_token`, `version` |

状态只允许 `PROMOTED / BLOCKED / REJECTED / REVOKED`，但记录本身 append-only；撤销优先通过 Lifecycle Event 表达，不原位更新 Promotion。

#### workflow_role_binding_candidate_snapshot

| 字段组 | 建议字段 |
| --- | --- |
| 身份 | `id`, `snapshot_id`, `promotion_id`, `activation_id` |
| Hash | `activation_hash`, `promotion_hash`, `resolver_contract_hash`, `binding_hash`, `candidate_hash`, `directory_contract_hash`, `activation_evidence_hash`, `promotion_evidence_hash`, `evidence_set_hash`, `snapshot_hash` |
| Resolver | `resolver_code`, `resolver_version` |
| 范围 | `directory_revision`, `business_scope`, `effective_from`, `effective_until` |
| 治理 | `canonical_version`, 标准审计字段、`deleted=0`, `delete_token=0`, `version=0` |

Snapshot 表不保存可变 status；当前状态由 Event 表派生，避免违反 insert-only。

#### workflow_role_binding_snapshot_event

建议字段：`id`, `snapshot_id`, `sequence_no`, `from_status`, `to_status`, `reason_code`, `source_evidence_hash`, `previous_event_hash`, `event_hash`, `occurred_at`, `operator_id`, `canonical_version` 及标准 append-only 审计字段。

### 7.4 索引与唯一约束

建议至少包含：

- Promotion：`UNIQUE(promotion_id)`；
- Promotion 幂等：`UNIQUE(idempotency_key, request_hash)`，并由 Trigger 拒绝同 key 不同 hash；
- Snapshot：`UNIQUE(snapshot_id)`、`UNIQUE(promotion_id)`、`UNIQUE(snapshot_hash)`；
- Snapshot 所有权键：`UNIQUE(id, snapshot_id, promotion_id, activation_id)`；
- Event：`UNIQUE(snapshot_id, sequence_no)`、`UNIQUE(event_hash)`；
- 查询索引：Activation、Resolver Code/Version、Promotion status、effective window、Lifecycle status/time；
- 所有唯一约束使用稳定业务键，禁止依赖不确定自增 ID 进行业务幂等。

### 7.5 外键

建议：

- Promotion `(activation_id, delete_token)` → V2.6.13 Request 对应唯一键；
- Snapshot `promotion_id` → Promotion 明确 UNIQUE 键；
- Snapshot `(activation_id, delete_token)` → V2.6.13 Request；
- Event `snapshot_id` → Snapshot 明确 UNIQUE 键；
- 如拆分 Evidence：Evidence 使用完整 Snapshot 所有权复合外键；
- 所有 FK 使用 `ON UPDATE RESTRICT / ON DELETE RESTRICT`；
- 必须先创建父表 UNIQUE 键，再创建 FK，避免重复 MySQL 6125 类问题。

不在 V2.6.14 中关联 Workflow Instance、Task、Candidate Pool 或 Claim。

### 7.6 CHECK 约束

建议覆盖：

- Resolver Code/Version 大写稳定格式，case-sensitive；
- 所有 Hash 为 64 位小写 hex；
- `directory_revision >= 0`；
- `effective_until > effective_from`；
- Promotion 状态、Lifecycle 状态及合法空值组合；
- `canonical_version = ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1`；
- Snapshot `deleted=0 AND delete_token=0 AND version=0`；
- Event sequence 正数；首事件和 previous hash 组合合法；
- business scope、reason、operator 等必填文本不得空白。

### 7.7 Trigger

建议：

- Promotion/Snapshot/Event 的 BEFORE UPDATE、BEFORE DELETE 全部以 1644/45000 拒绝；
- Promotion INSERT Guard 校验 V2.6.13 Activation 状态和 Hash；
- Snapshot INSERT Guard 校验 Promotion= PROMOTED、Activation/Promotion/Contract/Binding/Candidate/Revision 一致并重算 Snapshot Hash；
- Event INSERT Guard 校验连续序号、previous event hash 和状态边；
- Trigger 不调用 Directory、不创建下游对象、不自动修复历史数据；
- 所有字符串比较显式使用 binary/ascii_bin，禁止依赖数据库默认 collation。

### 7.8 Migration Guard 与失败安全

永久 DDL 前检查：

1. 目标表、索引、Trigger 不存在，拒绝 partial install；
2. V2.6.13 Request 所需唯一键存在；
3. 现有 Activation Evidence 无非法 Hash/状态；
4. 数据库字符集和 MySQL 版本满足约束/Trigger 要求。

发现异常立即失败，禁止删除、回填、推断或修正数据。Fresh、V2.6.13 Upgrade 和失败重试路径必须得到相同 Schema Fingerprint；第二次 migrate 必须 no-op。

## 8. Legacy 兼容

### USER + DIRECT

- 完全绕过 ROLE Promotion/Snapshot 链；
- `EXPLICIT_USER_V1`、Task assignee 和 Assignment Snapshot 行为不变；
- 不要求创建 ROLE Snapshot。

### SINGLE_NODE_LEGACY

- 不迁移、不回填、不推断 Activation、Promotion 或 ROLE Snapshot；
- 不根据历史 assignee、权限或角色名称补造来源；
- 继续使用原 Legacy 读取和执行路径。

### 历史 Workflow Instance

- 不修改现有 Instance、Binding Set、Node Binding、Task 或 Candidate Pool；
- 不把已持久化 Candidate Snapshot 绑定到历史 Instance；
- 历史 Task 永不重新执行 Resolver，历史 Candidate Pool 永不刷新；
- 新 Snapshot 只能由 V2.6.13 Activation + WF5.12 PROMOTED Candidate 正向生成。

## 9. 风险清单

| 风险 | 等级 | 控制 |
| --- | --- | --- |
| 误复用 V2.6.9 后置运行快照，迫使提前创建 Instance | P0 | 新建前置候选快照表，保留两类快照明确语义 |
| ACTIVE 被误解为 Runtime Enabled | P0 | 状态只表示 Snapshot 权威性；执行开关独立且当前 DISABLED |
| Promotion 仍在内存导致来源不可恢复 | P0 | V2.6.14 同时持久化 Promotion Reference 和 Snapshot |
| Activation/Promotion/Snapshot Hash 漂移 | P0 | 复合 FK、INSERT Guard、canonical 重算、binary 比较 |
| Snapshot 状态通过 UPDATE 演进 | P0 | 主表无可变状态；生命周期以 append-only Event 表达 |
| Directory 变化覆盖历史证据 | P0 | 冻结 Revision/Hash，不刷新、不存成员明细 |
| 重复或并发创建多个 Snapshot | P0 | Promotion/Snapshot 唯一键、幂等键和固定锁顺序 |
| 撤销未传播给未来消费者 | P0 | REVOKED 事件；物化前强制读取最新连续事件 |
| Evidence 使用 JSON 弱关联 | P1 | 优先固定字段/子表和完整复合 FK，禁止 JSON 代替所有权约束 |
| Trigger 与国产数据库兼容性差异 | P1 | SQL 实施前分别设计 MySQL/目标国产数据库验收矩阵 |
| Repository 暴露 save/update | P0 | Domain Port 只允许 insert/query/append |
| 权限被当作运行资格 | P0 | RBAC、Snapshot ACTIVE、Resolver ACTIVE、Runtime Enable 四层分离 |

## 10. WF5.14 实施边界

如果进入 WF5.14，只允许实现持久化基础设施，不得进入 Runtime：

允许：

- 实现本设计五个纯 Domain 对象；
- 实现 `ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1`；
- 创建 V2.6.14 候选 SQL、Entity、Mapper、insert/query Repository；
- 原子写入 Promotion、Snapshot、Evidence、CREATED Event；
- 实现 Integrity Policy 和 append-only Lifecycle；
- 增加 Fresh/Upgrade 契约测试，但真实 MySQL 验收必须作为独立 Sprint；
- 验证 EXPLICIT_USER 和 Legacy 完整回归。

禁止：

- 修改 V2.6.13 或 V2.6.9–V2.6.12；
- 将 `ROLE_DIRECTORY_V1` 改为 ACTIVE；
- 创建或修改 Workflow Instance；
- 写入现有 `workflow_role_runtime_binding_snapshot`；
- 创建 NodeExecution、Task、Candidate Pool 或 Claim；
- 调用真实 Role Directory；
- 接入 Investment；
- 提供 Runtime Enable、人工改状态或动态刷新 API。

WF5.14 完成态最多为 `ROLE_RUNTIME_BINDING_PERSISTENCE_IMPLEMENTED / ROLE_RUNTIME_DISABLED`。进入任何物化或执行阶段前，仍需独立冻结 Snapshot 到 V2.6.5 Binding Set 的映射、人工 Runtime Enable 门禁、撤销传播和恢复策略。

## 11. 冻结结论

本设计决定：

- RuntimeBindingCandidate 必须先形成独立的前置持久化 Snapshot；
- 现有 V2.6.9 `workflow_role_runtime_binding_snapshot` 是实例后的运行证据，不能在本阶段复用；
- Snapshot、Evidence、Promotion Reference 和 Lifecycle 全部采用 insert/append-only；
- V2.6.14 对正式持久化是必要的，但本 Sprint 仅完成方案，不创建 SQL；
- USER + DIRECT、Legacy 和所有历史 Instance 保持不变；
- Snapshot ACTIVE 不启用 Resolver 或 ROLE Runtime。

最终状态：

- `ROLE_RUNTIME_BINDING_PERSISTENCE_DESIGN_READY`
- `ROLE_RUNTIME_DISABLED`

本 Sprint 未修改代码、测试、Registry 或 Migration，未创建 Task、Candidate Pool、Claim，未接入 Role Directory、Investment，也未进入 ROLE Runtime 执行。
