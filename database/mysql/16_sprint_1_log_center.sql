-- Sprint 1-5 操作日志中心权限与菜单初始化；不修改任何表结构，可重复执行。
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
       0, 0, 'Sprint 1-5 操作日志中心', 0
FROM (
    SELECT 2601 id, '操作日志查看' permission_name, 'system:log:view' permission_code,
           '/system/log/**' resource_path, 'GET' http_method
    UNION ALL SELECT 2602, '操作日志查询', 'system:log:query', '/system/log/page', 'GET'
    UNION ALL SELECT 2603, '操作日志详情', 'system:log:detail', '/system/log/*', 'GET'
    UNION ALL SELECT 2604, '操作日志删除', 'system:log:delete', '/system/log/**', 'DELETE'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.permission_code = s.permission_code AND p.deleted = 0
);

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 960, '操作日志', parent.id, 'C', '/system/log', 'system/log/index',
       'system:log:view', 'Document', 5, 1, 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0,
       'Sprint 1-5 操作日志中心', 0
FROM sys_menu parent
WHERE parent.permission = 'system:view' AND parent.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.permission = 'system:log:view' AND m.deleted = 0);

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.menu_name, parent.id, 'B', NULL, NULL, s.permission, NULL, s.sort_no,
       0, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0,
       'Sprint 1-5 操作日志中心', 0
FROM (
    SELECT 961 id, '日志查询' menu_name, 'system:log:query' permission, 1 sort_no
    UNION ALL SELECT 962, '日志详情', 'system:log:detail', 2
    UNION ALL SELECT 963, '日志删除', 'system:log:delete', 3
) s
JOIN sys_menu parent ON parent.permission = 'system:log:view' AND parent.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.permission = s.permission AND m.deleted = 0);

INSERT INTO sys_role_permission (
    id, role_id, permission_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 700000 + r.id * 10000 + p.id, r.id, p.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0,
       'Sprint 1-5 超级管理员日志授权', 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'system:log:view', 'system:log:query', 'system:log:detail', 'system:log:delete'
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
SELECT 800000 + r.id * 10000 + m.id, r.id, m.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0,
       'Sprint 1-5 超级管理员日志菜单授权', 0
FROM sys_role r
JOIN sys_menu m ON m.permission IN (
    'system:log:view', 'system:log:query', 'system:log:detail', 'system:log:delete'
) AND m.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );

COMMIT;
