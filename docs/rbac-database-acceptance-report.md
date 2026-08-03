# RBAC数据库验收报告

## 1. 验收信息

- 验收日期：2026-08-01
- 数据库：MySQL Community Server 8.0.46
- 验收实例：`127.0.0.1:34060`临时隔离实例
- 数据库名称：`enterprise_platform`
- 基线脚本：`01_database.sql`至`13_sprint_1_role_permissions.sql`
- 验收脚本：`14_sprint_1_rbac_acceptance.sql`
- 测试夹具：`manual/15_sprint_1_3_1_acceptance_fixture.sql`
- 表数量：118
- 业务代码修改：无

## 2. SQL执行结果

| 执行范围 | 结果 |
|---|---|
| 01至08基础脚本 | 成功 |
| 09风险脚本 | 成功；`project_risk`、`investment_risk`已由前序模块创建，返回1050提示 |
| 10至13初始化及系统权限脚本 | 成功 |
| 14 RBAC验收脚本 | 成功 |
| 15验收测试夹具 | 成功 |
| 14、15重复执行 | 成功，数据未重复 |

幂等性复核结果：测试用户3个、测试团队3个、测试用户角色关系3条。

## 3. 用户列表

| ID | 用户名 | 姓名 | 组织ID | 状态 |
|---:|---|---|---:|---|
| 90001 | project_manager | 项目经理测试 | 10303 | 正常 |
| 90002 | employee_test | 普通员工测试 | 10301 | 正常 |
| 90003 | digital_manager | 数字产业部负责人测试 | 103 | 正常 |

## 4. 角色列表

| ID | 角色 | 编码 | 数据范围 | 状态 |
|---:|---|---|---|---|
| 1 | 系统管理员 | SUPER_ADMIN | ALL | 正常 |
| 2 | 董事长 | CHAIRMAN | ALL | 正常 |
| 3 | 党委书记 | PARTY_SECRETARY | ALL | 正常 |
| 4 | 总经理 | GENERAL_MANAGER | ALL | 正常 |
| 5 | 部门负责人 | DEPT_MANAGER | ORG_AND_CHILDREN | 正常 |
| 6 | 项目经理 | PROJECT_MANAGER | ORG_AND_CHILDREN | 正常 |
| 7 | 普通员工 | COMMON_USER | SELF | 正常 |

## 5. 用户角色关系

| 用户名 | 角色 | 角色编码 | 数据范围 |
|---|---|---|---|
| project_manager | 项目经理 | PROJECT_MANAGER | ORG_AND_CHILDREN |
| employee_test | 普通员工 | COMMON_USER | SELF |
| digital_manager | 部门负责人 | DEPT_MANAGER | ORG_AND_CHILDREN |

## 6. 角色菜单关系

| 角色 | 菜单数 | 有效菜单权限 |
|---|---:|---|
| SUPER_ADMIN | 27 | 全部27个有效菜单，包括目录、页面和按钮 |
| PROJECT_MANAGER | 4 | project:view、project:lifecycle:list、project:add、project:edit |
| COMMON_USER | 1 | profile:view |
| DEPT_MANAGER | 2 | system:view、system:org:view |

超级管理员菜单覆盖率为`27/27`，权限资源覆盖率为`30/30`，不存在遗漏菜单或权限。

## 7. 数据权限范围

### 项目经理

- 数据范围：`ORG_AND_CHILDREN`
- 权限：`project:view`、`project:lifecycle:list`、`project:add`、`project:edit`
- 未分配投资管理、数据资产管理、系统管理权限。

### 普通员工

- 数据范围：`SELF`
- 唯一权限：`profile:view`

### 数字产业部负责人

- 数据范围：`ORG_AND_CHILDREN`
- 组织范围实际查询结果：
  - 数字产业部（103）
  - 数据标注团队（10301）
  - 数据采集团队（10302）
  - 平台研发团队（10303）

## 8. 验收结论

RBAC数据库真实验收通过。角色、用户、菜单、权限及组织数据范围符合Sprint 1-3.2验收要求，验收脚本具备重复执行能力。

## 9. 存在问题

1. 初始化数据只创建`SUPER_ADMIN`角色，不自动创建管理员登录账号。本次验证的是角色菜单及权限覆盖，不包含管理员账号登录验证。
2. `09_risk.sql`会对前序脚本已创建的`project_risk`和`investment_risk`输出表已存在提示；因使用`CREATE TABLE IF NOT EXISTS`，不影响执行结果。
3. 测试账号及组织数据仅应存在于验收环境，禁止执行到生产数据库。
