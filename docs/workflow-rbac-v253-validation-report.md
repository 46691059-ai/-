# V2.5.3 Workflow RBAC Migration真实MySQL验收报告

项目：《县域国企数字化运营治理平台》
Sprint：2-3.7-WF2.5.1
验收日期：2026-08-10
结论：**FAIL / NOT PROMOTED**

## 1. 验收边界与环境

本次只验收`V2.5.3__initialize_workflow_rbac.sql`，未修改该SQL，未修改V2.4.0—V2.5.2，未修改Workflow或Investment业务代码，也未创建V2.5.4。

验收使用两个新建、只监听`127.0.0.1`的一次性MySQL实例；未连接生产、未知或既有验收数据库。

| 项目 | Fresh | Upgrade |
|---|---|---|
| MySQL | Community Server 8.4.9 | Community Server 8.4.9 |
| Flyway | 13.0.0 | 13.0.0 |
| 临时端口 | 33780 | 33781 |
| 基线版本 | 2.0.0 | 2.0.0 |
| 路径 | 完整权威链至2.5.3 | 完整权威链至2.5.2，再仅升级2.5.3 |

正式目录`SHA256SUMS`共19项，全部匹配；V2.4.0—V2.5.2共13个受保护资产摘要漂移为0。

## 2. Flyway执行结果

| 检查 | Fresh | Upgrade |
|---|---:|---:|
| 最终版本 | 2.5.3 | 2.5.3 |
| history成功/失败 | 20 / 0 | 20 / 0 |
| V2.5.3 installed_rank | 20 | 20 |
| V2.5.3执行时间 | 13ms | 108ms |
| strict validate | PASS | PASS |
| 第二次migrate | no-op | no-op |

Upgrade路径在升级前停于2.5.2；加入候选文件后只执行1条Migration并到达2.5.3。

V2.5.3两条路径的Flyway记录一致：

```text
version: 2.5.3
description: initialize workflow rbac
type: SQL
checksum: -393819095
success: 1
```

SQL SHA-256在验收前后保持：

```text
6d6904da8028830cdc8fa7b9125994d37d2c916a09277ae9d4526f5646144e26
```

## 3. RBAC数据核查

Fresh与Upgrade结果完全一致：

| 核查项 | 结果 |
|---|---:|
| Workflow有效权限 | 9 |
| 权限编码去重后数量 | 9 |
| Workflow M/C/B菜单 | 10 |
| SUPER_ADMIN Workflow权限授权 | 9 |
| SUPER_ADMIN Workflow菜单授权 | 10 |

权限矩阵：

```text
workflow:manage
workflow:definition:view
workflow:definition:create
workflow:definition:edit
workflow:definition:publish
workflow:view
workflow:start
workflow:approve
workflow:withdraw
```

菜单矩阵为1个M目录、3个C菜单、6个B按钮。当前`sys_menu`没有`menu_code`字段，V2.5.3使用M/C菜单路径与B菜单“父路径+permission”作为业务稳定键。

## 4. 负向测试

负向测试在两个实例分别执行，结果一致：

| 场景 | 数据库结果 | 判定 |
|---|---|---|
| 重复`permission_code` | MySQL 1062，`uk_sys_permission_code` | PASS |
| 重复角色权限授权 | MySQL 1062，`uk_sys_role_permission` | PASS |
| 重复角色菜单授权 | MySQL 1062，`uk_sys_role_menu` | PASS |
| 非法`permission_id`引用 | MySQL 1452，`fk_sys_rp_permission` | PASS |
| 重复Workflow根菜单稳定键 | 插入成功，事务内有效`/workflow`从1变2 | **FAIL** |

最后一项在同一事务中回滚，回滚后有效`/workflow`仍为1，未污染验收数据。

失败原因：`sys_menu`既没有`menu_code`，也没有对活动菜单路径或“父节点+类型+permission+delete_token”的等价唯一约束。V2.5.3的`NOT EXISTS`可避免正常执行时重复初始化，但不能提供数据库级并发与旁路写入唯一性保证。

## 5. 双路径一致性

只读结构指纹脚本覆盖tables、columns、indexes、foreign keys和check constraints：

| 指纹 | Fresh | Upgrade | 结果 |
|---|---|---|---|
| Schema行数 | 5015 | 5015 | 一致 |
| Schema SHA-256 | `2349e283c7e69baf4dce2fd7ca8be15ac78fd0896e6c16b019022545c6f28c88` | 同左 | 一致 |
| Workflow RBAC数据SHA-256 | `5029cdb2d09bd7b608e9dbb8ae19926f24000aca410cfc7d37d992407742ea68` | 同左 | 一致 |

## 6. 资产状态

由于菜单唯一性负向测试失败，不满足“通过后晋级”条件：

```text
asset_status: CANDIDATE
execution_status: NOT_EXECUTED
flyway_checksum: null
```

这里的`NOT_EXECUTED`继续表示未在受管或生产环境确认执行；本报告另行记录一次性隔离MySQL验收尝试。V2.5.3未标记为`CANONICAL_IMMUTABLE`或`EPHEMERAL_MYSQL8_VALIDATED`。`SHA256SUMS`中的候选摘要保持不变。

## 7. 阻断项与处理建议

P0阻断：当前RBAC核心表无法数据库级保证菜单稳定业务键唯一。该缺口不能通过修改已执行候选SQL或Workflow业务代码规避。

建议下一Sprint先审批RBAC兼容Migration方案，在更高版本中为`sys_menu`建立可兼容逻辑删除的稳定唯一键。可选方案是新增非空`menu_code`并建立`(menu_code, delete_token)`唯一索引；若禁止新增字段，则需分别设计M/C路径与B按钮业务键的可索引规范化列。完成真实MySQL升级与并发负向测试后，再重新验收V2.5.3资产链。

## 8. 最终结论

V2.5.3的Flyway执行、checksum、validate、no-op、RBAC数量、SUPER_ADMIN授权、权限唯一、授权唯一和非法外键引用均通过；菜单稳定键数据库约束未通过。

最终结论：**FAIL / BLOCKED / V2.5.3保持候选状态**。
