# V2.6.7 → V2.6.8 预生产发布准入与演练设计

状态：`DESIGN_FROZEN / PREPROD_NOT_EXECUTED`

适用范围：Sprint 2-3.7-WF4.2.5，仅用于预生产演练与生产变更评审设计。本设计不代表预生产或生产已执行。

## 1. 发布目标

在生产等量或近似数据副本上，证明 V2.6.7 与 V2.6.8 可以在同一受控窗口连续执行，并量化 DDL metadata lock、索引/外键构建、IO、redo、undo、binlog、复制延迟和应用兼容风险。只有真实演练全部通过，状态才可由本设计阶段推进为 `PREPROD_RELEASE_READY`。

核心不可变规则：

- V2.6.7 不是可独立停留的完整性终点；执行后必须立即执行 V2.6.8。
- Migration 期间禁止对受治理 Workflow 表产生新写入。
- Guard 发现历史异常时只允许 NO-GO、建治理单、人工复核和重新演练；禁止现场自动修数。
- 成功执行后不把删除约束、删除 Flyway history、修改 checksum 或 `flyway repair` 作为普通回滚。

## 2. 发布范围

仅包含数据库资产 V2.6.7、V2.6.8，以及为其服务的应用停写、Flyway 执行、验证、Smoke Test 和观察流程。不包含 Workflow 功能扩展、Investment 改造、ROLE/POSITION/ORG、Release/Transfer/Delegate。

| 对象 | V2.6.7/V2.6.8 影响 | 类型 | 初始风险 |
|---|---|---|---|
| `workflow_task` | 替换 assignment mode CHECK；新增三列复合唯一键 | ALTER/CHECK/索引 | HIGH |
| `workflow_node_execution` | 被 Claim 复合 FK 引用；不直接 ALTER | FK 父表扫描/MDL关联 | MEDIUM |
| `workflow_task_candidate_pool` | 新增四列复合唯一键 | ALTER/索引构建 | HIGH |
| `workflow_task_candidate_member` | 新增五列复合唯一键 | ALTER/索引构建 | HIGH |
| `workflow_task_claim` | V2.6.7 新表；V2.6.8 新增八列复合唯一键及三个复合 FK | CREATE/ALTER/FK校验 | HIGH |
| `workflow_task_claim_audit` | V2.6.7 新表；V2.6.8 新增复合 FK、SUCCESS CHECK、两个 Trigger | CREATE/ALTER/FK/CHECK/Trigger | HIGH |

所有 `ALTER TABLE` 都可能等待 metadata lock。MySQL 实际采用 INSTANT、INPLACE 或 COPY 不能仅凭理论宣称，必须在预生产使用 `EXPLAIN ALTER TABLE`（版本支持时）、`performance_schema.metadata_locks`、processlist、实际计时和表空间变化确认。任何 COPY、不可接受的重建或超窗行为均为 P0 BLOCKING。

## 3. 版本资产

| 版本 | 资产状态 | SHA-256 | Flyway checksum |
|---|---|---|---:|
| V2.6.7 | `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED_WITH_V268` | `800e1a5b1af67e1680a3f0233e9d5400c0b42f39349d181f1777c7ce04ac6aed` | `1689998435` |
| V2.6.8 | `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED` | `4cfd089f4aa05f6e4fb9d9dc250159c0229b3d98eb400089e5e08a758cb4a207` | `1316060036` |

制品还必须冻结 Git Commit、应用镜像 digest、Flyway 容器/CLI digest、JDBC 驱动版本和配置模板版本。变更单中任一摘要漂移即 NO-GO。

## 4. 数据库前置检查

所有检查只读执行并保存带时间、环境标识和执行人的原始输出。不得输出密码。

### 4.1 版本与 Flyway

```sql
SELECT VERSION(), @@version_comment, @@server_uuid;
SELECT installed_rank, version, description, checksum, installed_on, success
FROM flyway_schema_history ORDER BY installed_rank;
SELECT COUNT(*) AS failed FROM flyway_schema_history WHERE success = 0;
```

准入要求：MySQL 版本与预生产批准基线一致；V2.6.6 已成功且 checksum 正确；V2.6.7/V2.6.8 尚未执行；不存在 failed、missing、pending out-of-order 或 checksum drift。Flyway 在迁移前执行策略检查和 `info`，迁移后执行 strict `validate`，禁止自动 repair。

### 4.2 事务、锁与连接

```sql
SELECT * FROM information_schema.innodb_trx ORDER BY trx_started;
SELECT * FROM performance_schema.metadata_locks
WHERE OBJECT_SCHEMA = 'enterprise_platform' AND LOCK_STATUS = 'PENDING';
SELECT * FROM performance_schema.data_lock_waits;
SHOW FULL PROCESSLIST;
```

最长允许事务时长不是仓库固定常量：以预生产正常 P99 事务时长和 DDL 实测确定，变更单冻结为 `max(业务P99×3, 经DBA批准的最小操作值)`。目标表存在超阈值事务、PENDING MDL 或锁等待即 NO-GO。不得由执行人擅自 KILL；必须由 DBA 提出、Application Owner确认影响、Release Approver批准，Operations执行并保留审计。

### 4.3 容量与复制

采集 `@@innodb_redo_log_capacity`、`@@log_bin`、binlog保留策略、undo tablespace、临时表空间、数据盘可用空间、备份空间和复制拓扑。对副本执行 `SHOW REPLICA STATUS`，记录 SQL/IO thread、GTID、relay backlog 和 lag。

最小安全可用空间按实测峰值制定，冻结规则为：

`required_free = max(2 × 目标表(data+index), 3 × 演练期间redo+undo+binlog增量, 备份/快照额外需求, 平台最低水位)`。

任一计算项缺失、磁盘低于 required_free、复制线程异常或延迟持续增长均 NO-GO。

## 5. 数据规模采样

每个目标表保存以下基线：`TABLE_ROWS`（仅估算）、`DATA_LENGTH`、`INDEX_LENGTH`、`AVG_ROW_LENGTH`、`DATA_FREE`、`AUTO_INCREMENT`；精确行数在可接受开销下离线统计，不得在高峰直接全表 COUNT。

```sql
SELECT TABLE_NAME,TABLE_ROWS,DATA_LENGTH,INDEX_LENGTH,AVG_ROW_LENGTH,
       DATA_FREE,AUTO_INCREMENT
FROM information_schema.tables
WHERE table_schema='enterprise_platform'
  AND table_name IN ('workflow_task','workflow_node_execution',
  'workflow_task_candidate_pool','workflow_task_candidate_member',
  'workflow_task_claim','workflow_task_claim_audit');
```

结合近7/30日日增量、峰值 TPS、读写比例、P50/P95/P99 将容量分为：Small（单窗口实测远低于资源/时间预算）、Medium（接近预算50%）、Large（超过预算50%或触发COPY/复制风险）。等级以演练数据写入变更单，不预设拍脑袋行数。

## 6. DDL 风险分析

| 顺序 | DDL | 主要成本 | 必验项 |
|---:|---|---|---|
| 1 | `workflow_task` 替换 CHECK | MDL、全表约束校验可能性 | 算法、锁、耗时、并发阻塞 |
| 2 | CandidateMember复合唯一键 | 排序/索引构建、重复值失败 | 临时空间、IO、唯一性预检 |
| 3 | 创建 Claim/Audit 表 | 字典锁、初始索引/FK | FK父键存在、建表耗时 |
| 4 | Task/Pool/Claim复合唯一键 | 父键索引构建、表重建风险 | algorithm、data/index增长 |
| 5 | Claim三个复合 FK | 历史扫描、父子MDL | FK校验耗时、孤儿数=0 |
| 6 | Audit复合 FK | 宽索引和历史校验 | IO/redo/临时空间 |
| 7 | SUCCESS CHECK | 历史扫描 | Guard先通过、校验耗时 |
| 8 | 两个 Audit Trigger | 短MDL、写路径额外开销 | 三路径定义、写延迟回归 |

V2.6.8 在一次性小数据环境约5.73秒，该数字不可作为生产估算。预生产须逐条采集 start/end、duration_ms、MDL wait、CPU、IOPS、redo、undo、binlog、replication lag、表/索引增长；重复至少三次可复现演练时记录 P50/P95/max，只有一次时明确标注 single observation。

## 7. 发布顺序

1. Phase 0：Change ID、窗口、RACI、备份、证据目录和沟通频道确认。
2. Phase 1：停止会写受治理 Workflow 表的业务能力。
3. Phase 2：通过网关 + 应用 Feature Flag 双门禁关闭 Claim，返回明确维护状态，不排队写入。
4. Phase 3：关闭所有可能写 Workflow/Investment Workflow 表的 Worker、Replay、Scheduler。
5. Phase 4：确认应用存活但目标写入为0，排空在途请求和事务。
6. Phase 5：执行数据库准入、容量、复制、锁、Guard和摘要只读检查。
7. Phase 6：Flyway仅执行 V2.6.7。
8. Phase 7：在同一执行会话/作业中立即执行 V2.6.8；中间不恢复流量、不等待人工窗口。
9. Phase 8：Flyway strict validate、info、第二次 migrate no-op。
10. Phase 9：生成 Full、Workflow、Claim Integrity fingerprints。
11. Phase 10：执行八项业务完整性查询并确认全0。
12. Phase 11：启动冻结的新应用，Worker继续关闭、Claim入口继续关闭。
13. Phase 12：以隔离测试租户/账号执行 Smoke Test。
14. Phase 13：经联合签署后灰度恢复 Claim 入口。
15. Phase 14：确认积压、幂等和资源正常后按顺序恢复 Worker。
16. Phase 15：至少30分钟观察，完成签署后结束窗口。

## 8. 应用停启与兼容矩阵

| 应用/Schema | V2.6.6 | V2.6.7 | V2.6.8 |
|---|---|---|---|
| 不含Claim能力的旧应用 | 预生产既有基线可运行 | 禁止作为发布终态 | 仅在预生产证明不写受保护表且全回归通过后允许应急回退 |
| 当前Claim应用 | 禁止启动Claim入口 | 禁止停留或对外服务 | 唯一推荐运行组合 |
| Migration作业 | 可作为起点 | 仅瞬时中间态 | 必须到达并validate |

推荐严格停写切换：旧应用先关闭目标写入并排空；Migration到V2.6.8；再启动新应用。未经预生产证明，不默认旧应用兼容V2.6.8。应用启动失败时保持入口关闭，先判断已验证的旧应用兼容性，不能现场猜测。

Claim流量控制采用“网关阻断 + Feature Flag”双层方案，维护响应建议 HTTP 503、稳定业务错误码和 `Retry-After`；禁止仅靠前端隐藏或应用整体粗暴只读。当前仓库没有 Claim 专属 Worker。

## 9. Flyway 执行流程

执行器使用冻结的 Flyway 13.0.0 制品与只读 Migration 挂载：

1. 离线复核 SHA-256 和 manifest；
2. `info` 与策略检查，确认目标为V2.6.6且只有V2.6.7/V2.6.8 pending；
3. 受控作业连续执行 migrate 到V2.6.8；作业必须设置超时和中断升级机制，但不得在DDL状态不明时强杀；
4. `info`、strict `validate`；
5. 第二次 `migrate` 必须 no-op；
6. 导出 history、checksum、耗时、日志和Schema指纹。

禁止 `clean`、`repair`、out-of-order、删除history或修改checksum。

## 10. Worker及异步任务控制

代码审计确认存在 Investment `WorkflowOutboxWorker` 与 `WorkflowInboxGapWorker`，由 Scheduling 启动；Outbox 由 `WORKFLOW_OUTBOX_WORKER_ENABLED` 控制，但 Inbox Gap Worker是否受同一门禁必须在演练环境实证。还需逐项盘点 Workflow Scheduler、Inbox Replay管理入口和部署平台定时任务。

发布前统一将 `WORKFLOW_OUTBOX_WORKER_ENABLED=false`，撤销/禁用人工Replay入口，停止所有可能写 `workflow_*` 或 `investment_workflow_*` 的调度实例，并用数据库写审计确认0写入。恢复顺序为：Claim Smoke通过→入口灰度→单实例Worker小批量→Inbox补偿→其余Scheduler。若任何组件缺乏可验证开关，则通过停应用实例隔离，作为演练整改项。

## 11. 监控指标

采集发布前基线，以及发布后5、15、30分钟：

- HTTP：请求量、5xx、维护响应、Claim成功/拒绝/冲突/幂等重放率；
- 应用：Claim P50/P95/P99、审批P95、连接池、线程池、错误日志、Trigger/FK/CHECK错误码；
- MySQL：QPS/TPS、threads running、row lock/MDL wait、buffer pool、CPU、IOPS、吞吐、fsync、临时表；
- 日志：redo/undo/binlog增量、checkpoint age/压力；
- 复制：SQL/IO thread、GTID gap、relay backlog、lag趋势；
- 业务：活动Claim、SUCCESS Audit、Claim无Audit、Claim后审批成功率。

无监控平台时，使用固定间隔SQL快照、操作系统性能采集和结构化应用日志临时采集；时间戳统一、保存原始数据，禁止只记录截图结论。

## 12. NO-GO阈值

以下任一项为P0一票否决：

1. Migration SHA/Flyway checksum或应用制品摘要不一致；
2. Flyway history异常、failed、版本非V2.6.6起点；
3. Guard任何违规计数非0；
4. 可用空间低于第4.3节计算值；
5. 目标表存在超批准阈值的长事务或未知活跃写入；
6. 目标表存在PENDING MDL、data lock wait；
7. 复制线程异常、GTID缺口异常或lag持续增长；具体秒数由预生产正常基线和演练峰值联合冻结，不在设计阶段拍定；
8. 任一DDL实测P95/max超过批准窗口预算，或出现COPY/不可接受重建；
9. 三类Schema Fingerprint与基线不一致；
10. 八项业务完整性检查任一非0；
11. 两个Append-only Trigger缺失或定义漂移；
12. CHECK/FK未ENFORCED或真实负向验证未拒绝；
13. 应用兼容矩阵、停写或Worker门禁未经实证；
14. 可恢复备份、应用失败演练或前向修复流程未完成；
15. Smoke、并发单赢家、Audit或30分钟观察失败。

复制lag和性能数值阈值由演练测量形成：建议以“正常P99 + 已批准安全裕量”与RPO/RTO较严格者为门槛，并同时要求趋势不连续恶化。

## 13. 回滚与前向修复策略

数据库变更成功后原则上不执行向后DDL回滚。分场景处理：

- V2.6.7前失败：保持停写，排除环境问题后重新评审；未执行时可取消窗口。
- V2.6.7成功、V2.6.8未开始：P0紧急状态，保持所有流量和Worker关闭，立即按既定作业执行V2.6.8，不启动应用。
- V2.6.8 Guard失败：永久DDL尚未发生；保持停写，建立异常数据治理单、备份证据、人工复核，不现场修数。
- V2.6.8永久DDL中失败：保持流量关闭，保存Flyway/MySQL/MDL证据，由DBA确认实际对象状态；禁止repair或重复盲跑，进入专项前向修复评审。
- Migration成功但新应用失败：若旧应用已在预生产证明兼容V2.6.8，则回退应用制品；否则保持入口关闭并前向修复应用。
- 运行期异常：Feature Flag和网关关闭Claim、停止Worker、保留数据与审计，不删除约束或history。

恢复操作必须满足已验证备份、批准人、双人复核和证据留存。数据库灾难恢复仅用于不可恢复故障，不是普通应用回滚手段。

## 14. 数据一致性核查

迁移后、应用启动前执行与V2.6.8 Guard同源的只读核查，至少输出以下八个命名计数且全部为0：

1. Claim orphan NodeExecution；
2. Claim跨Instance；
3. Claim跨Task/Task-Execution；
4. Claim跨Pool/Pool-Execution；
5. Claim跨Member；
6. SUCCESS Audit `claim_id IS NULL`或Claim不存在；
7. SUCCESS Audit与Claim归属漂移；
8. 重复活动Claim。

另外检查活动Claim=CLAIMED Task/Pool的双向一致性、每个成功Claim对应且仅对应一个SUCCESS Audit。SQL应复用冻结Guard语义，不能临场改变口径。

## 15. 复制延迟治理

演练必须使用与生产相同复制模式。DDL前记录稳定基线；DDL期间至少每秒/平台最小粒度采集 lag、GTID、relay backlog、SQL/IO thread；DDL后持续至追平。阈值在演练后由DBA按正常P99、业务RPO、演练峰值和追平时长冻结。

复制线程停止、错误、GTID分叉、lag持续三个采样周期增长、预测无法在观察窗口/RPO内追平时立即NO-GO或关闭恢复流量。不得仅看 `Seconds_Behind_Source=0`；必须同时核对线程和GTID。

## 16. 性能与容量

每条DDL形成 Duration Matrix：对象、算法、开始/结束、duration、MDL wait、表行数/大小、data/index增量、CPU/IOPS、redo/undo/binlog、replica peak lag、追平时间。Claim路径形成发布前后P50/P95/P99对比。

准入建议：预生产数据量不低于生产目标表当前量，并加入窗口增长裕量；至少做一次峰值并发回放。任何指标超过容量80%、发生持续换页/临时盘增长、复制不能在窗口内追平或Claim P95/P99超过批准SLO，均不进入生产评审。

## 17. 预生产演练方案

1. 从经脱敏且校验完整的生产等量/近似副本创建全新预生产演练库；记录来源时间和规模比。
2. 完成RACI、窗口、备份恢复验证、制品摘要和只读准入。
3. 采集发布前性能/复制基线和表容量。
4. 关闭Claim入口、相关写入、Worker和Replay，排空事务。
5. 独立执行Guard演练；非0即停止并建治理单。
6. 连续执行V2.6.7、V2.6.8，采集逐DDL指标。
7. strict validate、第二次migrate no-op、history核查。
8. 生成三类指纹并与冻结基线比较：Full `092e9ba6374d749ccd9f1b9427a386e0e7e4fe34a7a68bda20642141c1512c38`；Workflow `3dbaae27e7c11f3492b28b5ba53d398ba5394cb7ff0921be5bacdf2fa418ba58`；Claim `2ef7874f10c305944b1041fa4fe649d359ba8c5ed73ebc164c5188f7b5f9642a`。
9. 执行八项完整性核查及真实FK/CHECK/Trigger负向验证。
10. 启动冻结应用，Worker保持关闭；使用隔离测试数据完成Smoke。
11. 执行同Task双会话Claim单赢家与幂等重放。
12. 灰度入口、恢复Worker并观察30分钟。
13. 失败注入：至少模拟“Migration成功但新应用启动失败”，验证旧应用兼容回退或保持停流量前向修复。
14. 另演练Guard失败、MDL阻塞和复制延迟门禁；不得以破坏真实业务数据方式注入。
15. 汇总证据、问题和实测阈值，由Release Approver签署或判定NO-GO。

Smoke使用预生产专用账号和测试业务ID：DIRECT Task读取、CandidatePool查询、合法Candidate Claim、非Candidate拒绝、幂等Claim、Claim后审批、SUCCESS Audit存在、非Claimant审批拒绝。测试数据必须可识别且按审计规则清理，禁止使用生产真实项目。

## 18. 生产变更单字段

生产变更单至少包含：Change ID、标题、环境、Release Version、Git Commit、应用镜像digest、Artifact SHA-256、V2.6.7/V2.6.8版本/SHA/Flyway checksum、Flyway执行器digest、DBA、Application Owner、Operations、Security/Audit、Business Owner、Approver、计划/实际开始结束、窗口、预期/实测DDL矩阵、目标表规模、复制拓扑、Backup ID/时间/恢复验证、RPO/RTO、Precheck、Guard、NO-GO检查、停写证据、Worker状态、执行日志、rollback/forward-fix、validate/no-op、完整性结果、Smoke结果、三类Fingerprint、观察结果、异常与决策、各方Sign-off。

## 19. 责任人与RACI

| 活动 | Application Owner | DBA | Operations | Security/Audit | Business Owner | Release Approver |
|---|---|---|---|---|---|---|
| 业务停写/Claim门禁 | A/R | C | R | I | C | I |
| 备份与恢复验证 | C | A/R | R | I | I | I |
| 数据库Precheck/Guard | C | A/R | C | I | I | I |
| 执行Migration | I | A/R | R | I | I | I |
| 批准Kill事务 | C | R | R | I | I | A |
| Worker停启 | C | I | A/R | I | I | I |
| Smoke Test | A/R | C | R | C | R | I |
| 恢复流量 | R | C | R | C | A | A |
| GO/NO-GO宣布 | C | C | C | C | C | A/R |

R=执行，A=最终负责，C=会签/咨询，I=知会。真实姓名、联系方式和替补必须在变更单冻结，角色不可空缺。

## 20. 最终发布门禁与验收矩阵

| 验收项 | 通过标准 | Evidence | Owner | 阻断级别 |
|---|---|---|---|---|
| Precheck | 版本、history、锁、容量全部合格 | SQL原始输出 | DBA | P0 |
| Backup | 恢复验证且满足RPO/RTO | 恢复演练记录 | DBA/Ops | P0 |
| Guard | 所有违规计数=0 | Guard结果 | DBA | P0 |
| DDL | 连续执行且未超窗 | Duration Matrix | DBA | P0 |
| Flyway validate/no-op | strict PASS、no-op | CLI日志 | DBA | P0 |
| Fingerprint | 三类均匹配冻结值 | 指纹文件 | DBA/Security | P0 |
| Integrity | 八项及扩展计数=0 | SQL结果 | DBA/App | P0 |
| Smoke | 8项全部PASS | API/审计证据 | App/Business | P0 |
| Claim concurrency | 双会话单赢家，无1213/1205 | 会话日志 | App/DBA | P0 |
| Audit | 单条SUCCESS且不可改删 | SQL/Trigger证据 | Security | P0 |
| Performance | 指标不超批准SLO/容量 | 监控导出 | Ops/DBA | P0 |
| Replication | 线程正常，lag按门槛追平 | Replica证据 | DBA | P0 |
| Application rollback | 成功模拟启动失败并处置 | 演练记录 | App/Ops | P0 |
| Observation | 30分钟无阻断异常 | Dashboard/日志 | Ops | P0 |

发布后至少观察30分钟。HTTP错误、Claim失败、数据库错误、Trigger/FK/CHECK拒绝异常增长、锁等待、CPU/IO、复制延迟或应用错误日志超过演练冻结阈值时，立即重新关闭Claim入口、停止Worker、保留证据并进入事件处置。

只有上述矩阵全部PASS并完成签署，才能标记 `PREPROD_RELEASE_READY` 并进入生产发布评审。本设计冻结不等于生产准入。

技术开发准入与生产部署准入分离：ROLE/POSITION/ORG可以在独立feature branch继续设计或开发，但不得修改V2.6.7/V2.6.8、不得复用未通过演练的生产发布结论，也不得在生产部署前绕过本门禁。

## 21. 备份与恢复要求

发布前必须记录可恢复备份的ID、完成时间、一致性点、binlog/GTID位置、加密与保留策略，并在隔离环境完成恢复验证。优先级：生产平台支持且验证过的一致性物理快照/物理备份用于快速恢复；binlog/GTID用于PITR；逻辑备份作为结构与关键小表补充，不可单独承担大库RTO。

RPO/RTO由业务与DBA批准并写入变更单。仅有“备份成功”而无恢复演练、恢复耗时或校验结果，按备份未就绪处理并NO-GO。

## 22. 剩余风险与后续动作

- 本Sprint未执行预生产，因此DDL算法、锁窗口、容量和复制阈值仍未获得实测值。
- V2.6.8包含宽复合唯一键/FK，可能增加写放大和大表索引构建压力。
- 旧应用对V2.6.8的应急兼容性必须实测，不能仅靠代码阅读断言。
- Inbox Gap Worker是否完全受统一开关控制需要演练验证；否则必须采用实例隔离。
- TaskClaim整体换绑到另一套合法聚合的数据库级不可变风险仍由Repository/Application契约控制，不属于本次DDL范围。

下一步单独执行真实预生产等量数据演练。演练通过后生成带实测阈值和证据索引的报告并申请 `PREPROD_RELEASE_READY`；失败则保持NO-GO并发起独立治理Sprint，不修改历史Migration。
