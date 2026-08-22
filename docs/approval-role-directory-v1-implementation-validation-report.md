# Sprint ORG-DIR-1 — Organization / Governance Approval Role Directory V1 实施与验收报告

## 1. Executive Summary

Sprint ORG-DIR-1 已完成设计、数据库、领域、持久化、应用服务、Workflow 冻结契约适配及真实 MySQL/Flyway 验收。最终状态为 `APPROVAL_ROLE_DIRECTORY_V1_IMPLEMENTED`、`APPROVAL_ROLE_DIRECTORY_V1_DB_VALIDATED` 和 `INTERNAL_APPROVAL_ROLE_DIRECTORY_AVAILABLE`。Workflow V1 未被修改或启用：`ROLE_DIRECTORY_V1_PREPARED_NON_EXECUTABLE`、`ROLE_RUNTIME_DISABLED`、`ROLE_EXTERNAL_VALIDATION_PENDING` 均保持不变。

## 2. Scope

V1 只解析精确的 `enterpriseId + BUSINESS_ORG organizationId + roleCode + effectiveAt`，支持单/多成员、同用户多证据、历史有效期、Correction、单调 Revision、完整结果和冻结 Hash。未实现下级组织、父级继承、POSITION/ORG/GROUP Resolver、公开管理 API、Workflow Task/Candidate/Claim 或 Investment 集成。

## 3. Context Boundary

能力位于 `modules.organization.approvalrole`，属于 Organization/Governance Context。`sys_org`、`sys_user` 仅作为既有主数据读取边界；未平行创建 Enterprise、Organization、User。Workflow 仍只认识冻结的 `RoleDirectoryPort`，不会读取 Approval Role 表、Mapper 或 Entity。适配器位于 Organization infrastructure 的反腐层，Organization Domain 与 Workflow/Investment 均无依赖。

## 4. Existing Model Audit

- 仓库原有模块为 investment、project、system、workflow，无 Organization 业务模块。
- 组织和用户主数据复用 `sys_org(id)`、`sys_user(id)`；两者均有可引用主键。
- 仓库没有独立 Enterprise 主表，因此 V1 保存大小写敏感的稳定 `enterprise_id`，不虚构 Enterprise FK。
- 全局 Flyway 最高冻结版本为 V2.6.16，V2.6.17 未占用。
- Workflow 冻结契约为 `ROLE_DIRECTORY_PORT_V1`，Contract Hash 为 `5e8f9870effc7f886671f3cc5077a7bf0419cd9a6698dd6ca01e9de0c01f708d`，Canonical 为 `ROLE_CANONICAL_JSON_V1`。

## 5. Database Changes

新增 `V2.6.17__create_approval_role_directory.sql`，创建四张最小治理表：

| 表 | 用途 | 核心治理 |
| --- | --- | --- |
| `approval_role` | 流程审批角色定义 | 企业内 roleCode 活动业务唯一；固定 Role Type/Scope |
| `approval_role_assignment` | 有效期任职证据 | 用户/组织/角色 FK、半开区间、业务键、重叠门禁、受控更新 |
| `approval_role_revision_head` | Role-Org 聚合当前 Revision | 行锁 + CAS，每次只允许 `revision/version + 1` |
| `approval_role_revision` | 不可变 Revision 账本 | `(enterprise, org, role, revision)` 唯一，UPDATE/DELETE Trigger 拒绝 |

所有表包含主键、审计字段、逻辑删除、`delete_token`、乐观锁或不可变版本约束、索引、CHECK；Hash/业务代码字段使用 ASCII/ascii_bin。Migration 前置 Guard 验证 `sys_org`、`sys_user` 及其主键，不自动补数据。

## 6. Domain Model

新增 `ApprovalRole`、`ApprovalRoleCode`、`ApprovalRoleStatus`、`ApprovalRoleAssignment`、`ApprovalRoleAssignmentStatus`、`ApprovalRoleAssignmentSource`、`ApprovalRoleAssignmentSourceType`、`ApprovalRoleRevision`、`ApprovalRoleRevisionHead`、Directory Query/Result/Member、Canonical、Failure/Error Code、Revision Policy 和 Directory Policy。Domain 纯净扫描 PASS：不依赖 Spring、MyBatis、Entity、Workflow、Investment 或 infrastructure。

## 7. Role Model

`roleCode` 经 Unicode NFC 后必须匹配 `[A-Z][A-Z0-9_]{2,99}`，稳定且大小写敏感；`roleType` 固定 `PROCESS_APPROVAL_ROLE`；`organizationScopeType` 固定 `BUSINESS_ORG`；状态仅 `ACTIVE/INACTIVE`。V1 未预置大批业务角色，仅使用测试角色。

## 8. Assignment Model

Assignment 保存企业、业务组织、角色、用户、`[effectiveFrom, effectiveTo)`、状态、来源系统/引用/Hash、证据优先级及审计信息。允许同一 Role-Org 同时有多个 userId，也允许同一 userId 有多个合法来源；不按排序选择“第一人”。`assignment_key_hash + delete_token` 阻止完全重复事实，Trigger 与应用行锁共同拒绝同源重叠区间。

## 9. Effective Time

有效规则严格为 `effectiveFrom <= effectiveAt && (effectiveTo == null || effectiveAt < effectiveTo)`，未使用 BETWEEN。真实 MySQL 验证：2026-05-01 返回交接前用户及两项来源证据，2026-06-01 仅返回交接后用户；开始点有效、结束点无效。

## 10. Revision Model

Revision 粒度为 `enterpriseId + organizationId + roleCode`。`approval_role_revision_head` 作为唯一可变 CAS 头，业务事务先锁定它；账本 Revision 只能 INSERT。批量任职变更只发布一个新 Revision。双会话同时发布不同合法成员后，head 从 R1 到 R3、version 从 0 到 2，两项事实均存在，无重复 Revision 或 Lost Update。

## 11. Correction

`correctAssignment` 将原事实受控标记为 `CORRECTED`，插入替代事实，并追加一个 `CORRECTION` Revision，记录 reason、reference、affected time range、previous/new result Hash。旧 Revision 不被更新；已冻结的 Workflow Candidate Pool 不被回写。

## 12. Source Governance

支持 `HR_ASSIGNMENT`、`GOVERNANCE_DECISION`、`MANUAL_GOVERNANCE_RECORD`、`IMPORT`。同用户的同义证据按 userId 合并且全部保留；优先级仅参与稳定排序。当同一 sourceSystem/sourceReference 对不同用户或不同证据 Hash 作出矛盾断言时，Directory 以 `DIRECTORY_SOURCE_CONFLICT` Fail Closed。

## 13. Canonical

`ApprovalRoleCanonical` 实现与 `ROLE_CANONICAL_JSON_V1` 等价的显式序列化：UTF-8、Unicode NFC、UTC 毫秒、固定 NULL/Boolean/数字语义、固定字段顺序、userId 与 assignment evidence 稳定排序、JSON escaping、SHA-256 小写 64 hex。未创建语义分叉的 Canonical 版本。

## 14. Result Hash

Hash 覆盖冻结 Workflow Contract 实际表达的 roleCode、organizationId、effectiveAt、revision、complete、成员及每项 assignment evidence；内部结果另保留 enterpriseId/resolvedAt 元数据。固定向量证明：相同输入和成员重排 Hash 不变；成员、Revision、effectiveAt、roleCode、organization 或证据变化 Hash 改变。

## 15. Complete Semantics

`complete=true` 只由成功的权威、非分页、Revision 一致、Canonical 可验证结果生成。零成员是合法权威结果：`complete=true, members=[]`，Directory 不伪造人员。角色/组织失效、Revision 缺失、来源冲突、非法查询或 Hash 无法证明均抛出稳定错误并 Fail Closed，不返回伪完整结果。

## 16. Workflow Contract Compatibility

`OrganizationApprovalRoleDirectoryAdapter` 实现冻结 `RoleDirectoryPort` 投影，Contract Version、Contract Hash、Canonical Version、字段及 Result Hash 固定向量全部通过。Contract Hash 无漂移；Workflow Domain 未引用 Organization Entity/Mapper。适配器未注册为生产 Bean，因 ROLE Runtime 仍被禁用。

## 17. Repository

Domain 定义 `ApprovalRoleRepository`、`ApprovalRoleAssignmentRepository`、`ApprovalRoleRevisionRepository`、`ApprovalRoleIdentityGenerator` 与 `OrganizationUserDirectoryPort`。Infrastructure 提供 MyBatis Entity/Mapper/Adapter；Repository 支持按业务键查询、行锁、有效期解析、历史查询、重叠锁定、Revision append 与 head CAS。`SystemOrganizationUserDirectoryAdapter` 只读取活动 `sys_org/sys_user`。

## 18. Application Services

内部 `ApprovalRoleCommandService` 提供 `createRole`、`activateRole`、`deactivateRole`、`assignUser/assignUsers`、`endAssignment`、`correctAssignment`。内部 `ApprovalRoleDirectoryService` 提供 `resolve`、`getCurrentRevision`、`getRole`、`getAssignmentHistory`、`getRevisionHistory`。未新增 Controller、菜单或 RBAC 权限。

## 19. Adapter

链路为 `Organization/Governance Domain → ApprovalRoleDirectoryService → OrganizationApprovalRoleDirectoryAdapter → frozen RoleDirectoryPort result`。Adapter 只做精确组织 ID、Contract 与 evidence 投影及 Hash 再校验，不执行继承、fallback、候选裁剪或默认负责人选择。

## 20. Transaction Boundary

任职新增/结束/更正事务固定执行：锁角色与 Revision Head、校验组织/用户及重叠、写事实、计算 Canonical 结果、插入新 Revision、CAS 推进 head、提交。任何一步失败整体回滚。真实 MySQL 重复 Revision 注入导致事务失败后，新 Revision 记录数为 0。

## 21. Concurrency

- 同聚合两个会话新增不同合法成员：两者成功，最终 `head=3/version=2`、facts=2，Revision 确定排序且无 Lost Update。
- 两个会话新增相同 Assignment：单赢家，另一会话收到 `APPROVAL_ROLE_ASSIGNMENT_OVERLAP`。
- 两项测试均未出现 MySQL 1213 deadlock 或 1205 lock timeout。

## 22. MySQL Validation

环境：MySQL Community Server 8.4.9、Flyway Community 13.0.0、Java 21.0.12、Maven 3.9.9。最终证据根为 `D:\codex-validation-org-dir-1-20260820-171135`。

- Fresh：V2.0.0 → V2.6.17 全链成功，history 0 failure，strict validate PASS，第二次 migrate no-op。
- Upgrade：V2.6.16 → V2.6.17 只新增一个成功 history 行，strict validate PASS，第二次 migrate no-op。
- V2.6.17 Flyway checksum：`-1602960257`。

## 23. Schema Fingerprints

Fresh 与 Upgrade 完全一致：

| 范围 | SHA-256 |
| --- | --- |
| Full Schema | `5bb8b13a993e805b6e3699785665a8908146bfe8dcbb47e8436ad0edbdf76d48` |
| Organization/Governance | `34f0afdd005c3e443d5763a84a6c42b63b3f9d8da518313820df8b2fc3d31041` |
| Approval Role Directory | `17ab828c6201a64c2f818da74b8892e5d4678fb9be5cf61fe31152de18e20f9b` |

## 24. Negative Matrix

真实 MySQL 全部 PASS：小写 roleCode、非法 roleType/scope、重复 role、无效 org FK、跨企业 role 引用、非法有效区间、大写 Hash、同源重叠、重复 Assignment、旧 Revision UPDATE/DELETE、Assignment DELETE、非 CAS head 更新均被拒绝。应用/Domain 矩阵另覆盖无成员、来源冲突、inactive role/org、非法查询、Correction、Canonical 稳定性和 batch revision 只加一。

## 25. Workflow Regression

Workflow 341 项测试 PASS。`EXPLICIT_USER_V1=ACTIVE`；`ROLE_DIRECTORY_V1=PREPARED/NON_EXECUTABLE`；ROLE Runtime Gate 仍为 DISABLED。没有修改冻结 Contract 或 Hash，没有生成 Task/Candidate Pool/Claim，也没有自动解除 `DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED`。

## 26. Migration Asset State

- Version：V2.6.17
- Filename：`V2.6.17__create_approval_role_directory.sql`
- SHA-256：`d5fdf54be071a1f4c347b61a447771bb0c849f2ff8d4113e9bedc7ae4d145b65`
- Flyway checksum：`-1602960257`
- 状态：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- SHA 清单：39/39 PASS；V2.6.16 及历史 Migration 无漂移。

## 27. PII / Security

Directory 只保存稳定 userId 与审批资格证据，不复制姓名、身份证、手机号、地址、工资或人员档案。无公网/业务 Controller；Workflow Administrator 无目录维护权。未来权限预留为 view/create/assign/correct/publish，但本 Sprint 未创建权限数据。

RACI：Business Owner 负责 roleCode 语义；Organization/HR Owner 负责主数据；Directory Data Owner 负责 Assignment；Security/Audit Owner 审计；Workflow Owner 只消费；Release Approver 批准生产变更。

## 28. Remaining Risks

仓库缺少 Enterprise 主表，`enterprise_id` 目前由 Directory 业务键与跨表 Trigger 约束，尚无 Enterprise FK。真实 TEST/PREPROD Directory Provider、服务认证、网络连通性和外部资源验收尚未完成。V1 不处理组织继承、POSITION/ORG/GROUP、委托/转签/超时等扩展语义。生产数据容量下的长期索引与锁等待仍需在预生产压测。

## 29. WF5.25 Resource Impact

内部权威 Directory 已可作为后续 TEST/PREPROD Provider 数据源，但这不等于外部 Connectivity Ready。WF5.25 原阻断项应从“无内部目录实现”缩小为“尚未包装/部署 Provider，且 TEST/PREPROD 地址、凭据、认证、网络与真实资源未交付”。`DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED` 继续保持。

## 30. Next Step

下一步由独立任务将本内部 Application Boundary 包装为 TEST/PREPROD Directory Provider，完成安全配置、服务部署和真实 Connectivity 验收后，再恢复 WF5.25；在此之前不得启用 ROLE Runtime。ORG-DIR-1 到此停止，不自动进入 ORG-DIR-2。

## 附录：修改文件清单

- 启动扫描：`backend/src/main/java/cn/gov/enterprise/EnterprisePlatformApplication.java`
- Domain 与 Repository Port：`backend/src/main/java/cn/gov/enterprise/modules/organization/approvalrole/domain/**`
- Application：`backend/src/main/java/cn/gov/enterprise/modules/organization/approvalrole/application/**`
- Entity/Mapper/Repository/Master Data/Workflow Adapter：`backend/src/main/java/cn/gov/enterprise/modules/organization/approvalrole/infrastructure/**`
- 测试：`backend/src/test/java/cn/gov/enterprise/modules/organization/approvalrole/**`
- Migration：`database/migration/mysql/V2.6.17__create_approval_role_directory.sql`
- 验收脚本：`database/flyway/scripts/validate-v2617-approval-role-directory.ps1`
- 治理资产：`database/flyway/migration-inventory.yml`、`database/migration/mysql/SHA256SUMS`、`database/migration/mysql/README.md`
- 本报告：`docs/approval-role-directory-v1-implementation-validation-report.md`

应用回归最终结果：Java 21 编译与 Spring Boot Context PASS；后端全量 562 项测试 PASS（0 failure、0 error、0 skipped）；Organization 27 项、Workflow 341 项；Domain purity、PII 扫描、无 Controller、Migration SHA 39/39 与 `git diff --check` 均 PASS。Investment 文件未修改。
