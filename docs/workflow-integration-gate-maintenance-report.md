# Workflow联调启动门禁维护报告

## 1. 报告信息

| 项目 | 内容 |
|---|---|
| Sprint | Sprint 2-3.7.10 |
| 维护日期 | 2026-08-10（Asia/Shanghai） |
| 冻结状态 | `VERSION_FREEZE_READY` |
| 资源状态 | `WORKFLOW_RESOURCE_NOT_READY` |
| 启动决策 | **NO_GO** |

本次仅维护冻结状态和联调启动门禁。未修改业务代码，未修改V2.4.0—V2.4.9 Migration，未启动Outbox Worker，未解除或移动冻结标签，也未伪造资源或READY结果。

## 2. 冻结状态检查

### 2.1 Git候选基线

| 检查项 | 当前结果 | 状态 |
|---|---|---|
| 冻结Commit | `abb5fc9eb3a075b723eb5e98d9d9d7932429b8c8` | PASS |
| 冻结Tag | `v2.4.9-workflow-integration-rc1` | PASS |
| Tag实际指向 | 与冻结Commit完全一致 | PASS |
| 检查时HEAD | `7e46293ae40dc1d7674ac716989bc63cc5f251a4` | 信息项 |
| 检查前工作区 | 干净 | PASS |
| 候选后文件变更 | 2份治理报告 | PASS |
| 候选后业务代码变更 | 0 | PASS |
| 候选后V2.4.0—V2.4.9变更 | 0 | PASS |

候选Tag继续作为真实联调的唯一代码、配置和Migration基线。候选后的治理报告不进入冻结Tree，也不改变Tag指向。

### 2.2 Migration冻结

| 检查项 | 当前结果 | 状态 |
|---|---|---|
| 正式Migration数量 | 15 | PASS |
| 版本范围 | V2.1.0—V2.4.9 | PASS |
| 最高版本 | V2.4.9 | PASS |
| 重复版本 | 0 | PASS |
| V2.4.0—V2.4.9权威资产 | 10/10 SHA-256与Inventory一致 | PASS |

后续如发现缺陷，必须新增更高版本Migration；禁止修改、覆盖或重新解释V2.4.0—V2.4.9。

### 2.3 Worker冻结

`WORKFLOW_OUTBOX_WORKER_ENABLED`当前未设置，应用默认值为`false`。本次没有启动Investment或Worker，没有扫描、发送、重试或重放任何真实消息。

## 3. 阻断清单维护

### 3.1 资源状态

| 阻断域 | 当前证据 | 状态 | 本次变化 |
|---|---|---|---|
| Investment服务 | 地址未设置；默认`8080`不可达 | OPEN | 无变化 |
| Workflow服务 | 地址未设置；默认`8090`不可达 | OPEN | 无变化 |
| Redis | 配置未设置；默认`6379`不可达 | OPEN | 无变化 |
| MySQL应用数据源 | JDBC未设置；默认`3306`不可达 | OPEN | 无变化 |
| 本机`3307` MySQL | 仅有监听，身份、认证和Schema未知 | OPEN | 无变化 |
| Workflow安全配置 | 五项配置均未设置 | OPEN | 无变化 |
| Workflow流程资源 | 真实流程ID、版本和节点未交付 | OPEN | 无变化 |
| 测试账号和节点权限 | 发起人、审批人及授权未交付 | OPEN | 无变化 |
| 双向网络和TLS | 无运行实例，未验证 | OPEN | 无变化 |
| 数据库V2.4.9准入 | 无JDBC，history、validate和指纹未执行 | OPEN | 无变化 |

### 3.2 缺失项

1. 可访问的Investment非生产部署地址及健康检查证据。
2. 可访问的Workflow非生产地址、健康接口和契约版本。
3. 可访问的Redis地址、认证引用、TLS及TTL/Nonce/锁能力证明。
4. MySQL 8 JDBC、账号、安全凭证引用和目标Schema信息。
5. 目标数据库Flyway history、严格`validate`、最终V2.4.9版本和结构指纹。
6. `WORKFLOW_SERVICE_TOKEN`、`WORKFLOW_CALLBACK_TOKEN`、当前HMAC Key ID和Secret的安全注入。
7. Workflow真实流程ID、定义键、固定版本、发布状态、节点、路由和回调订阅。
8. Investment发起人、Workflow审批人、候选组、任务权限和跨系统身份映射。
9. Investment与Workflow双向DNS、TLS、允许列表、超时和限流配置。
10. 当前环境的traceId、审计、告警、失败停止、死信和重放审批能力。

### 3.3 阻断解除条件

| 阻断域 | 解除条件 | 必要证据 |
|---|---|---|
| Investment | 冻结候选制品部署成功，健康及依赖为UP | 健康响应、版本/Commit标识、部署记录 |
| Workflow | 非生产服务可达且契约兼容 | 健康响应、服务版本、契约版本 |
| Redis | 连接、认证、TLS、TTL、Nonce和锁通过 | 脱敏连接结果及功能检查记录 |
| MySQL | JDBC连接成功，MySQL 8及目标Schema明确 | 只读版本、Schema和连接结果 |
| 数据库版本 | Flyway无失败，最终V2.4.9，checksum及指纹一致 | history、严格validate、结构指纹 |
| 安全配置 | Token/HMAC通过安全设施注入且握手成功 | 仅记录存在、认证/签名结果和有效期结论 |
| 流程定义 | 真实ID、固定版本、发布状态、节点和路由一致 | Workflow API返回及冻结路线比对 |
| 账号权限 | 实际账号、RBAC、候选组、任务权限和职责分离通过 | 登录、权限查询和任务授权结果 |
| 网络回调 | 双向TLS和允许列表通过 | 调用及回调握手、错误请求拒绝结果 |
| Worker | 所有P0阻断关闭且Go/No-Go批准 | 批准记录、窗口、负责人和启动基线 |

任何阻断项必须经历`OPEN → DELIVERED_PENDING_VALIDATION → VERIFIED → CLOSED`。资源仅被声明或写入文档时不得直接关闭。

## 4. READY判定规则

### 4.1 READY必要条件

只有以下条件全部满足，才允许将资源状态改为`READY`：

1. 冻结Tag仍指向`abb5fc9eb3a075b723eb5e98d9d9d7932429b8c8`，候选业务代码和历史Migration无变化。
2. Investment、Workflow、Redis和MySQL 8在指定非生产环境实际可达。
3. Investment运行制品可追溯到冻结候选，健康接口及依赖状态正常。
4. 目标数据库Flyway最终版本为V2.4.9，无失败记录，严格`validate`和Schema指纹通过。
5. Workflow真实流程ID、固定版本、发布状态、节点、路由、候选组和异常路线通过API核验。
6. 五项Workflow配置由受控环境及密钥设施注入，服务认证、回调认证和HMAC握手通过。
7. 时间同步、Nonce、防重放、双向TLS、网络允许列表和审计告警通过。
8. 发起人、审批人和复核人实际可用，RBAC、Workflow任务权限、身份映射和职责分离通过。
9. Sprint 2-3.7.6定义的全部P0阻断项均为`CLOSED`。
10. 联调负责人、Workflow负责人、安全、DBA和运维完成Go/No-Go联合确认。

### 4.2 保持NO_GO的条件

出现以下任一情况必须保持`NO_GO`：

- 任何P0阻断仍为`OPEN`、`DELIVERED_PENDING_VALIDATION`或`REOPENED`；
- 冻结Tag移动、候选代码变化或历史Migration checksum变化；
- 使用历史临时环境、源码默认值、Mock响应或设计文档代替当前环境实测；
- Token/HMAC缺失、泄露、过期或未通过真实握手；
- Workflow版本未固定或节点配置与冻结路线不一致；
- Flyway失败、checksum/Schema指纹不一致或执行未批准的`repair`；
- 账号越权、身份映射不一致或职责分离不成立；
- Worker在Go/No-Go批准前被启用。

### 4.3 Worker启动门禁

在资源正式READY前持续保持`WORKFLOW_OUTBOX_WORKER_ENABLED=false`。只有全部P0阻断关闭、环境基线留存、Outbox积压审计完成并获批准后，才允许在受控窗口以单实例、小批量方式启动。

发生版本漂移、认证/签名异常、越权、数据库或Redis故障、重复流程、乱序缺口、死信增长、审计失效或敏感信息泄露时，必须立即关闭Worker、暂停新提交、保留证据并将对应阻断重新打开。

## 5. 本次门禁结论

| 门禁 | 结果 |
|---|---|
| 冻结Commit/Tag | PASS |
| Migration冻结 | PASS |
| Worker保持关闭 | PASS |
| 服务资源 | FAIL |
| 数据库资源 | FAIL |
| Workflow资源 | FAIL |
| 安全配置 | FAIL |
| 账号权限 | FAIL |
| READY | **DENIED** |

当前状态保持：

**VERSION_FREEZE_READY / WORKFLOW_RESOURCE_NOT_READY / NO_GO**

继续等待真实资源交付。资源到位后应从`DELIVERED_PENDING_VALIDATION`开始逐项复核，在全部门禁关闭前不得启动Worker或进入真实Workflow联调。
