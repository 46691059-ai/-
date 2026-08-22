# Sprint 2-3.7-WF5.20.1 Production Role Directory Integration Readiness 报告

## 1. Current Canonical Baseline

- 数据库最高版本：V2.6.15
- 状态：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- SHA-256：`db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44`
- Flyway checksum：`538981271`（沿用验收基线，本 Sprint 未执行 Flyway）
- `ROLE_DIRECTORY_V1 = PREPARED / NON_EXECUTABLE`
- `ROLE_RUNTIME = DISABLED`

## 2. Framework Baseline

WF5.20 已提供 Production Adapter、HTTP Transport、Contract/Hash/Complete/Revision/Candidate Limit 校验、有限重试、Circuit Breaker、Startup Gate、Metrics、Audit Evidence 及 Fake/Production 隔离。本 Sprint 在此基础上新增 Readiness Gate，不接真实 Directory。

新增生产代码：

- `RoleDirectoryIntegrationReadiness`
- `RoleDirectoryIntegrationReadinessRequest`
- `RoleDirectoryIntegrationReadinessResult`
- `RoleDirectoryIntegrationBlockReason`
- `RoleDirectoryIntegrationEvidence`
- `RoleDirectoryEnvironmentIdentity`
- `RoleDirectoryEndpointDescriptor`
- `RoleDirectorySecretProviderPort`
- `RoleDirectoryContractHandshake`
- `RoleDirectoryOwnershipDescriptor`
- `RoleDirectoryRaciApproval`
- `RoleDirectorySecurityReadiness`
- `RoleDirectoryCorrelation`

同时以兼容方式增强 `RoleDirectoryMetricsPort`、`DirectoryResolutionAuditEvidence`、`ProductionRoleDirectoryAdapter` 和 Transport DTO 的 PII 白名单行为；未修改 Domain Port 或 Resolver Registry。

## 3. Readiness 模型

状态冻结为：`NOT_READY / READY_FOR_CONNECTIVITY_TEST / READY_FOR_INTEGRATION_TEST / BLOCKED`。

当前 Gate 是真实接入前 Gate：22 类治理项全部满足时最高返回 `READY_FOR_CONNECTIVITY_TEST`。返回结果始终携带 `resolverExecutionEligible=false`，不会产生 Registry mutation。

## 4. Environment Identity

环境枚举：`LOCAL / TEST / PREPROD / PRODUCTION`。Gate 要求 expected、actual、provider handshake 三方环境完全一致。生产或预生产身份不能由 Spring Profile/bean 顺序隐式推断。

## 5. Endpoint Identity

Endpoint Descriptor 同时冻结 providerCode、environment、serviceIdentity、URI、contractVersion 与 contractHash。仅 URL 相同不足以通过；期望 Endpoint、实际 Endpoint 与 Provider Handshake 必须共同一致。

## 6. Secret 边界

新增 `RoleDirectorySecretProviderPort`，只回答 Credential Reference Capability 是否存在，不返回 Secret 内容。真实 Secret 未来只能来自 Secret Manager、环境 Secret 或统一安全配置源；本次未新增地址、Token、证书或密码。

## 7. TLS

PREPROD/PRODUCTION 必须使用 HTTPS，并同时声明证书校验与 hostname verification 能力。未提供关闭证书校验的测试或生产开关；mTLS 只保留后续能力边界。

## 8. Connectivity Gate

Gate 检查 Endpoint/Provider/Environment、Secret Provider、TLS、typed timeout/retry、Circuit 状态和生产依赖策略。当前实现不执行 DNS、TCP、TLS 或认证请求；这些只能在后续 TEST/PREPROD Connectivity Sprint 中显式授权后执行。

## 9. Contract Handshake

Handshake 包含 providerCode、providerVersion、environment、contractVersion、contractHash、canonicalVersion、canonicalVectorHash 和 supportedCapabilities。契约必须精确匹配：

- Version：`ROLE_DIRECTORY_PORT_V1`
- Hash：`5e8f9870effc7f886671f3cc5077a7bf0419cd9a6698dd6ca01e9de0c01f708d`

## 10. Canonical Gate

固定 `ROLE_CANONICAL_JSON_V1`。本地固定 Test Vector Hash 与 Provider 返回值必须完全一致；不一致映射 `CANONICAL_MISMATCH` 并 fail closed。

## 11. Revision 能力

Provider 必须声明 `AGGREGATE_REVISION`，并由接入证据证明 enterprise + organization + role 范围内 revision 单调递增、不可复用、不可回退。声明或证据任一缺失均 BLOCKED。

## 12. Historical Query

Provider 必须声明并证明 `HISTORICAL_EFFECTIVE_AT`，按 `[effectiveFrom, effectiveTo)` 支持历史查询。只返回当前成员不能通过 Gate。

## 13. Complete 语义

Provider 必须声明 `COMPLETE_RESULT`，且证明 `complete=true` 代表 Query 范围内所有 Source/Partition/Page 的完整成员集合。

## 14. Pagination

Readiness 要求分页聚合原子性：所有页成功后才能生成 complete 结果；任一页失败整体 fail closed，禁止使用已返回部分页。

## 15. Candidate Limit

只冻结 `effectiveLimit=min(platformMax,businessMax)` 的治理规则，不设置生产最终阈值。后续压测应覆盖 1/10/50/100/500/1000 候选规模，测量 latency、memory、hash、serialization 和 payload 后再冻结 platformMax。

## 16. SLA

进入 Connectivity Test 前必须具备 P50/P95/P99、timeout/error/partial/revisionMismatch/hashMismatch 的采集方案。当前只检查采集计划存在，不伪造阈值。

## 17. Timeout / Retry

沿用 typed config：timeout 必须为有限正值，retry 为有限次数；只有 retryable 错误可重试，non-retryable 零重试。Readiness Gate 要求配置已通过校验。

## 18. Circuit Breaker

只有 `CLOSED` 可进入 Connectivity Ready。`OPEN` 被阻断；`HALF_OPEN` 仅允许后续专用探测，不能承担业务流量，因此当前同样 BLOCKED。

## 19. Fake / Production 隔离

LOCAL/TEST 可包含 Fake；PREPROD/PRODUCTION 出现 Fake 均阻断。框架没有 `@Primary` 或隐式 bean 选择逻辑。

## 20. Multiple Adapter 保护

Production Adapter 数量必须精确为 1；0 或大于 1 均 `PRODUCTION_ADAPTER_NOT_UNIQUE`。Transport/Adapter 的实际部署唯一性仍由 WF5.20 Startup Gate共同约束。

## 21. Ownership / RACI

Ownership 使用稳定责任引用，不存个人姓名：Business、Data、Organization/HR、Technical、Security/Audit Owner 必须齐全。RACI 要求 Directory Owner、Workflow Owner、Security/Audit、Release Approver 四方确认，任一缺失均 NOT READY/BLOCKED。

## 22. Security

P0 项包括认证方式、最小授权、TLS 证书校验、hostname verification、受管 Secret 来源、日志脱敏、PII 最小化和访问审计。任何一项缺失均阻断。

## 23. PII

允许进入 Workflow 的字段限于 userId、organizationId、roleCode、assignment/source 证据、effectiveFrom/effectiveTo、source reference。Transport DTO 通过未知字段忽略策略丢弃额外 phone/idCard/salary 等字段；Domain、Metrics、Audit Evidence 不提供对应字段。

## 24. Logging

允许记录 correlationId、providerCode、revision、resultHash、candidateCount、latency、outcome。禁止记录 Candidate 完整对象、Credential、Secret 或敏感 PII。本次没有新增生产日志输出。

## 25. Metrics

Metrics 抽象已覆盖 request/success/failure、latency、timeout、retry、partial、revision mismatch、hash mismatch、candidate count 和 circuit state；仅使用 no-op/Fake 验证，不接监控平台。

## 26. Audit Evidence

成功和失败均可生成最小 Directory Evidence：queryHash、providerCode、providerVersion、contractHash、revision、resultHash、candidateCount、resolvedAt 与 outcome。失败证据不包含成员或 Secret。

## 27. Correlation

Directory 侧冻结 correlationId、directoryRequestId、workflowAdmissionId 三类引用，为未来 Admission → Directory → Candidate → Claim 追踪提供关联，但本 Sprint 不进入后续链路。

## 28. Failure Injection

现有 Fake/Stub 测试覆盖 HTTP 500、timeout 分类、partial、contract/hash/revision、empty/overflow、duplicate/source conflict、circuit open、配置错误和环境/Provider 漂移。后续 Connectivity Validation 仍需在隔离 TEST/PREPROD 中覆盖 DNS、TCP/TLS、503、真实认证和 malformed Provider payload。

## 29. Checklist

代码 Gate 固化以下准入项：Environment、Endpoint、Secret Provider、TLS、Provider、Contract Version/Hash、Canonical Version/Vector、Revision、Historical Query、Complete、Pagination、Candidate Limit、SLA、Timeout/Retry、Circuit、Metrics、Audit、PII、Ownership/RACI、Security、Fake Isolation、唯一 Adapter 和依赖策略。

全部通过只允许进入 `READY_FOR_CONNECTIVITY_TEST`。

## 30. Connectivity Test 边界

下一阶段只允许连接专用 TEST/PREPROD Provider，验证 DNS/TCP/TLS、Authentication、Handshake、Contract 与 Environment；禁止生产 Provider，禁止业务角色解析。

## 31. Integration Test 边界

只有 Connectivity Test 通过后才可执行专用测试 enterprise/org/role/users 的 Integration Query。禁止使用真实业务审批成员和生产数据。

## 32. 状态分层

`FRAMEWORK_READY → READY_FOR_CONNECTIVITY_TEST → READY_FOR_INTEGRATION_TEST → PREPROD_VALIDATED → PRODUCTION_READY`。

任何一层均不自动等于 Resolver `EXECUTION_ELIGIBLE` 或 `ACTIVE`。

## 33. Resolver 边界

`ROLE_DIRECTORY_V1` 仍为 `PREPARED / enabled=false`。Readiness 类不依赖 ResolverRegistry，不提供 mutation、promotion、fallback 或运行入口。

## 34. 测试结果

- Java 21.0.12 compile：PASS。
- Spring Boot Context：PASS。
- Readiness/Directory 相关专项：27 项 PASS。
- 后端全量：500 项，0 failures，0 errors，0 skipped。
- Provider 三方身份、Contract/Canonical、Revision/Historical/Complete、Secret/TLS、Circuit、Fake/重复 Adapter、Owner/RACI/Security、Metrics/Audit/PII/依赖策略负向测试：PASS。
- 失败 Audit Evidence 与细粒度 Metrics：PASS。
- Transport 额外敏感字段丢弃：PASS。
- Production Dependency Scan：PASS。
- `git diff --check`：PASS。

## 35. Migration 状态

- Migration 变化：0。
- V2.6.16：不存在。
- SHA256SUMS：37/37 匹配。
- V2.6.15 SHA：`db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44`，无漂移。
- V2.6.15 保持 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。

## 36. 剩余风险

- 未提供真实 TEST/PREPROD Endpoint、证书、认证能力或 Secret Reference，因此尚未执行 DNS/TCP/TLS/Authentication Handshake。
- Provider 的 revision 单调性、历史查询和 complete 语义目前只由治理模型与 Stub 测试证明，尚无外部证据。
- 生产 SLA 阈值、Candidate Limit、Circuit 参数仍需隔离压测后冻结。
- 真实 Security/Audit/RACI 签署与 Owner 责任引用尚未交付。
- 当前状态不能用于真实 ROLE 查询或 Runtime。

## 37. 下一步建议

下一步仅建议 `WF5.20.2 TEST/PREPROD Directory Connectivity Validation`：在明确授权且资源交付后，只连接隔离 TEST/PREPROD Provider，验证 TLS、认证、Handshake、Contract 和 Environment，不执行真实业务 ROLE 解析。也可转入 `WF5.21 Realtime Eligibility Capability` 的设计阶段。

最终状态：

- `PRODUCTION_ROLE_DIRECTORY_INTEGRATION_READINESS_READY`
- `ROLE_DIRECTORY_V1_PREPARED_NON_EXECUTABLE`
- `ROLE_RUNTIME_DISABLED`

此处的 `READINESS_READY` 仅表示治理框架允许评估下一阶段 TEST/PREPROD Connectivity，不表示外部资源已 READY、已连接真实 Directory 或 Resolver 可执行。
