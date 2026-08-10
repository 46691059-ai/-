# V2.4.4—V2.4.6 Investment Decision Migration真实MySQL验收报告

项目：县域国企数字化运营治理平台

Sprint：2-3.2.1

验收日期：2026-08-08

结论：**PASS**

## 1. 环境信息

| 项目 | 验收值 |
| --- | --- |
| 环境性质 | 本机临时隔离环境，非开发共享库、非测试共享库、非生产库 |
| MySQL | MySQL Community Server 8.4.9，Win64 x86_64 |
| Flyway | Redgate Flyway Community Edition 13.0.0 |
| Flyway扫描资产 | `database/migration/mysql`的字节一致临时副本 |
| 基础数据库 | 权威`01_database.sql`—`16_sprint_1_log_center.sql`及`V1.1.0__investment_data_risk_bi.sql` |
| Flyway baseline | V2.0.0 |
| 空库完整链端口 | `127.0.0.1:33316` |
| V2.4.3升级链端口 | `127.0.0.1:33317` |
| 初始表数 | 每个实例120张业务表 |
| 最终表数 | 每个实例143张表，包含`flyway_schema_history` |
| Workflow数据库 | 未创建、未连接 |
| 生产连接 | 无 |

两个MySQL实例均使用独立临时数据目录、独立端口并只绑定环回地址。验收结束后已停止临时实例。

Flyway CLI来自Redgate官方独立命令行分发包；该分发包含自身运行时，无需修改项目JDK或应用依赖。

## 2. 验收路径

### 2.1 路径A：空库完整迁移

```text
初始化120张权威基础表
  -> Flyway baseline V2.0.0
  -> pre资产与info检查
  -> migrate V2.1.0—V2.4.6
  -> post严格validate
  -> 第二次migrate no-op
  -> information_schema与Schema指纹检查
```

结果：12个版本化Migration全部成功，最终版本V2.4.6。

### 2.2 路径B：V2.4.3升级

```text
初始化120张权威基础表
  -> Flyway baseline V2.0.0
  -> target=V2.4.3 migrate
  -> 确认V2.4.4新增表数量为0
  -> pre资产与info检查
  -> migrate V2.4.4—V2.4.6
  -> post严格validate
  -> 第二次migrate no-op
  -> information_schema与Schema指纹检查
```

V2.4.3基线阶段成功执行9个版本化Migration。升级前以下表均不存在：

- `investment_decision_snapshot`；
- `investment_workflow_binding`；
- `investment_decision_audit_event`。

随后只执行V2.4.4、V2.4.5、V2.4.6三个Migration并到达V2.4.6。

## 3. Migration结果

| 检查项 | 空库完整链 | V2.4.3升级链 |
| --- | --- | --- |
| pre策略与资产清单 | 12/12 SHA-256匹配 | 12/12 SHA-256匹配 |
| migrate结果 | 成功执行12个版本 | 成功执行3个目标版本 |
| V2.4.4—V2.4.6内部执行时间 | 1,574 ms | 1,458 ms |
| post严格validate | 13条记录验证成功 | 13条记录验证成功 |
| 第二次migrate | `No migration necessary` | `No migration necessary` |
| Flyway history成功记录 | 13 | 13 |
| Flyway history失败记录 | 0 | 0 |
| 最终版本 | V2.4.6 | V2.4.6 |

说明：13条history由1条V2.0.0 baseline和12条版本化Migration组成。

### 3.1 V2.4.4—V2.4.6执行时间

| 版本 | 空库链 | 升级链 |
| --- | ---: | ---: |
| V2.4.4 | 425 ms | 455 ms |
| V2.4.5 | 524 ms | 399 ms |
| V2.4.6 | 625 ms | 604 ms |

两条路径均未出现失败Migration、checksum漂移或重复执行。

## 4. Flyway Checksum

| 版本 | Flyway checksum | SHA-256 |
| --- | ---: | --- |
| V2.4.4 | `-914691717` | `eb827c2a052ce175dc83aab33c7c42acd7aa910d253273c39c9a178fddc622c1` |
| V2.4.5 | `-1465863783` | `c7fd269066b5d21813d87bab2602c848f3ae70dc5f67b494b608507b17e671dc` |
| V2.4.6 | `1708717665` | `3e7b0a6550960d012cf861f00e61248d0ec2ca1d7b59afd91783bf3a0c677f64` |

两条路径的三个Flyway checksum完全一致。验收前后再次计算V2.4.0—V2.4.6 SHA-256，冻结SQL内容未变化。

## 5. Schema变化

### 5.1 新增表

以下三张表在两个实例均存在：

1. `investment_decision_snapshot`；
2. `investment_workflow_binding`；
3. `investment_decision_audit_event`。

验收统计：

| 指标 | 数量 |
| --- | ---: |
| 新增目标表 | 3 |
| 三张目标表字段 | 95 |
| 三张目标表索引 | 32 |
| 三张目标表外键 | 14 |
| 三张目标表CHECK约束 | 18 |
| 七张投资决策相关表外键合计 | 33 |
| 七张投资决策相关表CHECK合计 | 36 |
| 未启用CHECK约束 | 0 |

### 5.2 既有表增量字段

对以下既有表共核对26个预期新增字段，结果为26/26：

- `investment_decision`：7个；
- `investment_decision_node`：9个；
- `investment_decision_condition`：5个；
- `investment_decision_condition_action`：5个。

### 5.3 索引

已确认：

- 快照版本、快照哈希及快照所有权唯一约束；
- Workflow决策尝试、快照尝试、幂等键及流程实例唯一约束；
- 审计事件编号及来源事件唯一约束；
- 决策、风险、企业、状态、事件水位、TraceId和操作主体查询索引；
- MySQL为部分外键自动生成的支撑索引有效存在。

### 5.4 外键

已确认三张新表14个外键全部建立成功，引用范围仅包括：

- Investment本域的决策、方案版本、可研版本、尽调包、节点和条件；
- 系统基础域的`sys_user`和`sys_org`；
- 本地`investment_workflow_binding`稳定绑定。

没有外键指向Workflow数据库或Workflow自有表。

### 5.5 CHECK约束

information_schema显示目标表全部18个CHECK约束为`ENFORCED=YES`，覆盖：

- 快照版本、状态、布尔标志、风险门禁、逻辑删除及乐观锁；
- Workflow业务类型、尝试号、定义版本、事件序号、状态、逻辑删除及乐观锁；
- 审计事件类型、来源系统、操作主体类型、逻辑删除及乐观锁。

既有决策、节点、条件和条件动作表新增的状态约束也全部成功建立。

### 5.6 Workflow边界

查询`information_schema.tables`确认：

```text
workflow\_% 表数量 = 0
```

本次Migration只保存Workflow不透明引用，没有创建审批引擎、流程定义、任务或候选人表。

## 6. Schema指纹

采用`database/mysql/verification/schema_fingerprint.sql`生成规范化结构流，排除Flyway history和发布审计表。

| 路径 | 指纹行数 | SHA-256 |
| --- | ---: | --- |
| 空库完整链 | 4,594 | `166ba8139c6834bd42b2c26c08b30c45d5873de4b5a14c8fb37715d581d7f91a` |
| V2.4.3升级链 | 4,594 | `166ba8139c6834bd42b2c26c08b30c45d5873de4b5a14c8fb37715d581d7f91a` |

两条路径的目标表元数据哈希同样一致：

```text
ad701d4490bbcba61e23406a6d75767053240f3d6828d2d772acdf89a3b8c3de
```

结论：空库完整链和V2.4.3升级链得到相同Schema。

## 7. 资产状态

`database/flyway/migration-inventory.yml`已更新：

| 版本 | 资产状态 | 执行状态 |
| --- | --- | --- |
| V2.4.4 | `CANONICAL_IMMUTABLE` | `EPHEMERAL_MYSQL8_VALIDATED` |
| V2.4.5 | `CANONICAL_IMMUTABLE` | `EPHEMERAL_MYSQL8_VALIDATED` |
| V2.4.6 | `CANONICAL_IMMUTABLE` | `EPHEMERAL_MYSQL8_VALIDATED` |

状态只表示隔离MySQL 8验收通过，不表示任何开发、测试、预生产或生产数据库已应用。

## 8. 验收过程发现

以下问题均发生在临时验收工具链准备阶段，不是Migration缺陷：

1. Windows批处理会解释JDBC URL中的`&`。验收改用无需查询参数的本地JDBC URL，首次命令未创建history或执行Migration；
2. 初始Migration临时复制使用了不展开通配符的参数，Flyway `info`明确显示磁盘Migration为0。修正为逐文件复制并完成12/12哈希校验后才执行正式迁移；
3. `V1.1.0__investment_data_risk_bi.sql`没有`USE`语句，验收器必须显式指定`enterprise_platform`。未修改历史SQL；
4. 临时MySQL首次启用`skip-name-resolve`导致`root@localhost`拒绝环回TCP连接。实例在执行基础SQL前停止并按隔离配置重启。

以上失败尝试均发生在正式Migration前，最终两条验收路径使用干净、可核查的Flyway history。

## 9. 风险清单

1. 本次使用空业务数据验证结构兼容；生产发布前仍需在脱敏数据副本验证历史状态值和DDL锁时长；
2. `investment_decision.current_snapshot_id`为兼容历史记录保持可空，后续收紧必须使用更高版本Migration；
3. 新增CHECK约束在目标MySQL 8.4.9全部生效，但达梦和人大金仓需独立适配与验收；
4. Workflow、Risk和Audit引用长度及格式仍需在跨模块联调时验证；
5. `investment_decision_audit_event`保留平台统一逻辑删除字段，应用层必须禁止普通删除和覆盖；
6. 本次结果不授权生产执行。生产发布仍需备份、变更窗口、pre策略检查、migrate、post validate及Schema指纹比对。

## 10. 最终结论

V2.4.4、V2.4.5和V2.4.6在MySQL Community Server 8.4.9上通过：

- 空库完整Migration；
- V2.4.3增量升级；
- Flyway严格validate；
- 二次migrate no-op；
- checksum和history一致性；
- 表、字段、索引、外键和CHECK约束检查；
- 双路径Schema指纹一致性；
- Workflow零建表和零跨库外键检查。

验收结果：**PASS，可进入下一阶段应用持久化模型开发；仍不得据此直接执行生产Migration。**
