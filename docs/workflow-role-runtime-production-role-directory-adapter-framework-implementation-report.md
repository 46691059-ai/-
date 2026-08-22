# Sprint 2-3.7-WF5.20 Production Role Directory Adapter Framework 实施报告

## 1. 修改文件清单

生产代码新增于 `backend/src/main/java/cn/gov/enterprise/modules/workflow/infrastructure/directory/`：

- `ProductionRoleDirectoryAdapter.java`
- `RoleDirectoryClient.java`
- `JdkHttpRoleDirectoryClient.java`
- `RoleDirectoryTransportResponse.java`
- `RoleDirectoryClientProperties.java`
- `DirectoryFailure.java`
- `DirectoryCandidateLimitPolicy.java`
- `DirectoryRevisionFence.java`
- `RoleDirectoryCircuitBreaker.java`
- `InMemoryRoleDirectoryCircuitBreaker.java`
- `AdapterQualificationPolicy.java`
- `RoleDirectoryCapabilityDescriptor.java`
- `RoleDirectoryProductionStartupGate.java`
- `RoleDirectoryMetricsPort.java`
- `RoleDirectoryAuditPort.java`
- `DirectoryResolutionAuditEvidence.java`

测试代码新增：

- `ProductionRoleDirectoryAdapterTest.java`
- `JdkHttpRoleDirectoryClientIntegrationTest.java`
- `RoleDirectoryStartupGateTest.java`
- `ProductionRoleDirectoryDependencyGuardTest.java`

未修改 Controller、Investment、Resolver Registry、运行时任务链路、配置文件或 Migration。

## 2. Current Canonical Baseline

- 数据库最高版本：V2.6.15
- 资产状态：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- SHA-256：`db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44`
- Flyway checksum：`538981271`（沿用已验收基线，本 Sprint 未执行 Flyway）
- Resolver：`EXPLICIT_USER_V1 = ACTIVE`
- Resolver：`ROLE_DIRECTORY_V1 = PREPARED / NON_EXECUTABLE`
- Runtime：`ROLE_RUNTIME_DISABLED`

## 3. Existing Port Audit

现有 `RoleDirectoryPort`、`RoleDirectoryQuery`、`RoleDirectoryResult`、`RoleDirectoryMember` 与 `RoleDirectoryResolver` 已具备企业、组织、角色、生效时间、revision、complete、成员证据、result hash 和 contract hash 等核心语义。

本次未修改端口方法签名、查询模型、结果模型、`ROLE_DIRECTORY_PORT_V1`、既有错误枚举或冻结 Contract Hash。传输元数据 `providerVersion`、`resolvedAt` 和严格基础设施错误分类放在新基础设施边界中，避免静默改变既有 Domain Contract。

## 4. Contract 兼容结论

- 本地契约版本固定为 `ROLE_DIRECTORY_PORT_V1`。
- 本地期望 Contract Hash 固定为 `5e8f9870effc7f886671f3cc5077a7bf0419cd9a6698dd6ca01e9de0c01f708d`。
- 远端 `contractVersion` 与 `contractHash` 必须逐字完全一致，否则 fail closed。
- 没有新增 V2 Contract，也没有改变 V1 canonical 规则。

## 5. Production Adapter

`ProductionRoleDirectoryAdapter` 实现 `RoleDirectoryPort`，执行固定链路：

`RoleDirectoryQuery → Client → Contract → Complete → Scope/Revision → Member Evidence → Local Hash → Candidate Limit → RoleDirectoryResult`。

适配器只返回目录领域结果与可选 Revision Fence，不创建 WorkflowInstance、NodeExecution、Task、CandidatePool 或 Claim。该类没有 `@Component`、`@Service`、`@Bean` 或 `@Primary`，不会因出现在 classpath 自动进入运行态。

## 6. Transport Client

`RoleDirectoryClient` 是纯传输抽象；`JdkHttpRoleDirectoryClient` 使用 JDK HttpClient 发送不可变查询并解析 `RoleDirectoryTransportResponse`。HTTP timeout、5xx、4xx 和 IO/中断分别映射到稳定失败类型。鉴权/Secret 未硬编码，真实服务认证留给后续集成阶段的外层策略。

## 7. Config 模型

`RoleDirectoryClientProperties` 是 typed、secret-free 配置，包含 endpoint identity、connect/read timeout、有限 retry、environment identity、provider code、期望 contract version/hash。它拒绝相对地址、非 HTTP(S)、零/负/无限式 timeout、越界 retry、未知环境、非稳定 provider code 和非小写 SHA-256。

仓库未新增真实生产地址或明文 Secret。

## 8. Fake / Production 隔离

`AdapterQualificationPolicy` 依据 environment、adapter type、provider code 和 contract 信息显式判定。生产环境出现 Fake、Production Adapter 不唯一、Transport Client 不唯一或元数据不一致时均 BLOCKED；不依赖 bean 顺序或 `@Primary`。

## 9. Revision Fence

`DirectoryRevisionFence` 冻结 enterprise、organization、role、effectiveAt、revision、resultHash 与 contractHash。Verify 要求所有事实 exact match；`R2/H1`、`R1/H2` 或契约漂移均拒绝，不允许 revision-only 放行。

## 10. Complete 语义

只有 `complete=true` 的原子结果可进入 Domain。`complete=false` 映射 `PARTIAL_RESULT` 并 fail closed，部分成员不会返回 Resolver。

## 11. Contract Hash

Adapter 同时核对配置期望值、远端版本/Hash 与冻结本地版本。任何不一致均为 non-retryable `CONTRACT_MISMATCH`，无 fallback。

## 12. Result Hash

远端传入 `resultHash` 后，本地通过既有 `RoleDirectoryResult.hasValidHash()` 使用 `ROLE_CANONICAL_JSON_V1` 重算。成员输入顺序不影响 Hash，成员、revision 或其他治理事实变化会改变 Hash。远端 Hash 不被直接信任。

## 13. Candidate Limit

`DirectoryCandidateLimitPolicy` 使用 `min(platformMax, businessMax)`。零成员拒绝；1/N 成员完整保留；超过限制拒绝且绝不截断。生产最终阈值未写死。

## 14. Error Model

基础设施错误分为：

- Retryable：`NETWORK_TIMEOUT`、`TEMPORARY_UNAVAILABLE`、`TRANSIENT_DEPENDENCY_FAILURE`。
- Non-retryable：角色/组织、partial、source conflict、contract、revision、hash、candidate limit、配置与 circuit open 错误。

错误分类位于基础设施层，不改变冻结的 `RoleDirectoryErrorCode` 契约。

## 15. Timeout / Retry

连接和读取 timeout 均为强类型正时长。重试次数有限，只处理 retryable 错误；non-retryable 错误零重试。每次重试复用同一不可变业务 Query，只有传输关联信息可由外层变化。

## 16. Circuit Breaker

定义 `CLOSED / OPEN / HALF_OPEN` 抽象并提供最小内存实现。OPEN 时 Adapter 直接 fail closed，不读取旧缓存、不 fallback 到 EXPLICIT_USER。生产阈值和分布式实现留待真实集成准备阶段。

## 17. Cache 边界

本实现不跨请求缓存角色成员、有效性、DataScope 或 SoD。允许未来缓存的仅是 Contract Descriptor 和 Provider Metadata。

## 18. Startup Gate

`RoleDirectoryProductionStartupGate` 检查 typed config、唯一 client、唯一 Production Adapter、Fake 泄漏、provider/contract/environment、一致的依赖策略以及冻结 Resolver Descriptor。

Gate 通过仅返回 `READY_FOR_INTEGRATION_TEST`；返回值的 `resolverExecutionEligible` 固定为 `false`，且 Gate 无 Registry 引用和 mutation 能力。

## 19. Capability Descriptor

只读 Descriptor 包含 providerCode、providerVersion、contractVersion、contractHash、adapterType、environmentIdentity 和 capabilityStatus。Capability 状态与 Resolver Registry 状态是两个独立概念。

## 20. Fake Server Integration

测试通过本地 JDK `HttpServer` 覆盖真实 HTTP Transport 边界。首个 500 被分类为暂时不可用，有限重试后完整响应通过 Contract、Complete、本地 Hash、Revision 与 Candidate 校验；共调用两次。测试不连接任何外部 Directory。

## 21. Metrics

`RoleDirectoryMetricsPort` 提供 outcome、latency、candidate count 抽象及 no-op 实现；未接生产监控系统。

## 22. Audit Evidence

`DirectoryResolutionAuditEvidence` 仅记录 queryHash、revision、resultHash、contractHash、providerVersion、resolvedAt、candidateCount 与 outcome；通过 append port 输出，未接真实审计系统。

## 23. PII

日志/审计模型没有手机号、身份证、私人地址或人员姓名字段。成员证据只使用 userId、角色/组织、revision/hash 与必要来源事实。

## 24. Resolver 隔离

- `ROLE_DIRECTORY_V1` Descriptor 未修改，仍为 `PREPARED`、`enabled=false`。
- Production Adapter 未注入 `WorkflowAssignmentResolverConfiguration`。
- Startup Gate 不持有、修改或替换 `ResolverRegistry`。
- Adapter Framework READY 不等于 Resolver executable，更不等于 ACTIVE。

## 25. 测试结果

- Java：21.0.12，编译通过。
- Spring Boot Context：通过。
- 新增专项测试：10 项通过，0 失败。
- 本地 Fake HTTP Server 集成：通过。
- 后端全量：491 项通过，0 failures，0 errors，0 skipped。
- Domain 纯净：既有全量约束测试通过；新增代码位于 Infrastructure。
- Production Dependency Scan：通过，Adapter 不依赖 Investment、Task/CandidatePool/Claim Service、系统角色 Mapper、HR/Organization Mapper，也没有自动注册注解。
- PII 模型扫描：通过。
- `git diff --check`：通过。

## 26. Migration 状态

- Migration 变化：0。
- V2.6.16：不存在。
- `SHA256SUMS`：37/37 匹配。
- V2.6.15 SHA-256：`db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44`，无漂移。
- V2.6.15 继续保持 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。

## 27. 剩余风险

- 尚未接入真实 Approval Role Directory，因此真实鉴权、证书、网络拓扑、SLA 和 Provider 行为未验收。
- 当前 Circuit Breaker 是框架级内存实现，不是生产分布式治理实现。
- Startup Gate 是纯治理组件，尚未接入真实部署配置与生产启动生命周期。
- 未实现真实 DataScope、SoD、Audit、Feature Flag 或 Kill Switch Adapter。
- 没有生产级 Secret 引用/轮换实现；本 Sprint 明确禁止接入真实 Secret。

## 28. 下一步建议

仅建议进入 `WF5.20.1 Production Directory Integration Readiness`，准备真实 Provider 合同、非生产 Endpoint、服务认证、证书、SLA、配置交付和启动门禁接线；或者按路线进入 `WF5.21 Realtime Eligibility Capability` 的设计阶段。任何后续工作仍应保持 ROLE Runtime Disabled，未经验收不得创建 ROLE Task、CandidatePool 或 Claim。

最终状态：

- `PRODUCTION_ROLE_DIRECTORY_ADAPTER_FRAMEWORK_READY`
- `ROLE_DIRECTORY_V1_PREPARED_NON_EXECUTABLE`
- `ROLE_RUNTIME_DISABLED`
