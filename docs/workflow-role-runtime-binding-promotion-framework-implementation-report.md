# Workflow ROLE Runtime Binding Promotion Framework 实施报告

> Sprint：2-3.7-WF5.12
> 完成状态：`ROLE_RUNTIME_BINDING_PROMOTION_READY`
> 运行状态：`ROLE_RUNTIME_DISABLED`

## 1. 修改文件清单

### Domain

- `domain/role/promotion/RuntimeBindingPromotionStatus.java`
- `domain/role/promotion/RuntimeBindingPromotionRequest.java`
- `domain/role/promotion/RuntimeBindingPromotionEvidence.java`
- `domain/role/promotion/RuntimeBindingPromotionDecision.java`
- `domain/role/promotion/RuntimeBindingCandidate.java`
- `domain/role/promotion/RuntimeBindingPromotionAudit.java`
- `domain/role/promotion/RuntimeBindingPromotionCanonical.java`
- `domain/role/promotion/RuntimeBindingPromotionPolicy.java`
- `domain/repository/RuntimeBindingPromotionRepository.java`

### Application / Infrastructure

- `application/service/RuntimeBindingPromotionApplicationService.java`
- `infrastructure/persistence/InMemoryRuntimeBindingPromotionRepository.java`

### Test / Documentation

- `WorkflowRoleRuntimeBindingPromotionFrameworkTest.java`
- `docs/workflow-role-runtime-binding-promotion-framework-implementation-report.md`

未修改 Investment、V2.6.13 或任何历史 Migration；未新增 Controller、Mapper、Entity 或生产配置。

## 2. Domain 模型

| 模型 | 职责 |
| --- | --- |
| `RuntimeBindingPromotionRequest` | 冻结 Activation、Resolver、Binding、Candidate、Directory Revision、有效窗口和权限证据 |
| `RuntimeBindingPromotionEvidence` | 从权威 `ActivationAuditTrail` 生成 hash-only、不可变证据投影 |
| `RuntimeBindingPromotionDecision` | 保存 Promotion 状态、原因、Candidate 与完整审计轨迹 |
| `RuntimeBindingCandidate` | Promotion 成功后的非可执行治理候选；明确 `runtimeEnabled=false` |
| `RuntimeBindingPromotionAudit` | 以不可变列表追加状态迁移及每步 Hash |
| `RuntimeBindingPromotionPolicy` | 执行固定顺序、fail-closed 校验链 |
| `RuntimeBindingPromotionCanonical` | 实现 `ROLE_RUNTIME_BINDING_PROMOTION_CANONICAL_V1` |

所有 Domain 类型均不依赖 Spring、MyBatis、Entity 或数据库 DTO。Promotion 使用独立包，未改变既有 Eligibility 阶段的同名治理 Candidate 行为。

## 3. Promotion 链路

```text
ActivationAuditTrail（V2.6.13权威证据）
  ↓ hash-only projection
RuntimeBindingPromotionEvidence
  ↓
RuntimeBindingPromotionRequest
  ↓
RuntimeBindingPromotionPolicy
  ↓ fixed-order validation
RuntimeBindingPromotionDecision
  ↓ only when PROMOTED
RuntimeBindingCandidate(runtimeEnabled=false)
  ↓
InMemory append-only Repository
```

固定检查顺序：

1. Activation Evidence 存在；
2. Activation 已经过 APPROVED 并处于持久化终态 PERSISTED；
3. Activation Hash 一致；
4. Resolver Code、Version、Contract Hash 一致；
5. Binding Hash 一致；
6. Candidate Hash 一致；
7. Directory Revision 一致；
8. Business Scope 与 Effective Time 有效；
9. Resolver Registry 精确元数据检查通过；
10. Promotion 权限证据有效。

任一步失败均返回 `BLOCKED`，不执行后续补偿，不 fallback，不调用 Resolver，不重新解析 Directory，也不重新生成 Candidate 成员。

## 4. 状态机

正常链路：

```text
CREATED → VALIDATING → ELIGIBLE → APPROVED → PROMOTED
```

异常状态：`BLOCKED / REJECTED / REVOKED`。

`RuntimeBindingPromotionAudit` 校验所有状态边，禁止跳步。`PROMOTED` 只有在 Candidate 存在时成立；Candidate 构造器拒绝 `runtimeEnabled=true`。状态机不包含 ENABLED，也不存在从 PROMOTED 自动启用 Runtime 的代码路径。

## 5. Hash 治理

Canonical 版本：`ROLE_RUNTIME_BINDING_PROMOTION_CANONICAL_V1`。

Promotion Hash 覆盖：

- Activation ID / Hash；
- Resolver Code / Version / Contract Hash；
- Binding Hash；
- Candidate Hash；
- Directory Revision；
- Business Scope；
- Effective From / Until；
- Permission Evidence Hash；
- Activation Promotion Evidence Hash。

Evidence Hash 额外冻结持久化状态、Approval Evidence Hash、Activation Audit Hash、Directory Contract Hash 与生效时间。序列化使用固定字段顺序、长度前缀和 UTF-8 SHA-256，Hash 统一为 64 位小写十六进制。

不包含数据库 ID、审计创建/更新时间、乐观锁、线程信息或当前 Registry 顺序。同输入 Hash 稳定，Activation、Contract、Binding、Candidate、Directory Revision 或有效范围变化都会改变结果或被校验链阻断。

## 6. Repository 边界

`RuntimeBindingPromotionRepository` 只暴露：

- `insert(decision)`；
- `findByPromotionId(id)`；
- `findByPromotionHash(hash)`。

没有 update、delete、enable 或 Runtime Binding 写入能力。当前 `InMemoryRuntimeBindingPromotionRepository` 使用 `putIfAbsent` 保证单 JVM 内同一 Promotion ID 只能追加一次；它未注册为 Spring Bean，也不是生产持久化方案。

`RuntimeBindingPromotionApplicationService` 同样没有 `@Service`，只作为内部框架入口，由调用者显式装配。重复 Promotion 被拒绝，不覆盖既有审计结果。

## 7. 测试结果

新增 8 项定向测试覆盖：

1. 完整 Activation Evidence 正常 Promotion；
2. Evidence 缺失及未批准状态拒绝；
3. Activation、Contract、Binding、Candidate Hash 漂移拒绝；
4. Directory Revision、Effective Time、Promotion 权限校验；
5. 重复 Promotion 拒绝；
6. 不创建 WorkflowInstance、Task、CandidatePool 或 Claim；
7. EXPLICIT_USER_V1 保持 ACTIVE，Legacy 不进入 Promotion；
8. Domain 无框架、Mapper、Entity 依赖。

执行结果：

| 检查 | 结果 |
| --- | --- |
| Java 21 compile | PASS |
| Promotion 定向测试 | 8/8 PASS |
| Spring Boot context | PASS |
| 后端全量测试 | 442 通过，0 失败，0 错误，0 跳过 |
| Domain 纯净检查 | PASS |
| Migration SHA | 35/35 |
| `git diff --check` | PASS |

## 8. Migration 状态

本 Sprint Migration 数量为 **0**：

- 未创建 V2.6.14；
- 未修改 V2.6.13；
- V2.6.13 SHA 与既有 35 项 Migration 摘要全部保持；
- 未执行 Flyway migrate、repair、baseline 或 history 修改。

原因：当前只生成内存治理 Candidate，不要求跨进程恢复，也不写入真实 Binding Set。正式持久化 Promotion 前仍可依据 WF5.11 规划独立评审 V2.6.14，但不得与 Runtime 启用合并实施。

## 9. API 与 Registry 状态

- 新增 API：0；
- 新增 Controller：0；
- `EXPLICIT_USER_V1`：`ACTIVE`；
- `ROLE_DIRECTORY_V1`：`PREPARED / NON_EXECUTABLE`；
- ROLE Runtime：`DISABLED`。

Promotion Registry 检查仅调用 metadata-only `RuntimeEligibilityRegistry.inspect`。Promotion 成功不会修改 ResolverRegistry，也不会使 PREPARED Resolver 可执行。

## 10. 风险

- 内存 Repository 不具备跨实例、重启恢复或数据库级并发唯一性，只适用于当前治理框架阶段。
- Promotion 权限以已经完成上游校验的不可变权限证据 Hash 和布尔结论输入；正式接入前必须由安全 Application Port 产生，禁止信任外部请求直接传值。
- 当前 Effective Scope 仅有业务范围和时间窗口；未来绑定具体 Definition/Version/Node 时还需冻结目标节点 Hash。
- PREPARED Resolver 可通过治理元数据检查，但仍不可执行；后续物化层必须再次校验 ACTIVE、人工 Runtime 开关及撤销事实。
- 当前未接真实 Directory，因此没有证明人员目录的可用性、时效性或业务 SoD。

## 11. 完成结论

Runtime Binding Promotion Framework 已完成 Activation Evidence 到非可执行 Runtime Binding Candidate 的治理转换。未生成 Workflow Instance、Node Execution、Task、Candidate Pool 或 Claim，未接入 Investment/Directory，未启用 ROLE Runtime。

最终状态：

- `ROLE_RUNTIME_BINDING_PROMOTION_READY`
- `ROLE_RUNTIME_DISABLED`

下一阶段不得直接进入 ROLE Task、Candidate Pool Runtime、Claim Runtime 或 Investment Integration。若继续，应先独立治理 Promotion 持久化、目标 Node 绑定、跨实例幂等、撤销传播和人工 Runtime Enable 门禁。
