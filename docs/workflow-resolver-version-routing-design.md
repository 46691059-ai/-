# Workflow Resolver Version Routing设计

> Sprint：2-3.7-WF3.6.3
> 基线：V2.6.2、WF3.6.1、WF3.6.2
> 状态：DESIGN_FROZEN / NOT_IMPLEMENTED
> 范围：Resolver多版本治理；不实现ROLE/POSITION/ORG，不修改Investment。

## 1. 领域模型

### 1.1 ResolverCode

Resolver实现族的稳定业务键，例如`EXPLICIT_USER`。Code一经进入生产快照不得改名或复用；重构Java类名不影响ResolverCode。

### 1.2 ResolverVersion

Resolver行为契约版本，例如`EXPLICIT_USER_V1`。版本标识不可覆盖发布：任何会改变候选集合、排序、过滤、规则解释或审计内容的变化必须创建新版本。

禁止使用`latest`、数据库自增ID或构建时间作为业务版本。相同Code + Version必须对应确定性一致的解析行为。

### 1.3 ResolverStatus

```text
ACTIVE -> DEPRECATED -> RETIRED
   \---------------------> RETIRED  （仅限紧急停用，需审计）
```

| 状态 | 新实例选择 | 已绑定实例继续使用 | 历史Task读取 | 含义 |
| --- | --- | --- | --- | --- |
| ACTIVE | 允许 | 允许 | 允许 | 当前可用于新绑定的稳定版本 |
| DEPRECATED | 禁止默认选择；允许显式兼容绑定 | 允许 | 允许 | 停止扩散，等待存量实例结束 |
| RETIRED | 禁止 | 默认禁止执行新的解析；应走人工治理 | 允许只读 | 完全退出新解析，但历史证据永久保留 |

RETIRED不等于删除实现。只要仍存在可能重试或恢复的存量实例，部署包必须保留对应实现，或先完成经审计的实例迁移。

状态不可逆，禁止`RETIRED -> ACTIVE`。需要恢复时创建新ResolverVersion。

### 1.4 EffectiveTime

版本路由使用半开时间窗：`effectiveFrom <= selectionTime < effectiveTo`。`effectiveTo`为空表示未设结束时间。

- 时间统一按UTC持久化，精度为毫秒；API展示时再转换时区。
- 同一ResolverCode在任一时刻最多一个ACTIVE默认版本。
- 时间窗决定能否建立新绑定，不影响已冻结绑定和历史Snapshot。
- MySQL难以仅靠CHECK阻止时间窗重叠，必须采用事务锁、应用校验和发布前一致性检查。

## 2. 版本状态机

### 2.1 发布

1. 新实现先随应用静态注册，但不得立即参与路由；
2. 校验Code、Version、行为契约Hash和数据库元数据一致；
3. 在测试环境完成确定性、兼容和回放测试；
4. 将新版本设为ACTIVE并设置生效时间；
5. 原ACTIVE版本原子变更为DEPRECATED；
6. 记录操作者、原因、变更前后状态和TraceId。

状态切换与默认路由变更必须在同一数据库事务中完成。不能出现两个默认ACTIVE版本，也不能出现无ACTIVE版本却仍允许创建新实例的窗口。

### 2.2 废弃与退休

- DEPRECATED版本不再分配给新实例，但已绑定实例继续使用。
- 只有存量实例、待创建节点任务、重试和补偿均清零后，才允许常规退休。
- 安全事件可紧急退休；受影响实例转为阻断状态并进入人工处置，禁止静默切换到其他版本。
- 退休不删除Registry记录、Snapshot或审计事件。

## 3. Registry模型

### 3.1 当前模型

WF3.6.2为Static Registry：

```text
Spring Bean
 -> ResolverRegistry
 -> ResolverCode + ResolverVersion + StrategyType
 -> Java Resolver实现
```

它解决实现发现和启动期一致性，但不能表达生效时间、废弃、退休及环境级默认路由。

### 3.2 目标模型：Static + Database Registry

```text
Static Implementation Registry
  Code + Version -> 可执行Java实现
                   \
                    -> VersionRoutingService -> Frozen Binding
                   /
Database Routing Registry
  Code + Version + Status + EffectiveTime + ContractHash
```

职责边界：

- Static Registry决定“当前部署包能否执行该版本”。
- Database Registry决定“该环境是否允许将该版本绑定给新实例”。
- Routing Service只允许选择两者交集。
- 数据库不能动态加载类、脚本或表达式，`implementation_key`只能引用部署包白名单。
- Registry缓存可以存在，但状态切换后必须按版本号失效；缓存不可成为权威来源。

### 3.3 启动校验

应用启动时应检查：

1. 数据库ACTIVE/DEPRECATED且仍有存量绑定的版本均存在静态实现；
2. Code、Version、StrategyType和ContractHash一致；
3. 不存在重复默认ACTIVE版本或重叠生效窗口；
4. `EXPLICIT_USER_V1`始终可执行，直到所有V1实例完成治理；
5. 不一致时阻止Workflow新实例启动，但允许历史Snapshot只读查询。

## 4. 版本选择规则

### 4.1 冻结层级

版本选择只发生一次，优先级如下：

1. 已存在Task：使用Task Assignment Snapshot中的Code + Version，禁止重新选择；
2. 已存在Workflow Instance但尚未创建后续Task：使用Instance Resolver Binding；
3. 新Workflow Instance：依据已发布Workflow Version的Resolver策略建立实例级绑定；
4. 新Workflow Version发布：才允许根据Database Registry的ACTIVE默认版本生成配置绑定。

禁止在每次Task创建时查询“当前最新ACTIVE版本”。否则长流程中前后节点可能使用不同Resolver行为。

### 4.2 新任务

新实例启动时，为所有需要Assignment的节点创建`InstanceResolverBinding`：

- workflowVersionId、nodeId；
- strategyType；
- resolverCode、resolverVersion；
- ruleVersion；
- bindingSource；
- contractHash；
- selectedAt。

Task创建时只读取该绑定并验证静态实现仍可用，然后生成CandidatePool和Assignment Snapshot。

当前兼容规则：USER节点若没有显式版本信息，唯一合法绑定为`EXPLICIT_USER / EXPLICIT_USER_V1`。该规则保持现有运行结果不变。

### 4.3 历史任务

- V2.6.2结构化Snapshot按`LEGACY_V262_IMPLICIT`解释为`EXPLICIT_USER_V1`只读证据；不得批量覆盖历史行。
- Legacy Task无Snapshot时保持`LEGACY_UNVERSIONED`，不得伪造版本、解析时间或Hash。
- 查询历史Task不调用Resolver、不访问Database Registry、不重新计算候选。
- 重复请求使用原Snapshot和幂等结果，不执行新路由。

### 4.4 版本不匹配

绑定版本在Static Registry不存在、ContractHash不一致或已被紧急退休时：

- 禁止自动降级或选择其他ACTIVE版本；
- 阻断Task生成，记录明确错误分类；
- 保持当前Instance状态不前移；
- 由管理员按受控迁移流程处理。

## 5. 兼容方案

| 场景 | 使用版本 | 是否重新解析 |
| --- | --- | --- |
| V2.6.2历史Snapshot查询 | 隐式`EXPLICIT_USER_V1`兼容投影 | 否 |
| 无Snapshot的Legacy Task | `LEGACY_UNVERSIONED` | 否 |
| 升级前已启动、已有Instance Binding | 绑定版本 | 仅创建新Task时按原绑定解析 |
| 升级后新实例 | Workflow Version绑定的ACTIVE版本 | 是，首次生成Task时执行 |
| Resolver V2发布后旧Task重试 | 原Snapshot版本 | 否 |
| Resolver V2发布后旧实例进入后续节点 | 原Instance Binding版本 | 是，但仍执行V1 |
| Resolver V2发布后新Workflow Version | 可绑定V2 | 是 |

`EXPLICIT_USER_V1`行为冻结：目标快照、唯一候选、DIRECT选择、审计Reason及现有Task字段含义均不改变。

## 6. 审计方案

### 6.1 必须冻结的字段

- `resolver_code`
- `resolver_version`
- `resolver_status_at_selection`
- `rule_version`
- `binding_source`
- `implementation_contract_hash`
- `rule_snapshot`
- `candidate_snapshot`
- `snapshot_hash`
- `hash_algorithm`
- `selected_at/resolved_at`
- `selected_by/resolved_by`
- `trace_id`

### 6.2 Snapshot Hash

采用规范JSON + SHA-256。Hash输入包括：

```text
resolverCode
resolverVersion
ruleVersion
strategyType
targetType
targetSnapshot
candidateUserIds（稳定排序）
assignmentReason
instanceId/versionId/nodeId/nodeExecutionId/taskId
```

不包含数据库行自增ID、createdTime、updatedTime、remark、展示名称和非确定性审计字段。Hash算法必须独立记录，算法升级创建新版本，禁止覆盖旧Hash。

### 6.3 审计事件

发布、废弃、退休、绑定、解析失败、版本缺失、Hash不一致和人工迁移均写追加式事件。不得记录Token、手机号、身份证号或完整外部目录响应。

## 7. V2.6.3数据库规划

V2.6.3仅为候选规划，本Sprint不创建SQL、不登记Inventory、不执行Migration。

### 7.1 `workflow_assignment_resolver_version`

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| resolver_code | VARCHAR(64) | 稳定Code |
| resolver_version | VARCHAR(64) | 行为版本 |
| strategy_type | VARCHAR(30) | 当前仅USER |
| implementation_key | VARCHAR(100) | 静态实现白名单键 |
| status | VARCHAR(20) | ACTIVE/DEPRECATED/RETIRED |
| effective_from | DATETIME(3) | 生效起点 |
| effective_to | DATETIME(3) NULL | 生效终点 |
| contract_hash | CHAR(64) | 实现契约Hash |
| status_reason | VARCHAR(500) | 非敏感原因 |
| audit fields | - | created/updated、deleted、delete_token、version |

约束与索引：

- 唯一`(resolver_code, resolver_version, delete_token)`；
- 索引`(resolver_code, status, effective_from, effective_to, deleted)`；
- CHECK状态、时间窗、Hash格式、逻辑删除和乐观锁；
- 不允许数据库保存可执行脚本。

### 7.2 `workflow_instance_resolver_binding`

按`instance_id + node_id + strategy_type`冻结Resolver绑定。字段包括Version、RuleVersion、ContractHash、BindingSource、SelectedAt、审计字段、delete_token和乐观锁。

- 唯一活动绑定：`(instance_id, node_id, strategy_type, delete_token)`；
- 外键仅指向Workflow上下文内Instance、Node和Resolver Version；
- 绑定创建后不可更新Code、Version或RuleVersion。

### 7.3 `workflow_task_assignment_snapshot`增量字段

建议新增可空字段：

- `resolver_binding_id`
- `resolver_code`
- `resolver_version`
- `rule_version`
- `snapshot_hash`
- `hash_algorithm`

历史行保持NULL并通过兼容投影读取，禁止根据现有文本批量伪造Hash或版本。新任务写入时强制非空由应用契约保障；待历史治理完成后再评估数据库NOT NULL迁移。

### 7.4 Migration边界

V2.6.3如落地，只允许：

1. 新建Resolver Version与Instance Binding表；
2. 对V2.6.2 Snapshot做向后兼容的可空增量；
3. 初始化唯一`EXPLICIT_USER_V1`元数据；
4. 不修改历史Migration；
5. 不新增ROLE/POSITION/ORG实现或初始化数据；
6. 不修改Investment；
7. Fresh与V2.6.2 Upgrade必须获得一致Schema指纹。

## 8. WF3.7实施边界

WF3.7建议仅实现版本路由基础设施：

1. 扩展Static Registry支持同Code多Version索引；
2. 实现只读Database Registry Repository和VersionRoutingService；
3. 只注册并路由`EXPLICIT_USER_V1`，确保行为完全等价；
4. 实现Instance Resolver Binding冻结与查询；
5. 如确有数据库需要，创建V2.6.3候选但不得在同Sprint提前晋级；
6. 增加时间窗、双ACTIVE、版本缺失、Hash不一致和历史任务不重算测试；
7. 不实现动态代码加载、在线脚本、自动降级或跨版本迁移；
8. 不实现ROLE/POSITION/ORG，不修改Investment。

WF3.7完成前，生产运行仍只允许Static Registry中的`EXPLICIT_USER_V1 + USER + DIRECT`。Database Registry、多版本路由和实例绑定均保持未启用状态。
