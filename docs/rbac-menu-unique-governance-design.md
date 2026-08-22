# RBAC菜单唯一键治理设计

项目：《县域国企数字化运营治理平台》
Sprint：2-3.7-WF2.5.2
状态：DESIGN ONLY
日期：2026-08-10

## 0. 设计结论

`sys_menu`当前只有技术主键`id`，不存在可作为全生命周期业务标识的字段。`permission`、`path`、`parent_id`均不能单独承担菜单业务唯一键。

推荐新增稳定业务字段：

```text
menu_code varchar(128) NOT NULL
UNIQUE KEY uk_sys_menu_code (menu_code, delete_token)
```

`menu_code`在菜单创建时确定，后续菜单改名、换路由、换组件、移动父节点均不得自动改变。活动记录固定`delete_token=0`；逻辑删除记录使用自身`id`作为`delete_token`，允许以后以相同`menu_code`重建活动菜单。

本设计不修改代码、不创建Migration、不修改V2.5.3。文中SQL均为后续V2.5.4候选的设计草案，不是可执行资产。

## 1. 当前sys_menu结构审计

权威初始化结构来源为`database/mysql/02_sys.sql`。现有字段如下：

| 类别 | 字段 |
|---|---|
| 技术标识 | `id bigint`主键 |
| 展示与树关系 | `menu_name`、`parent_id`、`menu_type`、`icon`、`sort_no`、`visible` |
| 路由与权限 | `path`、`component`、`permission` |
| 状态 | `status` |
| 审计 | `create_time/create_by/update_time/update_by/remark/version` |
| 逻辑删除 | `deleted`、`delete_token` |

现有索引及约束：

```text
PRIMARY KEY (id)
KEY idx_sys_menu_parent (parent_id, deleted, sort_no)
KEY idx_sys_menu_permission (permission)
FOREIGN KEY (parent_id) REFERENCES sys_menu(id)
```

缺失项：

- 没有`menu_code`；
- 没有活动路由唯一约束；
- 没有按钮“父菜单+permission”唯一约束；
- 没有`deleted/delete_token`组合语义CHECK；
- 数据库不能阻止并发请求、旁路SQL或错误脚本创建重复活动菜单。

当前应用只在Service中检查`permission`重复。这是应用层校验，存在并发时间窗，且不能覆盖没有权限码的菜单，也不能表达同一权限对应多个页面的合法场景。

另有兼容缺口：V2.5.3允许“流程实例”和“待办任务”共同使用`workflow:view`，但当前菜单编辑Service按全表检查`permission`唯一。此规则可能导致共享权限菜单即使不修改权限码也无法保存。后续实现需把菜单唯一性改由`menu_code`承担，并将`permission`校验调整为权限资源存在性及按业务需要复用，而不是继续假定一对一。

## 2. 现有字段能否承担业务唯一键

| 候选字段 | 结论 | 原因 |
|---|---|---|
| `id` | 不可用 | Snowflake/人工ID是技术标识，跨环境不稳定，不表达业务语义 |
| `menu_name` | 不可用 | 可改名、可国际化，不同父节点允许同名 |
| `path` | 不可用 | B按钮为空；路由可调整；根目录和子页面可能共用路径 |
| `permission` | 不可用 | 可为空；一个查看权限可合法对应多个菜单，例如两个Workflow页面均使用`workflow:view` |
| `parent_id + path` | 不可用 | 父ID跨环境不稳定；B按钮仍无path |
| `parent_id + permission` | 不可用 | 父ID不稳定；M/C权限可能为空或复用 |

结论：当前没有可直接复用的业务唯一字段，必须引入独立`menu_code`。

## 3. menu_code方案

### 3.1 字段定义

推荐定义：

```sql
menu_code VARCHAR(128) NOT NULL COMMENT '菜单稳定业务编码，创建后原则上不可变'
```

编码规范：

- 只允许小写字母、数字、下划线和点；
- 首段以小写字母开头；
- 使用点表达菜单语义层级，不绑定数据库ID；
- 推荐正则：`^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){0,7}$`；
- 不包含租户、组织、角色、显示名称和路由地址；
- 删除后的编码可用于同语义菜单重建，不得用于另一种业务语义；
- 菜单移动、改名、换组件、换path时编码不变；
- 编码变更必须走专项Migration或受审计的治理流程，不能作为普通编辑字段。

### 3.2 Workflow编码冻结建议

| 类型 | 当前节点 | menu_code |
|---|---|---|
| M | 流程治理中心 | `workflow` |
| C | 流程定义 | `workflow.definition` |
| B | 新建定义 | `workflow.definition.create` |
| B | 编辑定义 | `workflow.definition.edit` |
| B | 发布定义 | `workflow.definition.publish` |
| C | 流程实例 | `workflow.instance` |
| B | 启动流程 | `workflow.instance.start` |
| C | 待办任务 | `workflow.task` |
| B | 审批任务 | `workflow.task.approve` |
| B | 撤回任务 | `workflow.task.withdraw` |

`menu_code`与RBAC权限码是两个概念。例如`workflow:view`可以被多个菜单引用，但每个菜单的`menu_code`必须唯一。

### 3.3 应用兼容要求

后续实施Sprint需要同步设计，但不在本Sprint修改：

- Entity、DTO、VO增加`menuCode`；
- 创建菜单必须显式提供并校验编码；
- 普通编辑接口不得修改已存在的`menuCode`；
- 查询、缓存和动态路由继续以菜单ID建立树关系，不强制改成编码关联；
- 初始化脚本和后续Migration关联菜单时优先使用`menu_code`；
- Service层保留友好错误提示，数据库唯一约束作为最终安全边界；
- 捕获`uk_sys_menu_code`冲突并统一返回业务冲突错误，而不是泄露数据库异常。

## 4. 历史菜单重复检测与回填治理

### 4.1 上线前只读检测

以下检测必须在开发、测试、预生产和生产分别执行并归档结果。

活动M/C路径重复：

```sql
SELECT menu_type, path, COUNT(*) AS duplicate_count,
       GROUP_CONCAT(id ORDER BY id) AS ids
FROM sys_menu
WHERE deleted = 0 AND menu_type IN ('M', 'C')
GROUP BY menu_type, path
HAVING path IS NULL OR path = '' OR COUNT(*) > 1;
```

活动B按钮稳定候选键重复：

```sql
SELECT parent_id, permission, COUNT(*) AS duplicate_count,
       GROUP_CONCAT(id ORDER BY id) AS ids
FROM sys_menu
WHERE deleted = 0 AND menu_type = 'B'
GROUP BY parent_id, permission
HAVING permission IS NULL OR permission = '' OR COUNT(*) > 1;
```

逻辑删除标记不合规：

```sql
SELECT id, deleted, delete_token
FROM sys_menu
WHERE (deleted = 0 AND delete_token <> 0)
   OR (deleted = 1 AND delete_token = 0)
   OR deleted NOT IN (0, 1);
```

父节点、类型和层级异常：

```sql
SELECT c.id, c.parent_id, c.menu_type, p.menu_type AS parent_type
FROM sys_menu c
LEFT JOIN sys_menu p ON p.id = c.parent_id
WHERE (c.parent_id IS NOT NULL AND p.id IS NULL)
   OR (c.parent_id IS NOT NULL AND p.menu_type = 'B')
   OR (c.menu_type = 'B' AND c.parent_id IS NULL);
```

### 4.2 回填原则

禁止仅根据中文名称自动生成编码，禁止使用`legacy.<id>`作为正式业务编码，禁止在冲突时随意选择“最小ID保留”。

回填必须依赖经评审的菜单编码清单，清单至少包含：

```text
现有id（定位辅助）
父菜单业务定位
menu_type
path
permission
目标menu_code
责任模块
确认人/确认时间/确认依据
```

`id`只用于定位当前行，不能作为编码语义。回填时需要同时匹配`id + menu_type + path/permission + parent`，任意特征不一致即中止，防止将编码写到错误菜单。

### 4.3 重复处理规则

发现重复时不得由Migration自动合并：

1. 判断是否确为同一业务菜单；
2. 核查`sys_role_menu`引用、动态路由、审计记录和用户影响；
3. 选择保留记录并迁移角色关系；
4. 对重复关系先去重，再逻辑删除冗余菜单；
5. 删除前设置`delete_token=id`；
6. 保存审批单、影响分析和执行前后快照；
7. 重跑全部检测，结果必须为0。

生产重复数据清理应使用独立、可审计的前置数据修复Migration，不应混入V2.5.4结构Migration。

## 5. 唯一约束Migration方案

### 5.1 推荐约束

```sql
UNIQUE KEY uk_sys_menu_code (menu_code, delete_token)
```

可选数据语义CHECK：

```sql
CONSTRAINT chk_sys_menu_deleted_token CHECK (
    (deleted = 0 AND delete_token = 0)
 OR (deleted = 1 AND delete_token <> 0)
)
```

CHECK只有在所有历史删除记录已规范化后才能增加。

V2.5.4默认**不增加该CHECK**。当前`MenuManagementServiceImpl.delete()`先执行一次`updateById`设置`delete_token=id`，随后再调用MyBatis Plus逻辑删除；第一条SQL会短暂形成`deleted=0, delete_token=id`。若直接启用CHECK，现有删除流程会在第一步失败。只有后续将删除改为一条原子SQL同时设置`deleted=1, delete_token=id`并完成测试后，才能通过更高版本Migration启用该CHECK。

### 5.2 为什么不只约束path或permission

- `UNIQUE(path, delete_token)`无法覆盖B按钮，并会阻断合法路由复用；
- `UNIQUE(permission, delete_token)`会阻断多个页面共享查看权限；
- 按M/C/B分别建立多个条件唯一索引在MySQL中需要生成列，复杂度和国产数据库迁移成本更高；
- 独立`menu_code`语义清晰，并能作为未来初始化、审计和跨环境比对的稳定键。

### 5.3 执行前置条件

- 菜单写操作进入维护门禁；
- 当前Schema版本、V2.5.3 checksum和结构指纹匹配；
- 重复检测、逻辑删除检测、编码清单完整性检测全部为0问题；
- 已完成`sys_menu`和`sys_role_menu`一致性备份；
- 应用兼容版本已构建并待发布；
- V2.5.4 SQL通过静态检查且历史Migration摘要无漂移。

## 6. 逻辑删除兼容方案

活动菜单：

```text
deleted = 0
delete_token = 0
menu_code = 稳定编码
```

逻辑删除菜单：

```text
deleted = 1
delete_token = id
menu_code = 原稳定编码
```

因此同一编码可以同时存在一条活动记录和多条历史删除记录，但不能存在两条活动记录。

在V2.5.4只建立唯一索引、不启用严格CHECK的前提下，现有两步删除仍可兼容：先把`delete_token`设置为菜单ID，再执行MyBatis Plus逻辑删除。若只把`deleted`改为1而未更新`delete_token`，将占用`(menu_code,0)`并阻断重建。

长期推荐把两步删除收敛为单条原子SQL：

```sql
UPDATE sys_menu
SET deleted = 1,
    delete_token = id,
    update_time = CURRENT_TIMESTAMP(3),
    update_by = ?,
    version = version + 1
WHERE id = ? AND deleted = 0 AND version = ?;
```

完成原子删除改造后，才评估增加`chk_sys_menu_deleted_token`。该改造不属于本设计Sprint。

历史数据规范化规则：

- `deleted=0, delete_token<>0`：视为异常，禁止自动归零，先调查是否错误恢复；
- `deleted=1, delete_token=0`：可在确认主键唯一且无恢复事务后修正为`delete_token=id`；
- `deleted=1, delete_token`与其他历史记录冲突：必须人工核查；
- 不允许物理删除来规避冲突。

## 7. V2.5.4候选Migration设计

候选文件名规划：

```text
V2.5.4__govern_sys_menu_business_key.sql
```

本Sprint不创建该文件。未来候选建议按以下顺序实现：

1. 校验目标库版本为V2.5.3且历史checksum一致；
2. 创建连接级临时预检表，不创建永久业务表；
3. 将活动M/C路径候选键、B按钮候选键写入带主键的临时表，重复时由1062立即失败；
4. 将经评审的`id -> menu_code`映射写入临时表，`menu_code`设唯一键；
5. 使用临时CHECK断言表确认“现有菜单数=映射数”“未映射数=0”“删除标记异常数=0”；
6. 只有全部预检通过，才给`sys_menu`增加可空`menu_code`列；
7. 按多特征匹配回填，不允许仅按ID无条件更新；
8. 再次断言无NULL、无空值、无重复；
9. 将`menu_code`修改为NOT NULL；
10. 增加`uk_sys_menu_code(menu_code, delete_token)`；
11. V2.5.4不增加删除语义CHECK，只保留验收查询；
12. 删除临时表，输出Flyway执行证据。

关键原则：所有可能发现存量问题的检测必须在第一次永久DDL之前完成。MySQL DDL会隐式提交，不能假设整个Migration可事务回滚。

部署采用维护窗口方案：先停菜单写入，运行V2.5.4，再部署已支持`menu_code`的应用版本，验证后才恢复菜单写入。旧应用不支持必填`menu_code`，不得在V2.5.4后继续开放菜单新增接口。

若无法接受维护窗口，应改用“扩展—回填—收紧”的多版本方案，而不是强行把全部动作塞进V2.5.4；该替代方案需要重新审批版本链。

## 8. Fresh与Upgrade验收方案

### 8.1 Fresh路径

```text
空MySQL 8
→ 平台基础Schema
→ baseline 2.0.0
→ V2.1.x—V2.5.3
→ V2.5.4
→ strict validate
→ 二次migrate no-op
```

核查：

- 每条`sys_menu`均有合法`menu_code`；
- Workflow 10个菜单编码与冻结矩阵一致；
- 活动`menu_code`数量等于去重数量；
- SUPER_ADMIN权限和菜单授权保持9/10；
- 动态路由树数量与V2.5.3验收结果一致；
- V2.5.4是唯一新增history记录，checksum固定。

### 8.2 Upgrade路径

至少覆盖三套一次性数据库：

1. 干净V2.5.3数据：升级成功且与Fresh结构/数据指纹一致；
2. 注入重复`/workflow`根菜单：V2.5.4必须在永久DDL前失败；
3. 注入未映射自定义菜单或错误删除标记：必须在永久DDL前失败并给出可定位证据。

### 8.3 负向与并发测试

- 重复活动`menu_code`：MySQL 1062；
- 同一编码的历史删除记录与新活动记录：允许；
- 两条活动记录并发插入同一编码：只能一条提交成功；
- `menu_code`为空、空字符串、非法格式：拒绝；
- 删除时未更新`delete_token`后尝试重建：拒绝并产生明确业务错误；
- 正确逻辑删除后重建同编码：成功；
- 修改名称、path、component、parent后编码保持不变；
- 旧版新增菜单请求在版本门禁中被禁止，而不是写入NULL。

### 8.4 验收资产

通过后才允许：

- 固化V2.5.4 SHA-256和Flyway checksum；
- 更新Migration Inventory；
- 记录Fresh/Upgrade结构与菜单数据指纹；
- 将V2.5.4标记为`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`；
- 重新评估并验收V2.5.3—V2.5.4完整资产链。

## 9. 失败回滚边界

### 9.1 永久DDL之前失败

临时预检表和断言失败只影响当前连接。关闭连接后自动清理，数据库结构不变；修复数据后重新执行。禁止使用Flyway `repair`掩盖失败。

### 9.2 增加menu_code后、唯一索引前失败

MySQL DDL已隐式提交。保持菜单写门禁，禁止应用流量进入。优先修复并继续前向完成；若必须回退，在确认无新写入后执行经评审的手工回退脚本，删除新增列，并恢复Migration前备份和Flyway前状态。不能直接修改`flyway_schema_history`。

### 9.3 唯一索引建立后、应用发布前失败

继续保持维护窗口。若兼容应用无法发布，可在确认没有任何新菜单写入后，按“删除唯一索引→删除menu_code列”的逆序手工回退。每一步均需审计和结构指纹复核。

### 9.4 恢复业务写入后失败

一旦支持`menu_code`的应用写入新数据，原则上只允许前向修复，不再删除唯一约束或字段。回退会丢失业务标识并重新暴露重复菜单风险。

数据库备份必须包含`sys_menu`、`sys_role_menu`以及Flyway history快照；恢复顺序要保证父菜单外键和角色菜单外键一致。

## 10. 风险清单

| 级别 | 风险 | 控制措施 |
|---|---|---|
| P0 | 生产存在未登记菜单，回填清单不完整 | 预检要求映射数与表记录数完全一致，任何未映射行中止 |
| P0 | 旧应用在NOT NULL后继续新增菜单 | 维护窗口和应用版本门禁，数据库迁移与兼容应用协同发布 |
| P0 | MySQL DDL隐式提交导致半完成 | 永久DDL前完成全部数据预检，准备显式手工回退与前向修复方案 |
| P1 | 现有重复菜单关联多个角色 | 独立数据修复流程迁移并去重`sys_role_menu`，禁止自动删最小/最大ID |
| P1 | `deleted=1,delete_token=0`阻断编码重建 | 上线前规范化并持续执行验收查询；原子删除落地后再增加CHECK |
| P1 | 当前两步逻辑删除与严格CHECK不兼容 | V2.5.4暂不加CHECK；先在后续代码Sprint改为原子删除，再以更高Migration启用CHECK |
| P1 | `menu_code`被普通编辑接口修改 | 应用层设为创建必填、更新只读；变更必须审计 |
| P1 | 只做Service校验仍存在并发窗口 | 数据库唯一索引作为最终边界 |
| P1 | 当前Service把`permission`误当全局唯一，和共享查看权限冲突 | 实施时以`menu_code`校验菜单唯一，`permission`只校验资源存在及授权语义 |
| P2 | 编码命名与权限码混淆 | 文档、DTO注释和审查规则明确两者职责 |
| P2 | 达梦/人大金仓对CHECK及在线DDL差异 | 为国产数据库分别实现等价Migration并做真实兼容验收 |
| P2 | 大表ALTER锁等待 | 预估行数、锁时间和维护窗口；设置超时并准备停止条件 |
| P2 | 缓存保留旧菜单结构 | 发布后清理权限/动态路由缓存并验证所有角色菜单 |

## 11. 后续实施门禁

进入V2.5.4实现前必须获得以下确认：

1. `menu_code`字段和命名规范评审通过；
2. 全量菜单编码清单由系统管理责任人确认；
3. 各环境重复检测报告完成；
4. 应用兼容改造范围和发布顺序获批；
5. 回滚脚本、备份和维护窗口获批；
6. V2.5.3保持原文且历史Migration摘要无漂移。

本Sprint到此停止，不创建V2.5.4文件，不执行任何数据库变更。
