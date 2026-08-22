# Workflow Center V2.5.0 Migration 实施报告

> Sprint：2-3.7-WF2.1
> 资产状态：`CANDIDATE`
> 执行状态：`NOT_EXECUTED`
> 报告日期：2026-08-10

## 1. 实施结论

已创建 Workflow 定义域唯一候选 Migration：

- `database/migration/mysql/V2.5.0__create_workflow_definition_domain.sql`
- 文件 SHA-256：`5b41f9b787bbf4bdf0927b5f30dc487c6d88085d81aedf19e784d44474c02926`
- 前置版本：V2.4.9
- 新建表：`workflow_definition`、`workflow_version`、`workflow_node`

本次未连接或修改任何数据库，未产生 Flyway checksum，未执行生产或非生产 Migration，未启动 Workflow 流程。V2.4.0—V2.4.9 内容保持不变。

## 2. 修改文件

### 2.1 新增

- `database/migration/mysql/V2.5.0__create_workflow_definition_domain.sql`
- `docs/workflow-v250-migration-implementation-report.md`

### 2.2 修改

- `database/flyway/migration-inventory.yml`
  - `generated_at` 更新为 2026-08-10；
  - 新增 V2.5.0 `CANDIDATE / NOT_EXECUTED` 条目；
  - `flyway_checksum` 保持 `null`，禁止伪造执行证据。
- `database/migration/mysql/SHA256SUMS`
  - 新增 V2.5.0 文件级 SHA-256；
  - V2.4.0—V2.4.9 原有记录未改动。
- `database/migration/mysql/README.md`
  - 登记V2.5.0为首个Workflow自有候选资产；
  - 明确其仍为`CANDIDATE / NOT_EXECUTED`，且不访问Investment数据。

Investment 源代码、Workflow 业务接口和 V2.4.0—V2.4.9 历史 Migration 均未修改。

## 3. Migration 内容

### 3.1 workflow_definition

流程定义聚合根，保存企业内稳定流程身份和当前发布版本指针。

主要字段：

| 分类 | 字段 |
| --- | --- |
| 身份 | `id`、`definition_code`、`definition_name` |
| 适用范围 | `business_type`、`enterprise_id`、`owner_org_id` |
| 状态与版本 | `status`、`current_version_id` |
| 描述 | `description` |
| 审计 | `created_by/created_time/updated_by/updated_time` |
| 治理 | `deleted/delete_token/remark/version` |

索引与约束：

- 企业内有效定义编码唯一：`(enterprise_id, definition_code, delete_token)`；
- 业务类型、状态和归属组织查询索引；
- 状态限定为 `DRAFT/ACTIVE/INACTIVE/ARCHIVED`；
- 有效记录要求 `deleted=0 AND delete_token=0`，删除记录要求 `delete_token=id`；
- 当前版本使用后置复合外键，保证版本属于当前定义。

### 3.2 workflow_version

保存流程定义版本。发布不可变规则由后续 Domain/Application 实现，数据库保存发布证据。

主要字段：

- `definition_id/version_no/status/schema_version`；
- `content_hash/change_note`；
- `effective_from/effective_to`；
- `published_by/published_time/source_version_id`；
- 完整审计字段、逻辑删除字段和乐观锁字段。

索引与约束：

- 定义内版本号唯一：`(definition_id, version_no, delete_token)`；
- 定义内内容哈希唯一；
- `version_no > 0`；
- 状态限定为 `DRAFT/PUBLISHED/RETIRED`；
- 非草稿版本必须包含内容哈希、发布人和发布时间；
- 生效结束时间必须晚于开始时间；
- `definition_id` 使用定义外键，`(definition_id, source_version_id)` 使用版本归属复合外键，均为 `RESTRICT`；复制来源不能跨定义。

### 3.3 workflow_node

保存版本化流程节点模板，不保存运行任务。

支持：

- 节点类型：`APPROVAL/COUNTERSIGN/CONDITION`；
- 治理类型：普通审批、党委前置研究、董事会决策、经理层决策；
- 审批模式：`SINGLE/ALL/ANY/QUORUM`；
- 分配规则：用户、组织、岗位、组织岗位组合和受控规则；
- 进入条件、完成条件、超时和撤回配置。

索引与约束：

- 版本内节点编码、顺序唯一；
- 节点类型、治理类型、审批模式和分配规则均有 CHECK 白名单；
- `QUORUM` 必须提供大于0且不超过100的阈值，其他模式禁止填写阈值；
- `node_order > 0`，超时时长为空或大于0；
- `version_id` 物理关联 `workflow_version`，删除策略为 `RESTRICT`。

## 4. 外键创建顺序

```text
1. workflow_definition
   current_version_id 暂不增加外键

2. workflow_version
   definition_id -> workflow_definition.id
   (definition_id, source_version_id)
     -> workflow_version(definition_id, id)

3. workflow_node
   version_id -> workflow_version.id

4. 后置补充
   workflow_definition(id, current_version_id)
     -> workflow_version(definition_id, id)
```

后置复合外键解决定义与当前版本的循环创建依赖，同时确保不能把其他定义的版本设置为当前版本。所有外键使用 `ON DELETE RESTRICT ON UPDATE RESTRICT`，不允许级联删除。

## 5. 审计字段说明

本次按照 Sprint 指令采用：

- `created_by`
- `created_time`
- `updated_by`
- `updated_time`
- `deleted`
- `delete_token`
- `remark`
- `version`

这与现有通用 `BaseEntity` 的 `create_by/create_time/update_by/update_time` 命名不同。当前 Sprint 不创建 Entity；后续持久化 Sprint 必须采用 Workflow 专用审计映射或显式 `@TableField`，禁止为适配 BaseEntity 修改本 Migration，也禁止修改平台既有业务实体。

## 6. Flyway 资产治理

Inventory 记录：

```yaml
version: "2.5.0"
asset_status: "CANDIDATE"
execution_status: "NOT_EXECUTED"
automatic_scan: true
sha256: "5b41f9b787bbf4bdf0927b5f30dc487c6d88085d81aedf19e784d44474c02926"
flyway_checksum: null
depends_on: ["2.4.9"]
```

两类校验不可混用：

- SHA-256 校验仓库文件的字节级完整性；
- Flyway checksum 由 Flyway 解析 SQL 后写入 `flyway_schema_history`，只能在真实隔离数据库执行后登记。

晋级条件：

1. SQL 静态评审通过；
2. Fresh 和 V2.4.9 Upgrade 两条路径真实执行通过；
3. 迁移后严格 `validate` 通过；
4. 第二次 `migrate` 为 no-op；
5. 两条路径 Schema 指纹一致；
6. 记录 Flyway checksum 和执行时间；
7. 才能把资产晋级为 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`。

禁止使用 `repair` 掩盖 checksum 差异，禁止执行 `clean`。

## 7. Fresh数据库验收准备

### 7.1 环境

- 使用隔离、非生产 MySQL 8 实例；
- 数据库名称使用一次性随机后缀，如 `enterprise_platform_wf250_fresh_<runId>`；
- 仅绑定本机或CI隔离网络；
- 所有 Workflow Worker 和业务服务保持关闭；
- 使用最小权限验收账号，凭据只通过环境变量注入。

### 7.2 执行步骤

```text
1. 复制正式扫描目录到只读验收制品
2. 校验所有受管SQL与SHA256SUMS、Inventory一一对应
3. 校验命名、版本顺序和outOfOrder=false
4. 执行Flyway info
5. 执行Flyway migrate至V2.5.0
6. 执行迁移后严格Flyway validate
7. 查询flyway_schema_history并记录V2.5.0 checksum/执行时间
8. 再次migrate，必须no-op
9. 核查三张表、字段、索引、FK和CHECK
10. 执行Schema指纹SQL
```

Fresh验收不是生产部署，也不得使用生产数据。

## 8. V2.4.9升级库验收准备

### 8.1 起始条件

- 使用隔离的 V2.4.9 Schema 克隆或由受管 Migration 重建；
- `flyway_schema_history` 到 V2.4.9 全部成功；
- V2.4.0—V2.4.9 checksum 与 Inventory 一致；
- 起始结构指纹与已验证 V2.4.9 基线相符；
- 不使用未知数据库实例或历史临时库替代。

### 8.2 执行步骤

```text
1. 只读核查V2.4.9 history和起始Schema指纹
2. 校验V2.5.0候选SHA-256
3. Flyway info确认仅V2.5.0为Pending
4. Flyway migrate
5. 迁移后严格validate
6. 检查V2.5.0 history/checksum/执行时间
7. 第二次migrate必须no-op
8. 核查Workflow三表结构
9. 确认Investment表数量和结构未变化
10. 生成升级路径最终Schema指纹
11. 与Fresh路径最终指纹比较
```

## 9. 结构核查清单

验收时基于 `information_schema` 检查：

- 表集合严格包含三张新增 `workflow_*` 表；
- 三表均包含 `created_by/created_time/updated_by/updated_time/deleted/delete_token/version`；
- 唯一索引均包含 `delete_token`；
- 四条外键存在且全部为 `RESTRICT`；
- 状态、逻辑删除、版本、阈值、布尔和超时 CHECK 存在；
- `workflow_definition.current_version_id` 只能引用本定义版本；
- SQL 不包含 `INSERT/UPDATE/DELETE investment_*`；
- 不存在 `workflow_instance/workflow_task` 等超出 V2.5.0 的运行域表；
- 不存在业务流程定义或测试数据。

建议负向验证：

1. 插入非法状态，应被 CHECK 拒绝；
2. 插入重复定义编码，应被唯一约束拒绝；
3. 将定义当前版本指向其他定义版本，应被复合外键拒绝；
4. 为非QUORUM节点填写阈值，应被 CHECK 拒绝；
5. 删除被引用定义或版本，应被外键拒绝；
6. 逻辑删除时未设置 `delete_token=id`，应被 CHECK 拒绝。

## 10. 回滚边界

Flyway 不提供自动 Down Migration。只有在以下条件全部满足时，才允许通过单独审批的运维回滚脚本移除候选结构：

- 未发布任何流程版本；
- 没有任何业务或审计数据；
- 应用未部署依赖该结构的代码；
- 已确认无并发连接；
- 已备份并记录变更单。

回滚顺序：删除定义当前版本复合外键，依次删除 `workflow_node`、`workflow_version`、`workflow_definition`。一旦产生有效数据，只允许创建更高版本进行前向修复。

## 11. 静态检查结果

| 检查项 | 结果 |
| --- | --- |
| 文件名符合Flyway版本规范 | 通过 |
| 仅新增V2.5.0 | 通过 |
| 创建表数量 | 3 |
| 审计、逻辑删除、乐观锁字段 | 三表齐全 |
| 主键、唯一索引、普通索引 | 已定义 |
| 外键与CHECK | 已定义 |
| Investment表或代码引用 | 无 |
| V2.4.0—V2.4.9文件SHA-256复核 | 10/10一致 |
| V2.5.0文件SHA-256与清单/Inventory | 一致 |
| Flyway真实执行 | 未执行 |
| 生产Migration | 未执行 |

## 12. 当前风险

| 风险 | 说明 | 后续控制 |
| --- | --- | --- |
| SQL尚未真实解析执行 | 复合外键和CHECK仍需MySQL 8验证 | 执行Fresh/Upgrade隔离验收 |
| 审计字段与BaseEntity命名不同 | 后续Entity不能直接依赖现有字段约定 | Workflow专用审计映射 |
| 发布不可变无法完全由CHECK表达 | 数据库不能阻止合法字段UPDATE | Domain状态机、Repository限制和审计测试 |
| 规则JSON存储为TEXT | 数据库不校验JSON Schema | 发布前白名单Schema校验及内容哈希 |
| 国产数据库方言未验证 | CHECK、时间默认值、索引语法可能不同 | 后续建立DM/Kingbase等价Migration |
| 循环引用增加运维复杂度 | Definition与Version互相引用 | 后置FK、明确回滚顺序和Schema测试 |

## 13. 下一步门禁

V2.5.0 当前只能保持 `CANDIDATE / NOT_EXECUTED`。在真实隔离 MySQL 8 的Fresh和V2.4.9 Upgrade验收完成前：

- 不得标记 `CANONICAL_IMMUTABLE`；
- 不得登记Flyway checksum；
- 不得部署到生产；
- 不得继续启动真实Workflow业务流程；
- 不得修改V2.5.0已登记内容而不重新计算SHA-256并重新评审。
