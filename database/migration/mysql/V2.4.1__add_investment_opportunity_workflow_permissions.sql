-- Sprint 2-2.3 Investment opportunity workflow permissions.
-- Business tables are unchanged; only idempotent RBAC seed data is added.
USE enterprise_platform;
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_permission (
    id, permission_name, permission_code, permission_type, resource_path, http_method,
    module_code, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.permission_name, s.permission_code, 'BUTTON', NULL, NULL,
       'investment', 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-2.3 投资机会闭环权限', 0
FROM (
    SELECT 2711 id, '投资机会创建' permission_name,
           'investment:opportunity:create' permission_code
    UNION ALL SELECT 2712, '投资机会审核', 'investment:opportunity:review'
    UNION ALL SELECT 2713, '投资机会转项目', 'investment:opportunity:convert'
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
SELECT s.id, s.menu_name, parent.id, 'B', NULL, NULL, s.permission, NULL, s.sort_no,
       0, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-2.3 投资机会闭环按钮权限', 0
FROM (
    SELECT 613 id, '投资机会创建' menu_name,
           'investment:opportunity:create' permission, 3 sort_no
    UNION ALL SELECT 614, '投资机会审核', 'investment:opportunity:review', 4
    UNION ALL SELECT 615, '投资机会转项目', 'investment:opportunity:convert', 5
) s
JOIN sys_menu parent
  ON parent.path = '/investment/opportunities' AND parent.deleted = 0
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
       0, 0, 'Sprint 2-2.3 超级管理员投资机会权限', 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'investment:opportunity:create',
    'investment:opportunity:review',
    'investment:opportunity:convert'
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
       0, 0, 'Sprint 2-2.3 超级管理员投资机会菜单', 0
FROM sys_role r
JOIN sys_menu m ON m.permission IN (
    'investment:opportunity:create',
    'investment:opportunity:review',
    'investment:opportunity:convert'
) AND m.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );

COMMIT;
