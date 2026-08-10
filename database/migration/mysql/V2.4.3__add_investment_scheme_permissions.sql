-- Sprint 2-2.5 investment scheme RBAC metadata only.
USE enterprise_platform;
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_permission (
    id, permission_name, permission_code, permission_type, resource_path, http_method,
    module_code, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.name, s.code, 'BUTTON', NULL, NULL, 'investment', 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-2.5 投资方案权限', 0
FROM (
    SELECT 2731 id, '投资方案查看' name, 'investment:scheme:view' code
    UNION ALL SELECT 2732, '投资方案编辑', 'investment:scheme:edit'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.permission_code = s.code AND p.deleted = 0
);

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 650, '投资方案', parent.id, 'C', '/investment/schemes',
       'investment/scheme/index', 'investment:scheme:view',
       'Document', 4, 1, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-2.5 投资方案入口', 0
FROM sys_menu parent
WHERE parent.permission = 'investment:view' AND parent.parent_id IS NULL AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu m WHERE m.path = '/investment/schemes' AND m.deleted = 0
  );

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.name, parent.id, 'B', NULL, NULL, s.permission, NULL, s.sort_no,
       0, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-2.5 投资方案按钮权限', 0
FROM (
    SELECT 651 id, '投资方案查看' name, 'investment:scheme:view' permission, 1 sort_no
    UNION ALL SELECT 652, '投资方案编辑', 'investment:scheme:edit', 2
) s
JOIN sys_menu parent ON parent.path = '/investment/schemes' AND parent.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m
    WHERE m.parent_id = parent.id AND m.permission = s.permission AND m.deleted = 0
);

INSERT INTO sys_role_permission (
    id, role_id, permission_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 920000000 + r.id * 10000 + p.id, r.id, p.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-2.5 超级管理员投资方案权限', 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'investment:scheme:view', 'investment:scheme:edit'
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
SELECT 930000000 + r.id * 10000 + m.id, r.id, m.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-2.5 超级管理员投资方案菜单', 0
FROM sys_role r
JOIN sys_menu m ON m.id IN (650, 651, 652) AND m.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );

COMMIT;
