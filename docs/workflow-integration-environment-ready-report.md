# Workflow真实联调环境准入报告

## 1. 报告信息

| 项目 | 内容 |
|---|---|
| Sprint | Sprint 2-3.7.2 |
| 检查日期 | 2026-08-10（Asia/Shanghai） |
| 目标环境 | 非生产Workflow联调环境 |
| 检查方式 | 环境变量存在性、TCP/HTTP探测、进程及仓库配置检查 |
| 最终状态 | **NOT_READY / ENVIRONMENT_NOT_PROVISIONED** |

本次仅进行联调环境建设检查。未修改业务代码，未修改V2.4.0—V2.4.9 Migration，未创建Workflow数据库，未启动Outbox Worker，也未执行或伪造真实流程联调。密钥类配置只检查是否存在，未在命令输出、报告或仓库中保存其值。

## 2. 环境清单

| 组件 | 预期用途 | 当前检查结果 | 准入状态 |
|---|---|---|---|
| Workflow服务 | 流程启动、任务、审批、事件回调 | `WORKFLOW_BASE_URL`未配置；默认`127.0.0.1:8090`无监听，健康端点不可达 | 阻断 |
| Investment服务 | 投资决策提交、绑定、Inbox/Outbox及审计 | `INVESTMENT_BASE_URL`未配置；默认`127.0.0.1:8080`无监听，无Java进程 | 阻断 |
| MySQL 8 | Investment业务及可靠性数据 | 应用默认`127.0.0.1:3306`不可达，数据库连接变量未配置 | 阻断 |
| 本机MySQL实例 | 非准入实例 | 发现`mysqld`监听`127.0.0.1:3307`，但未提供对应URL、账号及Schema证明，未纳入联调 | 未验证 |
| Redis | Nonce、防重放、分布式锁及缓存 | 默认`127.0.0.1:6379`无监听，无Redis进程 | 阻断 |
| Docker工具 | 可选的本地依赖启动方式 | 当前终端未发现Docker CLI | 不可用 |

当前机器上没有可访问的Workflow或Investment实例。MySQL `3307`仅证明存在监听，不能证明数据库类型、版本、认证、Schema或Migration状态符合要求。

## 3. 配置检查

### 3.1 用户指定配置项

| 环境变量 | 存在性 | 是否被当前应用直接读取 | 结果 |
|---|---|---|---|
| `WORKFLOW_BASE_URL` | 未配置 | 是 | 阻断 |
| `WORKFLOW_SERVICE_TOKEN` | 未配置 | 是 | 阻断 |
| `WORKFLOW_CALLBACK_TOKEN` | 未配置 | 是 | 阻断 |
| `WORKFLOW_HMAC_KEY_ID` | 未配置 | 否 | 名称不兼容 |
| `WORKFLOW_HMAC_SECRET` | 未配置 | 否 | 名称不兼容 |

### 3.2 HMAC变量兼容结论

当前应用实际绑定以下变量：

- `WORKFLOW_HMAC_CURRENT_KEY_ID`
- `WORKFLOW_HMAC_CURRENT_SECRET`

这两个实际绑定变量当前也均未配置。仅设置`WORKFLOW_HMAC_KEY_ID`和`WORKFLOW_HMAC_SECRET`不会进入应用配置。由于本Sprint禁止修改业务代码，非生产部署层必须将密钥源映射为应用实际读取的`WORKFLOW_HMAC_CURRENT_*`变量；如保留用户指定名称，应由部署编排或密钥注入器完成名称映射。

### 3.3 其他运行配置

| 环境变量 | 存在性 | 说明 |
|---|---|---|
| `WORKFLOW_DECISION_DEFINITION_KEY` | 未配置 | 不能依赖源码默认值进行真实联调 |
| `WORKFLOW_DECISION_DEFINITION_VERSION` | 未配置 | 必须锁定已发布版本 |
| `WORKFLOW_OUTBOX_WORKER_ENABLED` | 未配置 | 应用默认`false`，符合准入准备期要求 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 均未配置 | Investment无法连接准入数据库 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | 均未配置 | Redis无法进入连接验证 |

所有Token和Secret必须由非生产密钥管理系统或受控运行时环境注入。禁止提交到`.env`、YAML、Docker镜像、Git、测试数据、截图或普通日志。

## 4. 流程定义

| 检查项 | 当前结果 | 准入要求 |
|---|---|---|
| 投资决策流程ID/定义键 | 未确认 | 由Workflow管理员提供真实定义ID及`definitionKey` |
| 流程版本 | 未确认 | 提供已发布、启用且不可漂移的显式版本 |
| 流程状态 | 未确认 | 必须为非生产环境可启动状态 |
| 节点配置 | 未确认 | 实际节点、顺序、候选组、驳回及撤回路径需可查询 |
| 回调配置 | 未确认 | 回调地址、事件类型、签名策略和网络允许列表需生效 |

源码默认键为`investment-decision`、默认版本为`1`；接口契约示例使用版本`3`。两者都不是外部Workflow中真实流程已存在的证据，禁止据此推断联调版本。

真实定义至少应按冻结的业务路线支持以下节点集合，并允许不同决策路线选择必要节点：

- `BUSINESS_REVIEW`
- `FINANCE_REVIEW`
- `LEGAL_COMPLIANCE_REVIEW`
- `RISK_REVIEW`
- `INVESTMENT_REVIEW`
- `MAJOR_DECISION_REGISTRATION`
- `PARTY_COMMITTEE_PRE_STUDY`
- `MANAGEMENT_DECISION`
- `BOARD_DECISION`
- `SHAREHOLDER_DECISION`
- `REGULATORY_APPROVAL`
- 附条件批准的复核、关闭或豁免节点

节点是否必经应由冻结的决策路线决定，不能将全部节点机械串联，也不能由Investment Controller硬编码。

## 5. 账号权限

| 主体 | 当前结果 | 必要权限与证据 |
|---|---|---|
| Investment发起人 | 未提供 | 有效非生产用户、组织归属及`investment:decision:submit`权限 |
| Workflow审批人 | 未提供 | 有效办理主体、候选组/角色及对应节点任务权限 |
| 条件复核人 | 未提供 | 对附条件批准任务具有复核或关闭授权 |
| 联调管理员 | 未提供 | 仅具有必要的查询、失败处置和受控重放权限 |
| 身份映射 | 未建立证据 | Investment用户ID与Workflow主体ID存在稳定映射 |
| 职责分离 | 未验证 | 发起人不得作为同一事项的唯一最终审批人 |

仓库Migration中存在Investment决策权限节点，但这不证明真实测试账号已创建、已授权或已在Workflow候选组中生效。账号、角色、组织和任务授权必须通过实际系统查询验证。

## 6. 联调启动条件

Sprint 2-3.7只有在以下条件全部满足后方可启动：

1. Workflow和Investment均部署在隔离的非生产环境，健康检查连续成功。
2. MySQL 8可连接，目标Schema存在，Flyway历史达到V2.4.9且严格`validate`通过。
3. Redis可连接，并验证Nonce存储、过期策略及分布式锁能力。
4. Workflow服务Token、回调Token和HMAC密钥通过安全设施注入；应用实际读取的变量名匹配。
5. 投资决策流程真实ID、定义键、固定版本、发布状态及节点路线由Workflow管理员确认。
6. 发起人、审批人、条件复核人及管理员账号可用，RBAC、候选组和任务权限验证通过。
7. Investment与Workflow双向网络、TLS、回调允许列表、Token和HMAC握手通过。
8. 准入阶段保持`WORKFLOW_OUTBOX_WORKER_ENABLED=false`；先完成只读查询和单次受控直连验证。
9. 具备隔离测试数据、traceId检索、审计日志、失败停止、事件重放审批和数据清理方案。
10. 准入证据需包含脱敏后的时间、端点、HTTP状态、流程定义元数据、账号权限结果和Schema版本，不得只以配置文件声明代替真实验证。

建议启动顺序：MySQL → Redis → Workflow → Investment（Worker关闭）→ 健康检查 → 流程与账号核验 → HMAC握手 → 单次受控联调 → Worker受控开启。

## 7. 结论

非生产Workflow联调环境尚未建立，当前不能进入Sprint 2-3.7真实联调。阻断项为外部服务不可用、运行配置与安全凭证缺失、HMAC变量名未完成部署映射、流程定义未发布验证以及测试账号权限未准备。

待环境负责人和Workflow管理员补齐上述条件后，应重新执行本报告全部检查。只有全部阻断项转为真实验证通过，状态才能更新为`READY`。
