# Workflow Claim 预生产资源准入报告

Sprint：`2-3.7-WF4.2.6`

结论：`BLOCKED / PREPROD_RESOURCE_NOT_READY`

检查时间：2026-08-12（Asia/Shanghai）

本次仅检查资源是否已明确交付及能否验证环境身份。未连接数据库、Redis、应用或网关，未执行Flyway、Guard、Migration、Smoke Test、并发测试或Worker操作；未使用本地一次性MySQL、开发库或未知3306/3307实例替代预生产。

## 1. 已确认内容

| 项目 | 结果 | 证据/说明 |
|---|---|---|
| V2.6.7 SHA-256 | PASS | `800e1a5b1af67e1680a3f0233e9d5400c0b42f39349d181f1777c7ce04ac6aed` |
| V2.6.8 SHA-256 | PASS | `4cfd089f4aa05f6e4fb9d9dc250159c0229b3d98eb400089e5e08a758cb4a207` |
| V2.6.7 Flyway checksum | 冻结基线 | `1689998435`；尚未从预生产history读取 |
| V2.6.8 Flyway checksum | 冻结基线 | `1316060036`；尚未从预生产history读取 |
| 仓库Commit | 已识别 | `f2429571c5255366b26553fe29064b6052faa437`，工作区为dirty，不能替代待交付应用制品身份 |
| V2.6.9 | 不存在 | 符合Sprint边界 |

V2.6.7、V2.6.8 SQL未修改。V2.6.7只能作为必须立即升级到V2.6.8的中间版本，不能成为最终部署状态。

## 2. 缺失资源与责任方

| # | 必需资源 | 当前状态 | 责任方 | 需要交付的非敏感证明 | 验收方式 |
|---:|---|---|---|---|---|
| 1 | 预生产数据库JDBC及环境身份 | NOT_DELIVERED | DBA / Operations | 环境名、脱敏host标识、port、schema、instance ID、主从角色、MySQL版本 | 仅对批准地址连接，核对`VERSION()`、`server_uuid`和schema |
| 2 | 数据库账号与Flyway权限 | NOT_DELIVERED | DBA / Security | 密钥系统引用、最小权限清单、有效窗口；不提交明文 | 认证后执行只读权限检查及Flyway策略检查 |
| 3 | 预生产应用地址 | NOT_DELIVERED | Application Owner / Operations | URL、版本、Git Commit、镜像digest、Artifact SHA | 健康接口、制品清单和部署平台记录交叉核对 |
| 4 | Redis地址与身份 | NOT_DELIVERED | Operations | 环境标识、拓扑、只读健康检查授权 | 对批准实例执行PING/应用健康校验，不输出密码 |
| 5 | 网关Claim阻断能力 | NOT_DELIVERED | Operations / Application Owner | 路由规则、维护响应、操作及恢复步骤 | 实测Claim入口被阻断且其他只读能力符合预期 |
| 6 | Claim Feature Flag | NOT_DELIVERED | Application Owner | Flag名称、作用范围、默认值、审计与回切方式 | 双门禁测试并观察目标表写入为0 |
| 7 | Worker/Scheduler控制 | NOT_DELIVERED | Application Owner / Operations | Outbox、Inbox Gap、Replay、Workflow Scheduler逐项启停方法 | 逐项状态核查；停用后验证无后台写入 |
| 8 | 预生产测试账号与隔离测试数据 | NOT_DELIVERED | Business Owner / Security | 发起人、Candidate、非Candidate、审批人、SoD/DataScope场景账号引用 | 登录和最小权限检查；不得输出凭据 |
| 9 | DBA或等价责任人 | NOT_DELIVERED | Release Management | 姓名/值班标识、联系方式、替补、Kill审批链 | RACI签署和变更窗口确认 |
| 10 | 可恢复备份/快照 | NOT_DELIVERED | DBA / Operations | Backup ID、时间、一致性点、GTID/binlog、RPO/RTO、恢复报告 | 在隔离环境完成恢复并核对schema/数据 |
| 11 | 监控指标读取能力 | NOT_DELIVERED | Operations / DBA | Dashboard或临时采集方案、指标权限、证据导出位置 | 读取CPU、IOPS、锁、redo/undo/binlog、应用延迟 |
| 12 | 复制状态读取能力/不适用证明 | NOT_DELIVERED | DBA | 拓扑说明；如无复制需正式标记N/A | `SHOW REPLICA STATUS`、GTID、线程和relay backlog核查 |
| 13 | 批准的演练变更窗口 | NOT_DELIVERED | Business Owner / Release Approver | Change ID、起止时间、停写影响、沟通与升级渠道 | 各责任方签署并冻结GO/NO-GO时间点 |

当前进程环境中 `DB_URL`、数据库凭据、`REDIS_HOST`、预生产应用/网关、Feature Flag、测试账号、备份恢复、监控、变更窗口、DBA和复制模式标识均未设置。源码中的 `127.0.0.1` 默认值与Docker开发编排不具有预生产身份，不允许用于本次演练。

## 3. 阻断项

以下P0阻断尚未关闭：

1. 无法确认真实预生产数据库身份、版本、规模、Flyway history与权限。
2. 无法证明Claim写入可冻结，也无法控制并验证全部Worker/Scheduler状态。
3. 无法获得Backup ID、恢复验证、RPO/RTO，因而不具备数据库变更准入。
4. 无法采集DDL、MDL、IO、redo/undo/binlog、复制延迟和30分钟观察指标。
5. 无预生产应用制品、专用测试账号、网关和Smoke Test环境。
6. 无批准变更窗口、DBA、业务负责人和Release Approver签署。

因此不得执行Guard或V2.6.7/V2.6.8，不得声明数据规模、DDL耗时、Schema Fingerprint、Smoke、并发、Failure Injection或观察窗口结果。

## 4. READY条件

只有以下条件全部满足，资源状态才可变更为`READY_FOR_PREPROD_REHEARSAL`：

- 13项资源全部交付并由对应责任人确认；敏感信息通过受控密钥系统注入。
- 数据库、应用、Redis、网关均有明确预生产身份，且不是本地、开发或未知实例。
- Git Commit、应用镜像、V2.6.7/V2.6.8摘要和Flyway执行器版本冻结。
- 备份已实际恢复验证，并满足批准的RPO/RTO。
- Claim网关门禁、Feature Flag及所有写线程可停、可核查、可恢复。
- 监控可覆盖DDL/MDL、容量、redo/undo/binlog、复制、HTTP、Claim延迟和数据库错误。
- 预生产专用账号与隔离测试数据可覆盖合法Claim、拒绝、幂等、审批、DataScope及SoD。
- Change ID、窗口和RACI完成签署。

资源交付后应重新从身份核验开始；不得直接沿用本报告把状态推为READY。

## 5. 下一步动作

1. Release Management建立单一资源交付单，并为上表每项指定责任人和截止时间。
2. DBA交付脱敏数据库身份、最小权限引用、复制拓扑和已恢复验证的Backup ID。
3. Application Owner与Operations交付冻结应用制品、Claim双门禁、Worker逐项控制和测试账号。
4. Operations开放只读监控证据导出；Business Owner和Release Approver批准窗口及影响范围。
5. 全部资源到位后重新执行WF4.2.6完整预生产演练；通过才可输出`PREPROD_RELEASE_READY`，否则保持`BLOCKED / PREPROD_NOT_READY`。

最终状态：`BLOCKED / PREPROD_RESOURCE_NOT_READY`。
