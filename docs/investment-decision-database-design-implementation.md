# 投资决策数据库Migration设计与实施报告

项目：县域国企数字化运营治理平台

Sprint：2-3.2

状态：**SQL_IMPLEMENTED_PENDING_MYSQL_VALIDATION**

## 1. 实施结论

本阶段已在正式Flyway扫描目录`database/migration/mysql`新增V2.4.4—V2.4.6三个增量Migration，未修改V2.4.0—V2.4.3及其他历史Migration。

既有结构审计结论：

- `investment_decision`由`07_investment.sql`创建，并已由V2.4.0扩展；
- `investment_decision_node`、`investment_decision_condition`和`investment_decision_condition_action`已由V2.4.0创建；
- 本阶段不重复创建以上四表，只通过`ALTER TABLE`进行兼容扩展；
- 新建`investment_decision_snapshot`、`investment_workflow_binding`和`investment_decision_audit_event`；
- Workflow、Risk和Audit均只保存稳定引用，不创建跨库外键，不读取外部模块数据库表；
- 历史决策允许`current_snapshot_id`为空，新提交必须由应用层绑定冻结快照。

## 2. Migration版本

| 版本 | 文件 | 实施内容 | 当前状态 |
| --- | --- | --- | --- |
| V2.4.4 | `V2.4.4__create_investment_decision_snapshot.sql` | 创建决策快照；补充决策当前快照、risk和audit字段 | 静态校验通过，未连接MySQL执行 |
| V2.4.5 | `V2.4.5__add_investment_workflow_binding.sql` | 创建Workflow绑定；补充决策节点的快照和Workflow引用 | 静态校验通过，未连接MySQL执行 |
| V2.4.6 | `V2.4.6__add_investment_decision_risk_audit.sql` | 补强条件及动作；创建不可变业务审计事件 | 静态校验通过，未连接MySQL执行 |

执行顺序固定为：

```text
V2.4.0—V2.4.3
  -> V2.4.4 决策快照
  -> V2.4.5 Workflow绑定
  -> V2.4.6 风险与审计
```

V2.4.7及更高版本保留给RBAC初始化、约束收紧或后续功能，不在本阶段创建。

## 3. 表设计

### 3.1 `investment_decision`

定位：投资决策聚合根的持久化主表，沿用既有表。

本次新增字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `current_snapshot_id` | BIGINT NULL | 当前提交绑定的冻结快照；历史数据允许为空 |
| `risk_level_snapshot` | VARCHAR(30) NULL | 当前决策风险等级快照 |
| `risk_assessment_ref` | VARCHAR(100) NULL | Risk中心风险评估稳定引用 |
| `risk_gate_result` | VARCHAR(30) NULL | 风险门禁结果 |
| `risk_rule_version` | VARCHAR(50) NULL | 风险规则版本 |
| `risk_snapshot_hash` | VARCHAR(128) NULL | 风险结论快照哈希 |
| `audit_ref` | VARCHAR(100) NULL | Audit中心稳定引用 |

使用复合外键`(id, current_snapshot_id)`引用快照`(decision_id, id)`，数据库层保证当前快照属于当前决策。

### 3.2 `investment_decision_snapshot`

定位：提交Workflow前形成的不可变决策材料快照。

字段分组：

| 分组 | 字段 |
| --- | --- |
| 身份与版本 | `id, decision_id, snapshot_version, snapshot_status, decision_package_version` |
| 冻结业务版本 | `scheme_version_id, feasibility_version_id, due_diligence_package_id` |
| 内容证据 | `scheme_content_hash, feasibility_content_hash, due_diligence_content_hash, snapshot_hash` |
| 决策路线 | `route_code, route_rule_version, route_snapshot_hash, major_decision_applicable, party_pre_study_required, final_decision_body` |
| 风险 | `risk_level_snapshot, risk_assessment_ref, risk_gate_result, risk_rule_version, risk_snapshot_hash` |
| 审计 | `audit_ref, frozen_by, frozen_time`及统一审计字段 |
| 并发及删除 | `deleted, delete_token, version` |

设计约束：

- 同一决策的`snapshot_version`唯一；
- 同一决策的`snapshot_hash`唯一；
- 方案、可研和尽调包使用本库外键锁定稳定版本；
- `frozen_by`关联`sys_user`；
- 快照状态仅允许`FROZEN/SUPERSEDED`；
- 应用层禁止覆盖快照内容；如材料变化，必须新增快照版本。

### 3.3 `investment_decision_node`

定位：冻结决策路线中的业务节点及Workflow结果摘要，沿用V2.4.0表。

新增字段：

- `snapshot_id`：节点所属快照；
- `workflow_binding_id`：所属Workflow尝试；
- `workflow_node_instance_ref`：Workflow节点实例稳定引用；
- `workflow_task_ref`：Workflow任务稳定引用；
- `last_workflow_event_id/workflow_event_sequence`：事件同步证据；
- `opinion_hash`：完整审批意见哈希，正文仍由Workflow权威维护；
- `risk_ref/audit_ref`：风险和审计稳定引用。

历史节点允许快照及Workflow字段为空；新流程由应用层强制完整绑定。

### 3.4 `investment_decision_condition`

定位：附条件批准后形成的整改、复核及关闭任务，沿用V2.4.0表。

新增字段：

- `snapshot_id`：条件形成时的快照；
- `source_risk_ref/risk_level_snapshot`：来源风险和等级快照；
- `waiver_workflow_binding_id`：豁免审批流程绑定；
- `audit_ref`：审计稳定引用。

条件状态受CHECK约束，支持`OPEN、IN_PROGRESS、SUBMITTED、VERIFIED、CLOSED、WAIVED、REJECTED、EXPIRED、CANCELLED`。

### 3.5 `investment_decision_condition_action`

定位：条件任务的追加式动作历史，沿用V2.4.0表。

新增字段：

- `workflow_event_id/workflow_event_sequence`：触发动作的Workflow事件证据；
- `payload_hash`：动作载荷哈希；
- `risk_ref/audit_ref`：风险和审计稳定引用。

`workflow_event_id + delete_token`唯一，防止重复回调生成重复条件动作。

### 3.6 `investment_workflow_binding`

定位：Investment决策提交与外部Workflow实例之间的本地绑定，不是流程引擎表。

| 分组 | 字段 |
| --- | --- |
| 业务身份 | `decision_id, snapshot_id, snapshot_hash, attempt_no` |
| Workflow业务键 | `business_type, business_id, business_key, enterprise_id` |
| 定义与实例 | `workflow_instance_id, definition_key, definition_version` |
| 幂等证据 | `idempotency_key, request_hash` |
| 同步水位 | `workflow_status, last_event_sequence, last_event_id, last_synced_time` |
| 时间与失败 | `started_time, completed_time, failure_code` |
| 风险审计 | `risk_ref, audit_ref, trace_id`及统一审计字段 |
| 并发及删除 | `deleted, delete_token, version` |

关键规则：

- `decision_id + attempt_no`唯一；
- `snapshot_id + attempt_no`唯一；
- `idempotency_key`唯一；
- `workflow_instance_id`在非空时唯一；
- 通过复合外键保证快照属于该决策；
- `enterprise_id`关联`sys_org`，作为企业隔离依据；
- 不对`workflow_instance_id`建立外部数据库外键。

### 3.7 `investment_decision_audit_event`

定位：Investment域内不可变业务审计留痕，不复制Workflow完整审批记录。

包含：

- 决策、快照、节点、条件和Workflow绑定的本地关联；
- `event_type/source_system/source_event_id`；
- 操作主体、组织、前后状态；
- 脱敏摘要、载荷哈希、risk/audit引用和TraceId；
- 业务发生时间及统一审计字段；
- 逻辑删除和乐观锁字段仅为平台结构一致性保留，应用层禁止普通更新、删除审计事实。

## 4. 字段规范

所有新表均包含：

```text
id
create_time / create_by
update_time / update_by
deleted / delete_token
remark
version
```

规范说明：

- `deleted`为逻辑删除标识，`delete_token`解决逻辑删除后的业务唯一键复用；
- `version`为MyBatis Plus乐观锁版本，初始值为0；
- 跨模块对象使用`*_ref`保存稳定引用，不保存外部表主键外键；
- 哈希字段为规范化内容SHA-256表示，应用层需统一前缀和编码；
- 时间统一使用`DATETIME(3)`，跨系统接口使用UTC ISO-8601并在适配层转换；
- 审批正文、完整意见、敏感附件和Workflow任务明细不得复制到Investment表。

## 5. 索引与约束

### 5.1 唯一约束

| 表 | 唯一约束 | 目的 |
| --- | --- | --- |
| 快照 | `decision_id + snapshot_version + delete_token` | 决策内版本唯一 |
| 快照 | `decision_id + snapshot_hash + delete_token` | 防止同内容重复冻结 |
| Workflow绑定 | `decision_id + attempt_no + delete_token` | 每次提交唯一 |
| Workflow绑定 | `idempotency_key + delete_token` | 启动幂等 |
| Workflow绑定 | `workflow_instance_id + delete_token` | 一个外部实例只绑定一次 |
| 条件动作 | `workflow_event_id + delete_token` | 回调去重 |
| 审计事件 | `event_no + delete_token` | 业务事件唯一 |
| 审计事件 | `source_system + source_event_id + delete_token` | 外部事件去重 |

### 5.2 外键

只对同一数据库内权威表建立外键：

- 决策、快照、节点、条件、条件动作和审计事件之间的关联；
- 快照对方案版本、可研版本和尽调包的冻结引用；
- 企业和冻结用户对`sys_org/sys_user`的引用；
- 所有外键采用默认限制删除语义，不级联删除决策事实。

Workflow、Risk和Audit中心引用不建立外键，避免跨库耦合及跨服务事务。

### 5.3 CHECK约束

已增加以下数据库约束：

- 决策业务状态兼容历史V2.4.0值和冻结后的新状态；
- 快照状态、风险门禁和布尔标志；
- Workflow业务类型、流程状态、版本、尝试号和事件序号；
- 节点状态、节点结果和事件序号；
- 条件状态、复核结果和条件动作前后状态；
- 审计事件类型、来源系统、操作主体类型；
- 所有新表逻辑删除值及乐观锁非负。

### 5.4 查询索引

索引覆盖：

- 决策当前快照和风险门禁；
- 快照版本、状态及三类材料版本反查；
- Workflow业务键、状态、企业隔离和事件水位；
- 节点快照、Workflow绑定和节点实例引用；
- 条件负责人、阻断状态、风险及豁免流程；
- 审计事件按决策、快照、Workflow、TraceId和操作主体查询。

## 6. Flyway与静态校验

### 6.1 已完成

- 文件名符合`V<major>.<minor>.<patch>__<description>.sql`；
- V2.4.4、V2.4.5和V2.4.6版本唯一且顺序连续；
- 12个正式SQL与`SHA256SUMS`一一对应；
- 三个新增文件SHA-256已写入资产清单；
- `migration-inventory.yml`已登记为`CANONICAL_PENDING_MYSQL_VALIDATION / NOT_EXECUTED`；
- 新增约束及索引符号没有重复或与历史Migration冲突，最长名称小于MySQL 64字符限制；
- 未出现`DROP、TRUNCATE、DELETE、clean、repair`；
- 未创建Workflow表，未出现跨数据库外键；
- V2.4.0—V2.4.3文件内容未修改。

新增资产校验值：

| 版本 | SHA-256 |
| --- | --- |
| V2.4.4 | `eb827c2a052ce175dc83aab33c7c42acd7aa910d253273c39c9a178fddc622c1` |
| V2.4.5 | `c7fd269066b5d21813d87bab2602c848f3ae70dc5f67b494b608507b17e671dc` |
| V2.4.6 | `3e7b0a6550960d012cf861f00e61248d0ec2ca1d7b59afd91783bf3a0c677f64` |

### 6.2 未执行

当前工作环境未安装Docker、Flyway CLI、MySQL客户端和Java运行时，因此本阶段无法执行数据库连接型`flyway migrate/validate`，也未生成Flyway checksum或Schema指纹。

这不是MySQL真实验收通过的声明。三个资产保持`NOT_EXECUTED`，禁止在真实验收前标记为`EPHEMERAL_MYSQL8_VALIDATED`或生产可发布。

## 7. 回滚策略

MySQL DDL存在隐式提交，且快照、Workflow绑定和审计事件均属于业务事实，不采用自动Down Migration。

### 7.1 发布前

1. 备份目标Schema和关键业务表；
2. 记录Flyway history、Schema指纹及V2.4.3数据基线；
3. 执行历史状态值预检查，确保CHECK约束可建立；
4. 在相同版本MySQL的副本完成升级演练。

### 7.2 尚未产生新业务数据

若迁移失败，在隔离环境确认失败点后可恢复V2.4.3备份，修正内容必须发布更高版本Migration；禁止修改已经发布或执行的V2.4.4—V2.4.6文件。

### 7.3 已产生新业务数据

- 禁止直接删除快照、绑定和审计表；
- 停止新决策提交，保留只读查询；
- 通过更高版本前向Migration修复；
- 若必须回退应用，旧应用必须确认会忽略新增可空字段和新表；
- Workflow已创建的实例按契约执行暂停、撤回或人工处置，不能通过数据库回滚伪造流程结果。

## 8. 真实MySQL验收方案

后续验收必须覆盖两条路径：

1. 空业务库按权威初始化基线后，从V2.0.0 baseline执行至V2.4.6；
2. 已完成V2.4.3的升级库只执行V2.4.4—V2.4.6。

流程：

```text
pre策略检查与SHA-256校验
  -> flyway info
  -> flyway migrate
  -> post严格flyway validate
  -> 第二次migrate确认no-op
  -> information_schema结构及约束核查
  -> Schema指纹
```

重点验证：

- 历史`investment_decision`和节点数据无损；
- 历史记录允许快照及Workflow引用为空；
- 新快照的版本、哈希及材料外键有效；
- 复合外键阻止跨决策绑定快照；
- 重复幂等键、流程实例和Workflow事件被唯一约束拒绝；
- CHECK约束拒绝非法状态；
- Flyway history无失败记录且第二次迁移为no-op。

## 9. 后续开发计划

### Sprint 2-3.2.1：真实MySQL Migration验收

- 执行两路径Flyway迁移；
- 记录Flyway checksum、执行时间、history和Schema指纹；
- 验证约束、索引、历史兼容性及no-op。

### Sprint 2-3.3：持久化模型实现

- 创建快照、Workflow绑定和审计事件Entity/Mapper/Repository Adapter；
- 使用MyBatis Plus逻辑删除及乐观锁；
- 不实现Workflow审批引擎。

### Sprint 2-3.4：Workflow适配器

- 实现已冻结的Command、Query和Event端口；
- 建设Outbox/Inbox及幂等消费者；
- 所有外部引用保持不透明字符串。

### Sprint 2-3.5：决策业务闭环

- 实现快照冻结、流程提交、节点结果同步和附条件批准；
- 接入权限、数据权限、风险门禁和审计；
- 开展重复回调、乱序、撤回和故障补偿测试。

## 10. 剩余风险

1. 三个新增Migration尚未在真实MySQL 8执行，SQL语法、DDL锁时长及历史数据CHECK兼容性仍需实库确认；
2. 当前`investment_decision`来自初始化基线而非V2.4.0创建，目标环境必须先完成权威基线识别；
3. `current_snapshot_id`对历史记录保持可空，后续只能在完成回填审计后通过更高版本收紧；
4. Workflow、Risk、Audit稳定引用的最大长度和字符规范仍需跨团队确认；
5. 审计事件表虽然保留平台统一逻辑删除字段，应用层必须禁止普通删除和覆盖；
6. 国产数据库需要独立适配Migration，不能直接复用MySQL的CHECK、索引和ALTER语法。
