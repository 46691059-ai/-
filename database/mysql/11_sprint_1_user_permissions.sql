-- Sprint 1-1 用户管理权限与菜单初始化（不修改表结构，可重复执行）
USE enterprise_platform;
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_permission (
    id, permission_name, permission_code, permission_type, resource_path, http_method,
    module_code, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.permission_name, s.permission_code, s.permission_type,
       s.resource_path, s.http_method, 'system', 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM (
    SELECT 2101 AS id, '用户管理查看' AS permission_name, 'system:user:view' AS permission_code,
           'API' AS permission_type, '/system/user/**' AS resource_path, 'GET' AS http_method
    UNION ALL SELECT 2102, '用户新增', 'user:add', 'API', '/system/user', 'POST'
    UNION ALL SELECT 2103, '用户编辑', 'user:edit', 'API', '/system/user/**', 'PUT'
    UNION ALL SELECT 2104, '用户删除', 'user:delete', 'API', '/system/user/**', 'DELETE'
    UNION ALL SELECT 2105, '用户密码重置', 'user:resetPassword', 'API', '/system/user/resetPassword', 'PUT'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p
    WHERE p.permission_code = s.permission_code AND p.deleted = 0
);

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 910, '用户管理', parent.id, 'C', '/system/user',
       'system/user/index', 'system:user:view', 'User', 1,
       1, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM sys_menu parent
WHERE parent.permission = 'system:view' AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu m WHERE m.permission = 'system:user:view' AND m.deleted = 0
  );

INSERT INTO sys_role_permission (
    id, role_id, permission_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 10000 + p.id, r.id, p.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'system:user:view', 'user:add', 'user:edit', 'user:delete', 'user:resetPassword'
) AND p.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted = 0
  );

INSERT INTO sys_role_menu (
    id, role_id, menu_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 13100, r.id, m.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM sys_role r
JOIN sys_menu m ON m.permission = 'system:user:view' AND m.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );

COMMIT;
