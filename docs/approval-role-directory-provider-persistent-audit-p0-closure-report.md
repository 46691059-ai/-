# Approval Role Directory Provider Persistent Audit P0 Closure Report

## 1. Final Conclusion

`PASS`。原 Release Baseline Freeze P0 已关闭：生产 Provider 不再包含或装配 InMemory/No-op Audit，真实 TEST HTTPS Provider 使用 MySQL 持久化、append-only、PII 最小化且 fail-closed 的审计链路。最终状态为 `APPROVAL_ROLE_DIRECTORY_PROVIDER_PERSISTENT_AUDIT_VALIDATED / PRODUCTION_FAKE_SCAN_PASS`；`ROLE_RUNTIME_DISABLED / CANARY_NOT_AUTHORIZED / CANARY_NOT_ENABLED` 保持不变。

## 2. Original P0

原 `InMemoryApprovalRoleDirectoryProviderAuditSink` 位于 `src/main` 并在 Provider 开启时成为唯一审计 Bean，证据随进程退出丢失。该类已从生产源码删除，仅在 `src/test` 保留同名 test double。

## 3. Existing Audit Infrastructure Audit

V2.6.18 的 Workflow external-audit outbox/receipt 和 V2.6.19 的 Governance External Audit Sink 都是 ROLE Claim 专用强结构，强制要求 instance/node/task/claim/admission/eligibility 字段。它们具备可复用的 SHA-256、稳定业务键、append-only Trigger 和 fail-closed 设计模式，但不能在不伪造 Claim 身份的情况下承载 Directory Query。结论：复用治理模式和基础组件，不复用错误业务表；新增一个最小 Directory Provider ledger 是真实 Schema P0 修复。

## 4. Chosen Persistence Model

每次物理 Provider 调用生成独立 `auditId`、`auditEventId` 和 Evidence；同一 `requestId` 重试会追加新事件（幂等语义 A），不会覆盖历史。`requestId`/`correlationId` 可检索，`auditEventId` 与 `evidenceHash` 唯一。

## 5. Domain / Port

新增纯 Domain `ApprovalRoleDirectoryProviderAuditEvidence` 与 `ApprovalRoleDirectoryProviderAuditPort`。模型冻结 `SUCCESS / REJECTED / FAILED`、Request Canonical V1、Evidence Canonical V1，并在构造时验证小写 SHA-256、成功结果完整性与时间顺序。Domain forbidden-import scan 为 0。

## 6. Adapter

`PersistentApprovalRoleDirectoryProviderAuditAdapter` 通过 MyBatis Mapper 写入 MySQL，并将任何数据库运行异常统一转换为 `PROVIDER_AUDIT_PERSISTENCE_FAILED`。生产条件装配下只有该 Adapter；不存在 InMemory 或 No-op fallback。Entity/Mapper/Adapter/query 链路已实现，查询限制为 1—1000。

## 7. Database

V2.6.20 新增 `approval_role_directory_provider_audit`。字段覆盖 audit/event/correlation/request、Provider/服务/环境/调用方、企业/组织/角色/effectiveAt、Contract/Canonical、Revision/Result Hash/Candidate Count、Outcome/Failure、Request/Evidence Hash、started/completed/created 和未审批的 retention policy。未保存成员明细或 Credential。

## 8. Append-only

`BEFORE UPDATE` 与 `BEFORE DELETE` 均以 MySQL 1644/45000 和稳定错误 `APPROVAL_ROLE_DIRECTORY_PROVIDER_AUDIT_APPEND_ONLY` 拒绝。真实 MySQL 负向测试通过。

## 9. Hash

Request Hash 覆盖 caller、enterprise、organization、role、effectiveAt、contractVersion、correlation 语义，不含 Token/Credential。Evidence Hash 额外覆盖 auditEventId、Provider identity/version、service/environment、contractHash、revision/resultHash/candidateCount、outcome/failureCode 与毫秒级开始/完成时间。同输入 Request Hash 稳定；每次物理调用因 auditEventId 不同而具有唯一 Evidence Hash。

## 10. Failure Semantics

SUCCESS 只能在审计 INSERT 确认后返回。业务拒绝尽可能先写 REJECTED；依赖异常尽可能写 FAILED。若该证据也无法写入，Audit Failure 成为对外主失败，原业务异常作为 suppressed context，禁止返回可信 Directory Result。认证前只记录安全可得身份，不伪造 caller。

## 11. Transaction Boundary

顺序冻结为 Query → Validate → Build Result → Build Evidence → Persist Audit → Return Result。Directory 查询为只读事务，Audit 为单行原子 INSERT；审计失败不会产生部分 Audit，也不会返回业务成功，因而无需补偿或递归审计。

## 12. Success Test

TLS 1.3 TEST Provider → MySQL Directory → Persistent Audit 通过。代表性 SUCCESS 的 revision、directoryResultHash、candidateCount、contractHash、outcome 与 HTTP 返回完全一致。最终账本为 161 行：156 SUCCESS、5 REJECTED、0 FAILED。

## 13. Negative Test

认证缺失、错误 Token、错误 caller identity 和 source conflict 均 fail closed，并产生安全的 REJECTED 证据。数据库拒绝重复 event/evidence、非法环境、非法 Outcome、大写/非法 Hash、缺失 SUCCESS facts 与负 candidateCount。Contract/Canonical/Environment 握手错误由客户端在调用前拒绝，不伪造 Provider 执行事件。

## 14. Failure Injection

临时 BEFORE INSERT 故障注入使 Directory 业务查询成功但 Audit INSERT 失败，HTTP 返回 503 `PROVIDER_AUDIT_PERSISTENCE_FAILED`，没有 Result。关闭 MySQL 后调用返回非成功 5xx 且无 members/resultHash，证明连接故障同样 fail closed。Mapper connection failure 单元契约也通过。

## 15. Restart Recovery

一次性 MySQL 进程停止并用同一 datadir 重启后，88 条已写 Audit 全部保留；InMemory 原实现不具备的恢复能力已真实验证。

## 16. Concurrency

数据库 10/25/50 并发 INSERT 数量精确，无重复 auditEventId、无丢失、无部分记录。真实 HTTPS Provider 额外完成 10/25/50 并发 Directory Query；总审计数量精确为 161，无 1213/1205 结构性死锁。

## 17. Idempotency

冻结为“每次物理调用独立 Audit”。同 requestId/correlationId 允许多行，便于证明重试次数；每行由唯一 auditEventId 和 evidenceHash 区分。不会将不同物理尝试错误折叠为一个成功事实。

## 18. PII / Secret

表结构与生产实现扫描为 0 hit：不包含 Authorization、Token、Secret、Private Key、证书私钥、姓名、电话、证件号、地址、薪资或完整成员集合。仅保存查询业务键和 candidateCount。`retentionPolicy` 固定为 `NOT_YET_PRODUCTION_APPROVED`，未来归档/合规期限需单独审批。

## 19. Production Fake Scan

`src/main` 中 `InMemoryApprovalRoleDirectoryProviderAuditSink` 命中 0；Spring-wired Fake/InMemory/NoOp 命中 0。Test double 仅位于 `src/test`。结果：`PRODUCTION_FAKE_SCAN_PASS`。

## 20. Migration

- Version: `V2.6.20`
- SHA-256: `160f0b649ac85cc177d82c2ff0aa5070639b29c7f2dda72b02438041bd063201`
- Flyway checksum: `1721966129`
- Assets: `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- Fresh / V2.6.19 Upgrade / strict validate / second migrate no-op: PASS
- Fresh = Upgrade schema fingerprint: `c8aeccbe16f7172d91ec87dfc741306aa5312734533524ebb6d18872219049bb`
- Governed Migration SHA: `42/42 PASS`

首次重启探针因旧临时端口残留得到 FAIL，证据保留在 `D:/codex-validation-v2620-20260821-121701`；更换全新隔离端口后完整 PASS 位于 `D:/codex-validation-v2620-20260821-122042/evidence`。未使用 repair/baseline 绕过，旧 Migration 无漂移。

## 21. Workflow Regression

`EXPLICIT_USER_V1=ACTIVE`；`ROLE_DIRECTORY_V1=EXECUTION_ELIGIBLE / NON_ACTIVE`；`ROLE_RUNTIME=DISABLED`。USER/DIRECT/Legacy 未重新解析，未开启 Worker 或 Canary。

## 22. Full Tests

Java 21 compile、Spring Context、Directory/Provider/Audit/Workflow 专项和全量 Backend 均通过。最终全量：587 tests、0 failures、0 errors、6 个既有条件性 skip。Domain purity、Production dependency/Fake、PII/Secret、42/42 SHA 与 `git diff --check` 全部 PASS。真实 HTTPS 最终证据位于 `D:/codex-validation-org-dir-2-20260821-125102/evidence`。

## 23. Remaining Risks

生产 retention/归档期限尚未获批；本任务只验证隔离 TEST TLS 与 MySQL 8.4.9，不代表生产容量或合规审批。数据库整体不可用时 Provider 返回 fail-closed 5xx；由于 Directory 与 Audit 共库，无法持久记录“数据库完全不可达”本身，必须由平台日志/监控补充告警，但不会泄露可信 SUCCESS。

## 24. Release Freeze Re-entry Gate

P0 技术门禁已关闭，允许重新执行 Release Baseline Freeze。该许可不等于授权 commit/tag/push、Canary 或 ROLE Runtime activation；工作区仍 dirty，必须按原 Freeze 流程重新核验唯一可复现提交与外部资源授权。
