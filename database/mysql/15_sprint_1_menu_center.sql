-- Sprint 1-4 菜单中心权限与初始化菜单补全；不修改任何表结构，可重复执行。
USE enterprise_platform;
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_permission (
    id, permission_name, permission_code, permission_type, resource_path, http_method,
    module_code, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.permission_name, s.permission_code, 'API', s.resource_path, s.http_method,
       'system', 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM (
    SELECT 2501 id, '菜单管理查看' permission_name, 'system:menu:view' permission_code,
           '/system/menu/**' resource_path, 'GET' http_method
    UNION ALL SELECT 2502, '菜单新增', 'menu:add', '/system/menu', 'POST'
    UNION ALL SELECT 2503, '菜单修改', 'menu:edit', '/system/menu', 'PUT'
    UNION ALL SELECT 2504, '菜单删除', 'menu:delete', '/system/menu/**', 'DELETE'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.permission_code = s.permission_code AND p.deleted = 0
);

-- 首页路由统一为现有前端首页地址。
UPDATE sys_menu SET path = '/home', component = 'home/index', update_by = 'system',
    update_time = CURRENT_TIMESTAMP(3), version = version + 1
WHERE permission = 'dashboard:view' AND deleted = 0
  AND (path <> '/home' OR component <> 'home/index');

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 940, '菜单管理', parent.id, 'C', '/system/menu', 'system/menu/index',
       'system:menu:view', 'Menu', 4, 1, 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM sys_menu parent
WHERE parent.permission = 'system:view' AND parent.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.permission = 'system:menu:view' AND m.deleted = 0);

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.menu_name, parent.id, 'B', NULL, NULL, s.permission, NULL, s.sort_no,
       0, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM (
    SELECT 941 id, '菜单新增' menu_name, 'menu:add' permission, 1 sort_no
    UNION ALL SELECT 942, '菜单修改', 'menu:edit', 2
    UNION ALL SELECT 943, '菜单删除', 'menu:delete', 3
) s
JOIN sys_menu parent ON parent.permission = 'system:menu:view' AND parent.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.permission = s.permission AND m.deleted = 0);

INSERT INTO sys_role_permission (
    id, role_id, permission_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 500000 + r.id * 10000 + p.id, r.id, p.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('system:menu:view','menu:add','menu:edit','menu:delete')
    AND p.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted = 0
  );

INSERT INTO sys_role_menu (
    id, role_id, menu_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 600000 + r.id * 10000 + m.id, r.id, m.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM sys_role r
JOIN sys_menu m ON m.permission IN ('system:menu:view','menu:add','menu:edit','menu:delete')
    AND m.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );

COMMIT;
