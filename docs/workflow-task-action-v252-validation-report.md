# V2.5.2 Workflow任务动作Migration真实MySQL验收报告

项目：《县域国企数字化运营治理平台》
Sprint：2-3.7-WF2.4.1
验收日期：2026-08-10
结论：**PASS**

## 1. 验收边界与环境

本次只验收`V2.5.2__create_workflow_task_action.sql`。没有修改V2.4.0—V2.4.9、V2.5.0、V2.5.1或V2.5.2，没有修改Workflow或Investment业务代码，没有创建V2.5.3及更高Migration，也没有启动Workflow业务流程。

验收使用两个全新初始化、只监听`127.0.0.1`的一次性实例。没有连接生产、未知、受管或此前使用过的验收数据库。

| 项目 | Fresh | Upgrade |
|---|---|---|
| MySQL | Community Server 8.4.9 | Community Server 8.4.9 |
| Flyway | 13.0.0 | 13.0.0 |
| 临时端口 | 33770 | 33771 |
| 基础表数量 | 118 | 118 |
| 基线版本 | 2.0.0 | 2.0.0 |
| 迁移路径 | 完整权威链至2.5.2 | 完整权威链至2.5.1，再升级2.5.2 |

迁移文件由正式扫描目录逐文件复制后按`SHA256SUMS`复核，18项全部匹配。

## 2. Fresh迁移结果

- baseline：成功，版本2.0.0；
- migrate：成功执行18个版本化Migration；
- 最终版本：2.5.2；
- post strict validate：成功验证19条记录；
- 第二次migrate：`No migration necessary`；
- history：19条成功、0条失败；
- V2.5.2执行时间：97ms。

## 3. Upgrade迁移结果

- 先迁移至V2.5.1：成功，V2.5.2是唯一Pending Migration；
- V2.5.1 → V2.5.2：成功，仅执行1个Migration；
- 最终版本：2.5.2；
- post strict validate：成功验证19条记录；
- 第二次migrate：`No migration necessary`；
- history：19条成功、0条失败；
- V2.5.2执行时间：126ms。

## 4. Flyway history、checksum与摘要

Fresh与Upgrade中V2.5.2的记录一致：

```text
installed_rank: 19
version: 2.5.2
description: create workflow task action
type: SQL
checksum: -586677216
success: 1
```

V2.5.2 SHA-256在验收前后保持：

```text
00ad19ad16dec01228f5ec45eb48ad0f8a9f887b4dfe27c797c4b5e56f508302
```

## 5. workflow_task_action结构核查

表存在，InnoDB，`utf8mb4_general_ci`，两个路径结构一致。

### 5.1 字段

共20个字段：

```text
id bigint NOT NULL
action_no varchar(100) NOT NULL
task_id bigint NOT NULL
instance_id bigint NOT NULL
action_type varchar(30) NOT NULL
operator_user_id bigint NOT NULL
operator_org_id bigint NOT NULL
action_comment varchar(1000) NULL
action_time datetime(3) NOT NULL
idempotency_key varchar(200) NOT NULL
request_hash varchar(128) NOT NULL
trace_id varchar(64) NULL
created_by varchar(64) NULL
created_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
updated_by varchar(64) NULL
updated_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
deleted smallint NOT NULL DEFAULT 0
delete_token bigint NOT NULL DEFAULT 0
remark varchar(500) NULL
version int NOT NULL DEFAULT 0
```

用户要求中的`operator_id`在权威SQL中具体拆分为`operator_user_id`和`operator_org_id`，两者均已核查。

### 5.2 主键与索引

共6个逻辑索引、16个索引列：

| 类型 | 名称 | 列 |
|---|---|---|
| 主键 | PRIMARY | id |
| 唯一 | uk_workflow_task_action_no | action_no, delete_token |
| 唯一 | uk_workflow_task_action_idempotency | task_id, idempotency_key, delete_token |
| 普通 | idx_workflow_task_action_instance | instance_id, action_time, deleted |
| 普通 | idx_workflow_task_action_operator | operator_user_id, action_time, deleted |
| 普通 | idx_workflow_task_action_task_type | task_id, action_type, action_time, deleted |

### 5.3 外键

| 外键 | 本表字段 | 引用 |
|---|---|---|
| fk_workflow_task_action_task | task_id | workflow_task(id) |
| fk_workflow_task_action_instance | instance_id | workflow_instance(id) |

两者均为`ON DELETE RESTRICT / ON UPDATE RESTRICT`。

### 5.4 CHECK约束

共5项，Fresh与Upgrade均为`ENFORCED=YES`：

- `chk_workflow_task_action_type`；
- `chk_workflow_task_action_operator`；
- `chk_workflow_task_action_deleted`；
- `chk_workflow_task_action_optimistic`；
- `chk_workflow_task_action_delete_token`。

审计字段、`deleted`、`delete_token`和`version`均存在且定义符合V2.5.2。

## 6. 负向约束测试

以下测试在Fresh与Upgrade两个实例分别执行，结果一致：

| 场景 | 预期MySQL错误 | Fresh | Upgrade |
|---|---:|---|---|
| 重复任务幂等键 | 1062 | PASS | PASS |
| 不存在的task_id | 1452 | PASS | PASS |
| 不存在的instance_id | 1452 | PASS | PASS |
| INVALID_ACTION | 3819 | PASS | PASS |
| operator_user_id=0 | 3819 | PASS | PASS |
| 非法delete_token组合 | 3819 | PASS | PASS |
| version=-1 | 3819 | PASS | PASS |

验收数据已按外键逆序清理，两个实例中测试动作剩余数量均为0。

## 7. 双路径Schema fingerprint

指纹使用仓库只读脚本`database/mysql/verification/schema_fingerprint.sql`计算，覆盖tables、columns、indexes、foreign keys和check constraints，并排除Flyway history等发布元数据。

| 范围 | 行数 | Fresh SHA-256 | Upgrade SHA-256 | 结果 |
|---|---:|---|---|---|
| 完整Schema | 5015 | d33171cce6734c21359f3c35b7ee2125536dc4d8a0f3275e265db137dee83e78 | d33171cce6734c21359f3c35b7ee2125536dc4d8a0f3275e265db137dee83e78 | 一致 |
| Workflow | 321 | 05c345e7b276a8cdb37b28ad16462779157471c41cfc3b0919d41392c8514837 | 05c345e7b276a8cdb37b28ad16462779157471c41cfc3b0919d41392c8514837 | 一致 |
| workflow_task_action | 44 | d7ab4c894ead6aaa91c63e468d5c60b8b9a55ee676dcf6f645437b72bd5e0f80 | d7ab4c894ead6aaa91c63e468d5c60b8b9a55ee676dcf6f645437b72bd5e0f80 | 一致 |

## 8. 历史Migration哈希复核

V2.4.0—V2.4.9及V2.5.0—V2.5.2共13个资产全部与`SHA256SUMS`一致，摘要漂移为0。

关键摘要：

```text
V2.5.0  5b41f9b787bbf4bdf0927b5f30dc487c6d88085d81aedf19e784d44474c02926
V2.5.1  e52ca6bdd00426bc7fee1c9b92cd3dc3fecd5084e3533f955c89c4867ceb4469
V2.5.2  00ad19ad16dec01228f5ec45eb48ad0f8a9f887b4dfe27c797c4b5e56f508302
```

## 9. 资产治理与临时环境清理

全部验收通过后，V2.5.2已晋级为：

```text
CANONICAL_IMMUTABLE
EPHEMERAL_MYSQL8_VALIDATED
Flyway checksum: -586677216
```

`SHA256SUMS`原有V2.5.2记录正确，因此本Sprint未改写摘要清单。

临时环境清理结果：

```text
Fresh mysqladmin shutdown exit: 0
Upgrade mysqladmin shutdown exit: 0
33770 listening: false
33771 listening: false
Fresh process running: false
Upgrade process running: false
负向测试动作剩余记录: 0 / 0
```

一次性数据目录保留在本机临时目录中作为本次验收证据，不注册为服务且当前无进程占用；未删除或触碰其他MySQL实例。

## 10. 最终结论与剩余风险

V2.5.2通过MySQL Community Server 8.4.9 Fresh完整迁移、V2.5.1 Upgrade迁移、Flyway history/checksum、strict validate、二次migrate no-op、表结构、外键、索引、CHECK强制状态、双路径Schema指纹和七类负向约束测试。

验收结论：**PASS**。

剩余风险：

1. 本次状态仅证明一次性隔离MySQL 8验收通过，不代表开发、测试、预生产或生产环境已经执行V2.5.2。
2. 尚未完成达梦、人大金仓对CHECK、外键、`datetime(3)`及逻辑删除唯一索引语义的专项验收。
3. 当前仍是单审批节点基础实现，不包含多节点推进、条件路由、会签、加签、转办、自动审批或Investment回调。
