# Workflow联调资源准入复核报告

## 1. 报告信息

| 项目 | 内容 |
|---|---|
| Sprint | Sprint 2-3.7.5 |
| 复核日期 | 2026-08-10（Asia/Shanghai） |
| 基线状态 | Sprint 2-3.7.4：`RESOURCE_DELIVERY_PENDING / NOT_READY` |
| 本次结论 | **NOT_READY / RESOURCE_DELIVERY_PENDING** |

本次仅复核真实联调资源，不执行业务联调。未修改业务代码，未修改V2.4.0—V2.4.9 Migration，未创建Workflow数据库，未启动Outbox Worker。所有Token和Secret仅验证存在性，未输出或保存其值；历史验收环境未作为当前环境证据。

## 2. 资源状态

### 2.1 服务资源

| 资源 | 配置状态 | 运行状态 | 复核结果 |
|---|---|---|---|
| Investment服务 | `INVESTMENT_BASE_URL`未设置 | 默认`127.0.0.1:8080`无监听，健康接口不可达，无Java进程 | FAIL |
| Workflow服务 | `WORKFLOW_BASE_URL`未设置 | 默认`127.0.0.1:8090`无监听，健康接口不可达 | FAIL |
| Redis | 主机、端口及认证配置未设置 | 默认`127.0.0.1:6379`无监听，无Redis进程 | FAIL |
| MySQL应用数据源 | JDBC、账号及认证配置未设置 | 默认`127.0.0.1:3306`无监听 | FAIL |
| 本机其他MySQL | 未配置给应用 | `127.0.0.1:3307`存在`mysqld`监听，但身份和用途未知 | NOT_VERIFIED |

当前终端没有Java、Maven、MySQL Client和Redis CLI。无法在本轮启动Investment，也无法通过客户端登录数据库或Redis。`3307`监听没有JDBC、认证、版本、Schema和Flyway证据，不能作为已交付联调资源。

### 2.2 数据库资源

| 检查项 | 当前结果 | 准入状态 |
|---|---|---|
| JDBC连接 | 未配置、未连接 | FAIL |
| 数据库产品和版本 | 未读取 | FAIL |
| 目标Schema | 未确认 | FAIL |
| `flyway_schema_history` | 未读取 | FAIL |
| Flyway严格`validate` | 未执行 | FAIL |
| 当前Schema版本 | 未确认 | FAIL |
| 当前V2.4.9结构 | 未查询 | FAIL |
| 当前Schema结构指纹 | 未计算 | FAIL |
| 本地V2.4.9 Migration资产 | SHA-256与Inventory一致 | PASS（仅工程资产） |
| 指纹及Flyway检查工具 | 仓库内存在 | PASS（仅工具可用性） |

仓库中记录的历史V2.4.9临时MySQL验收结果不代表当前目标数据库已应用V2.4.9。目标库必须独立验证Flyway成功记录、checksum、最终版本和结构指纹，禁止复制历史结果签署准入。

### 2.3 Workflow资源

| 检查项 | 当前结果 | 准入状态 |
|---|---|---|
| 健康接口 | 服务地址未交付，默认地址不可达 | FAIL |
| 真实流程ID | 未交付、未查询 | FAIL |
| 流程定义键 | 环境未配置；仅有源码默认值 | NOT_VERIFIED |
| 流程版本 | 环境未配置、未查询 | FAIL |
| 发布及启用状态 | 未查询 | FAIL |
| 节点配置 | 未查询 | FAIL |
| 候选组和任务权限 | 未查询 | FAIL |
| 回调订阅及安全策略 | 未验证 | FAIL |

源码默认值、接口契约示例和设计文档只能说明客户端预期，不能证明真实Workflow环境中的流程、版本或节点已部署。

### 2.4 安全资源

以下检查只记录存在性：

| 环境变量 | 存在性 | 结果 |
|---|---|---|
| `WORKFLOW_BASE_URL` | NOT_SET | FAIL |
| `WORKFLOW_SERVICE_TOKEN` | NOT_SET | FAIL |
| `WORKFLOW_CALLBACK_TOKEN` | NOT_SET | FAIL |
| `WORKFLOW_HMAC_CURRENT_KEY_ID` | NOT_SET | FAIL |
| `WORKFLOW_HMAC_CURRENT_SECRET` | NOT_SET | FAIL |

由于变量均不存在，本轮没有进行Token有效性、HMAC签名、时间窗口、Nonce和密钥轮换验证。报告、命令输出和仓库均未写入任何密钥值。

`WORKFLOW_OUTBOX_WORKER_ENABLED`未设置，应用默认保持`false`，符合准入复核阶段的安全要求。

### 2.5 账号资源

| 检查项 | 当前结果 | 准入状态 |
|---|---|---|
| Investment发起人 | 未交付 | FAIL |
| Workflow审批人 | 未交付 | FAIL |
| Workflow节点候选权限 | 未查询 | FAIL |
| 任务查询、签收和办理权限 | 未验证 | FAIL |
| Investment与Workflow身份映射 | 未提供 | FAIL |
| 发起人与最终审批人职责分离 | 未验证 | FAIL |

Migration中存在权限节点不等于测试账号已经创建或获得真实Workflow任务权限，必须使用当前环境实际账号验证。

## 3. 缺失项

1. 可访问的Investment非生产地址及成功健康检查证据。
2. 可访问的Workflow非生产地址、健康接口和契约版本信息。
3. 可访问的Redis地址、认证引用、TLS要求以及TTL、Nonce和锁能力证明。
4. MySQL 8 JDBC、账号和安全凭证引用，以及目标Schema信息。
5. 目标数据库的Flyway history、严格`validate`、最终V2.4.9版本和结构指纹结果。
6. Workflow Service Token、Callback Token、当前HMAC Key ID及Secret的安全注入。
7. 真实投资决策流程ID、定义键、固定版本、发布状态和完整节点配置。
8. Workflow回调Investment的网络路由、TLS、允许列表和事件订阅。
9. Investment发起人、Workflow审批人、候选组、任务权限和跨系统身份映射。
10. Java 21、Maven及数据库检查客户端可用的标准运行或发布环境。

## 4. 准入结论

### 4.1 门禁结果

| 门禁 | 结果 |
|---|---|
| 服务门禁 | FAIL |
| 数据库门禁 | FAIL |
| Workflow定义门禁 | FAIL |
| 安全门禁 | FAIL |
| 账号权限门禁 | FAIL |
| 最终准入 | **DENIED** |

与Sprint 2-3.7.4相比，当前没有发现新增的真实联调资源或可验证证据。状态继续保持：

**NOT_READY / RESOURCE_DELIVERY_PENDING**

本状态不允许启动Sprint 2-3.7真实Workflow联调，也不允许启用Outbox Worker。

## 5. 启动条件

只有以下条件全部满足，才能重新评审并进入真实联调：

1. Investment、Workflow、Redis和MySQL 8均在隔离的非生产环境实际可达。
2. Investment以Java 21成功构建、启动，健康接口及MySQL、Redis依赖状态正常。
3. 五项Workflow安全配置通过受控密钥设施注入，存在性检查和认证/HMAC握手通过。
4. 目标数据库连接成功，Flyway无失败记录，严格`validate`通过，最终版本为V2.4.9。
5. 目标数据库重新计算的Schema指纹与批准基准一致，V2.4.9可靠性结构核查通过。
6. Workflow API返回真实流程ID、固定版本、发布状态、节点、路由及候选组配置。
7. 发起人和审批人能够实际登录，Investment RBAC、Workflow任务权限和职责分离验证通过。
8. Investment到Workflow调用及Workflow到Investment回调的网络、TLS、Token、HMAC、时间戳和Nonce验证通过。
9. 准入阶段继续保持`WORKFLOW_OUTBOX_WORKER_ENABLED=false`；只有单次受控直连通过并获批准后，才可在正式联调窗口启用。
10. 所有准入证据必须来自本次目标非生产环境，不得以历史临时数据库、设计文档或模拟响应替代。

资源交付后需重新执行本报告全部检查；在获得真实证据前不得将状态修改为`READY`。
