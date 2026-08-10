# Sprint 2-0.10 生命周期V2实施报告

## 1. Migration文件

本次没有修改 `database/mysql/05_project.sql`，新增四个严格递增、职责单一的正式migration：

| 版本 | 文件 | 作用 |
| --- | --- | --- |
| V2.1.0 | `V2.1.0__create_project_lifecycle_v2_structure.sql` | 创建模板头、版本、活动版本槽位、阶段模板、阶段条件、生命周期实例、阶段快照和条件快照 |
| V2.1.1 | `V2.1.1__link_project_stage_lifecycle_snapshot.sql` | 为 `project_stage` 增加生命周期实例和阶段快照关联、索引及外键 |
| V2.1.2 | `V2.1.2__backfill_legacy_project_lifecycle.sql` | 幂等登记历史项目与阶段快照，来源统一为 `LEGACY` |
| V2.1.3 | `V2.1.3__seed_project_lifecycle_templates.sql` | 初始化标准投资项目和标准中标项目的活动模板V1 |

Docker新库仍只有 `database/mysql/init/00_enterprise_platform.sql` 一个入口。清单显式执行上述四个已评审migration，不扫描 `migration` 目录，也不执行 `manual` 或 `deprecated` 内容。

## 2. 数据变化

### 2.1 新增结构

新增8张表：

1. `project_lifecycle_template`
2. `project_lifecycle_template_version`
3. `project_lifecycle_template_active`
4. `project_lifecycle_stage_template`
5. `project_lifecycle_stage_condition`
6. `project_lifecycle_instance`
7. `project_lifecycle_stage_snapshot`
8. `project_lifecycle_condition_snapshot`

`project_stage` 新增可空字段：

- `lifecycle_instance_id`
- `stage_snapshot_id`

两个字段第一阶段保持可空，用于安全回填和异常隔离；V2.1.4完整性收紧不在本Sprint范围。

### 2.2 LEGACY回填

- 每个有效历史项目生成一个确定性生命周期实例，实例ID复用项目ID；
- `source_type=LEGACY`；
- `template_id`、`template_version_id`及模板版本快照全部为空；
- 每个有效历史阶段生成一个快照，快照ID复用阶段ID；
- `source_stage_template_id=NULL`、`weight_source=UNCONFIRMED`；
- 不按阶段名称、顺序、数量或旧通用模板推断来源；
- 不修改项目状态、进度、当前阶段及阶段运行状态；
- 无阶段或数量不一致的实例标记为 `INVALID`，不静默伪造READY状态；
- migration可重入，不重复生成实例或快照。

### 2.3 标准模板

- `INVESTMENT_STANDARD`，项目类型 `01`，版本V1、状态ACTIVE，共8个阶段；
- `DELIVERY_STANDARD`，项目类型 `02`，版本V1、状态ACTIVE，共8个阶段；
- 每个版本使用独立活动槽位；
- 阶段权重合计100.0000；
- 内容校验值按完整阶段定义计算，应用读取时重新计算并核对。

## 3. 代码变化

### 3.1 模板选择

新增 `LifecycleTemplateRepository`，选择优先级为：

1. 当前组织、精确项目类型；
2. 平台模板、精确项目类型；
3. 当前组织、ALL；
4. 平台模板、ALL。

只接受模板头 `ENABLED`、模板版本 `ACTIVE`、位于活动版本槽位且在生效期内的版本。同一优先级存在多个候选、阶段顺序不连续、阶段编码重复、权重不为100、首阶段未唯一自动启动或内容校验值不一致时，创建被阻断。

### 3.2 新项目创建

`ProjectApplicationService.createProject()` 已删除兼容8阶段常量和硬编码回退。当前流程为：

```text
权限与主数据校验
  -> 查询唯一ACTIVE模板
  -> 复制阶段定义为领域快照
  -> 创建TEMPLATE生命周期实例
  -> 保存Project
  -> 保存BUILDING实例
  -> 保存阶段快照
  -> 保存关联后的project_stage
  -> 初始化项目负责人
  -> 实例置READY
```

上述写入位于同一Spring事务。没有活动模板时创建失败，不生成项目、实例或阶段。项目 `current_stage_code` 来自模板首阶段，不再固定为 `RESERVE`。

### 3.3 历史兼容读取

`ProjectRepositoryImpl` 按以下规则重建聚合：

- TEMPLATE实例：必须为READY，且运行阶段与阶段快照完整对应；
- 已回填LEGACY实例：保留历史运行状态，使用LEGACY快照解释定义；
- 回填窗口内尚未登记的历史项目：继续从 `project_stage` 构建兼容LEGACY聚合；
- 旧REST路径、阶段更新、任务、成员及权限入口保持兼容。

## 4. 测试结果

### 4.1 自动化验证

- Java 21编译成功；
- Spring Boot测试上下文启动成功；
- Maven全量测试119个通过，失败0、错误0、跳过0；
- migration文件存在性、顺序和关键安全规则检查通过；
- H2 MySQL兼容模式按顺序执行V2.1.0至V2.1.3成功；
- 空库创建8张V2表、2个活动模板和16个模板阶段成功；
- LEGACY样例项目回填成功，模板ID和版本ID保持NULL；
- LEGACY阶段数量、快照数量和关联数量一致；
- 新项目活动模板选择、生命周期生成、快照持久化通过；
- 无活动模板创建失败且Repository未写入；
- 同优先级多模板、模板校验值异常会被阻断；
- 既有ProjectAccessPolicy和DataScope安全测试通过；
- `05_project.sql`无差异，创建链路内硬编码8阶段已清除。

### 4.2 环境限制

当前开发机没有Docker、MySQL客户端或监听于3306端口的MySQL服务。因此本次没有声称完成真实MySQL 8实例执行；自动化测试使用H2 MySQL兼容模式验证结构、执行顺序、种子数据和LEGACY回填语义。

真实MySQL 8空库与存量库演练是生产发布阻断项，必须保存执行日志、校验查询结果和Schema指纹。

## 5. 剩余风险

1. 必须在真实MySQL 8预发布环境依次执行四个migration，评估 `project_stage` DDL锁、索引耗时、binlog和复制延迟。
2. 达梦和人大金仓方言脚本及一致性测试尚未落地；当前migration是MySQL版本。
3. 已创建阶段条件和条件快照结构，但两个标准模板尚未发布条件规则，当前新项目阶段标记 `condition_snapshot_status=NONE`。
4. 目前只初始化项目类型01和02；类型03至06在没有新增ACTIVE模板前会按设计拒绝创建。
5. 模板运营管理接口和启用事务尚未开发；模板启用/冻结与项目创建的并发锁定需在模板管理Sprint补充。
6. V2.1.4完整性收紧尚未执行，`project_stage`两个关联字段继续允许NULL。
7. 当前项目没有Flyway/Liquibase迁移历史表；生产升级仍需受控DBA流水线记录版本、校验和和执行人。
8. Java 21测试存在Mockito动态Agent未来兼容警告，不影响当前结果，但应在构建治理中配置显式Agent。
