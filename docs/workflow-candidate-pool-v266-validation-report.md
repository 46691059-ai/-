# V2.6.6 Workflow Candidate Pool Foundation 真实 MySQL/Flyway 验收报告

## 1. 验收环境

- Sprint：2-3.7-WF4.1.1
- 日期：2026-08-12（Asia/Shanghai）
- 数据库：MySQL Community Server 8.4.9
- Flyway：13.0.0
- 应用测试：Java 21.0.12、Maven 3.9.9
- 环境：两个新建、隔离、仅回环地址可访问的一次性 MySQL 数据目录
- 路径：Fresh 空业务库；V2.6.5 完成库 Upgrade 至 V2.6.6
- 边界：未连接生产、托管或未知数据库；未启动 Claim；未实现 ROLE/POSITION/ORG Resolver

## 2. Fresh 结果

- 完整权威 Migration 链执行至 V2.6.6：PASS
- `flyway_schema_history` 成功记录：29，失败记录：0
- strict validate：PASS
- 第二次 migrate：No migration necessary
- V2.6.6 执行次数：1

## 3. Upgrade 结果

- 基线：V2.6.5
- Upgrade 仅执行 V2.6.6 一次：PASS
- strict validate：PASS
- 第二次 migrate：No migration necessary
- Fresh/Upgrade Flyway checksum 与最终 Schema：一致

## 4. V2.6.6 Checksum

- Flyway checksum：`398007297`
- Repository SHA-256：`3b73653d571a5f5497d10865b3ef692e694387b8d220ec2af383bea9c82e5d4f`
- V2.5.0—V2.6.6 文件摘要与 `SHA256SUMS`：全部一致，无历史漂移

## 5. Schema 结构

| 对象 | 字段数 | CHECK | 外键 | 唯一约束（含主键） |
|---|---:|---:|---:|---:|
| `workflow_task` | 28 | 8 | 3 | 5 |
| `workflow_task_candidate_pool` | 32 | 10 | 4 | 4 |
| `workflow_task_candidate_member` | 24 | 10 | 1 | 3 |

全部 28 个 CHECK 均为 ENFORCED。Candidate Pool 与 Member 均具备主键、审计字段、`deleted`、`delete_token`、`version`、普通/唯一索引和复合外键。Pool 通过复合键绑定 Task、NodeExecution、Instance、Resolver Binding 和 Assignment Snapshot。

## 6. assignment_mode 验证

- `USER + DIRECT`：保持有效。
- DIRECT Task 缺少 `assignee_user_id`：数据库拒绝。
- CANDIDATE_POOL Task 的初始 `assignee_user_id=NULL`：数据库接受。
- V2.6.6 允许 ROLE/POSITION/ORG + CANDIDATE_POOL 的持久化形状，但当前 Static Registry 仍只提供 `EXPLICIT_USER_V1 + USER + DIRECT`；314 项应用测试证明不可执行的 Resolver 未被开放。

## 7. Candidate Pool 验证

测试 Pool 正确关联 Task `96661`、Instance `96620`、NodeExecution `96651`、Resolver Binding `96631`、Assignment Snapshot `96671`。同一活动 Task 的第二套 Pool 被 `uk_workflow_candidate_pool_task` 唯一约束拒绝。

## 8. Candidate Member 验证

测试 Pool 保存 2 个成员，覆盖 `candidate_user_id`、来源、组织/岗位/角色快照、资格快照、排序与状态。相同 Pool 的重复用户、非法状态及非法排序均被数据库拒绝。

## 9. Hash 验证

- 相同 canonical 内容：SHA-256 稳定一致。
- Candidate 内容变化：SHA-256 发生变化。
- 合法 64 位小写十六进制：接受。
- 长度错误、非 Hex、大写、混合大小写：全部拒绝。
- Candidate Pool 相关 Schema fingerprint：`7aabbe431fce77fce1af5929d71c965e50a31c5451079e3bc82bfe4903dbdc1d`

## 10. 外键负向测试

不存在或交叉组合的 Task、NodeExecution、Instance、Resolver Binding、Assignment Snapshot 均被唯一/复合外键链拒绝。Pool/Member 的 task、instance 组合无法产生孤立或跨实例数据。

## 11. CHECK 负向测试

共执行 22 项 Candidate Pool 专项负向测试，全部 PASS。覆盖非法 assignment mode、Pool/Member status、Hash、delete token、负 version、candidate count、sort order、重复用户以及缺失/交叉引用。

## 12. DIRECT 兼容

数据库夹具中 2 个有效 DIRECT Task 均保留 assignee，Candidate Pool 数量为 0，Candidate Member 数量为 0。应用层 `GET /workflow/tasks/{taskId}/candidates` 的 DIRECT 兼容投影由全量测试覆盖，查询不触发持久化回填。

## 13. Legacy 兼容

V2.6.6 不对 SINGLE_NODE_LEGACY、旧 USER+DIRECT 或既有 Multi-Node DIRECT Task 回填 Pool/Member。一次性验收库没有伪造 Legacy 数据；Legacy 只读投影及“不重新运行 Resolver”由既有自动化测试覆盖。全量后端结果：314/314 PASS。

## 14. 事务回滚

Pool 与 Members 同一事务写入。制造重复 Member 后事务失败，复核 Pool=0、Member=0，无空 Pool 或部分成员残留：PASS。

## 15. 并发测试

两个独立 MySQL 会话同时为同一 Task 创建 Pool。结果为一方提交成功，另一方被唯一约束拒绝；最终仅 1 个 Pool、2 个 Member：PASS。

## 16. Schema Fingerprint

- Fresh 完整 Schema：`bf3b961609484b58dec1f37973cee254c18920a9927e0b5ab7a2b0fed5f89f0e`
- Upgrade 完整 Schema：同上
- Fresh/Upgrade Workflow Schema：`591dd48c4551e289d5e00c87c21c689b1e90bec7cd7fa978ade3a3ab7190f0ce`
- Binding Schema：`90059038a715c1c44634f375b355705ca5698afb0c5bc2184298b8ecbd814781`
- Candidate Pool 相关 Schema：`7aabbe431fce77fce1af5929d71c965e50a31c5451079e3bc82bfe4903dbdc1d`

双路径全部一致。

## 17. 历史 Migration 摘要

V2.5.0—V2.6.6 逐文件重新计算 SHA-256，均与仓库清单匹配。V2.6.6 SQL 未修改；V2.5.0—V2.6.5 历史 Migration 未修改。

## 18. 临时环境清理

验收结束后已停止两个隔离 MySQL 进程。一次性数据目录在证据写入本报告及 Inventory 后删除；未影响任何已知数据库实例。

## 19. 最终结论

`PASS`。V2.6.6 晋级为：

- `CANONICAL_IMMUTABLE`
- `EPHEMERAL_MYSQL8_VALIDATED`

该状态只证明一次性 MySQL 8/Flyway 资产验收通过，不等同生产部署。Claim、Release、Transfer、ROLE/POSITION/ORG Resolver 及 Investment 流程改造仍不在本 Sprint 范围内。
