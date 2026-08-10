# Workflow联调资源接入准入报告

## 1. 报告信息

| 项目 | 内容 |
|---|---|
| Sprint | Sprint 2-3.7.9 |
| 检查日期 | 2026-08-10（Asia/Shanghai） |
| 冻结Commit | `abb5fc9eb3a075b723eb5e98d9d9d7932429b8c8` |
| 冻结Tag | `v2.4.9-workflow-integration-rc1` |
| 版本状态 | `VERSION_FREEZE_READY` |
| 资源准入结论 | **WORKFLOW_RESOURCE_NOT_READY / NO_GO** |

本次只进行真实Workflow联调资源接入检查。未修改冻结候选业务代码，未修改V2.4.0—V2.4.9 Migration，未启动Outbox Worker，未执行或伪造真实联调，也未输出或保存任何密钥值。

## 2. 冻结候选完整性

| 检查项 | 结果 |
|---|---|
| Tag指向 | 与冻结Commit完全一致 |
| 检查时HEAD | `f5d1731f5d60753a06c5f5c2acdea065be027d7f` |
| 检查前工作区 | 干净 |
| 候选后业务代码变更 | 0 |
| 候选后V2.4.0—V2.4.9变更 | 0 |
| 候选后变更 | 仅`workflow-integration-freeze-candidate-report.md`治理文档 |

结论：冻结候选仍然完整，资源接入检查不改变候选Tag指向。本报告属于候选后的治理证据，不进入冻结业务代码Tree。

## 3. 资源状态

### 3.1 Investment运行环境

| 检查项 | 当前结果 | 状态 |
|---|---|---|
| `INVESTMENT_BASE_URL` | 未设置 | FAIL |
| 默认端口`127.0.0.1:8080` | 无监听 | FAIL |
| 健康接口 | 不可达 | FAIL |
| Java服务进程 | 未发现 | FAIL |
| 冻结候选构建 | 已在Sprint 2-3.7.8通过187项测试并生成JAR | 仅版本资产通过 |

构建通过不代表Investment联调实例已经部署。当前没有可供Workflow回调的实际Investment运行地址。

### 3.2 Workflow服务

| 检查项 | 当前结果 | 状态 |
|---|---|---|
| `WORKFLOW_BASE_URL` | 未设置 | FAIL |
| 默认端口`127.0.0.1:8090` | 无监听 | FAIL |
| `/actuator/health` | 不可达 | FAIL |
| `/health` | 不可达 | FAIL |
| API契约版本 | 未读取 | NOT_VERIFIED |

### 3.3 Redis

| 检查项 | 当前结果 | 状态 |
|---|---|---|
| `REDIS_HOST` / `REDIS_PORT` | 未设置 | FAIL |
| 默认端口`127.0.0.1:6379` | 无监听 | FAIL |
| Redis进程 | 未发现 | FAIL |
| 认证、TLS、TTL、Nonce和锁 | 未验证 | NOT_VERIFIED |

### 3.4 MySQL

| 检查项 | 当前结果 | 状态 |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 未设置 | FAIL |
| 应用默认端口`127.0.0.1:3306` | 无监听 | FAIL |
| 本机`127.0.0.1:3307` | 有`mysqld`监听 | NOT_VERIFIED |
| `3307`用途、版本、认证和Schema | 未提供 | NOT_VERIFIED |

本机`3307`监听没有被应用JDBC引用，也没有身份和Schema证明，不能作为真实联调数据库。

## 4. 安全状态

本节只记录存在性，不记录变量内容、长度、摘要或任何可用于还原密钥的信息。

| 配置项 | 存在性 | 准入结果 |
|---|---|---|
| `WORKFLOW_BASE_URL` | NOT_SET | FAIL |
| `WORKFLOW_SERVICE_TOKEN` | NOT_SET | FAIL |
| `WORKFLOW_CALLBACK_TOKEN` | NOT_SET | FAIL |
| `WORKFLOW_HMAC_CURRENT_KEY_ID` | NOT_SET | FAIL |
| `WORKFLOW_HMAC_CURRENT_SECRET` | NOT_SET | FAIL |

由于安全资源未注入，本次没有进行服务认证、Callback Token校验、HMAC-SHA256签名、时间窗口、Nonce、防重放或密钥轮换验证。

`WORKFLOW_OUTBOX_WORKER_ENABLED`未设置，应用默认值保持`false`，Worker未启动。

## 5. 数据库状态

| 准入检查 | 当前结果 | 状态 |
|---|---|---|
| JDBC连接 | 无URL和认证信息，未连接 | FAIL |
| MySQL产品及版本 | 未读取 | NOT_VERIFIED |
| 目标Schema | 未确认 | NOT_VERIFIED |
| `flyway_schema_history` | 未读取 | NOT_VERIFIED |
| Flyway失败记录 | 无法查询 | NOT_VERIFIED |
| Flyway最终版本 | 无法查询 | NOT_VERIFIED |
| Flyway严格`validate` | 未执行 | NOT_VERIFIED |
| V2.4.9 checksum | 仅仓库资产已冻结，目标库未核验 | NOT_VERIFIED |
| V2.4.9结构 | 未查询 | NOT_VERIFIED |
| 当前Schema指纹 | 未计算 | NOT_VERIFIED |

冻结候选中的Migration文件完整不代表目标数据库已经应用V2.4.9。历史临时MySQL验收也不替代本次目标数据库的Flyway history、严格`validate`和结构指纹检查。

数据库资源接入后必须以只读或最小权限方式验证：

1. MySQL 8版本、目标Schema和字符集；
2. `flyway_schema_history`无失败记录且最终版本为V2.4.9；
3. 所有Migration checksum与冻结候选一致；
4. 严格`validate`成功，禁止`clean`和自动`repair`；
5. 当前Schema指纹与批准的V2.4.9基准一致；
6. V2.4.9可靠性表、字段、索引、外键、CHECK和重放权限存在。

## 6. Workflow状态

| 准入检查 | 当前结果 | 状态 |
|---|---|---|
| 健康接口 | 服务不可达 | FAIL |
| 真实流程ID | 未提供、未查询 | NOT_VERIFIED |
| 流程定义键 | 环境未设置；仅有冻结候选默认值 | NOT_VERIFIED |
| 显式流程版本 | 环境未设置、未查询 | NOT_VERIFIED |
| 发布及启用状态 | 未查询 | NOT_VERIFIED |
| 节点配置 | 未查询 | NOT_VERIFIED |
| 节点条件和顺序 | 未查询 | NOT_VERIFIED |
| 候选组及任务权限 | 未查询 | NOT_VERIFIED |
| 驳回、撤回和异常路线 | 未验证 | NOT_VERIFIED |
| 回调订阅 | 未验证 | NOT_VERIFIED |

源码默认值、契约示例和设计文档只表示Investment客户端预期，不能证明真实Workflow环境已部署对应流程。

Workflow资源接入后必须通过真实API返回并核验流程ID、定义键、固定版本、发布状态、节点、路由、候选组、异常路线和回调订阅。流程版本必须显式锁定，不允许自动漂移到最新版。

## 7. READY判定

### 7.1 门禁汇总

| 门禁域 | 结果 |
|---|---|
| 冻结候选完整性 | PASS |
| Investment运行环境 | FAIL |
| Workflow服务 | FAIL |
| Redis | FAIL |
| MySQL/JDBC | FAIL |
| 数据库V2.4.9准入 | NOT_VERIFIED |
| Workflow定义准入 | NOT_VERIFIED |
| 安全配置 | FAIL |
| Outbox Worker安全状态 | PASS（保持关闭） |
| 最终资源准入 | **DENIED** |

### 7.2 READY必要条件

只有以下条件全部满足，资源状态才能改为`READY`：

1. Investment、Workflow、Redis和MySQL 8在指定非生产环境实际可达。
2. Investment部署产物可追溯到冻结Commit，健康接口及MySQL、Redis依赖均为UP。
3. 五项Workflow配置通过受控环境和密钥设施注入，认证和HMAC握手成功。
4. 目标数据库Flyway无失败记录，最终版本V2.4.9，严格`validate`和Schema指纹通过。
5. Workflow真实流程ID、固定版本、发布状态、节点、路由、候选组及回调配置通过API核验。
6. 双向TLS、网络允许列表、时间同步、Nonce和防重放验证通过。
7. Sprint 2-3.7.6全部P0阻断关闭，并完成Go/No-Go复核。
8. 在正式签署`READY`前持续保持`WORKFLOW_OUTBOX_WORKER_ENABLED=false`。

## 8. 最终结论

冻结候选版本保持有效，但当前没有真实Workflow联调资源接入证据。数据库和Workflow深度准入检查因资源缺失未执行。

最终状态：**VERSION_FREEZE_READY / WORKFLOW_RESOURCE_NOT_READY / NO_GO**。

继续等待真实资源交付；资源到位后重新执行本报告全部检查，在此之前不启动Worker，不发起真实Workflow流程。
