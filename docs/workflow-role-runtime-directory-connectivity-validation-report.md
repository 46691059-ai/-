# Sprint 2-3.7-WF5.20.2 TEST/PREPROD Directory Connectivity Validation 报告

## 1. 最终结论

`BLOCKED / NO-GO`。

本机及工程部署配置中未发现已交付的 TEST/PREPROD Directory Endpoint、环境身份、服务身份、证书资料或安全 Credential Reference。依据 fail-closed 原则，未发起任何外部网络请求，不能将 Stub/Fake 测试结果冒充真实 Connectivity 验收。

当前状态：

- `PRODUCTION_ROLE_DIRECTORY_CONNECTIVITY_BLOCKED`
- `ROLE_DIRECTORY_V1_PREPARED_NON_EXECUTABLE`
- `ROLE_RUNTIME_DISABLED`

## 2. 测试环境

- 执行日期：2026-08-18
- Java：21.0.12
- Spring Boot：3.5.9
- 验收方式：本地代码审计、环境变量名称盘点、配置引用扫描、Stub Connectivity Contract Test、全量应用回归。
- 真实 TEST/PREPROD Provider：未交付。
- Production Provider：未访问。

## 3. Provider Identity

预期 providerCode、providerVersion、serviceIdentity 均未通过外部资源交付，因此实际 Provider Identity 状态为 `NOT_EXECUTED / BLOCKED`。Stub 测试证明错误 Provider 会 fail closed，但不构成真实 Provider 验收证据。

## 4. Environment Identity

环境变量中没有匹配 ROLE Directory 的配置名称，application/deploy 配置中也没有 TEST/PREPROD Directory 引用。无法证明 expectedEnvironment 与 actualProviderEnvironment 一致，状态为 `BLOCKED`。Validator 明确拒绝 LOCAL 和 PRODUCTION Connectivity。

## 5. Endpoint Identity

真实 endpointIdentity 未交付，未进行 DNS、TCP 或 HTTP 请求。代码模型要求 expected/actual Endpoint Descriptor 完全一致，并同时核对 providerCode、serviceIdentity、environment、contract version/hash。

## 6. Secret Source

- Secret source type：`NOT_PROVIDED`
- Credential identity/hash：`NOT_AVAILABLE`
- Secret 原文读取次数：0

新增 Connectivity Request 只能保存 Secret source type 与 credential identity hash，不提供 Token/Password 字段。

## 7. TLS

真实 TLS 验证未执行：TLS version、certificate subject hash、expiry、certificate chain 与 hostname verification 均无外部证据。Validator 要求 HTTPS、有效证书、hostname verification、可信证书摘要和未过期时间全部满足；禁止 trust-all。

## 8. Authentication

真实认证未执行，状态 `NOT_EXECUTED`。没有使用无效凭据测试 401/403，因为连合法 TEST/PREPROD Credential Reference 都未交付。Stub 测试证明 FAIL/NOT_EXECUTED 会阻断，且无 fallback/Fake 切换。

## 9. Contract Handshake

真实 Handshake 未执行。期望值保持：

- `ROLE_DIRECTORY_PORT_V1`
- `5e8f9870effc7f886671f3cc5077a7bf0419cd9a6698dd6ca01e9de0c01f708d`

Stub 测试覆盖 contract version/hash 漂移并 fail closed。

## 10. Canonical Handshake

真实 Provider 未返回 canonical metadata 或固定 Test Vector Hash，因此状态 `NOT_EXECUTED / BLOCKED`。Validator 要求 `ROLE_CANONICAL_JSON_V1` 与本地固定 Test Vector Hash 完全一致。

## 11. Revision Capability

未获得 Provider 关于 aggregate revision、单调递增、不可复用、不可回退的能力声明，状态 `NOT_READY`。Stub 缺失能力测试通过 fail-closed。

## 12. Historical Capability

未获得 `effectiveAt` 与 `[effectiveFrom,effectiveTo)` 历史语义声明，状态 `NOT_READY`。未查询任何真实历史成员。

## 13. Complete Semantics

未获得 Provider 对分页、分区、多 Source 全量聚合及 `complete=true` 的正式说明，状态 `NOT_READY`。

## 14. Pagination Capability

未获得 paginationSupported、pageSizeLimit、continuationTokenPolicy 或 completeAggregationSemantics 元数据。Connectivity Validator 要求分页聚合语义明确，否则阻断。

## 15. Timeout

真实 connect/read timeout 未执行。WF5.20 typed configuration 和 Stub 测试已验证零值、负值、超大值拒绝；这不替代真实 Endpoint timeout 测量。

## 16. Retry

Stub 测试验证 retry policy 必须有效、attempt 有界，且 contract/auth/canonical 等 non-retryable 错误不得重试。真实 503/timeout attempt count 未执行。

## 17. Circuit Breaker

Stub 验证 `OPEN` 与 `HALF_OPEN` 均不能取得 Connectivity Ready，只有 CLOSED 可进入下一层。未对真实 Provider 触发 Circuit。

## 18. Fake / Production Isolation

Connectivity 模型只接受 `AdapterType.PRODUCTION`；Fake 会产生 `FAKE_ADAPTER_LEAK`。新代码无 Spring 自动注册注解，Fake 未参与任何外部连接。

## 19. Adapter Uniqueness

模型要求 Production Adapter 实例数量精确为 1；大于或小于 1 均阻断。由于真实运行配置未交付，本次不能证明部署侧唯一实例，只证明框架检查有效。

## 20. Startup Gate

Startup Gate 逻辑与 Stub 测试通过，但真实环境缺少 Endpoint/Secret/TLS/Provider，实际 Startup Gate 不满足执行前提。Resolver Descriptor 未变化，仍是 PREPARED、enabled=false。

## 21. Connectivity Evidence

新增内部模型：

- `RoleDirectoryConnectivityRequest`
- `RoleDirectoryConnectivityValidator`
- `RoleDirectoryConnectivityResult`
- `RoleDirectoryConnectivityEvidence`
- `RoleDirectoryConnectivityBlockReason`
- `RoleDirectoryConnectivityCanonical`

Evidence 仅含 Provider/Environment、Endpoint Hash、Contract/Canonical、TLS/Auth 状态、能力布尔值、checkedAt、outcome 与 evidenceHash。当前没有生成“真实 Provider PASS Evidence”；仅生成 Stub 契约证据。

## 22. Metrics

框架可采集 handshake latency、connect/TLS/auth failure、contract/canonical mismatch、timeout、retry 与 circuit state；本次未接监控平台。由于未调用外部 Endpoint，真实 handshake latency 等指标为空。

## 23. Logging / PII

扫描通过：Connectivity Evidence 不包含 Secret、Token、Password、私钥或 Candidate 明细。代码没有输出 Credential；报告也不记录任何 Secret 值。

## 24. Production Isolation

- Production Endpoint 调用次数：0
- Production Secret 读取次数：0
- 真实生产 TLS/Handshake 调用：0

没有以未知地址或历史环境代替当前资源。

## 25. Business Query Isolation

- 真实 Role Member Query：0
- Candidate 解析：0
- CandidatePool 写入：0
- WorkflowInstance 创建：0
- Task 创建：0
- Claim 执行：0
- Investment 调用/修改：0

Connectivity 模型没有成员查询方法或业务运行入口。

## 26. Application Tests

- Connectivity/Readiness/Directory Stub 专项：通过。
- Failure Matrix：环境、Endpoint、Provider、TLS、认证、Contract、Canonical、Revision、Historical、Complete、Pagination、Retry、Circuit、Fake、多 Adapter、Startup、生产隔离和业务查询隔离均 fail closed。
- Evidence Hash：相同事实稳定，治理事实变化后改变。
- Java 21 compile：PASS。
- Spring Boot Context：PASS。
- 后端全量：505 项，0 failures，0 errors，0 skipped。
- Production Dependency Scan：0 个禁止依赖。
- Spring/Controller 自动注册扫描：0。
- `git diff --check`：PASS。

## 27. Migration State

- Migration 变化：0
- V2.6.16：不存在
- Migration SHA：37/37 匹配
- V2.6.15 SHA：`db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44`
- V2.6.15 保持 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- Flyway checksum 保持 `538981271`

## 28. Remaining Risks / 阻断项

以下 P0 资源未交付：

1. 明确标识为 TEST 或 PREPROD 的 Endpoint Descriptor。
2. providerCode、providerVersion、serviceIdentity 和 provider environment。
3. 合法的测试 Secret Reference 及来源类型，不含 Secret 原文。
4. TLS 证书链、hostname、有效期及信任策略。
5. Health/Metadata/Handshake/Canonical Vector Endpoint 契约。
6. Revision、Historical、Complete、Pagination 能力声明。
7. 受控 401/403、503、timeout 和 Circuit 探测方案。
8. 部署侧单一 Production Adapter 与无 Fake 的启动清单。

这些条件未关闭前不得标记 `PRODUCTION_ROLE_DIRECTORY_CONNECTIVITY_VALIDATED` 或 `READY_FOR_INTEGRATION_TEST`。

## 29. Next Step

保持本 Sprint 阻断，等待外部团队交付上述 TEST/PREPROD 资源。资源到位后重新执行 WF5.20.2，只调用 health、metadata、handshake、capability descriptor 和 canonical test vector，不调用成员查询。验收通过后才可建议 WF5.20.3；也可并行进入不依赖外部资源的 `WF5.21 Realtime Eligibility Capability` 设计。

最终结论：`PRODUCTION_ROLE_DIRECTORY_CONNECTIVITY_BLOCKED`。
