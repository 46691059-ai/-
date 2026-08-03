-- Sprint 1-3 角色权限中心初始化（不修改表结构，可重复执行）
USE enterprise_platform;
SET NAMES utf8mb4;
START TRANSACTION;

-- 七类标准角色：仅补齐缺失项，不覆盖已有配置。
INSERT INTO sys_role (
    id, role_name, role_code, description, data_scope_type, status,
    create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
)
SELECT s.id, s.role_name, s.role_code, s.description, s.data_scope_type, 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM (
    SELECT 1 AS id, '系统管理员' AS role_name, 'SUPER_ADMIN' AS role_code, '拥有系统全部权限' AS description, 'ALL' AS data_scope_type
    UNION ALL SELECT 2, '董事长', 'CHAIRMAN', '查看企业经营、投资、风险全局数据', 'ALL'
    UNION ALL SELECT 3, '党委书记', 'PARTY_SECRETARY', '负责党建、三重一大和干部治理', 'ALL'
    UNION ALL SELECT 4, '总经理', 'GENERAL_MANAGER', '负责企业经营管理', 'ALL'
    UNION ALL SELECT 5, '部门负责人', 'DEPT_MANAGER', '负责本组织及下级组织业务', 'ORG_AND_CHILDREN'
    UNION ALL SELECT 6, '项目经理', 'PROJECT_MANAGER', '负责授权项目全过程管理', 'SELF'
    UNION ALL SELECT 7, '普通员工', 'COMMON_USER', '普通业务人员', 'SELF'
) s
WHERE NOT EXISTS (SELECT 1 FROM sys_role r WHERE r.role_code = s.role_code AND r.deleted = 0);

INSERT INTO sys_permission (
    id, permission_name, permission_code, permission_type, resource_path, http_method,
    module_code, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.permission_name, s.permission_code, 'API', s.resource_path, s.http_method,
       'system', 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM (
    SELECT 2301 AS id, '角色管理查看' AS permission_name, 'system:role:view' AS permission_code, '/system/role/**' AS resource_path, 'GET' AS http_method
    UNION ALL SELECT 2302, '角色新增', 'role:add', '/system/role', 'POST'
    UNION ALL SELECT 2303, '角色编辑', 'role:edit', '/system/role', 'PUT'
    UNION ALL SELECT 2304, '角色删除', 'role:delete', '/system/role/**', 'DELETE'
    UNION ALL SELECT 2305, '角色权限配置', 'role:permission', '/system/role/**', NULL
) s
WHERE NOT EXISTS (SELECT 1 FROM sys_permission p WHERE p.permission_code = s.permission_code AND p.deleted = 0);

-- 角色页面菜单。
INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 930, '角色权限', parent.id, 'C', '/system/role', 'system/role/index',
       'system:role:view', 'Key', 3, 1, 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM sys_menu parent
WHERE parent.permission = 'system:view' AND parent.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.permission = 'system:role:view' AND m.deleted = 0);

-- 按钮权限使用sys_menu的B类型表达，与sys_permission资源一一对应。
INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.menu_name, parent.id, 'B', NULL, NULL, s.permission, NULL, s.sort_no,
       0, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM (
    SELECT 911 AS id, '用户新增' AS menu_name, 'system:user:view' AS parent_permission, 'user:add' AS permission, 1 AS sort_no
    UNION ALL SELECT 912, '用户编辑及授权', 'system:user:view', 'user:edit', 2
    UNION ALL SELECT 913, '用户删除', 'system:user:view', 'user:delete', 3
    UNION ALL SELECT 914, '用户密码重置', 'system:user:view', 'user:resetPassword', 4
    UNION ALL SELECT 921, '组织新增', 'system:org:view', 'org:add', 1
    UNION ALL SELECT 922, '组织编辑', 'system:org:view', 'org:edit', 2
    UNION ALL SELECT 923, '组织删除', 'system:org:view', 'org:delete', 3
    UNION ALL SELECT 931, '角色新增', 'system:role:view', 'role:add', 1
    UNION ALL SELECT 932, '角色编辑', 'system:role:view', 'role:edit', 2
    UNION ALL SELECT 933, '角色删除', 'system:role:view', 'role:delete', 3
    UNION ALL SELECT 934, '权限配置', 'system:role:view', 'role:permission', 4
) s
JOIN sys_menu parent ON parent.permission = s.parent_permission AND parent.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.permission = s.permission AND m.deleted = 0);

-- 超级管理员获得新增资源及所有系统管理按钮菜单。
INSERT INTO sys_role_permission (
    id, role_id, permission_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 10000 + p.id, r.id, p.id, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('system:role:view','role:add','role:edit','role:delete','role:permission') AND p.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted = 0);

INSERT INTO sys_role_menu (
    id, role_id, menu_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 20000 + m.id, r.id, m.id, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM sys_role r
JOIN sys_menu m ON m.permission IN (
    'system:role:view','role:add','role:edit','role:delete','role:permission',
    'user:add','user:edit','user:delete','user:resetPassword',
    'org:add','org:edit','org:delete'
) AND m.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0);

COMMIT;
