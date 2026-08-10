-- Sprint 2-2.4 feasibility and due-diligence RBAC metadata only.
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
       0, 0, 'Sprint 2-2.4 投资论证权限', 0
FROM (
    SELECT 2721 id, '可研查看' name, 'investment:feasibility:view' code
    UNION ALL SELECT 2722, '可研编辑', 'investment:feasibility:edit'
    UNION ALL SELECT 2723, '尽调查看', 'investment:due_diligence:view'
    UNION ALL SELECT 2724, '尽调编辑', 'investment:due_diligence:edit'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.permission_code = s.code AND p.deleted = 0
);

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 640, '尽职调查', parent.id, 'C', '/investment/due-diligence',
       'investment/due-diligence/index', 'investment:due_diligence:view',
       'Search', 3, 1, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-2.4 尽调入口', 0
FROM sys_menu parent
WHERE parent.permission = 'investment:view' AND parent.parent_id IS NULL AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu m WHERE m.path = '/investment/due-diligence' AND m.deleted = 0
  );

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.name, parent.id, 'B', NULL, NULL, s.permission, NULL, s.sort_no,
       0, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-2.4 投资论证按钮权限', 0
FROM (
    SELECT 622 id, '可研查看' name, '/investment/feasibility' parent_path,
           'investment:feasibility:view' permission, 2 sort_no
    UNION ALL SELECT 623, '可研编辑', '/investment/feasibility',
           'investment:feasibility:edit', 3
    UNION ALL SELECT 641, '尽调查看', '/investment/due-diligence',
           'investment:due_diligence:view', 1
    UNION ALL SELECT 642, '尽调编辑', '/investment/due-diligence',
           'investment:due_diligence:edit', 2
) s
JOIN sys_menu parent ON parent.path = s.parent_path AND parent.deleted = 0
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
       0, 0, 'Sprint 2-2.4 超级管理员投资论证权限', 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'investment:feasibility:view', 'investment:feasibility:edit',
    'investment:due_diligence:view', 'investment:due_diligence:edit'
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
       0, 0, 'Sprint 2-2.4 超级管理员投资论证菜单', 0
FROM sys_role r
JOIN sys_menu m ON m.id IN (622, 623, 640, 641, 642) AND m.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );

COMMIT;
