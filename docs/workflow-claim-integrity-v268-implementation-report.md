# V2.6.8 Workflow Claim数据库完整性前向修复实施报告

## 1. 修改文件清单

新增：

- `database/migration/mysql/V2.6.8__strengthen_workflow_claim_integrity.sql`
- `backend/src/test/java/cn/gov/enterprise/modules/workflow/infrastructure/WorkflowClaimIntegrityMigrationContractTest.java`
- `docs/workflow-claim-integrity-v268-implementation-report.md`

修改：

- `TaskClaimRepository.java`、`TaskClaimAuditRepository.java`
- `TaskClaimRepositoryImpl.java`、`TaskClaimAuditRepositoryImpl.java`
- `TaskClaimTransactionService.java`
- `WorkflowTaskClaimTransactionServiceTest.java`
- `database/flyway/migration-inventory.yml`
- `database/migration/mysql/SHA256SUMS`
- `database/migration/mysql/README.md`

未修改V2.6.7及更早Migration，未修改Investment，未创建V2.6.9。

## 2. 当前3项P0

V2.6.7真实MySQL验收已证明：Claim可引用不存在的NodeExecution；SUCCESS Audit冗余归属可改指无关对象；数据库允许`SUCCESS + claim_id NULL`。V2.6.8仅对这三类完整性缺口进行前向治理，不增加业务能力。

## 3. Schema审计

| 表 | 现有权威能力 | V2.6.7缺口 |
| --- | --- | --- |
| `workflow_instance` | 主键；Version/Instance候选键 | 无直接缺口 |
| `workflow_node_execution` | `(instance_id,id)`和`(instance_id,version_id,node_id,id)`候选键 | Claim未引用 |
| `workflow_task` | 已复合引用NodeExecution；已有含Version/Node/Execution候选键 | 缺少供Claim使用的窄候选键 |
| Candidate Pool | 已复合引用Task及NodeExecution | Claim仅引用Pool/Task/Instance，未包含Execution |
| Candidate Member | V2.6.7已有Member/Pool/Task/Instance/User候选键 | 可继续复用 |
| TaskClaim | Pool与Member外键、活动Claim/幂等唯一键 | Task/NodeExecution/Pool Execution链未闭合 |
| ClaimAudit | 可空单列`claim_id`外键；SUCCESS证据字段 | 缺少完整归属FK、SUCCESS非空约束和真正不可变机制 |

逻辑删除沿用`deleted + delete_token`；Claim/Audit事实不通过删除表达生命周期。现有Domain Repository没有归属update方法，但Mapper基类具备通用能力，因此继续通过Adapter封装、契约测试和数据库append-only机制隔离。

## 4. Claim权威归属模型

冻结链：Instance → NodeExecution → Task → CandidatePool → CandidateMember → TaskClaim → ClaimAudit。

V2.6.8使Claim同时受Task、NodeExecution、Pool和Member复合外键约束。任一ID虽然单独合法，只要不属于同一Instance/Task/NodeExecution/Pool聚合，数据库都应拒绝。

## 5. 复合唯一键

新增三个父级候选键：

1. Task：`(id,instance_id,node_execution_id)`；
2. Pool：`(id,task_id,instance_id,node_execution_id)`；
3. Claim：`(id,task_id,instance_id,node_execution_id,candidate_pool_id,candidate_member_id,candidate_user_id,operator_user_id)`。

Candidate Member继续复用V2.6.7的`(id,pool_id,task_id,instance_id,candidate_user_id)`。

## 6. 复合外键

新增：

- Claim `(task_id,instance_id,node_execution_id)` → Task；
- Claim `(instance_id,node_execution_id)` → NodeExecution；
- Claim `(candidate_pool_id,task_id,instance_id,node_execution_id)` → Pool；
- Audit完整八字段归属元组 → 同一Claim。

所有关系均为`ON DELETE RESTRICT ON UPDATE RESTRICT`。V2.6.7的Member强关联继续有效。

## 7. NodeExecution强关联

直接复合FK验证NodeExecution存在且属于同一Instance；Task复合FK进一步证明它是当前Task的NodeExecution。合法Task与伪造或其他Instance的Execution无法组合。

## 8. CandidatePool归属

Pool父键加入`node_execution_id`，Claim外键同时包含Pool、Task、Instance和Execution。Task A与Pool B即使各自合法，只要聚合归属不同仍会失败。

## 9. CandidateMember归属

沿用V2.6.7五字段复合外键，Member必须属于Claim的Pool、Task、Instance和Candidate User。当前不支持代领，Domain创建规则保持Candidate User与Operator一致，数据库既有Identity CHECK继续强制该规则。

## 10. SUCCESS Audit强关联

新增Audit到Claim的八字段复合FK，锁定Claim、Task、Instance、NodeExecution、Pool、Member、Claimant和Operator。新增CHECK要求SUCCESS必须是CLAIM事件，`claim_id`及完整归属、Hash、实时资格、RBAC、DataScope、SoD和状态证据全部非空。

失败Claim仍写平台安全审计，不在本表伪造SUCCESS事件。

## 11. Audit不可变治理

三层实现：

1. Domain Repository只开放`appendSuccess`，没有update/delete；
2. Application只持有Repository，不接受外部Audit业务归属ID；
3. MySQL候选Migration增加UPDATE和DELETE阻断Trigger。

Trigger不是唯一保护。部署时仍要求应用账号对Audit表仅具备`SELECT/INSERT`，Flyway使用独立账号；达梦和人大金仓需在各自方言Migration中实现等价不可变规则。

## 12. Application派生规则

事务链调整为：创建完整TaskClaim → Repository insert成功并返回已确认Claim → Task/Pool CAS → 从该Claim派生SUCCESS Audit → append。Controller和外部请求不能提交Audit的Task、Instance、Execution、Pool或Member ID。

Claim仍只确定Task经办权，未修改TaskAction、NodeExecution推进、Instance状态机或Approve接口。

## 13. Repository治理

- `TaskClaimRepository`仅保留find和`insert`；未新增归属更新入口。未来状态变化必须使用专用CAS。
- `TaskClaimAuditRepository`仅有`appendSuccess`；没有update/delete/ownership方法。
- Adapter仍将Entity setter限制在单次insert构造中；Mapper没有向Controller/Application暴露。
- 反射契约测试冻结上述API表面。

## 14. V2.6.8 Migration结构

- Phase A：临时CHECK Guard执行数据预检；
- Phase B：建立辅助复合唯一键；
- Phase C：建立Claim权威归属复合外键；
- Phase D：建立SUCCESS Audit到Claim完整归属外键；
- Phase E：收紧SUCCESS CHECK并建立append-only Trigger；
- Phase F：查询`information_schema`断言索引、FK、CHECK和Trigger存在，随后删除临时Guard。

没有数据自动修复、删除、改ID、合并、猜测或补造。

## 15. Migration预检

永久DDL前检测：Claim不存在/跨Instance NodeExecution、Claim与Task/Pool/Member归属不一致、SUCCESS Audit无Claim或引用不存在Claim、Audit与Claim七类归属/身份不一致、SUCCESS证据不完整、重复活动Claim。

临时Guard的`violation_count=0` CHECK使任一异常立即终止Migration。MySQL永久DDL尚未开始，因此避免先部分建约束再发现脏数据。

## 16. 测试结果

- Java 21编译：通过。
- Claim事务与Migration契约专项：14项通过，0失败。
- 18项负向场景已由预检、复合FK、SUCCESS CHECK、append-only Trigger及既有活动Claim唯一键形成静态契约。
- 后端全量测试336项通过，0失败、0错误、0跳过；Spring Boot上下文启动通过。
- 30项Migration SHA清单全部匹配；`git diff --check`通过。
- 按要求未执行真实MySQL/Flyway Migration，不能把静态契约测试表述为真实数据库验收。

## 17. V2.6.8 SHA-256

`4cfd089f4aa05f6e4fb9d9dc250159c0229b3d98eb400089e5e08a758cb4a207`

资产初始状态：`CANDIDATE / NOT_EXECUTED`，`flyway_checksum: null`。

## 18. V2.6.7摘要复核

V2.6.7 SHA-256仍为`800e1a5b1af67e1680a3f0233e9d5400c0b42f39349d181f1777c7ce04ac6aed`。其`CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`及原FAIL证据继续保留，未提前标记CANONICAL。

## 19. 剩余风险

1. 尚未确认Flyway 13对两个单语句SIGNAL Trigger的真实解析行为；
2. 尚未在MySQL验证复合索引字节、DDL锁、FK执行和18项负向约束；
3. Audit八字段索引会增加写放大，需真实并发压测；
4. MySQL DDL隐式提交，中途失败需按实际Schema前向修复；
5. 应用账号SELECT/INSERT权限需由部署流程落地，当前Migration不写死环境账号；
6. 达梦、金仓的Trigger、CHECK和正则语法需要独立方言实现与验收；
7. Release/Transfer/Delegate仍未实现，未来不得通过放宽本次归属约束实现。

## 20. 下一步真实MySQL组合验收方案

单独执行WF4.2.3.1：MySQL 8.4.x、Flyway 13.x；覆盖Fresh至V2.6.8、V2.6.7→V2.6.8 Upgrade、V2.6.7验证失败环境前向Repair三路径；执行pre-check脏数据阻断、strict validate、二次migrate no-op、Schema fingerprint、合法Claim链、18项负向测试、Audit UPDATE/DELETE、事务回滚、幂等与双会话单赢家。

组合验收通过前不得晋级V2.6.8，也不得改变V2.6.7独立FAIL历史。
