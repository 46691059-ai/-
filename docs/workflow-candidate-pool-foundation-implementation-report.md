# Workflow Candidate Pool Foundation Implementation Report

> Sprint：2-3.7-WF4.1
> 基线：V2.6.5 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
> 结论：`IMPLEMENTED / MYSQL_NOT_EXECUTED`
> V2.6.6：`CANDIDATE / NOT_EXECUTED`

## 1. 修改文件清单

### 新增

- `database/migration/mysql/V2.6.6__create_workflow_candidate_pool.sql`
- `backend/src/main/java/cn/gov/enterprise/modules/workflow/domain/candidate/CandidatePool.java`
- `CandidatePoolMember.java`、`CandidatePoolHash.java`
- `CandidatePoolStatus.java`、`CandidateMemberStatus.java`
- `CandidatePoolRepository.java`、`CandidatePoolMemberRepository.java`
- Candidate Pool/Member Entity、Mapper、Repository Adapter及映射器
- `FreezeCandidatePoolCommand.java`
- `CandidatePoolApplicationService.java`
- `WorkflowCandidatePoolDetail.java`
- 三个Candidate Pool测试类
- 本实施报告

### 修改

- `ResolverMode.java`：增加不可执行的`CANDIDATE_POOL`领域值；Static Registry保持不变。
- `WorkflowTask.java`、`WorkflowTaskEntity.java`、`WorkflowEntityMapper.java`：增加显式assignmentMode并保留旧构造器兼容。
- `WorkflowTaskController.java`：增加只读候选查询。
- `WorkflowTaskActionApplicationService.java`：未实现Claim时对Candidate Pool Task安全阻断。
- Migration Inventory、SHA256SUMS、Migration README。

未修改Investment及V2.5.0—V2.6.5历史Migration，未创建V2.6.7。

## 2. 现状约束审计

- `workflow_task.assignee_user_id`数据库允许NULL；旧单节点任务可为空，线性DIRECT由领域工厂保证非空。
- `workflow_task`在V2.6.5前没有显式assignment_mode。
- `workflow_task_assignment_snapshot`允许USER/ROLE/POSITION/ORG结构，但当前运行仅生成USER。
- V2.6.5的Resolver Binding CHECK仅允许DIRECT；Node Binding CHECK仅允许`USER + USER + DIRECT`。
- `WorkflowResolverBindingApplicationService`和Static Registry仅能生成/选择`EXPLICIT_USER_V1 + USER + DIRECT`。
- 现有Task创建链为Resolver→单候选→DIRECT Assignee→Task→Assignment Snapshot，不写Candidate Pool。

治理选择：V2.6.6新增显式assignment_mode；历史默认DIRECT。通过新增Migration按名称替换V2.6.5 CHECK，保留USER+DIRECT并受控预留ROLE/POSITION/ORG+CANDIDATE_POOL数据库形状。生产Registry仍拒绝这三类Resolver，因此Candidate Pool生产路径不可达。

## 3. 数据库变化

- `workflow_task`新增`assignment_mode`及CHECK/索引。
- 为Resolver Binding和Node Binding补充Candidate Pool复合Owner唯一键并替换等价增强CHECK。
- 为Assignment Snapshot补充复合Owner唯一键。
- 新增`workflow_task_candidate_pool`、`workflow_task_candidate_member`。
- 两表具有审计字段、逻辑删除、delete_token、乐观锁、普通/唯一索引、复合外键和CHECK。
- Pool通过复合外键同时约束Task、NodeExecution、Resolver Binding、Node Binding和Assignment Snapshot，阻断跨实例、版本、节点和任务引用。

## 4. Candidate Pool Domain模型

CandidatePool是不可变聚合头，绑定唯一Task、NodeExecution、Instance、Version、Resolver/Node Binding和Assignment Snapshot。聚合创建时校验成员数量、用户/排序唯一、时间窗和Pool Hash。WF4.1仅能由`CREATED`转换为`AVAILABLE`；CLAIMED/EXPIRED/CANCELLED/CLOSED仅预留，API不可触发。

## 5. Candidate Member模型

Member保存candidate_user_id、source_type/ref、组织/岗位/角色快照、资格快照/Hash、确定性排序、生成时间和状态。成员集合由`List.copyOf`冻结，不提供replace、refresh或reResolve能力。USER明确拒绝进入持久化候选池，保持DIRECT。

## 6. Pool Hash规则

使用UTF-8、长度前缀Canonical Serialization和SHA-256，输出64位小写Hex。输入包括Hash版本、Resolver Code/Version、Contract Hash、Rule Hash、Strategy、稳定排序后的候选用户、来源、组织/岗位/角色逻辑快照、资格快照/Hash、顺序和成员状态。数据库ID、审计时间、操作人和乐观锁版本不参与Hash。

## 7. Repository链路

`CandidatePoolRepository`只支持保存、按Task查询和存在性检查；`CandidatePoolMemberRepository`只支持批量追加和按Pool查询。接口位于Domain，Adapter通过MyBatis Plus Entity/Mapper持久化。没有覆盖式更新接口。

## 8. Application事务链路

内部`freeze()`按以下顺序执行：Task/Instance/NodeExecution校验→Assignment Snapshot→Node/Resolver Binding→成员建模→Hash→Pool AVAILABLE→保存Pool→保存Members。方法使用Spring事务，Member写入失败向外传播并回滚整个事务。该方法未暴露为REST API。

## 9. DIRECT兼容

- 旧构造器、`pendingLinear()`和现有Entity映射默认DIRECT。
- DIRECT线性Task继续要求assignee非空。
- DIRECT Task调用内部freeze会被拒绝。
- 当前Resolver Registry和Task创建链未接入Candidate Pool，正常生产路径不会产生Pool记录。

## 10. Legacy兼容

旧单节点和历史DIRECT Task不补造记录。`GET /workflow/tasks/{taskId}/candidates`只返回`LEGACY_DIRECT_ASSIGNMENT`或`DIRECT_ASSIGNMENT`兼容投影，不重新运行Resolver、不写数据库。

## 11. API列表

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/workflow/tasks/{taskId}/candidates` | `workflow:view` | 查询冻结Pool或DIRECT/Legacy兼容投影 |

无Claim、Release、Transfer、Delegate或动态Resolver API。

## 12. 测试结果

- Candidate Pool专项：12通过、0失败。
- 后端全量：314通过、0失败、0错误、0跳过。
- Java 21编译与Spring Boot上下文：通过。
- 覆盖纯净性、不可变、重复候选/排序、Hash稳定/变化/Canonical、事务边界、Member失败传播、重复Pool契约、跨实例、DIRECT不创建、Legacy不补造、兼容查询和Migration契约。
- 既有Resolver Registry测试确认ROLE/POSITION/ORG仍不可用；EXPLICIT_USER_V1及线性运行回归通过。
- V2.5.0—V2.6.5 SHA-256全部与SHA256SUMS一致。

未执行真实MySQL/Flyway Migration，因本Sprint明确禁止。

## 13. V2.6.6状态

```text
CANDIDATE
NOT_EXECUTED
flyway_checksum: null
```

## 14. SHA-256

`3b73653d571a5f5497d10865b3ef692e694387b8d220ec2af383bea9c82e5d4f`

## 15. 风险

- V2.6.6尚未经过真实MySQL Fresh/Upgrade，复合外键和CHECK只能视为候选设计。
- 数据库预留ROLE/POSITION/ORG+CANDIDATE_POOL，但运行Registry明确不可用；未来启用需独立Resolver Sprint。
- 目前无Claim，Candidate Pool Task不能获得assignee，也不能执行审批。
- Application freeze是内部能力，尚未接入真实Resolver；生产Pool表预计为空。
- 候选资格实时校验、人员变更、并发Claim和审计属于后续V2.6.7范围。

## 16. 下一步建议

下一Sprint仅执行V2.6.6真实MySQL/Flyway Fresh与V2.6.5 Upgrade验收，验证复合外键、CHECK、DIRECT回填、负向约束和Schema Fingerprint。验收通过前不得晋级资产，也不得进入Claim或ROLE/POSITION/ORG实现。
