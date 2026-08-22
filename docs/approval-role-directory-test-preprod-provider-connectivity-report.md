# Sprint ORG-DIR-2 Approval Role Directory TEST/PREPROD Provider & Connectivity Validation

## 1. Final Conclusion

**PASS**。本 Sprint 已在隔离的 TEST 环境完成 Provider、HTTPS/TLS、服务身份认证、Metadata Handshake、Canonical Cross Validation、目录查询、历史时点、Revision Fence、Complete 语义及 Workflow `ProductionRoleDirectoryAdapter` 的真实连通验收。

最终状态：

- `APPROVAL_ROLE_DIRECTORY_PROVIDER_READY`
- `ROLE_DIRECTORY_TEST_PREPROD_CONNECTIVITY_VALIDATED`
- `DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED_CLEARED`
- `APPROVAL_ROLE_DIRECTORY_V1_DB_VALIDATED`
- `INTERNAL_APPROVAL_ROLE_DIRECTORY_AVAILABLE`
- `ROLE_DIRECTORY_V1_PREPARED_NON_EXECUTABLE`
- `ROLE_RUNTIME_DISABLED`
- `WORKFLOW_V1_INTERNAL_ENGINEERING_COMPLETE`

本结论只证明隔离 TEST 环境链路；不等同于 PREPROD 部署验收或生产 SLA。

## 2. Provider Architecture

实现链路：`internal HTTPS endpoint -> service authentication -> environment/contract gate -> ApprovalRoleDirectoryProviderFacade -> ApprovalRoleDirectoryQueryService -> MySQL -> transport DTO`。Provider 不访问 Workflow Runtime 表，不承担 Task、Candidate Pool、Claim、Admission 或 Investment 业务逻辑。

新增的 Provider 组件包括配置、受控内部 Controller、认证 Filter、Facade、Transport DTO、异常映射和无敏感信息的审计 Sink。Workflow 侧新增带服务身份与 TLS 的 `AuthenticatedHttpsRoleDirectoryClient`，继续由既有 `ProductionRoleDirectoryAdapter` 执行契约、完整性、Revision Fence 和候选上限治理。

## 3. Environment

- 环境身份：`TEST`
- Java：21.0.12
- Maven：3.9.9
- Spring Boot：3.5.9
- MySQL Community Server：8.4.9
- Flyway Community：13.0.0
- 隔离证据根目录：`D:\codex-validation-org-dir-2-20260820-175053`
- 测试数据：仅使用 `TEST_ENTERPRISE`、测试组织、测试角色和非生产测试用户编号。

Provider 启用时仅接受 `TEST` 或 `PREPROD`；`LOCAL`、`DEV`、`PRODUCTION` 均被配置门禁拒绝。

## 4. Endpoint Identity

本次真实端点为 `https://localhost:37812/api/internal/approval-role-directory`，仅在一次性隔离环境存活。稳定身份：

- providerCode：`ORG_GOV_APPROVAL_ROLE_DIRECTORY`
- providerVersion：`1.0.0`
- serviceIdentity：`enterprise-platform-approval-role-directory`
- environmentIdentity：`TEST`

接口仅包括 `health`、`metadata`、`canonical/verify`、`resolve`；未新增普通用户菜单、公开目录浏览接口或 Resolver 管理接口。

## 5. Authentication

采用独立服务身份加 Bearer service credential。Credential 仅由运行时环境注入，仓库和报告均无明文。真实负向结果：缺失 credential 返回 401、非法 credential 返回 401、错误 service identity 返回 403；无匿名 fallback。用户 JWT Filter 对该 internal 路径不解析 service credential，由专用 Filter 独立治理。

当前模型不包含 token 内置到期声明，因此没有伪造“expired credential”结果；过期/轮换由外部 Secret 生命周期管理，仍是部署门禁项。

## 6. TLS

真实链路使用 TLS 1.3、3072-bit RSA、受控 `TEST_ONLY` CA、默认 JSSE 信任链和 hostname verification。无 `trustAll`，无 hostname 禁用，无 HTTP downgrade。

负向验证：不受信证书被拒绝，错误 hostname 被拒绝。由于本次 TEST CA 为短期新签证书，未额外伪造过期证书；证书有效期由 TLS 握手正常校验。

## 7. Health

`health` 独立检查应用存活、数据库连接及四张 Approval Role Directory 表是否存在。真实响应为 ready。Health 只表示基础可用，不替代 Metadata、Contract、Canonical 和业务查询验收。

## 8. Metadata Handshake

真实 Metadata 返回并验证了 providerCode、providerVersion、serviceIdentity、environmentIdentity、contractVersion、contractHash、canonicalVersion、revision/historical/complete 能力和 `paginationSupported=false`。Workflow expected environment 与 Provider actual environment 精确一致；错误环境由既有 Connectivity Validator 负向测试 Fail Closed。

## 9. Contract

- contractVersion：`ROLE_DIRECTORY_PORT_V1`
- contractHash：`5e8f9870effc7f886671f3cc5077a7bf0419cd9a6698dd6ca01e9de0c01f708d`
- canonicalVersion：`ROLE_CANONICAL_JSON_V1`

冻结契约未修改。wrong provider、wrong environment、wrong contract version/hash、wrong canonical version 均由现有 Connectivity/Production Adapter 契约矩阵拒绝；不重试契约错误。

## 10. Canonical

Provider 与 Workflow 对同一固定向量进行实际交叉计算，结果一致：`92000bf2f44ed17cc9cca96db24fb5fb51fd23ad5a94a2090409600f68e749a8`。既有测试继续覆盖成员顺序不影响 Hash，以及 evidence、effectiveAt、revision 变化导致 Hash 变化；错误 resultHash Fail Closed。

## 11. Query

真实 resolve 输入仅接受 enterpriseId、organizationId、roleCode、effectiveAt 和 correlationId；无 fuzzy、includeChildren、groupWide、current fallback、default org/role。Transport DTO 仅暴露契约字段和最小成员/evidence，不暴露 Entity、Mapper 或 HR 字段全集。

当前查询与 Workflow Production Adapter 均通过；普通前端用户不能直接访问该 internal endpoint。

## 12. Historical

真实 MySQL 数据验证 `[effectiveFrom,effectiveTo)`：

- `2026-05-31T12:00:00Z` 返回测试用户 U1（revision 1，合并两条同义 evidence）。
- `2026-06-01T00:00:00Z` 返回测试用户 U2。

Provider 未将 historical query 转换为 current query。

## 13. Revision

真实接口完成 R1/H1、R2/H2、R3/H3 单调修订：

- R2 Hash：`d0d0ab0fccf7bf42949f25f5958ec47d90087fa26b61fe37ca6a5f6b900a9538`
- R3 Hash：`64e250e83c3358b9d562f57334f401d1fbcef3fe81a79f826b7c3e6654f58449`

Workflow 保存旧 fence 后面对新 revision 会以 `REVISION_MISMATCH` 拒绝，不使用旧成员缓存 fallback。

## 14. Complete

合法零成员查询真实返回 `complete=true` 和空集合；Workflow Adapter 随后按业务规则拒绝为 `ROLE_NOT_FOUND`。Source conflict 返回 `complete=false` 并拒绝。数据库/修订/来源异常不被伪装为完整结果。

## 15. Pagination

V1 明确 `paginationSupported=false`，一次响应必须完整。Provider 不截断结果，不制造部分页或把部分集合标成 complete。

## 16. Candidate Limit

真实 Provider 对 500 人角色返回完整 500 人集合；Workflow effective limit 为 100 时以 `CANDIDATE_LIMIT_EXCEEDED` 拒绝，没有截断或取第一人。依据本次 TEST 观测，100 是当前保守联调建议值；是否调整生产默认仍需业务容量评审。

## 17. Source Conflict

同一用户的多条同义来源 evidence 被 canonical 合并并全部保留。真实矛盾来源测试返回 `DIRECTORY_SOURCE_CONFLICT` 与 `complete=false`，Provider 不猜测、不 fallback。

## 18. Correction

真实 TEST 数据执行 correction 后发布 R3/H3；旧 revision 证据仍保留，新查询返回更正后的权威事实和新 Hash。未修改任何历史 Workflow Candidate/Task/Pool。

## 19. Workflow Production Adapter

最终验收使用真实 `ProductionRoleDirectoryAdapter -> AuthenticatedHttpsRoleDirectoryClient -> HTTPS Provider -> ApprovalRoleDirectoryQueryService -> MySQL 8.4.9`，未以 Fake、Mock Repository 或直接 Application Service 调用代替。真实适配测试进程 exit=0；main production source 中 Fake Adapter 引用数为 0。

## 20. Failure Matrix

| 类别 | 场景 | 结果 |
|---|---|---|
| Auth | missing / invalid / wrong identity | 401 / 401 / 403 |
| TLS | untrusted CA / wrong hostname / HTTP downgrade | 拒绝 |
| Contract | provider/env/version/hash/canonical mismatch | Fail Closed |
| Canonical | resultHash 漂移 | Fail Closed，不重试 |
| Directory | zero member | Provider 完整返回，Workflow 拒绝 |
| Directory | source conflict | `DIRECTORY_SOURCE_CONFLICT` |
| Revision | stale fence | `REVISION_MISMATCH` |
| Capacity | 500 > effective limit | 完整返回后由 Workflow 拒绝 |
| Isolation | Fake production leakage | 0 |

## 21. SLA

以下仅为 **TEST OBSERVED**，不是 Production SLA：60 次真实 HTTPS/MySQL 请求，成功率 100%，错误率 0%，超时率 0%，P50 16.065 ms、P95 33.982 ms、P99 37.103 ms、平均 payload 30,210 bytes。Provider/Adapter 指标事件覆盖请求、结果、候选数和延迟；DB/hash 分段由 Micrometer 事件提供，但本报告不把一次性本地采样外推为生产分位数。

## 22. Load Test

每档 12 次：

| Candidate | P50 ms | P95 ms | P99 ms |
|---:|---:|---:|---:|
| 1 | 13.987 | 17.893 | 17.893 |
| 10 | 14.078 | 20.356 | 20.356 |
| 50 | 15.897 | 18.003 | 18.003 |
| 100 | 16.312 | 18.855 | 18.855 |
| 500 | 33.322 | 37.103 | 37.103 |

这是隔离单机观测，不代表并发生产容量。

## 23. Timeout/Retry

实际 HTTPS Client 配置 connect timeout=2s、read timeout=5s、bounded retry=1。真实 transport 集成测试和 Adapter 回归验证：503/网络瞬态错误可有限重试；contract/canonical/source conflict/candidate limit/zero member 不重试；禁止无限重试和旧结果 fallback。当前隔离 Provider 正常链路没有产生真实超时，超时失败语义由受控 transport 测试覆盖。

## 24. Circuit Breaker

既有 Production Adapter 测试验证 CLOSED→OPEN、HALF_OPEN probe→CLOSED，OPEN 时 Fail Closed 且无缓存 fallback；真实 Provider 正常链路结束时 circuit 状态为 CLOSED。受控故障矩阵与真实正常链路共同构成本 Sprint 证据，未在共享 TEST Provider 上人为制造不可控宕机。

## 25. Metrics

真实链路产生 request/outcome/latency/candidate-count 指标事件；回归矩阵覆盖 failure、timeout、retry、contract mismatch、canonical mismatch、revision mismatch、source conflict 和 circuit state。指标后端可替换，Provider/Adapter 不依赖具体监控产品。

## 26. Audit Evidence

真实 Production Adapter 生成 `DirectoryResolutionAuditEvidence`，包含 queryHash、providerCode/version、environment、contractHash、revision、resultHash、candidateCount、resolvedAt、outcome 和 correlationId。queryHash 经验证为 64 位小写 hex；证据不保存 Secret 或完整 Member JSON。

## 27. Security/PII

internal endpoint 只允许指定 service identity；普通用户/匿名浏览器无目录读取权。配置只引用环境 Secret，未硬编码 token；日志和报告未输出 Authorization、credential、private key、手机号、身份证、地址、工资或完整 Member JSON。允许的运维证据限 userId、revision、hash、count、provider 和 outcome。

## 28. MySQL

隔离 MySQL Community Server 8.4.9 使用 canonical V2.6.17 Schema。Flyway 成功验证 40 条 history 记录，当前 schema 2.6.17；二次 migrate 为 `No migration necessary`。真实 Provider 的 health、historical、revision、source conflict、correction 和 load 均读取该实例，不使用 H2/Fake Repository 替代最终验收。

## 29. Workflow Regression

- Java 21 compile/package：PASS
- Spring Boot context：PASS
- Backend full tests：568 passed / 0 failed / 0 errors / 0 skipped
- Workflow tests：343 passed
- Organization tests：31 passed
- Domain purity：0 violation
- production main Fake reference：0
- `git diff --check`：PASS
- EXPLICIT_USER_V1、USER/DIRECT、Legacy：PASS，未调用 Directory

## 30. Migration State

本 Sprint **NO MIGRATION**。V2.6.17 未修改：

- SHA-256：`d5fdf54be071a1f4c347b61a447771bb0c849f2ff8d4113e9bedc7ae4d145b65`
- Flyway checksum：`-1602960257`
- 状态：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- `SHA256SUMS`：39/39 匹配，无摘要漂移

## 31. Connectivity Blocker

本 Sprint 定义的 TEST Provider 资源、TLS、认证、身份、Contract、Canonical、Revision、Historical、Complete、真实 Adapter、Fake leak、PII、SLA 采样和 Failure Matrix 均通过，因此 `DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED` 对当前 TEST 准入已解除。PREPROD 独立部署、正式证书/Secret 和网络 ACL 仍需按同一脚本与门禁复验。

## 32. Resolver State

`ROLE_DIRECTORY_V1 = PREPARED / NON_EXECUTABLE`，未注册为 ACTIVE，未标记 execution eligible。`EXPLICIT_USER_V1 = ACTIVE` 保持不变。

## 33. Runtime State

`ROLE_RUNTIME_DISABLED`。未创建 ROLE Task、Candidate Pool Runtime 或 Claim；未改变 Registry；未接入 Investment。

## 34. Remaining Risks

1. 本次是隔离 TEST 单机观测，PREPROD 网络、正式 CA/Secret 轮换、并发容量和故障注入仍须单独执行同门禁。
2. 当前 service credential 本身不携带到期声明；需由部署平台负责 Secret 过期、轮换和吊销。
3. 500 候选的性能可接受仅是本机观测；生产 platformMax 需结合并发、组织规模与审批治理确认。
4. 超时、503 和 circuit breaker 的失败语义由真实 transport 边界加受控故障测试证明，未在共享 TEST Provider 上做破坏性长时间宕机。

## 35. WF5.25 Resume Gate

ORG-DIR-2 的前置条件已满足，**允许后续任务恢复 WF5.25 的评估与验收**；本 Sprint 不自动恢复 WF5.25。恢复后仍必须独立验证 Realtime Eligibility、DataScope、Platform/Business SoD、External Audit、Feature Flag、Canary、Kill Switch 与 E2E ROLE Claim，且在这些门禁通过前继续保持 `ROLE_RUNTIME_DISABLED`。

## 修改文件清单

核心新增：

- Organization internal Provider：properties、DTO、Facade、Controller、service authentication filter、exception handler、audit sink/evidence。
- Workflow transport：`AuthenticatedHttpsRoleDirectoryClient`。
- 验证：Provider contract/auth、HTTPS client、真实 Production Adapter connectivity/load、架构边界测试。
- 验收脚本：`database/flyway/scripts/validate-org-dir-2-provider-connectivity.ps1`。
- 本报告。

必要修正：Security internal 路由隔离、`application.yml` 环境模板，以及 Approval Role Mapper 中 XML 比较符和既有审计列名映射错误。未修改 Investment、Workflow 冻结契约或任何 Migration SQL。
