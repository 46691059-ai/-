# Sprint 2-3.3 投资决策 Workflow Adapter 实施报告

## 1. 实施结论

本 Sprint 已完成投资决策与外部 Workflow 中心的应用层集成。Investment 仅保存业务决策、冻结快照、外部实例绑定和消息投递状态，不实现审批引擎、不创建 Workflow 数据库，也不跨库访问 Workflow 数据。

提交链路采用本地事务 Outbox：决策、不可变快照、Workflow 绑定和发件箱消息在同一事务中写入；事务提交后才调用 Workflow。审批回调通过 Inbox 的 `event_id` 唯一约束和绑定表的 `last_event_sequence` 水位实现幂等及顺序保护。

## 2. 修改文件清单

### 后端新增

- 领域模型：`InvestmentDecisionCase`、`DecisionSnapshot`、`WorkflowBinding`
- 领域仓储：`InvestmentDecisionRepository`
- 应用命令与服务：`CreateInvestmentDecisionCommand`、`InvestmentDecisionApplicationService`
- Workflow 端口：`WorkflowGateway`、`WorkflowEvent`
- Workflow Adapter：`WorkflowClient`、`HttpWorkflowClient`、`WorkflowAdapter`、`WorkflowProperties`、`WorkflowClientConfig`
- 可靠消息：`WorkflowOutboxService`、`WorkflowInboxService`
- 持久化：5 个 Entity、5 个 Mapper、`InvestmentDecisionRepositoryImpl`、`DecisionMaterialsRow`
- REST：`InvestmentDecisionController`、`WorkflowCallbackController`
- 测试：`InvestmentDecisionWorkflowTest`

### 后端修改

- `SecurityConfig`：仅放行内部 Workflow 回调地址；回调仍由独立服务凭证认证。
- `InvestmentPermissions`：增加四项决策细分权限。
- `application.yml`：增加外部 Workflow 地址、服务令牌、回调令牌及流程定义配置，均支持环境变量覆盖。
- `InvestmentPermissionContractTest`：同步冻结后的权限集合。

### 数据库与治理文件

- 新增 `V2.4.7__add_investment_decision_workflow_reliability.sql`。
- 更新 `SHA256SUMS`、Migration Inventory 和 MySQL Migration README。
- 未修改 `V2.4.0` 至 `V2.4.6` 或其他历史 Migration。

## 3. 数据库变化

V2.4.7 为候选增量，尚未宣称真实 MySQL 验收通过：

- `investment_workflow_outbox`：保存启动/撤回命令、幂等键、载荷哈希、重试次数和投递状态。
- `investment_workflow_inbox`：保存回调事件、事件序号、处理状态和载荷哈希，用于重复事件过滤。
- 新增权限：
  - `investment:decision:view`
  - `investment:decision:create`
  - `investment:decision:submit`
  - `investment:decision:withdraw`
- 为既有投资决策菜单补充四个按钮节点，并为 `SUPER_ADMIN` 幂等授权。

所有新增业务表均包含审计字段、逻辑删除、`delete_token`、乐观锁字段、唯一约束、索引、外键和 CHECK 约束。Workflow 实例 ID 仅作为不透明字符串保存，没有跨库外键。

## 4. 业务调用链

### 创建决策

`Controller -> InvestmentDecisionApplicationService -> InvestmentDecisionRepository -> Mapper`

创建前通过 `InvestmentLifecycleStagePolicy` 复用 Project 访问控制，并检查决策编号唯一性。

### 提交决策

1. 悲观锁定决策记录并验证可提交状态。
2. 校验投资方案为 `FROZEN` 且具有内容哈希。
3. 校验可研版本为 `FROZEN + RECOMMENDED`。
4. 校验尽调包为 `FROZEN + PASS`、无未关闭阻断项且具有内容哈希。
5. 冻结上述精确版本引用并计算 SHA-256 快照哈希。
6. 创建 `attempt_no`、Workflow 绑定和 Outbox 消息。
7. 本地事务提交后由 Adapter 启动 Workflow。

相同决策处于 `SUBMITTING` 或 `IN_WORKFLOW` 时，重复提交直接返回现有快照和尝试次数，不重复启动流程。

### 回调

回调入口为内部 API，使用外部化 `WORKFLOW_CALLBACK_TOKEN` 做常量时间比对。处理时验证 Workflow 实例、决策、快照和尝试次数全部匹配；重复事件返回 `DUPLICATE`，序号跳跃事件保存为 `BUFFERED`，连续事件才更新业务状态和同步水位。

## 5. API 列表

| 方法 | 地址 | 权限 | 用途 |
|---|---|---|---|
| POST | `/api/investment/decisions` | `investment:decision:create` | 创建决策事项 |
| POST | `/api/investment/decisions/{id}/submit` | `investment:decision:submit` | 冻结快照并提交 Workflow |
| POST | `/api/investment/decisions/{id}/withdraw` | `investment:decision:withdraw` | 请求撤回外部流程 |
| GET | `/api/investment/decisions/{id}/status` | `investment:decision:view` | 查询业务及 Workflow 摘要状态 |
| GET | `/api/investment/decisions/{id}/workflow` | `investment:decision:view` | 查询外部流程状态 |
| GET | `/api/investment/decisions/{id}/tasks` | `investment:decision:view` | 查询外部流程任务 |
| POST | `/api/internal/investment/workflow/v1/events` | 服务凭证 | 接收 Workflow 审批事件 |

## 6. 测试结果

- Java：21.0.12。
- Maven 编译：通过。
- 后端全量测试：170 项通过，0 失败，0 错误。
- 新增专项测试：5 项通过。
  - 快照冻结精确版本及哈希。
  - 重复提交不重复调用 Workflow。
  - Workflow 启动失败写入 `FAILED/START_FAILED` 并保留重试事实。
  - 重复回调按事件 ID 过滤。
  - 四项应用服务权限注解契约。
- Spring Boot 上下文：通过。
- Migration 静态资产校验：`SHA256SUMS` 13/13 匹配，未发现漏登记 SQL。
- V2.4.7 真实 MySQL/Flyway 执行：本 Sprint 未执行，Inventory 状态为 `CANDIDATE / NOT_EXECUTED`。

## 7. 剩余风险与下一步

1. V2.4.7 必须在隔离 MySQL 8 环境执行 fresh/upgrade 两条 Flyway 路径后，才能晋级 `CANONICAL_IMMUTABLE`。
2. 当前 Outbox 在事务提交后立即投递并记录失败，尚需独立定时 Worker 对 `FAILED` 消息按退避策略重试及转入死信。
3. `BUFFERED` Inbox 已安全落库但尚需顺序补偿 Worker，在前序事件到达后自动重放。
4. 回调当前采用独立 Bearer 服务凭证；生产建议在网关层叠加 mTLS、时间戳/Nonce 和 HMAC 签名。
5. Risk/Audit 仍为稳定引用和字段预留，风险门禁、审计中心事件转发应在对应模块接入时完成。
