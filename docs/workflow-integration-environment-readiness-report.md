# Workflow真实联调环境准备检查报告

## 1. 报告信息

| 项目 | 内容 |
|---|---|
| Sprint | Sprint 2-3.7.1 |
| 检查日期 | 2026-08-08（Asia/Shanghai） |
| 检查范围 | Workflow、Investment、MySQL、Redis及联调身份与安全配置 |
| 检查性质 | 联调环境准入检查，不执行业务联调 |
| 总体结论 | **NOT_READY / BLOCKED** |

本次检查未修改业务代码、数据库结构或Migration，未创建Workflow数据库，未启动真实流程，未伪造任何接口、事件或审批结果。所有密钥类配置只检查是否存在，报告不记录配置值。

## 2. 环境检查结果

### 2.1 Workflow服务连通性

| 检查项 | 结果 | 说明 |
|---|---|---|
| `WORKFLOW_BASE_URL` | 缺失 | 当前进程环境未注入真实Workflow地址 |
| 源码默认地址 | 仅存在默认值 | `http://127.0.0.1:8090`仅为开发默认值，不代表真实环境 |
| TCP连通性 | 失败 | `127.0.0.1:8090`无监听 |
| 健康检查 | 失败 | 默认地址的`/actuator/health`和`/health`均不可达 |
| Workflow API | 未执行 | 缺少真实地址和服务凭证，不具备调用条件 |

结论：Workflow服务不可用，真实流程启动、任务查询、审批操作和结果回调均不能进入联调。

### 2.2 安全配置

| 配置项 | 结果 |
|---|---|
| `WORKFLOW_SERVICE_TOKEN` | 缺失 |
| `WORKFLOW_CALLBACK_TOKEN` | 缺失 |
| `WORKFLOW_HMAC_CURRENT_KEY_ID` | 缺失 |
| `WORKFLOW_HMAC_CURRENT_SECRET` | 缺失 |

安全结论：服务认证、回调认证、HMAC签名和防重放链路均无法验收。密钥值未读取到报告、日志或命令输出中。

`WORKFLOW_OUTBOX_WORKER_ENABLED`未设置，应用默认值为`false`。本次检查未启动Investment服务，Worker未被开启，也未产生消息发送副作用。

### 2.3 流程定义

| 检查项 | 结果 | 说明 |
|---|---|---|
| 本地默认流程键 | 已定义 | 源码默认值为`investment-decision` |
| 本地默认流程版本 | 已定义 | 源码默认值为`1` |
| 环境显式流程键/版本 | 缺失 | 未注入`WORKFLOW_DECISION_DEFINITION_KEY`和`WORKFLOW_DECISION_DEFINITION_VERSION` |
| Workflow真实流程定义 | 未验证 | Workflow服务不可达 |
| 节点配置 | 未验证 | 无法核查党委前置研究、董事会/经理层决策等实际节点 |
| 流程发布/启用状态 | 未验证 | 无法确认指定版本是否已发布且可启动 |

本地默认值只能说明客户端预期，不能作为Workflow中心真实流程定义、版本或节点配置存在的证据。

### 2.4 联调账号与权限

| 检查项 | 结果 | 准入要求 |
|---|---|---|
| Investment发起人 | 缺失 | 提供非生产测试用户ID、组织和`investment:decision:submit`权限 |
| Workflow审批人 | 缺失 | 提供非生产审批用户、候选组/角色和任务办理权限 |
| Workflow任务查询权限 | 未验证 | 审批人能够查询、签收并处理对应流程任务 |
| 职责分离 | 未验证 | 发起人不得作为同一事项的唯一最终审批人 |
| 身份映射 | 未验证 | Investment用户ID、Workflow主体ID及组织信息需有确定映射 |

当前环境未提供可识别的发起人、审批人或任务授权材料，因此不能执行账号权限验收。

### 2.5 基础环境

| 组件 | 结果 | 证据与说明 |
|---|---|---|
| Investment服务 | 不可用 | `INVESTMENT_BASE_URL`未配置；默认`127.0.0.1:8080`无监听，健康端点不可达；无Java进程 |
| MySQL | 未就绪 | `DB_URL`、用户名和密码未注入；应用默认`127.0.0.1:3306`不可达 |
| 本机其他MySQL实例 | 不纳入验收 | 检测到`mysqld`监听`127.0.0.1:3307`，但无匹配的应用连接配置和凭证，未执行数据库登录或Schema检查 |
| Redis | 不可用 | `REDIS_HOST`、端口和密码未注入；默认`127.0.0.1:6379`无监听，无Redis进程 |
| Docker工具 | 不可用 | 当前环境未发现Docker CLI，不能通过现有终端拉起依赖服务 |

## 3. 缺失项

1. 可访问的非生产Workflow地址及健康检查路径。
2. Workflow服务Token、回调Token、HMAC Key ID和HMAC Secret的安全注入。
3. 已发布且启用的投资决策流程定义、固定版本及完整节点配置。
4. Investment联调服务地址及可用实例。
5. 与应用配置匹配的MySQL连接信息，以及已完成至V2.4.9的Schema证明。
6. 可用Redis实例及连接配置。
7. Investment发起人、Workflow审批人、候选组/角色和任务权限。
8. Investment用户与Workflow办理主体的身份映射及职责分离确认。
9. Workflow回调到Investment内部接口的网络路由、允许列表和TLS配置。

## 4. 联调准入条件

只有以下条件全部满足，才能开始Sprint 2-3.7真实联调：

- `WORKFLOW_BASE_URL`指向隔离的开发或测试环境，并且健康检查连续成功。
- 服务Token、回调Token和HMAC密钥通过密钥管理或运行时环境安全注入，不写入Git、镜像、文档或普通日志。
- `investment-decision`的目标版本已发布、启用，节点、候选人、驳回、撤回和异常路线均经Workflow管理员确认。
- Investment服务健康，真实MySQL可连接且Flyway历史达到V2.4.9，严格`validate`通过。
- Redis可连接，并完成Nonce、防重放和分布式锁所需功能检查。
- 发起人和审批人账号可登录，RBAC、任务权限、组织归属和职责分离均验证通过。
- Workflow能够回调Investment，回调端验证服务Token、HMAC、时间戳和Nonce。
- 联调开始前保持`WORKFLOW_OUTBOX_WORKER_ENABLED=false`；完成单次直连和签名握手后，才在受控窗口临时启用Worker。
- 具备联调数据清理、事件重放授权、日志留存和故障停止方案。

## 5. 启动步骤

1. 由环境负责人提供非生产Workflow地址、健康检查地址、流程定义键与固定版本，并确认节点配置已发布。
2. 通过环境变量或密钥管理系统注入Workflow服务Token、回调Token、HMAC Key ID和Secret；禁止将值写入配置文件或报告。
3. 启动并验证MySQL，确认目标Schema、`flyway_schema_history`、V2.4.9状态和校验和；随后配置`DB_URL`、`DB_USERNAME`和`DB_PASSWORD`。
4. 启动并验证Redis，再配置主机、端口、认证信息和数据库编号。
5. 准备发起人、审批人及候选组，核查Investment RBAC、Workflow任务权限、组织映射和职责分离。
6. 保持`WORKFLOW_OUTBOX_WORKER_ENABLED=false`启动Investment，验证其健康状态、数据库连接、Redis连接和Workflow只读查询。
7. 完成Workflow到Investment回调地址的网络、TLS、Token与HMAC签名握手，验证错误签名和重复Nonce会被拒绝。
8. 使用独立测试事项执行一次受控流程启动、任务查询、审批和回调闭环；核对binding、outbox、inbox和审计日志。
9. 在受控联调窗口设置`WORKFLOW_OUTBOX_WORKER_ENABLED=true`，验证发送、确认、重试和死信；联调结束后按环境策略恢复安全状态。
10. 将实际请求时间、状态码、事件ID、traceId和脱敏日志补充到Sprint 2-3.7报告；任何未执行步骤必须继续标记为未验证。

## 6. 最终结论

当前环境未达到联调准入条件，Sprint 2-3.7继续保持外部环境阻断状态。待上述缺失项补齐后，应重新执行本报告的全部检查；在此之前不得宣称真实Workflow联调、可靠性验证或安全验证通过。
