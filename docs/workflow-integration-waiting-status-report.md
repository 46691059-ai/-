# Workflow联调等待状态报告

## 1. 报告信息

| 项目 | 内容 |
|---|---|
| Sprint | Sprint 2-3.7.14 |
| 维护日期 | 2026-08-10（Asia/Shanghai） |
| 工作模式 | 等待外部资源，不进行业务开发 |
| 当前状态 | **FAILED / VERSION_FREEZE_READY / WORKFLOW_RESOURCE_NOT_READY / NO_GO** |

本次仅维护等待状态、P0台账和READY门禁。未修改冻结Commit，未修改V2.4.0—V2.4.9 Migration，未启动Outbox Worker，未连接未知数据库，也未伪造资源或READY结果。

## 2. 当前状态

### 2.1 资源检查

| 资源 | 当前证据 | 状态 |
|---|---|---|
| Investment | 地址未设置；默认`8080`不可达 | 未交付 |
| Workflow | 地址未设置；默认`8090`不可达 | 未交付 |
| Redis | 地址未设置；默认`6379`不可达 | 未交付 |
| MySQL应用数据源 | JDBC未设置；默认`3306`不可达 | 未交付 |
| 本机`3307` MySQL | 只有未知监听，无用途、认证和Schema证明 | 隔离，不连接 |
| Workflow安全配置 | 五项配置均未设置 | 未交付 |
| Workflow定义 | 流程ID、版本和节点未交付 | 未交付 |
| 测试账号 | 发起人、审批人和任务权限未交付 | 未交付 |
| 部署环境文件 | `deploy/.env.production`不存在 | 未交付 |

### 2.2 P0汇总

| 分类 | P0数量 | OPEN | READY_FOR_VERIFY | VERIFIED | CLOSED |
|---|---:|---:|---:|---:|---:|
| 服务 | 4 | 4 | 0 | 0 | 0 |
| 数据库 | 3 | 3 | 0 | 0 | 0 |
| Workflow | 2 | 2 | 0 | 0 | 0 |
| 安全配置 | 3 | 3 | 0 | 0 | 0 |
| 流程定义 | 2 | 2 | 0 | 0 | 0 |
| 账号权限 | 3 | 3 | 0 | 0 | 0 |
| 合计 | 17 | 17 | 0 | 0 | 0 |

没有新增资源交付证据，因此本次没有P0发生状态流转。

## 3. 阻断项

状态只能按`OPEN → READY_FOR_VERIFY → VERIFIED → CLOSED`推进。资源或基线变化后，已验证项目必须退回`OPEN`重新检查。

### 3.1 服务

| 编号 | 状态 | 责任边界 | 关闭条件 |
|---|---|---|---|
| BLK-SVC-001 | OPEN | Investment团队负责冻结制品部署；运维负责运行资源 | 制品可追溯到冻结Commit，服务及MySQL/Redis健康为UP |
| BLK-SVC-002 | OPEN | Workflow团队负责服务；运维负责地址、DNS和TLS | Workflow健康连续成功，服务及契约版本兼容 |
| BLK-SVC-003 | OPEN | 中间件团队负责Redis；安全团队负责认证引用 | 连接、TLS、认证、TTL、Nonce和锁能力通过 |
| BLK-SVC-004 | OPEN | 运维和网络安全负责双向路由及允许列表 | Investment调用Workflow及Workflow回调Investment均通过 |

### 3.2 数据库

| 编号 | 状态 | 责任边界 | 关闭条件 |
|---|---|---|---|
| BLK-DB-001 | OPEN | DBA交付MySQL/JDBC；运维安全注入凭证 | MySQL 8、目标Schema及最小权限连接验证通过 |
| BLK-DB-002 | OPEN | DBA/发布团队执行Flyway只读核查 | history无失败、最终V2.4.9、严格`validate`通过 |
| BLK-DB-003 | OPEN | DBA/发布团队执行结构核查 | V2.4.9 checksum和当前Schema指纹与批准基准一致 |

未知`3307`实例不属于已交付JDBC，未经资源所有方和DBA确认不得连接。

### 3.3 Workflow

| 编号 | 状态 | 责任边界 | 关闭条件 |
|---|---|---|---|
| BLK-WF-001 | OPEN | Workflow团队维护健康、实例和API权威数据 | 健康、版本和只读API在当前环境通过 |
| BLK-WF-002 | OPEN | Workflow负责事件发送；Investment负责回调验签和业务处理 | 回调订阅、契约、重试、Token/HMAC握手通过 |

### 3.4 安全配置

| 编号 | 状态 | 责任边界 | 关闭条件 |
|---|---|---|---|
| BLK-SEC-001 | OPEN | 安全团队交付Token；双方服务按最小权限使用 | Service/Callback Token用途隔离，正向认证和错误Token拒绝通过 |
| BLK-SEC-002 | OPEN | 安全团队维护HMAC Key、轮换和吊销 | 正确签名通过，错误Key/签名被拒绝，轮换可执行 |
| BLK-SEC-003 | OPEN | 安全、运维和中间件共同保障时钟与Nonce | 过期时间戳、重复Nonce及Redis故障测试通过，审计脱敏 |

报告只维护配置存在性和验收结果，不保存Token、密码或Secret值。

### 3.5 流程定义

| 编号 | 状态 | 责任边界 | 关闭条件 |
|---|---|---|---|
| BLK-PROC-001 | OPEN | Workflow团队发布流程；业务治理方确认版本 | 真实流程ID、定义键、固定版本及启用状态通过API核验 |
| BLK-PROC-002 | OPEN | 业务治理方冻结路线；Workflow编排节点 | 节点、条件、顺序、候选组、驳回/撤回/异常路线一致 |

### 3.6 账号权限

| 编号 | 状态 | 责任边界 | 关闭条件 |
|---|---|---|---|
| BLK-IAM-001 | OPEN | IAM/系统管理员交付发起人；Investment维护RBAC和数据范围 | 实际登录、提交权限和数据隔离通过 |
| BLK-IAM-002 | OPEN | Workflow管理员交付审批主体和节点权限 | 候选、查询、签收、办理及越权拒绝通过 |
| BLK-IAM-003 | OPEN | IAM和业务管理员维护身份映射及职责分离 | 双系统映射唯一，发起人与唯一最终审批人分离 |

## 4. 冻结状态

| 检查项 | 当前结果 | 状态 |
|---|---|---|
| 冻结Commit | `abb5fc9eb3a075b723eb5e98d9d9d7932429b8c8` | PASS |
| 冻结Tag | `v2.4.9-workflow-integration-rc1` | PASS |
| Tag指向 | 与冻结Commit一致 | PASS |
| 检查时HEAD | `8f1bc34cfb6e4fbdd2533296ad7f6f675a8195e7` | 信息项 |
| 检查前工作区 | 干净 | PASS |
| 候选后业务代码变更 | 0 | PASS |
| 候选后V2.4.0—V2.4.9变更 | 0 | PASS |
| 正式Migration数量 | 15 | PASS |
| 最高Migration | V2.4.9 | PASS |
| V2.4.0—V2.4.9权威资产哈希 | 10/10通过 | PASS |

冻结状态保持`VERSION_FREEZE_READY`。治理报告不改变冻结候选Tree或Tag；缺陷修复必须形成新Commit、候选标签和更高版本Migration。

`WORKFLOW_OUTBOX_WORKER_ENABLED`未设置，默认保持`false`。当前无Investment实例，Worker未启动。

## 5. READY门禁

### 5.1 启动条件

进入真实Workflow联调前必须全部满足：

1. 17项P0全部依次验证并转为`CLOSED`。
2. 冻结Tag仍指向指定Commit，业务代码和历史Migration没有漂移。
3. Investment、Workflow、Redis和MySQL 8在指定非生产环境实际可达。
4. Investment运行制品可追溯到冻结候选，服务及依赖健康。
5. 目标数据库Flyway无失败、最终V2.4.9、严格`validate`和Schema指纹通过。
6. Workflow真实流程ID、固定版本、节点、路由、候选组和回调订阅通过API核验。
7. Token、HMAC、时间窗口、Nonce、TLS及允许列表的正向和负向验证通过。
8. 发起人、审批人、任务权限、身份映射、数据范围和职责分离通过。
9. traceId、审计、告警、失败停止、死信和人工重放审批能力就绪。
10. 联调负责人、Workflow、安全、DBA、运维和业务管理员完成Go/No-Go签署。

启动顺序：MySQL → Redis → Workflow → Investment（Worker关闭）→ 服务健康 → 数据库准入 → Workflow/账号验收 → 安全握手 → Go/No-Go → 受控开启单实例Worker → 首次隔离流程。

### 5.2 停止条件

出现以下任一情况必须保持或恢复`NO_GO`，并关闭Worker、暂停新增提交：

- 冻结Tag、候选代码、配置版本或Migration发生漂移；
- MySQL、Redis、Workflow或Investment不可用；
- Flyway失败、checksum或Schema指纹不一致；
- 流程ID、版本、节点或候选组与冻结基线不一致；
- Token/HMAC异常、Nonce重放、时钟漂移或TLS/允许列表失效；
- 越权、身份映射错误或职责分离失效；
- 重复流程、重复推进、事件乱序缺口、死信增长或幂等失效；
- traceId、审计或告警链路失效；
- 密钥或敏感业务数据出现在日志、响应、报告或监控中。

停止后保留证据，不执行`clean`、自动`repair`或删除可靠性消息，对应P0退回`OPEN`。

## 6. 下一步动作

1. 等待平台运维、Workflow、安全、DBA、中间件和IAM责任方交付资源。
2. 收到完整交付包后，只将对应P0转为`READY_FOR_VERIFY`。
3. 在当前指定非生产环境执行真实验证，通过后转为`VERIFIED`。
4. 责任方与联调负责人联合确认后转为`CLOSED`。
5. 17项全部关闭后重新进行Go/No-Go评审；此前持续关闭Worker。

在新的资源交付事件发生前，不重复执行深度验收、不连接未知数据库、不进入业务联调。

## 7. 当前结论

等待状态维护完成，17项P0全部保持`OPEN`，冻结版本稳定。

当前状态：**FAILED / VERSION_FREEZE_READY / WORKFLOW_RESOURCE_NOT_READY / NO_GO**。
