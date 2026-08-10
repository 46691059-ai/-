# Sprint 2-3.7 真实 Workflow 联调报告

项目：县域国企数字化运营治理平台
检查日期：2026-08-08
当前状态：**BLOCKED / REAL INTEGRATION NOT EXECUTED**

## 1. 联调环境

本次按真实 Workflow 联调要求执行环境发现，未使用模拟器、Mock 服务或自建 Workflow 数据库替代真实流程中心。

### 1.1 前置检查结果

| 检查项 | 结果 |
| --- | --- |
| `WORKFLOW_BASE_URL` | 未配置 |
| `WORKFLOW_SERVICE_TOKEN` | 未配置 |
| `WORKFLOW_CALLBACK_TOKEN` | 未配置 |
| `WORKFLOW_HMAC_CURRENT_KEY_ID` | 未配置 |
| `WORKFLOW_HMAC_CURRENT_SECRET` | 未配置 |
| 默认 Workflow 地址 `127.0.0.1:8090` | 无服务监听，健康检查不可达 |
| Investment 地址 `127.0.0.1:8080` | 无服务监听 |
| MySQL `127.0.0.1:3306` | 无服务监听 |
| Redis `127.0.0.1:6379` | 无服务监听 |
| Docker CLI | 当前环境不可用 |
| 仓库 Workflow 服务实现 | 不存在，符合“不实现 Workflow 引擎”原则 |
| 非示例联调配置文件 | 未发现 |
| Workflow 数据库 | 未创建、未连接 |
| 生产环境连接 | 未建立 |

仓库中的部署环境模板未包含可用的 Workflow 地址和凭据。后端默认值仅指向 `http://127.0.0.1:8090`，该地址当前不可用，不能视为真实联调环境。

### 1.2 Worker 状态

`WORKFLOW_OUTBOX_WORKER_ENABLED` 未设置，应用默认配置为 `false`。本次未启动 Investment，因此 Worker 从未开启，也没有产生真实消息发送、副作用或死信数据。

## 2. 接口验证

由于真实 Workflow 服务不可达，以下接口均未执行真实请求：

| 接口 | 状态 |
| --- | --- |
| `POST /api/workflow/v1/process-instances` | 未执行 |
| `GET /api/workflow/v1/process-instances/{id}` | 未执行 |
| `GET /api/workflow/v1/process-instances/{id}/tasks` | 未执行 |
| `GET /api/workflow/v1/process-instances/{id}/events` | 未执行 |
| `POST /api/workflow/v1/process-instances/{id}/withdrawals` | 未执行 |
| `POST /api/internal/investment/workflow/v1/events` | 未执行真实回调 |
| `POST /api/internal/investment/workflow/v1/inbox/{id}/replay` | 未执行 |

因此不能确认真实 Workflow 的流程定义、错误码、任务模型、事件结构、限流、幂等和撤回行为与冻结契约一致。

## 3. 事件链路

以下真实链路尚未执行：

```text
Investment提交决策
  -> 同事务写Outbox
  -> Worker抢占并启动Workflow
  -> 保存workflow binding
  -> Workflow创建任务
  -> 任务查询与审批操作
  -> HMAC签名回调
  -> Inbox去重和顺序处理
  -> Investment决策状态闭环
```

对应验收状态：

- 流程启动：未执行；
- Workflow binding 保存：未执行；
- 任务创建和查询：未执行；
- 审批通过：未执行；
- 审批驳回：未执行；
- 撤回：未执行；
- 异常事件：未执行。

## 4. 异常与可靠性测试

以下测试只在既有代码单元测试中有覆盖，本 Sprint 未获得真实外部服务证据：

| 场景 | 真实联调状态 |
| --- | --- |
| 重复事件 | 未执行 |
| 乱序事件和缺口补拉 | 未执行 |
| HMAC签名失败 | 未执行 |
| Nonce重复 | 未执行 |
| 时间窗口过期 | 未执行 |
| Workflow超时 | 未执行 |
| Worker自动重试 | 未执行 |
| 最大重试和死信 | 未执行 |
| 失败恢复后成功确认 | 未执行 |

禁止将此前的单元测试或本地 MySQL Migration 验收结果表述为“真实 Workflow 联调通过”。

## 5. 恢复联调所需条件

需要由联调环境负责人通过安全配置渠道提供或注入以下内容，敏感值不得写入 Git、报告或聊天记录：

1. 非生产 `WORKFLOW_BASE_URL` 和健康检查地址；
2. `WORKFLOW_SERVICE_TOKEN` 与 `WORKFLOW_CALLBACK_TOKEN`；
3. HMAC 当前 `keyId` 和密钥；
4. 可用的 `investment-decision` 流程定义版本；
5. 非生产 MySQL 8 数据库连接和已应用至 V2.4.9 的 Schema；
6. 非生产 Redis 连接；
7. Investment 发起人、Workflow 审批人和撤回人测试账号；
8. 党委前置研究、董事会或经理层的测试流程路线；
9. Workflow 事件补拉和任务审批接口的正式契约；
10. 允许进行超时、重复、乱序、签名失败和死信演练的联调窗口。

配置到位后，应先在 Worker 关闭状态完成健康检查、身份认证、HMAC 和单次同步接口验证；确认无误后才设置：

```text
WORKFLOW_OUTBOX_WORKER_ENABLED=true
```

联调结束必须恢复为 `false`，并确认不存在未处理 `PENDING`、`PROCESSING`、`FAILED`、`DEAD` 或 `BUFFERED` 记录。

## 6. 剩余风险

1. 真实 Workflow 服务及其流程定义版本尚未验证。
2. 真实审批任务授权、委托和职责分离尚未验证。
3. HMAC、Nonce、时间同步和密钥轮换尚未在跨服务环境验证。
4. Worker 并发、超时、重试、死信和积压恢复尚未形成真实运行证据。
5. 事件补拉接口与现有扁平 `WorkflowEvent` 模型的兼容性尚未确认。
6. 本报告不授权连接生产或开启生产 Worker。

## 7. 当前结论

本次已完成真实联调环境发现，但真实 Workflow 中心、凭据和依赖环境均不可用，无法安全执行流程启动、任务流转、回调闭环、可靠性和安全验证。当前结果不是失败测试，也不是联调通过，而是明确的前置环境阻断。

在真实非生产 Workflow 地址和安全配置到位前，本 Sprint 保持 **BLOCKED / NOT EXECUTED**。
