# Workflow资源到位验收报告

## 1. 报告信息

| 项目 | 内容 |
|---|---|
| Sprint | Sprint 2-3.7.11 |
| 验收日期 | 2026-08-10（Asia/Shanghai） |
| 验收类型 | 真实Workflow联调资源一次性READY验收 |
| 冻结Commit | `abb5fc9eb3a075b723eb5e98d9d9d7932429b8c8` |
| 冻结Tag | `v2.4.9-workflow-integration-rc1` |
| 最终结果 | **FAILED / NO_GO** |

本次验收在资源存在性门禁失败。未修改冻结Commit业务代码，未修改V2.4.0—V2.4.9 Migration，未启动Outbox Worker，未使用历史验收环境替代当前环境，也未输出、保存或推断任何密钥值。

## 2. 冻结基线确认

| 检查项 | 结果 | 状态 |
|---|---|---|
| Tag指向冻结Commit | 完全一致 | PASS |
| 检查时HEAD | `27464cf646f695db7036a0c0bcf1507fe395d0bc` | 信息项 |
| 检查前工作区 | 干净 | PASS |
| 候选后业务代码变更 | 0 | PASS |
| 候选后V2.4.0—V2.4.9变更 | 0 | PASS |
| V2.4.0—V2.4.9权威资产哈希 | 10/10通过 | PASS |

冻结候选保持稳定。本报告属于候选后的治理证据，不改变候选Tag指向。

## 3. 服务验收

### 3.1 Investment

| 检查项 | 实际结果 | 状态 |
|---|---|---|
| 运行地址 | `INVESTMENT_BASE_URL`未设置 | FAIL |
| 默认端口 | `127.0.0.1:8080`无监听 | FAIL |
| 健康接口 | 不可达 | FAIL |
| Java服务进程 | 未发现 | FAIL |

冻结候选此前构建测试通过，但当前没有实际部署的Investment联调实例，不能接收Workflow回调。

### 3.2 Workflow

| 检查项 | 实际结果 | 状态 |
|---|---|---|
| 运行地址 | `WORKFLOW_BASE_URL`未设置 | FAIL |
| 默认端口 | `127.0.0.1:8090`无监听 | FAIL |
| `/actuator/health` | 不可达 | FAIL |
| `/health` | 不可达 | FAIL |

### 3.3 Redis

| 检查项 | 实际结果 | 状态 |
|---|---|---|
| 主机、端口和认证 | 均未设置 | FAIL |
| 默认端口 | `127.0.0.1:6379`无监听 | FAIL |
| Redis进程 | 未发现 | FAIL |
| TTL、Nonce和分布式锁 | 因资源缺失未执行 | NOT_VERIFIED |

### 3.4 MySQL

| 检查项 | 实际结果 | 状态 |
|---|---|---|
| JDBC、账号和认证 | 均未设置 | FAIL |
| 应用默认端口 | `127.0.0.1:3306`无监听 | FAIL |
| 本机其他实例 | `127.0.0.1:3307`存在`mysqld`监听 | NOT_VERIFIED |
| `3307`用途、版本、认证和Schema | 未提供 | NOT_VERIFIED |

未知`3307`实例没有被应用JDBC引用，不属于已交付资源。本次未尝试猜测凭证、连接或修改该实例。

### 3.5 部署资源包

| 检查项 | 实际结果 | 状态 |
|---|---|---|
| `deploy/.env.production` | 不存在 | FAIL |
| Workflow Service Token secret文件 | 不存在 | FAIL |
| Callback Token secret文件 | 不存在 | FAIL |
| HMAC当前Secret文件 | 不存在 | FAIL |
| MySQL应用密码secret文件 | 不存在 | FAIL |
| Redis密码secret文件 | 不存在 | FAIL |
| JWT Secret文件 | 不存在 | FAIL |
| Docker CLI/Daemon | 当前环境不可用 | FAIL |

以上只检查文件存在性和服务工具状态，没有读取或输出任何密钥内容。

## 4. 安全验收

本节仅验证当前进程配置存在性：

| 配置 | 存在性 | 状态 |
|---|---|---|
| `WORKFLOW_BASE_URL` | NOT_SET | FAIL |
| `WORKFLOW_SERVICE_TOKEN` | NOT_SET | FAIL |
| `WORKFLOW_CALLBACK_TOKEN` | NOT_SET | FAIL |
| `WORKFLOW_HMAC_CURRENT_KEY_ID` | NOT_SET | FAIL |
| `WORKFLOW_HMAC_CURRENT_SECRET` | NOT_SET | FAIL |

安全资源未到位，因此没有执行服务Token认证、回调Token认证、HMAC-SHA256、Key ID匹配、时间窗口、Nonce、防重放和密钥轮换验收。

`WORKFLOW_OUTBOX_WORKER_ENABLED`未设置，应用默认保持`false`。当前无Investment进程，Worker从未启动。

## 5. 数据库验收

由于JDBC和安全凭证均未交付，本节按门禁规则停止，不连接任何猜测数据库。

| 检查项 | 实际结果 | 状态 |
|---|---|---|
| JDBC连接 | 未执行 | NOT_VERIFIED |
| MySQL 8版本 | 未读取 | NOT_VERIFIED |
| 目标Schema | 未确认 | NOT_VERIFIED |
| `flyway_schema_history` | 未读取 | NOT_VERIFIED |
| Flyway失败记录 | 未查询 | NOT_VERIFIED |
| Flyway最终版本 | 未查询 | NOT_VERIFIED |
| 严格`validate` | 未执行 | NOT_VERIFIED |
| V2.4.9 checksum | 仅冻结资产已确认，目标库未核验 | NOT_VERIFIED |
| V2.4.9结构 | 未查询 | NOT_VERIFIED |
| 当前Schema指纹 | 未计算 | NOT_VERIFIED |

历史临时MySQL的V2.4.9验收结果未用于本次判定。数据库READY必须以本次目标JDBC连接的实际history、validate和结构指纹为证据。

## 6. Workflow验收

由于Workflow地址和认证资源未交付，本节未调用任何真实Workflow API。

| 检查项 | 实际结果 | 状态 |
|---|---|---|
| 健康接口 | 不可达 | FAIL |
| 真实流程ID | 未查询 | NOT_VERIFIED |
| 流程定义键 | 环境未设置 | NOT_VERIFIED |
| 显式流程版本 | 环境未设置、未查询 | NOT_VERIFIED |
| 发布及启用状态 | 未查询 | NOT_VERIFIED |
| 节点编码和顺序 | 未查询 | NOT_VERIFIED |
| 条件和路由 | 未查询 | NOT_VERIFIED |
| 候选组和任务权限 | 未查询 | NOT_VERIFIED |
| 回调订阅 | 未查询 | NOT_VERIFIED |

冻结候选中的源码默认值和接口契约不构成外部Workflow资源已部署的证据。

## 7. 账号验收

| 检查项 | 实际结果 | 状态 |
|---|---|---|
| Investment发起人 | 未交付 | FAIL |
| Workflow审批人 | 未交付 | FAIL |
| 条件复核人 | 未交付 | FAIL |
| 发起人登录和提交权限 | 未执行 | NOT_VERIFIED |
| 审批人候选、签收和办理权限 | 未执行 | NOT_VERIFIED |
| Workflow节点权限 | 未执行 | NOT_VERIFIED |
| 跨系统身份和组织映射 | 未提供 | NOT_VERIFIED |
| 职责分离 | 未验证 | NOT_VERIFIED |

Migration中的RBAC权限定义不代表当前环境已有真实账号或Workflow任务授权。

## 8. READY判定

### 8.1 验收汇总

| 验收域 | 结果 |
|---|---|
| 冻结基线 | PASS |
| Investment服务 | FAIL |
| Workflow服务 | FAIL |
| Redis | FAIL |
| MySQL/JDBC | FAIL |
| 安全配置 | FAIL |
| 数据库V2.4.9 | NOT_VERIFIED |
| Workflow定义 | NOT_VERIFIED |
| 账号和任务权限 | FAIL / NOT_VERIFIED |
| Worker保持关闭 | PASS |
| 最终READY | **DENIED** |

### 8.2 失败原因

本次不是联调功能失败，而是外部资源仍未交付：没有运行服务、运行配置、密钥文件、目标JDBC、流程定义元数据或测试账号。资源存在性门禁失败后，深度验收按安全规则停止。

### 8.3 重新验收前置条件

1. 提供并启动指定非生产Investment、Workflow、Redis和MySQL 8实例。
2. 通过受控配置和密钥设施交付五项Workflow配置，不在报告或Git中保存值。
3. 提供明确的JDBC、目标Schema和最小权限数据库账号。
4. 提供Workflow真实流程ID、固定版本、节点、候选组和回调订阅。
5. 提供发起人、审批人、复核人及跨系统身份映射。
6. 保持冻结Tag不变，资源验收期间继续关闭Worker。
7. 资源交付后重新执行服务、数据库、Workflow、安全和账号全部检查，不复用本次或历史环境结果。

## 9. 最终结论

冻结版本有效，但外部资源没有到位，本次一次性READY验收失败。

最终状态：**VERSION_FREEZE_READY / WORKFLOW_RESOURCE_NOT_READY / NO_GO**。

不进入Sprint 2-3.7真实Workflow联调，继续等待资源提供；在新的资源交付事件发生前不启动Worker。
