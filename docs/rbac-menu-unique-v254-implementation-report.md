# V2.5.4 RBAC菜单稳定业务键与唯一性治理实施报告

项目：《县域国企数字化运营治理平台》
Sprint：2-3.7-WF2.5.3
日期：2026-08-10
结论：IMPLEMENTED / CANDIDATE / NOT_EXECUTED

## 1. 修改文件清单

新增：

- `database/migration/mysql/V2.5.4__govern_rbac_menu_unique_key.sql`
- `backend/src/test/java/cn/gov/enterprise/modules/system/menu/RbacMenuUniqueMigrationContractTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/system/menu/SysMenuEntityMappingTest.java`
- `docs/rbac-menu-unique-v254-implementation-report.md`

修改：

- `database/flyway/migration-inventory.yml`
- `database/migration/mysql/SHA256SUMS`
- `database/migration/mysql/README.md`
- `backend/src/main/java/cn/gov/enterprise/modules/system/entity/SysMenuEntity.java`
- `backend/src/main/java/cn/gov/enterprise/modules/system/menu/dto/MenuCreateRequest.java`
- `backend/src/main/java/cn/gov/enterprise/modules/system/menu/dto/MenuPageQuery.java`
- `backend/src/main/java/cn/gov/enterprise/modules/system/menu/dto/MenuUpdateRequest.java`
- `backend/src/main/java/cn/gov/enterprise/modules/system/menu/service/impl/MenuManagementServiceImpl.java`
- `backend/src/main/java/cn/gov/enterprise/modules/system/menu/vo/MenuVO.java`
- `backend/src/test/java/cn/gov/enterprise/config/DatabaseMappingCheckerTest.java`
- `backend/src/test/java/cn/gov/enterprise/modules/system/menu/service/MenuManagementServiceImplTest.java`
- `frontend/src/types/menu.ts`
- `frontend/src/views/system/menu/index.vue`
- `frontend/src/api/menu.test.ts`

未修改Investment代码、Workflow状态机、Workflow任务动作逻辑及V2.4.0—V2.5.3历史Migration。

## 2. sys_menu现状审计

权威结构来自`database/mysql/02_sys.sql`。为确认存量清单，本Sprint只读启动上一Sprint由本项目创建的已知隔离V2.5.3验收数据目录，没有执行Migration或写入业务数据，审计后立即关闭。

| 项目 | 审计结果 |
|---|---:|
| 字段 | 19个，不含`menu_code` |
| 菜单总数 | 71 |
| 活动菜单 | 71 |
| 已删除菜单 | 0 |
| M/C/B类型 | 均存在，父子关系满足现有规则 |
| 主键 | `PRIMARY(id)` |
| 普通索引 | `idx_sys_menu_parent`、`idx_sys_menu_permission` |
| 业务唯一约束 | 无 |
| 逻辑删除 | 活动记录`deleted=0, delete_token=0` |

设计文档与真实结构没有重大冲突。当前`permission`不能作为业务唯一键：多个投资页面复用`investment:view`，Workflow实例页与任务页复用`workflow:view`。

## 3. menu_code设计

V2.5.4新增：

```text
menu_code VARCHAR(128) NOT NULL
CHECK: 小写点分编码格式
UNIQUE(menu_code, delete_token)
```

`id`继续作为数据库主键；`menu_code`只作为稳定业务键。菜单名称、path、component或父节点变化不会自动改变编码。

编码格式：

```text
^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){0,7}$
```

本版本没有增加严格`deleted/delete_token` CHECK，以保持现有两步逻辑删除兼容。

## 4. 存量映射方案

V2.5.4内置71条经审计的显式映射。每条映射同时记录：

- 现有ID；
- 预期父ID；
- 类型；
- 名称；
- path；
- permission；
- deleted/delete_token；
- 目标menu_code。

Migration不按名称、ID、path或父节点自动生成编码。ID只定位当前行，必须与其余业务特征同时匹配。

临时映射表对ID建立主键，对`(menu_code, expected_delete_token)`建立唯一键。任何额外菜单、缺失菜单、特征变化、目标编码重复或删除状态变化都会在永久DDL前失败。

模块映射数量：

| 模块 | 数量 |
|---|---:|
| 首页/党建/人事/经营/数据资产/风险 | 6 |
| Project | 4 |
| Investment | 27 |
| System/Profile | 24 |
| Workflow | 10 |
| 合计 | 71 |

静态契约确认71个ID和71个menu_code均唯一。

## 5. Workflow菜单编码表

| ID | 节点 | menu_code |
|---:|---|---|
| 25300 | 流程治理中心 | `workflow` |
| 25310 | 流程定义 | `workflow.definition` |
| 25311 | 流程定义新建 | `workflow.definition.create` |
| 25312 | 流程定义编辑 | `workflow.definition.edit` |
| 25313 | 流程定义发布 | `workflow.definition.publish` |
| 25320 | 流程实例 | `workflow.instance` |
| 25321 | 流程启动 | `workflow.instance.start` |
| 25330 | 待办任务 | `workflow.task` |
| 25331 | 任务审批 | `workflow.task.approve` |
| 25332 | 任务撤回 | `workflow.task.withdraw` |

V2.5.3中的9项Workflow权限和SUPER_ADMIN授权逻辑未改变；V2.5.4只给已有10个菜单回填稳定编码，不插入第二套Workflow菜单。

## 6. V2.5.4 Migration执行顺序

```text
Phase A  创建临时映射/断言结构
Phase B  验证映射覆盖完整
Phase C  验证目标menu_code唯一及格式
Phase D  验证菜单指纹、树关系、删除语义和活动身份冲突
Phase E  全部预检通过后才进入永久DDL
Phase F  新增可空menu_code
Phase G  多特征匹配执行显式回填
Phase H  验证无NULL、空值、重复或非法编码
Phase I  收紧menu_code为NOT NULL
Phase J  增加格式CHECK和UNIQUE(menu_code, delete_token)
```

Migration在永久DDL前输出未映射、指纹不一致、重复M/C路由身份和重复B按钮身份的诊断结果。断言临时表通过CHECK让违规计数立即失败。

MySQL DDL具有隐式提交语义，因此所有可预见的数据失败检查均放在第一次`ALTER TABLE`之前。

## 7. 数据库变化

计划变化仅限`sys_menu`：

```sql
ADD COLUMN menu_code VARCHAR(128)
MODIFY menu_code VARCHAR(128) NOT NULL
ADD CONSTRAINT chk_sys_menu_code_format ...
ADD UNIQUE KEY uk_sys_menu_code (menu_code, delete_token)
```

无新业务表、无角色授权变化、无菜单新增删除、无逻辑删除CHECK、无Investment或Workflow业务表变化。

本Sprint没有执行真实MySQL Migration，以上仍是候选变化。

## 8. 代码最小适配

后端：

- `SysMenuEntity`映射`menu_code`；
- 创建DTO要求合法`menuCode`；
- 更新DTO携带编码，Service禁止修改既有编码；
- 分页查询支持按编码查询；
- VO返回编码；
- 创建前提供友好重复校验，数据库唯一索引作为并发最终边界；
- 取消把`permission`误当菜单全局唯一键，仍校验权限资源存在；
- `menu_id`主键和角色菜单关系保持不变。

前端系统菜单管理：

- 类型、查询条件、列表及编辑表单识别`menuCode`；
- 新增时必填并校验格式；
- 编辑时只读，禁止改变稳定编码。

未创建任何Workflow前端业务页面。

## 9. 测试结果

| 检查 | 结果 |
|---|---|
| Java版本 | Java 21.0.12 |
| Maven编译 | PASS |
| RBAC/Workflow专项契约 | 16/16 PASS |
| 后端全量测试 | 226/226 PASS |
| Spring Boot上下文 | PASS |
| menu_code实体映射 | PASS |
| 71条映射唯一性 | PASS |
| Workflow 10项编码完整性 | PASS |
| V2.5.3不可变性契约 | PASS |
| V2.5.4执行顺序/结构静态契约 | PASS |
| 前端TypeScript | PASS |
| 前端测试 | 9 files / 24 tests PASS |
| 前端生产构建 | PASS |
| Migration清单摘要 | 20/20匹配 |
| YAML资产清单解析 | PASS |
| `git diff --check` | PASS |

SQL只做了静态结构和契约检查，未连接MySQL执行V2.5.4，符合本Sprint边界。

## 10. V2.5.4 SHA-256

```text
c93111bd75795604837f019ffc7df48f23635fb0d0d7e69a4f1e984bc483b644
```

Inventory状态：

```text
asset_status: CANDIDATE
execution_status: NOT_EXECUTED
flyway_checksum: null
```

## 11. V2.5.3哈希复核

V2.5.3未修改，SHA-256仍为：

```text
6d6904da8028830cdc8fa7b9125994d37d2c916a09277ae9d4526f5646144e26
```

V2.5.0、V2.5.1、V2.5.2、V2.5.3及全部受治理Migration摘要均与`SHA256SUMS`匹配，无历史资产漂移。

## 12. 剩余风险

1. V2.5.4尚未经过MySQL 8 Fresh/Upgrade真实执行，SQL运行时语义、DDL锁和CHECK行为仍待验收。
2. 71条显式映射要求目标环境菜单数据与审计指纹完全一致；自定义、改名、删除或额外菜单会按设计在DDL前阻断，必须先人工治理。
3. V2.5.4执行后，旧应用无法新增缺少`menu_code`的菜单，必须在维护窗口中按“Migration→兼容应用→验证→开放写入”发布。
4. 当前仍采用两步逻辑删除，V2.5.4未增加严格删除语义CHECK；原子删除应在独立Sprint治理。
5. 达梦、人大金仓对临时表CHECK、正则CHECK和组合唯一索引的等价实现尚未验证。
6. V2.5.3仍是失败候选，不能因V2.5.4代码完成而单独晋级。

## 13. 下一步验收方案

下一Sprint应单独执行V2.5.3+V2.5.4真实MySQL组合验收：

- Fresh完整链；
- V2.5.3到V2.5.4 Upgrade；
- Flyway checksum、strict validate和二次migrate no-op；
- 71条编码回填及10条Workflow编码；
- 重复menu_code、并发插入、逻辑删除后重建负向测试；
- 未映射菜单、指纹变化、重复活动根菜单必须在永久DDL前失败；
- Fresh/Upgrade结构和数据指纹一致；
- 通过后再决定V2.5.3与V2.5.4是否联合晋级。

本Sprint到此停止，不执行真实MySQL验收，不进入V2.5.5或多节点Workflow开发。
