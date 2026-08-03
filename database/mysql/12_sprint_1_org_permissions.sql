-- Sprint 1-2 组织管理权限与菜单初始化（不修改表结构，可重复执行）
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
    SELECT 2201 AS id, '组织管理查看' AS permission_name, 'system:org:view' AS permission_code,
           '/system/org/**' AS resource_path, 'GET' AS http_method
    UNION ALL SELECT 2202, '组织新增', 'org:add', '/system/org', 'POST'
    UNION ALL SELECT 2203, '组织编辑', 'org:edit', '/system/org', 'PUT'
    UNION ALL SELECT 2204, '组织删除', 'org:delete', '/system/org/**', 'DELETE'
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
SELECT 920, '组织管理', parent.id, 'C', '/system/org',
       'system/org/index', 'system:org:view', 'OfficeBuilding', 2,
       1, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM sys_menu parent
WHERE parent.permission = 'system:view' AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu m WHERE m.permission = 'system:org:view' AND m.deleted = 0
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
    'system:org:view', 'org:add', 'org:edit', 'org:delete'
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
SELECT 13200, r.id, m.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM sys_role r
JOIN sys_menu m ON m.permission = 'system:org:view' AND m.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );

COMMIT;
