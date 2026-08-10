# Workflow联调阻断关闭跟踪报告

## 1. 报告信息

| 项目 | 内容 |
|---|---|
| Sprint | Sprint 2-3.7.12 |
| 跟踪日期 | 2026-08-10（Asia/Shanghai） |
| 冻结状态 | `VERSION_FREEZE_READY` |
| 资源状态 | `WORKFLOW_RESOURCE_NOT_READY` |
| 联调状态 | **FAILED / NO_GO** |

本次仅维护P0阻断关闭台账。未修改业务代码，未修改V2.4.0—V2.4.9 Migration，未启动Outbox Worker，未连接未知数据库实例，也未伪造资源或READY状态。

## 2. 跟踪规则

### 2.1 状态流转

| 状态 | 含义 |
|---|---|
| `OPEN` | 资源未交付或没有当前环境证据，阻止联调 |
| `DELIVERED_PENDING_VALIDATION` | 资源已通过安全渠道交付，等待实测 |
| `VERIFIED` | 当前环境实测通过，等待联合签署 |
| `CLOSED` | 责任方与联调负责人确认阻断解除 |
| `REOPENED` | 环境、版本、配置或权限变化导致证据失效 |

所有阻断均为P0，必须全部`CLOSED`才能判定READY。不得从`OPEN`直接跳转至`CLOSED`，不得使用历史环境、源码默认值、Mock响应或口头确认作为关闭证据。

### 2.2 当前汇总

| 分类 | P0数量 | OPEN | CLOSED |
|---|---:|---:|---:|
| 服务 | 4 | 4 | 0 |
| 数据库 | 3 | 3 | 0 |
| Workflow | 2 | 2 | 0 |
| 安全配置 | 3 | 3 | 0 |
| 流程定义 | 2 | 2 | 0 |
| 账号权限 | 3 | 3 | 0 |
| 合计 | 17 | 17 | 0 |

## 3. 阻断清单与解除条件

### 3.1 服务

| 编号 | 当前状态 | 阻断项 | 需要输入 | 验收方式 | 关闭条件 |
|---|---|---|---|---|---|
| BLK-SVC-001 | OPEN | Investment未部署 | 非生产地址、冻结制品、部署记录、健康路径 | TCP/HTTPS、健康接口、Commit/制品摘要核对 | 服务为UP，制品可追溯到冻结Commit，MySQL/Redis依赖正常 |
| BLK-SVC-002 | OPEN | Workflow不可访问 | 非生产地址、健康路径、服务及契约版本 | DNS/TLS/TCP、健康及版本接口 | 健康连续成功，契约版本兼容，地址被Investment实际使用 |
| BLK-SVC-003 | OPEN | Redis不可访问 | 地址、端口、TLS、认证引用、数据库编号 | 连接、认证、读写、TTL、Nonce、锁和故障测试 | 所有检查通过，认证信息由安全设施注入 |
| BLK-SVC-004 | OPEN | 双向网络未验证 | DNS、证书、允许列表、路由、超时和限流策略 | Investment调用Workflow及Workflow回调Investment握手 | 双向TLS成功，错误来源被拒绝，超时/限流符合契约 |

当前证据：默认`8080`、`8090`和`6379`均不可达，相关运行地址未设置。

### 3.2 数据库

| 编号 | 当前状态 | 阻断项 | 需要输入 | 验收方式 | 关闭条件 |
|---|---|---|---|---|---|
| BLK-DB-001 | OPEN | JDBC/MySQL资源未交付 | MySQL 8 JDBC、账号、安全凭证引用、目标Schema | 最小权限连接、版本、Schema、字符集和时区查询 | Investment数据源连接成功，目标库身份明确 |
| BLK-DB-002 | OPEN | Flyway状态未知 | 目标库读取权限、冻结Migration扫描目录 | 查询history并执行严格`validate` | 无失败记录，最终版本V2.4.9，所有checksum一致 |
| BLK-DB-003 | OPEN | V2.4.9 Schema未验证 | 指纹执行权限、批准基准、结构核查SQL | 只读结构指纹及表/字段/索引/外键/CHECK检查 | 当前指纹与批准基准一致，可靠性结构完整 |

当前证据：应用默认`3306`不可达，JDBC和认证未设置。本机`3307`只有未知`mysqld`监听，没有JDBC、用途、认证、版本或Schema证明；按原则保持隔离，禁止连接。

### 3.3 Workflow服务能力

| 编号 | 当前状态 | 阻断项 | 需要输入 | 验收方式 | 关闭条件 |
|---|---|---|---|---|---|
| BLK-WF-001 | OPEN | Workflow健康和API能力未知 | 服务地址、健康接口、API契约版本 | 真实健康、版本和只读能力查询 | 服务可用，契约与冻结客户端兼容 |
| BLK-WF-002 | OPEN | 回调订阅未配置 | Investment回调地址、事件清单、契约版本、重试策略 | Token/HMAC握手、允许列表和受控测试事件 | 双向绑定正确，回调认证成功，错误事件不推进业务 |

当前证据：Workflow地址未设置，默认健康接口不可达，未调用任何真实API。

### 3.4 安全配置

| 编号 | 当前状态 | 阻断项 | 需要输入 | 验收方式 | 关闭条件 |
|---|---|---|---|---|---|
| BLK-SEC-001 | OPEN | 服务及回调Token未交付 | Service Token、Callback Token的受控密钥引用、有效期和吊销联系人 | 只验证注入存在性、正向认证和错误Token拒绝 | 两类Token用途隔离、认证成功且不出现在日志/报告 |
| BLK-SEC-002 | OPEN | HMAC当前密钥未交付 | 当前Key ID、Secret密钥引用、轮换窗口 | 正确签名、错误Key、错误签名及轮换检查 | HMAC-SHA256通过，错误请求被拒绝，密钥可轮换 |
| BLK-SEC-003 | OPEN | 防重放未验证 | 时间同步、允许偏差、Nonce TTL和Redis保障 | 过期时间戳、重复Nonce、重复请求和Redis故障测试 | 所有负向测试通过，拒绝事件有脱敏审计 |

当前证据：五项Workflow配置均未设置；`deploy/.env.production`及Workflow/MySQL/Redis secret文件均未交付。报告不记录任何密钥值。

### 3.5 流程定义

| 编号 | 当前状态 | 阻断项 | 需要输入 | 验收方式 | 关闭条件 |
|---|---|---|---|---|---|
| BLK-PROC-001 | OPEN | 真实流程ID和版本未交付 | 流程ID、定义键、显式版本、发布及启用状态 | Workflow API只读查询并与Investment配置比对 | ID/键/版本唯一且一致，版本被固定，禁止自动漂移 |
| BLK-PROC-002 | OPEN | 节点及路由未确认 | 节点编码、顺序、条件、候选组、驳回/撤回/异常路线 | 与冻结决策路线逐项比对并执行权限查询 | 法定节点、条件、异常路线及回调事件配置完整 |

当前证据：流程定义键、版本、真实流程ID和节点元数据均未交付。源码默认值和设计文档不作为部署证据。

### 3.6 账号权限

| 编号 | 当前状态 | 阻断项 | 需要输入 | 验收方式 | 关闭条件 |
|---|---|---|---|---|---|
| BLK-IAM-001 | OPEN | 发起人未交付 | 测试用户、组织、数据范围和提交权限 | 实际登录、用户信息、RBAC和数据范围查询 | 可提交指定测试决策，不能越权读取其他数据 |
| BLK-IAM-002 | OPEN | 审批人及任务权限未交付 | Workflow主体、候选组、节点角色和办理权限 | 实际任务查询、候选、签收、办理权限检查 | 仅授权审批人可办理对应节点，越权用户被拒绝 |
| BLK-IAM-003 | OPEN | 身份映射和职责分离未确认 | Investment用户/组织与Workflow主体映射、职责矩阵 | 双系统查询比对和职责分离审查 | 映射稳定唯一，发起人不作为同一事项唯一最终审批人 |

当前证据：发起人和审批人标识均未设置，没有真实任务可用于权限验收。

## 4. 冻结状态

### 4.1 Git

| 检查项 | 结果 | 状态 |
|---|---|---|
| 冻结Commit | `abb5fc9eb3a075b723eb5e98d9d9d7932429b8c8` | PASS |
| 冻结Tag | `v2.4.9-workflow-integration-rc1` | PASS |
| Tag指向 | 与冻结Commit一致 | PASS |
| 检查时HEAD | `fc0da3b7e876b523f038b9323f162744618f94b7` | 信息项 |
| 检查前工作区 | 干净 | PASS |
| 候选后业务代码变更 | 0 | PASS |
| 候选后V2.4.0—V2.4.9变更 | 0 | PASS |

候选后的提交仅用于记录治理报告，不改变冻结候选Tree。不得移动、删除或重写候选Tag。

### 4.2 Migration

| 检查项 | 结果 | 状态 |
|---|---|---|
| 正式Migration数量 | 15 | PASS |
| 最高版本 | V2.4.9 | PASS |
| 重复版本 | 0 | PASS |
| V2.4.0—V2.4.9哈希 | 10/10与Inventory一致 | PASS |

冻结状态为`VERSION_FREEZE_READY`。后续修复只能形成新Commit、候选标签和更高版本Migration。

### 4.3 Worker

`WORKFLOW_OUTBOX_WORKER_ENABLED`未设置，默认保持`false`。当前没有Investment进程，Worker未启动。所有P0阻断关闭和Go/No-Go批准前不得改变该状态。

## 5. 下一步启动条件

### 5.1 资源交付触发

只有发生明确的外部资源交付事件后才重新执行READY验收。交付包至少包括：

1. Investment、Workflow、Redis和MySQL 8非生产地址；
2. 受控配置版本及密钥引用，不包含报告可见的密钥值；
3. 目标JDBC、Schema和最小权限数据库账号；
4. Workflow真实流程ID、固定版本、节点及回调订阅；
5. 发起人、审批人、复核人和跨系统身份映射；
6. 网络、TLS、允许列表、维护窗口和责任人。

### 5.2 READY条件

进入Sprint 2-3.7真实Workflow联调前必须同时满足：

- 17项P0阻断全部由`OPEN`经过验证转为`CLOSED`；
- 冻结Tag仍指向指定Commit，候选业务代码和历史Migration无变化；
- Investment、Workflow、Redis和MySQL健康及连通性通过；
- 目标数据库Flyway最终版本V2.4.9，严格`validate`和Schema指纹通过；
- Workflow流程ID、固定版本、节点、路由和候选组通过真实API核验；
- Token、HMAC、时间戳和Nonce正向及负向测试通过；
- 发起人、审批人、任务权限、身份映射和职责分离通过；
- 双向TLS、回调、安全审计、traceId和告警可用；
- 联调负责人、Workflow、安全、DBA和运维完成Go/No-Go签署。

### 5.3 启动顺序

资源READY后按以下顺序执行：MySQL → Redis → Workflow → Investment（Worker关闭）→ 健康检查 → 数据库准入 → Workflow定义与账号验收 → 安全握手 → Go/No-Go → 受控启动单实例Worker → 首次隔离流程。

## 6. 当前结论

17项P0阻断全部保持`OPEN`，冻结候选保持完整，没有达到真实联调启动条件。

当前状态：**FAILED / VERSION_FREEZE_READY / WORKFLOW_RESOURCE_NOT_READY / NO_GO**。

继续等待资源交付。在明确交付事件发生前，不连接未知`3307`数据库，不启动Worker，不进入真实Workflow联调。
