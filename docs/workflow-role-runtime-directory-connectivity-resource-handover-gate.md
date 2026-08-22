# Sprint 2-3.7-WF5.20.2-R1 Production Directory Connectivity Resource Handover Gate

## 1. 当前状态

- 数据库基线：V2.6.15 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- V2.6.15 SHA-256：`db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44`
- Flyway checksum：`538981271`
- `PRODUCTION_ROLE_DIRECTORY_ADAPTER_FRAMEWORK_READY`
- `PRODUCTION_ROLE_DIRECTORY_INTEGRATION_READINESS_READY`
- `PRODUCTION_ROLE_DIRECTORY_CONNECTIVITY_BLOCKED`
- `ROLE_DIRECTORY_V1_PREPARED_NON_EXECUTABLE`
- `ROLE_RUNTIME_DISABLED`

本门禁只管理资源交付，不执行 Connectivity，不访问外部 Endpoint，不读取 Secret，不进入成员查询或 Workflow Runtime。

## 2. WF5.20.2 阻断原因

当前环境变量、应用配置和部署模板中均没有可验证的 TEST/PREPROD Directory Endpoint、Provider/Environment Identity、TLS 证据或 Credential Reference。无法证明真实连接目标属于隔离 TEST/PREPROD 环境，因此 WF5.20.2 保持 `BLOCKED / NO-GO`。

## 3. Resource Checklist

状态只允许 `MISSING / DELIVERED_UNVERIFIED / VERIFIED`。本次没有收到外部交付包，所有项目均为 `MISSING`。

| 编号 | 分类 | resourceCode / 资源 | 状态 | Owner 角色 | Required Evidence | Level |
|---:|---|---|---|---|---|---|
| 1 | A | `ENVIRONMENT_NAME` TEST/PREPROD环境名称 | MISSING | Technical Owner | 环境台账引用 | P0 |
| 2 | A | `ENVIRONMENT_IDENTITY` | MISSING | Technical Owner | Provider签名/元数据中的TEST或PREPROD身份 | P0 |
| 3 | A | `PROVIDER_CODE` | MISSING | Directory Owner | 稳定providerCode及所有权说明 | P0 |
| 4 | A | `SERVICE_IDENTITY` | MISSING | Technical Owner | 服务注册/证书身份引用 | P0 |
| 5 | B | `ENDPOINT_IDENTITY` | MISSING | Network/Ops Owner | 受控Endpoint Descriptor | P0 |
| 6 | B | `HANDSHAKE_ENDPOINT` | MISSING | Technical Owner | 非成员查询Handshake路径契约 | P0 |
| 7 | B | `HEALTH_ENDPOINT` | MISSING | Technical Owner | Health路径及响应契约 | P1 |
| 8 | B | `CONTRACT_METADATA_ENDPOINT` | MISSING | Directory Owner | Metadata路径及字段契约 | P0 |
| 9 | B | `DIRECTORY_BASE_URL` | MISSING | Network/Ops Owner | TEST/PREPROD Base URL交付引用 | P0 |
| 10 | C | `TLS_CERTIFICATE_CHAIN` | MISSING | Security/Ops Owner | 证书链摘要、CA、subject hash、有效期 | P0 |
| 11 | C | `HOSTNAME_VERIFICATION` | MISSING | Security Owner | hostname/SAN校验要求 | P0 |
| 12 | D | `CREDENTIAL_REFERENCE` | MISSING | Security Owner | 只交付凭据引用，不交付原文 | P0 |
| 13 | D | `CREDENTIAL_DELIVERY_MODE` | MISSING | Security/Ops Owner | Secret Manager/安全环境变量/统一注入说明 | P0 |
| 14 | D | `AUTHENTICATION_SCHEME` | MISSING | Security Owner | mTLS/OAuth2/Service Token等协议说明 | P0 |
| 15 | B | `NETWORK_ALLOWLIST` | MISSING | Network/Ops Owner | 目标端口、方向、规则编号 | P0 |
| 16 | B | `SOURCE_IP_REQUIREMENT` | MISSING | Network/Ops Owner | 出口IP/CIDR与白名单确认 | P1 |
| 17 | E | `CONTRACT_VERSION` | MISSING | Directory Owner | 必须为ROLE_DIRECTORY_PORT_V1 | P0 |
| 18 | E | `CONTRACT_HASH` | MISSING | Directory Owner | 必须匹配冻结SHA-256 | P0 |
| 19 | F | `CANONICAL_VERSION` | MISSING | Directory Owner | 必须为ROLE_CANONICAL_JSON_V1 | P0 |
| 20 | F | `CANONICAL_TEST_VECTOR` | MISSING | Directory/Workflow Owner | 固定输入、Provider Hash、版本说明 | P0 |
| 21 | G | `REVISION_CAPABILITY` | MISSING | Data Owner | aggregate revision能力声明 | P0 |
| 22 | G | `REVISION_MONOTONICITY` | MISSING | Data Owner | 单调递增、不可复用、不可回退说明 | P0 |
| 23 | H | `HISTORICAL_EFFECTIVE_AT` | MISSING | Data/HR Owner | effectiveAt与历史快照能力声明 | P0 |
| 24 | I | `COMPLETE_SEMANTICS` | MISSING | Directory Owner | complete=true覆盖全成员/分区/Source说明 | P0 |
| 25 | I | `PAGINATION_SEMANTICS` | MISSING | Technical Owner | page limit、token、全页聚合失败语义 | P1 |
| 26 | E | `PROVIDER_ERROR_CODES` | MISSING | Technical Owner | HTTP/业务错误码表 | P1 |
| 27 | E | `RETRYABLE_ERRORS` | MISSING | Technical Owner | 明确可重试错误白名单 | P1 |
| 28 | E | `NON_RETRYABLE_ERRORS` | MISSING | Technical Owner | Auth/Contract/Canonical等不可重试清单 | P1 |
| 29 | K | `PROVIDER_SLA` | MISSING | Directory/Technical Owner | P50/P95/P99、error/timeout目标 | P1 |
| 30 | J | `TEST_ENTERPRISE_ID` | MISSING | Data Owner | 合成测试enterprise引用 | P0 |
| 31 | J | `TEST_ORGANIZATION_ID` | MISSING | Data/HR Owner | 合成测试org引用 | P0 |
| 32 | J | `TEST_ROLE_CODE` | MISSING | Business Owner | 非真实审批角色的稳定测试代码 | P0 |
| 33 | J | `TEST_MEMBER_DATA` | MISSING | Data/HR Owner | 合成用户与有效期证据 | P0 |
| 34 | J | `CANDIDATE_BOUNDARY_DATA` | MISSING | Data/Technical Owner | 0/1/N/overflow测试集说明 | P1 |
| 35 | L | `DIRECTORY_BUSINESS_OWNER` | MISSING | Sponsor | 责任角色引用与确认记录 | P0 |
| 36 | L | `DIRECTORY_DATA_OWNER` | MISSING | Sponsor | 数据责任角色引用与确认记录 | P0 |
| 37 | L | `ORGANIZATION_HR_OWNER` | MISSING | Sponsor | 组织人事责任角色引用 | P0 |
| 38 | L | `TECHNICAL_OWNER` | MISSING | Sponsor | 技术责任角色引用 | P0 |
| 39 | L | `SECURITY_AUDIT_OWNER` | MISSING | Sponsor | 安全审计责任角色引用与批准 | P0 |
| 40 | L | `CONNECTIVITY_TEST_APPROVER` | MISSING | Release Approver | WF5.20.2执行授权引用 | P0 |

## 4. Environment 资源

必须交付唯一 TEST 或 PREPROD 环境名称、`environmentIdentity`、providerCode 和 serviceIdentity。交付证据必须证明 `expectedEnvironment = providerEnvironment`，且不能引用 LOCAL、DEV 或 PRODUCTION。

## 5. Network 资源

交付 Base URL、Health/Handshake/Metadata Endpoint、DNS名称、端口、网络方向、Allowlist规则及 Source IP/CIDR。验收前只核对资源描述；不得预先探测地址。

## 6. TLS 资源

交付受信CA链、证书subject摘要或Hash、SAN/hostname规则、有效期、最低TLS版本和mTLS要求。禁止提供私钥，禁止 trust-all 或关闭hostname verification。

## 7. Authentication 资源

交付认证协议、Credential Reference、Secret来源类型、Owner、有效期和rotation policy。不得在邮件正文、工单正文、代码、YAML、测试Fixture或本报告中提交Secret原文。

## 8. Contract 资源

必须明确：

- Contract Version：`ROLE_DIRECTORY_PORT_V1`
- Contract Hash：`5e8f9870effc7f886671f3cc5077a7bf0419cd9a6698dd6ca01e9de0c01f708d`
- Handshake/Metadata响应Schema
- Provider错误码、retryable与non-retryable分类

任何漂移必须先走新Contract治理，不能在Connectivity阶段静默兼容。

## 9. Canonical 资源

必须声明 `ROLE_CANONICAL_JSON_V1`，并交付固定Test Vector的输入、预期Hash和生成版本。不得使用真实成员作为Test Vector。

## 10. Revision 资源

必须证明 enterpriseId + organizationId + roleCode 范围内使用 aggregate revision，且 revision单调递增、不可复用、不可回退；说明并发更新、回放及源系统合并时的生成规则。

## 11. Historical Query 资源

必须声明支持 `effectiveAt`，并明确 `[effectiveFrom,effectiveTo)` 语义、时区/精度和历史保留范围。仅支持当前成员视图不能通过P0门禁。

## 12. Complete / Pagination 资源

必须说明 `complete=true` 已涵盖所有页、分区和Source。分页需要提供pageSizeLimit、continuationTokenPolicy、过期语义及任一页失败时整体fail-closed的保证。

## 13. Test Data 资源

提前交付专用 testEnterprise、testOrg、testRole、合成testUsers及候选数量边界数据。不得包含真实党委会、董事会、经理层或生产审批人员。WF5.20.2 Connectivity仍不得调用成员查询；数据只供后续WF5.20.3使用。

## 14. SLA 资源

交付P50/P95/P99、error/timeout/partial目标、维护窗口、限流、最大payload、分页限制、告警责任和故障通知渠道。生产阈值不能由本项目自行猜测。

## 15. RACI

| 活动 | Responsible | Accountable | Consulted | Informed |
|---|---|---|---|---|
| Directory业务语义 | Directory Business Owner | Sponsor | Workflow Owner | Release Approver |
| 数据/历史/Revision | Directory Data Owner | Directory Business Owner | Organization/HR Owner | Workflow Owner |
| Endpoint/网络/TLS | Technical + Network/Ops Owner | Technical Owner | Security Owner | Workflow Owner |
| Credential/安全审计 | Security/Audit Owner | Security Approver | Technical Owner | Release Approver |
| Connectivity执行 | Workflow Technical Owner | Connectivity Test Approver | Directory/Security/Ops | Business Owner |

所有角色必须使用组织责任引用，不以未确认的个人姓名替代。

## 16. Secret 治理

报告和Evidence只允许记录：credentialReference、secretSourceType、owner、expiry、rotationPolicy及不可逆identity hash。禁止记录Secret、Token、Password、Private Key或可恢复内容。资源交付方必须通过受控安全渠道完成实际注入。

## 17. P0 门禁

以下全部达到 `VERIFIED` 前，禁止重新执行WF5.20.2：Environment Identity、Endpoint、TLS、Credential Reference、Provider/Service Identity、Contract Version/Hash、Canonical Version/Test Vector、Revision、Historical Query、Complete Semantics、专用Test Data、Technical Owner、Security Approval及Connectivity Test Approver。

`DELIVERED_UNVERIFIED` 不能关闭P0。

## 18. Evidence 要求

统一证据模型：

```text
RoleDirectoryResourceHandoverEvidence
- resourceCode
- status: MISSING | DELIVERED_UNVERIFIED | VERIFIED
- ownerRole
- evidenceReference
- environmentIdentity
- verifiedAt
- verifiedBy
- remarks
```

证据只在文档/交付台账中治理，本Sprint不持久化数据库。`evidenceReference` 必须指向受控配置、工单或安全资源标识，不能包含Secret正文。

状态流转：

`MISSING → DELIVERED_UNVERIFIED → VERIFIED`。

只有实际核验人与证据引用齐全才允许进入VERIFIED；禁止跳过核验或批量假定通过。

## 19. Resource Ready 判定

所有P0均为 `VERIFIED` 时才可标记：

`DIRECTORY_CONNECTIVITY_RESOURCE_READY`。

该状态仅表示允许重新启动WF5.20.2，不表示Connectivity PASS、Integration PASS、Resolver可执行或ROLE Runtime启用。当前40项均为MISSING，因此结论为：

`DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED`。

## 20. WF5.20.2 重启条件

1. P0台账全部VERIFIED。
2. Connectivity Test Approver给出一次性TEST/PREPROD执行授权。
3. 确认目标不是PRODUCTION、LOCAL或未知环境。
4. Secret通过安全注入机制提供且不输出值。
5. Startup Gate可以验证唯一Production Adapter、无Fake、环境/Provider/Contract一致。
6. 测试范围仍限定为Health、Metadata、Handshake、Canonical Vector、TLS和Authentication。
7. 明确禁止成员查询、Candidate解析、Task/Pool/Claim和Investment。

重启后必须重新验证TLS、Authentication、Provider/Environment Identity、Contract、Canonical、Revision/Historical/Complete能力及Fake隔离。

## 21. 风险

- 当前没有外部资源证据，任何Connectivity结论都不可复现。
- Credential若通过非安全渠道交付会造成泄漏风险。
- 环境或Provider标识不完整可能误连生产系统。
- 只交付URL而缺少Contract/Canonical/Revision语义，会导致“网络可达但业务契约不可用”。
- 使用真实审批角色或成员作为测试数据会突破数据与职责边界。

## 22. 下一步

将本清单分别交付 Directory Owner、组织人事负责人、技术负责人、安全负责人和运维/网络负责人。资源到位后逐项从 `MISSING` 更新为 `DELIVERED_UNVERIFIED`，完成证据核验后再进入 `VERIFIED`。所有P0 VERIFIED后，重新执行WF5.20.2；本阶段不得进入WF5.20.3。

最终状态：

- `DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED`
- `PRODUCTION_ROLE_DIRECTORY_CONNECTIVITY_BLOCKED`
- `ROLE_DIRECTORY_V1_PREPARED_NON_EXECUTABLE`
- `ROLE_RUNTIME_DISABLED`
