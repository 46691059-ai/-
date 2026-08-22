# Sprint 2-3.7-WF5.23 实时资格证据持久化实施报告

## 1. 结论与边界

WF5.23 已在冻结治理设计基础上完成持久化实现。实现只固化 ROLE 实时资格证据及其与 Claim 的单向关系，不读取真实 Directory、不启用 `ROLE_DIRECTORY_V1`、不创建 ROLE Task/Candidate Pool，也不改变 Investment、Legacy、USER 或 DIRECT 行为。

状态：`ROLE_REALTIME_ELIGIBILITY_EVIDENCE_PERSISTED`、`ROLE_REALTIME_ELIGIBILITY_RUNTIME_INTEGRATION_READY`、`ROLE_RUNTIME_DISABLED`。

## 2. 修改文件清单

- Migration：`database/migration/mysql/V2.6.16__create_role_realtime_eligibility_evidence.sql`
- Domain：`RoleRealtimeEligibilityPersistenceBundle`、`RoleRealtimeEligibilityPersistenceCanonical`
- Repository contract：`RoleRealtimeEligibilityEvidenceRepository`
- Infrastructure：4 个 Entity、4 个 MyBatis Mapper、Entity Mapper、Repository Adapter
- Application：`RoleRealtimeEligibilityEvidenceTransactionService`
- Tests：Canonical、事务幂等、Migration contract 三组新增测试
- Governance：`migration-inventory.yml`、`SHA256SUMS`、Migration README
- Candidate archive：首次失败候选 `V2.6.16__...__failed_1d7d4223.sql`

## 3. 数据库变化

新增四个 append-only 对象：

1. `workflow_role_realtime_eligibility_evidence`：所有权、双 Directory revision、27/10 计数、TTL、三层根 Hash。
2. `workflow_role_realtime_eligibility_validator_evidence`：27 项有序 Validator 证据。
3. `workflow_role_realtime_eligibility_capability_evidence`：10 项去重 Capability 证据。
4. `workflow_role_realtime_eligibility_event`：PREPARED、VERIFIED、CONSUMED、EXPIRED、REJECTED、CLAIM_NOT_COMMITTED 生命周期。

`workflow_task_claim` 新增 nullable Evidence FK/契约版本与单次消费唯一键；`workflow_task_claim_audit` 新增 Evidence/Persistence Hash 引用。Legacy/USER/DIRECT 历史行保持 NULL，不回填、不重算。

## 4. 三类 Hash

- Validator Evidence Hash：固定序号、编码、状态、原因和毫秒时间。
- Capability Evidence Hash：固定 Capability、Validator、状态、决策、Provider/Policy 与有效期。
- Aggregate Hash：Validator Root、Capability Root、Eligibility Hash 与 Persistence Hash。

Java 与 MySQL 使用相同的 UTF-8、长度前缀、UTC 毫秒和小写 SHA-256 规则。Claim 写入前数据库重算两个 Root 与 Persistence Hash，任何漂移 Fail Closed。

## 5. 事务、幂等和单向强绑定

事务服务按 eligibility request 与 `(task,user,claimRequest,attempt)` 双键查询；相同 persistence hash 返回已有事实，不同 payload 拒绝。新证据以 Header→27 Validator→10 Capability→初始 Event 在同一事务写入，失败整体回滚。

Claim 只能引用同 Task/Instance/NodeExecution/Pool/Member/User 的 ELIGIBLE Evidence，必须处于 TTL 内、包含 27 PASS、10 PASS 和 VERIFIED 事件。一个 Evidence 最多消费一次；绑定字段创建后不可变。Claim 失败不会把 Evidence 伪造成已消费。

## 6. Candidate 修订治理

首次 SHA `1d7d4223...e85f` 的未晋级候选在真实 MySQL 负向验证中暴露 P0：事件 Trigger 用 `MAX(event_hash)` 代替最大 `sequence_no` 对应事件。该候选已原样归档并记录 checksum `-2021432017`。同一 V2.6.16 未发布候选改为按 `sequence_no DESC LIMIT 1` 读取前驱事件，再从全新实例完整重验；没有创建后续 Migration 掩盖问题。

## 7. 测试结果

- Java 21 compile / Spring Boot context：PASS
- 后端全量：530/530 PASS
- Domain 纯净与 ROLE Runtime 隔离：PASS
- Migration contract：PASS
- Migration SHA：38/38 PASS
- `git diff --check`：PASS

## 8. 风险与后续边界

真实 Directory 资源仍未交付，本 Sprint 只验证数据库与冻结契约边界，未以 Fake 结果冒充真实 Directory 联调。后续可在资源准入后接入运行编排，但必须显式保持 Resolver/Runtime Gate；不得绕过本 Evidence→Claim 契约。
