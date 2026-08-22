# Workflow V1.0 发布准入检查清单

## A. Internal Engineering Gate — PASS

- [x] Domain/Application/Port/Infrastructure 边界审计通过
- [x] Java 21 编译、Spring Context、Workflow 专项与后端全量测试通过
- [x] Migration SHA 38/38；V2.6.15/V2.6.16 无漂移；无 V2.6.17
- [x] Canonical Contract、Legacy/USER/DIRECT、运行安全、PII、生产 Fake 依赖扫描通过
- [x] `git diff --check` 通过
- [x] `EXPLICIT_USER_V1=ACTIVE`
- [x] `ROLE_DIRECTORY_V1=PREPARED/NON_EXECUTABLE`
- [x] `ROLE_RUNTIME=DISABLED`；Kill Switch 默认 `STOP_NEW_AND_CLAIM`

结论：内部工程基线可冻结；该结论不代表 ROLE 可在生产启用。

## B. External Capability Gate — BLOCKED

- [ ] TEST/PREPROD Directory endpoint 与环境身份
- [ ] TLS CA、客户端证书（如需）、认证模式、Credential Reference
- [ ] Provider Contract handshake 与 Canonical Test Vector
- [ ] Revision fence、historical `effectiveAt`、complete/pagination 语义
- [ ] Directory 测试数据与 SLA
- [ ] Realtime Eligibility Directory、DataScope、平台 SoD、业务 SoD、External Audit

结论：`WF5.25_EXTERNAL_RESOURCE_BLOCKED` / `DIRECTORY_CONNECTIVITY_RESOURCE_BLOCKED`。

## C. TEST/PREPROD Gate — NOT_EXECUTED

- [ ] 真实 Directory 连通性、认证、TLS 与错误语义验证
- [ ] Historical、Revision、Complete、Pagination 与 SLA 验证
- [ ] DataScope、SoD、Audit 完整链验证
- [ ] 真实配置源与隔离数据库演练

## D. Canary Gate — NOT_EXECUTED

- [ ] Feature Flag 真实配置源
- [ ] 企业/流程定义/版本/节点精确 Canary 范围
- [ ] Kill Switch 生产控制源与权限隔离
- [ ] 监控、告警阈值、停止条件和证据保全演练

## E. Production Enablement Gate — NOT_EXECUTED

生产启用至少要求：真实 Directory 联调；Historical/Revision/Complete/SLA；DataScope；平台及业务 SoD；External Audit；Feature Flag、Canary、Kill Switch 的真实控制源；Security Approval；RACI Owner；备份；变更窗口；回滚方案；监控与告警阈值。任一项缺失均 Fail Closed。

禁止将 A 门通过解释为 B/C/D/E 通过，禁止输出 `PRODUCTION_READY`、`ROLE_RUNTIME_ENABLED` 或 `ROLE_DIRECTORY_ACTIVE`。
