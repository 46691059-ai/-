# Workflow真实联调准入报告

## 1. 报告信息

| 项目 | 内容 |
|---|---|
| Sprint | Sprint 2-3.7.3 |
| 检查日期 | 2026-08-10（Asia/Shanghai） |
| 目标 | Sprint 2-3.7真实Workflow联调前置准入 |
| 检查环境 | 当前本机及当前进程环境 |
| 准入结论 | **NOT_READY / ACCESS_DENIED** |

本次检查没有修改业务代码，没有修改V2.4.0—V2.4.9 Migration，没有创建Workflow数据库，没有启动Outbox Worker，也没有伪造服务、流程、账号或数据库验收结果。Token和Secret仅检查存在性及非敏感格式结论，未输出或保存其值。

## 2. 环境状态

| 服务 | 检查结果 | 实际证据 | 状态 |
|---|---|---|---|
| Investment | 未运行、不可访问 | `127.0.0.1:8080`无监听；健康端点不可达；无Java进程 | FAIL |
| Workflow | 未配置、不可访问 | `WORKFLOW_BASE_URL`缺失；默认`127.0.0.1:8090`无监听，健康端点不可达 | FAIL |
| Redis | 不可访问 | 默认`127.0.0.1:6379`无监听；无Redis进程 | FAIL |
| MySQL应用数据源 | 不可访问 | `DB_URL`未配置；应用默认`127.0.0.1:3306`无监听 | FAIL |
| 本机其他MySQL | 仅发现监听 | `mysqld`监听`127.0.0.1:3307`，但没有匹配的数据源配置、认证信息或Schema证据 | NOT_VERIFIED |

当前终端未提供`java`和`mvn`命令，且仓库没有Maven Wrapper，因此本次不能执行当前代码的构建或运行启动检查。历史测试报告和既有`target`内容不能替代本次实际启动及健康检查，Investment“可启动”未通过准入。

## 3. 配置状态

检查只返回`SET/NOT_SET`和格式结论，不记录变量内容。

| 配置项 | 存在性 | 格式检查 | 应用绑定 | 状态 |
|---|---|---|---|---|
| `WORKFLOW_BASE_URL` | NOT_SET | 无法检查 | 已绑定 | FAIL |
| `WORKFLOW_SERVICE_TOKEN` | NOT_SET | 无法检查 | 已绑定 | FAIL |
| `WORKFLOW_CALLBACK_TOKEN` | NOT_SET | 无法检查 | 已绑定 | FAIL |
| `WORKFLOW_HMAC_CURRENT_KEY_ID` | NOT_SET | 无法检查 | 已绑定 | FAIL |
| `WORKFLOW_HMAC_CURRENT_SECRET` | NOT_SET | 无法检查 | 已绑定 | FAIL |

格式准入规则如下：

- `WORKFLOW_BASE_URL`必须为绝对`https`地址；仅在隔离本机调试时允许`http`，不得包含用户信息、查询串或片段。
- Service Token和Callback Token必须非空、无空白字符，并由受控密钥系统生成和轮换；存在性或长度检查不能证明Token有效。
- HMAC Key ID只允许稳定的字母、数字、点、下划线和连字符标识。
- HMAC Secret必须使用足够强度的随机密钥；格式通过仍必须以真实签名握手验证有效性。
- 密钥必须由运行时密钥设施注入，禁止写入Git、普通`.env`、YAML、镜像、报告、截图或日志。

补充配置状态：

| 配置项 | 结果 | 影响 |
|---|---|---|
| `WORKFLOW_DECISION_DEFINITION_KEY` | NOT_SET | 只能落到源码默认值，不能证明真实定义 |
| `WORKFLOW_DECISION_DEFINITION_VERSION` | NOT_SET | 未锁定真实已发布版本 |
| `WORKFLOW_OUTBOX_WORKER_ENABLED` | NOT_SET | 默认`false`，符合准入阶段要求 |
| Workflow发起人/审批人标识 | NOT_SET | 不能进行账号和任务授权检查 |

## 4. 数据库状态

### 4.1 当前数据源

| 检查项 | 结果 |
|---|---|
| 应用数据源URL | 未配置 |
| 应用数据库账号 | 未配置 |
| 应用数据库认证 | 未配置 |
| 数据源TCP | 默认`3306`不可达 |
| 数据库登录 | 未执行；缺少数据源和客户端 |
| `flyway_schema_history` | 未读取 |
| 当前Schema版本 | 未确认 |
| 当前Schema结构指纹 | 未计算 |

本机`3307`监听不能自动替代应用数据源。未经连接、版本、Schema名称、只读查询和Migration历史验证，不得将其标记为联调数据库。

### 4.2 V2.4.9资产与目标Schema

| 检查项 | 结果 | 说明 |
|---|---|---|
| 本地V2.4.9 SQL SHA-256 | PASS | 当前文件与Migration Inventory登记值一致 |
| 本地Flyway checksum登记 | 存在 | Inventory登记为`-48227397` |
| 历史隔离MySQL验收 | PASS | 仅代表2026-08-08临时MySQL 8验收，不代表当前目标库 |
| 目标数据库V2.4.9状态 | NOT_VERIFIED | 无法读取当前`flyway_schema_history` |
| 目标数据库结构指纹 | NOT_VERIFIED | 无法执行结构指纹SQL |

历史验收的V2.4.9期望结构指纹为：

`7a12e14388e78ad0ff7e8e59b3c3b1860a6f46f7294fae005f9ba0a847fb7be3`

该值仅作为目标库只读比对基准。准入时必须在目标数据库重新执行严格`validate`与结构指纹计算；禁止直接复制历史结论。

数据库准入必须同时满足：MySQL 8版本明确、目标Schema正确、Flyway成功记录无失败、最终版本为V2.4.9、checksum一致、结构指纹一致、V2.4.9可靠性表/字段/索引/约束存在，并且不执行`clean`或自动`repair`。

## 5. 流程状态

| 检查项 | 结果 | 状态 |
|---|---|---|
| Workflow真实流程ID | 未提供、未查询 | FAIL |
| 流程定义键 | 环境未配置；源码默认`investment-decision` | NOT_VERIFIED |
| 流程版本 | 环境未配置 | FAIL |
| 发布及启用状态 | Workflow不可达，无法查询 | FAIL |
| 节点、顺序和路由 | Workflow不可达，无法查询 | FAIL |
| 候选组及任务权限映射 | 未提供、无法查询 | FAIL |
| 驳回、撤回、异常及回调配置 | 未验证 | FAIL |

接口契约示例中的版本与源码默认版本不同，两者都不能作为真实Workflow版本。准入必须由Workflow API返回流程ID、定义键、显式版本、发布状态和节点元数据。

按冻结设计，流程应能根据决策路线选择并编排以下节点，而不是把所有节点固定串联：

- 业务、财务、法务合规、风险和投资评审；
- 三重一大识别；
- 党委前置研究；
- 经理层、董事会、股东会或出资人决策；
- 外部监管审批；
- 附条件批准的复核、关闭或豁免。

## 6. 账号状态

| 主体 | 当前状态 | 必须验证的权限 |
|---|---|---|
| Investment发起人 | 未提供 | 有效账号、组织归属、数据范围及`investment:decision:submit` |
| Workflow审批人 | 未提供 | 对目标节点具有候选、签收、查询和办理权限 |
| 条件复核人 | 未提供 | 对附条件任务具有复核、关闭或豁免权限 |
| 联调管理员 | 未提供 | 只读排障及经授权的`investment:workflow:replay` |
| 服务身份 | 未建立证据 | Investment到Workflow服务认证及最小权限 |
| 身份映射 | 未建立证据 | Investment用户、组织与Workflow主体映射稳定且唯一 |

Migration中存在RBAC权限数据不等于测试用户已经获得真实权限。准入需要通过实际登录、权限查询及Workflow任务授权结果证明。

职责分离要求：发起人不得作为同一事项唯一最终审批人；业务编制、专业审核、法定决策、条件整改与复核需按冻结规则分离；服务账号不得冒充业务办理人。

## 7. 联调准入结论

### 7.1 门禁汇总

| 门禁域 | 结果 |
|---|---|
| 服务准入 | FAIL |
| 配置准入 | FAIL |
| 数据库准入 | FAIL |
| Workflow定义准入 | FAIL |
| 账号权限准入 | FAIL |
| 最终结论 | **NOT_READY / ACCESS_DENIED** |

### 7.2 解除阻断条件

1. 提供并启动隔离的非生产Workflow、Investment、MySQL 8和Redis实例。
2. 通过受控密钥设施注入五项Workflow配置，并完成格式检查、认证测试和HMAC握手。
3. 为当前终端或标准部署流水线提供Java 21及Maven运行时，完成Investment构建、启动和健康检查。
4. 对目标数据源执行只读Flyway历史检查、严格`validate`、版本检查及V2.4.9结构指纹比对。
5. 通过Workflow API确认流程ID、固定版本、发布状态、节点路线、候选组和回调配置。
6. 准备发起人、审批人、条件复核人及联调管理员，并完成实际RBAC和任务权限验证。
7. 验证Investment与Workflow双向网络、TLS、Token、HMAC、时间窗口和Nonce防重放。
8. 在以上门禁全部通过前保持`WORKFLOW_OUTBOX_WORKER_ENABLED=false`。

当前环境不允许进入Sprint 2-3.7真实Workflow联调。外部环境补齐后必须重新执行全部门禁；只有实际证据全部通过，才能将报告状态改为`READY`。
