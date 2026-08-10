# Sprint 2-3.6 Workflow 可靠性增强实施报告

项目：县域国企数字化运营治理平台
日期：2026-08-08
结论：**代码实现及单元测试通过；V2.4.9 保持候选、尚未执行真实 MySQL 验收。**

## 1. 实现范围

本 Sprint 在 Investment 边界内完成 Workflow 可靠投递、可靠接收、回调安全和审计增强。未实现 Workflow 引擎，未创建或访问 Workflow 数据库，未修改 V2.4.0 至 V2.4.8 历史 Migration，也未改变投资决策状态机的权威边界。

### 1.1 Outbox Worker

- 新增定时扫描和批量候选查询；
- Redis `SET NX + TTL` 控制跨实例扫描锁；
- MySQL `FOR UPDATE SKIP LOCKED` 与乐观版本实现并发抢占；
- Worker 租约支持进程崩溃后的超时接管；
- 可配置线程池批量并行发送；
- 发送前复核 `payload_hash`；
- 2xx 成功确认并清理租约；
- 网络、429 和 5xx 按指数退避重试；
- 4xx、摘要冲突、无效响应和超出最大重试进入死信；
- 原 `afterCommit` 调用仅保留低延迟尝试，定时 Worker 是可靠性保障。

### 1.2 Inbox 事件治理

- 回调事件与原始 JSON、SHA-256 摘要共同持久化；
- `eventId` 去重并校验重复事件摘要；
- 校验 Workflow 实例、决策、快照、尝试号和事件序号；
- 乱序事件先持久化为 `BUFFERED`；
- 连续事件完成后自动顺序排空缓冲事件；
- Gap Worker 从 Workflow 事件查询接口补拉缺失序号；
- 失败分为摘要冲突、序号缺口、Workflow 异常和非法状态流转；
- 失败事件不伪造审批结论，也不跳过本地事件水位；
- 人工重放重新经过绑定校验、序号校验、领域状态机和事务控制。

### 1.3 回调安全

- 保留独立服务 Bearer 凭据；
- 新增 HMAC-SHA256 请求签名；
- 签名覆盖 HTTP 方法、路径、原始请求体摘要、时间戳、Nonce、RequestId 和契约版本；
- 时间戳偏差默认限制为正负 300 秒；
- Nonce 使用 Redis 原子登记并设置有效期；
- 支持当前密钥与上一把密钥的轮换窗口；
- 签名和摘要使用恒定时间比较；
- Redis 防重放存储不可用时 fail-closed；
- 安全审计不保存令牌、密钥或完整签名。

### 1.4 审计

新增可靠性审计能力，记录：

- `AUTO_RETRY`：自动重试；
- `DEAD_LETTER`：死信；
- `INBOX_FAILURE`：事件处理或缺口恢复失败；
- `GAP_DETECTED/GAP_RECOVERED`：乱序缺口及恢复；
- `MANUAL_REPLAY`：人工重放；
- `SECURITY_REJECTED`：签名、防重放或载荷冲突；
- `ADMIN_OPERATION`：管理员操作预留。

审计只保存摘要、原因码、TraceId、操作人、复核人和工单号，不保存敏感载荷。

## 2. 修改文件清单

### 2.1 新增后端文件

- `WorkflowReliabilityProperties.java`
- `WorkflowCallbackSecurityProperties.java`
- `WorkflowReliabilityConfig.java`
- `WorkflowOutboxWorker.java`
- `WorkflowInboxGapWorker.java`
- `WorkflowCallbackSecurityService.java`
- `WorkflowReliabilityAuditService.java`
- `WorkflowReliabilityController.java`
- `WorkflowReliabilityAuditEntity.java`
- `WorkflowReliabilityAuditMapper.java`

### 2.2 修改后端文件

- `WorkflowOutboxService.java`
- `WorkflowInboxService.java`
- `WorkflowAdapter.java`
- `WorkflowClient.java`
- `HttpWorkflowClient.java`
- `WorkflowCallbackController.java`
- `WorkflowOutboxEntity.java`
- `WorkflowInboxEntity.java`
- `WorkflowOutboxMapper.java`
- `WorkflowInboxMapper.java`
- `application.yml`

### 2.3 测试文件

- 新增 `WorkflowReliabilityEnhancementTest.java`；
- 更新 `InvestmentDecisionWorkflowTest.java`；
- 更新 `InvestmentDecisionApprovalClosureTest.java`。

### 2.4 数据库与治理文件

- 新增 `V2.4.9__enhance_workflow_reliability.sql`；
- 更新 `SHA256SUMS`；
- 更新 `database/flyway/migration-inventory.yml`；
- 更新 `database/migration/mysql/README.md`；
- 新增本实施报告。

## 3. 数据库变化

### 3.1 `investment_workflow_outbox`

候选 Migration 增加：

- `worker_id`；
- `locked_at`；
- `lock_until`；
- `dead_time`；
- `dead_reason`；
- 租约扫描索引 `idx_inv_workflow_outbox_lease`。

### 3.2 `investment_workflow_inbox`

候选 Migration 增加：

- `payload_json`；
- `replay_count`；
- `last_replay_time`；
- `last_replay_by`；
- `manual_review_required`；
- 缺口处理索引和重放、人工核查 CHECK 约束。

### 3.3 新增审计表

`investment_workflow_reliability_audit` 保存自动重试、死信、缺口、人工重放、安全拒绝和管理员操作证据。该表只关联 Investment 本地决策、绑定、Outbox 和 Inbox，不引用 Workflow 数据库。

### 3.4 权限

候选 Migration 新增：

```text
investment:workflow:replay
```

同时创建按钮节点并为 `SUPER_ADMIN` 补充权限与菜单授权。普通业务角色不会自动获得人工重放权限。

### 3.5 Migration 状态

| 项目 | 状态 |
| --- | --- |
| 版本 | V2.4.9 |
| SHA-256 | `96436a5f7b845e69a0c9a9ea55761898aeb5701b9df0eee3af132b02f968f0b8` |
| 资产状态 | `CANDIDATE` |
| 执行状态 | `NOT_EXECUTED` |
| 历史 Migration 修改 | 无 |
| Workflow 数据库对象 | 无 |

15 个受治理 Migration 的 SHA-256 清单校验全部通过。V2.4.9 已通过禁止 `clean`、`repair`、创建或删除数据库等静态策略检查，但本 Sprint 未把它标记为真实 MySQL 已验收。

## 4. API 列表

### 4.1 Investment 接收接口

| 方法 | 地址 | 功能 | 安全策略 |
| --- | --- | --- | --- |
| POST | `/api/internal/investment/workflow/v1/events` | 接收 Workflow 事件 | 服务令牌、HMAC、时间戳、Nonce、绑定校验 |
| POST | `/api/internal/investment/workflow/v1/inbox/{id}/replay` | 受控人工重放 | JWT、`investment:workflow:replay`、双人复核参数 |

### 4.2 Investment 调用 Workflow

| 方法 | Workflow 地址 | 用途 |
| --- | --- | --- |
| POST | `/api/workflow/v1/process-instances` | 幂等启动流程 |
| GET | `/api/workflow/v1/process-instances/{id}` | 查询流程实例 |
| GET | `/api/workflow/v1/process-instances/{id}/tasks` | 查询实例任务 |
| GET | `/api/workflow/v1/process-instances/{id}/events` | 按序补拉缺失事件 |
| POST | `/api/workflow/v1/process-instances/{id}/withdrawals` | 幂等撤回流程 |

审批任务操作仍属于 Workflow 中心，不在 Investment 内实现。

## 5. 测试结果

### 5.1 新增场景

- 正确 HMAC 签名通过；
- 重复 Nonce 被拒绝并写安全审计；
- 相同 `eventId` 不同载荷被拒绝并标记人工核查；
- Worker 数据库租约抢占；
- 网络失败进入指数退避；
- 达到最大重试进入死信；
- 乱序事件先持久化，再在缺口关闭后按序排空；
- 决策状态只由顺序合法事件推进；
- 人工重放方法声明专用 RBAC 权限；
- 原审批闭环的重复、异常和状态流转测试继续通过。

### 5.2 执行结果

```text
Java: 21.0.12
Maven: 3.9.9
mvn test: PASS
Tests: 187
Failures: 0
Errors: 0
Skipped: 0
Test suites: 52
```

Spring Boot 上下文测试同时通过。测试环境使用 H2，数据库映射检查中的 Investment 表缺失警告来自既有测试 Schema 未加载业务 Migration，不代表 V2.4.9 已完成 MySQL 结构验收。

## 6. 运行配置

关键环境变量：

- `WORKFLOW_OUTBOX_WORKER_ENABLED`：默认 `false`，V2.4.9 应用并验收后才能启用；
- `WORKFLOW_OUTBOX_BATCH_SIZE`；
- `WORKFLOW_OUTBOX_WORKER_THREADS`；
- `WORKFLOW_OUTBOX_MAX_RETRIES`；
- `WORKFLOW_OUTBOX_LOCK_SECONDS`；
- `WORKFLOW_HMAC_CURRENT_KEY_ID`；
- `WORKFLOW_HMAC_CURRENT_SECRET`；
- `WORKFLOW_HMAC_PREVIOUS_KEY_ID`；
- `WORKFLOW_HMAC_PREVIOUS_SECRET`；
- `WORKFLOW_HMAC_ALLOWED_SKEW_SECONDS`；
- `WORKFLOW_HMAC_NONCE_TTL_SECONDS`。

HMAC 密钥和 Workflow 服务令牌必须由密钥管理设施注入，禁止写入仓库、镜像和日志。

## 7. 剩余风险

1. V2.4.9 尚未完成空库和 V2.4.8 升级库的真实 MySQL 8 Flyway 验收，Worker 必须保持关闭。
2. 真实 Workflow 的事件补拉接口、错误码、限流和幂等行为尚需契约联调。
3. 当前 Workflow 回调对象仍采用现有扁平事件模型；与冻结事件信封的完整字段映射需在真实联调中确认。
4. 人工重放已实现申请人/复核人分离，但复核人的实际授权和工单真实性仍需接入统一运维审批体系。
5. Redis 高可用故障、时钟漂移、密钥轮换和大规模消息积压仍需故障演练与容量测试。
6. 达梦、人大金仓对 `SKIP LOCKED`、CHECK 和 ALTER TABLE 行为尚未适配验收，需要独立数据库方言方案。
7. 当前实现不包含 Workflow 引擎或 Workflow 生产连接，不能据此开放生产审批提交。

## 8. 结论

Workflow 可靠性增强代码已完成并通过 187 项后端测试。实现保持 Investment 与 Workflow 边界，未修改历史 Migration。下一步应开展 V2.4.9 真实 MySQL 验收，验收通过后再启用 Worker，并进入真实 Workflow 契约联调和故障演练。
