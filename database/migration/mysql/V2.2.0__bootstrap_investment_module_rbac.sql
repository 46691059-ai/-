-- Sprint 2-2 Investment module bootstrap.
-- Adds only RBAC permissions and menu metadata; no investment business table is changed.
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
       0, 0, 'Sprint 2-2 Investment模块基础权限', 0
FROM (
    SELECT 1006 id, '投资管理查看' permission_name, 'investment:view' permission_code
    UNION ALL SELECT 2701, '投资事项创建', 'investment:create'
    UNION ALL SELECT 2702, '投资事项编辑', 'investment:edit'
    UNION ALL SELECT 2703, '投资论证审批', 'investment:approve'
    UNION ALL SELECT 2704, '投资决策处理', 'investment:decision'
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
SELECT 600, '投资管理', NULL, 'M', '/investment', NULL, 'investment:view', 'Money', 6,
       1, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-2 Investment模块入口', 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.permission = 'investment:view' AND m.deleted = 0
);

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.menu_name, parent.id, 'C', s.path, s.component, s.permission,
       s.icon, s.sort_no, 1, 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-2 Investment模块菜单骨架', 0
FROM (
    SELECT 610 id, '投资机会' menu_name, '/investment/opportunities' path,
           'investment/opportunity/index' component, 'investment:view' permission,
           'Opportunity' icon, 1 sort_no
    UNION ALL SELECT 620, '投资论证', '/investment/feasibility',
           'investment/feasibility/index', 'investment:view', 'DocumentChecked', 2
    UNION ALL SELECT 630, '投资决策', '/investment/decisions',
           'investment/decision/index', 'investment:view', 'Stamp', 3
) s
JOIN sys_menu parent ON parent.permission = 'investment:view'
    AND parent.parent_id IS NULL AND parent.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.path = s.path AND m.deleted = 0
);

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.menu_name, parent.id, 'B', NULL, NULL, s.permission, NULL, s.sort_no,
       0, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-2 Investment模块按钮权限', 0
FROM (
    SELECT 611 id, '投资事项创建' menu_name, '/investment/opportunities' parent_path,
           'investment:create' permission, 1 sort_no
    UNION ALL SELECT 612, '投资事项编辑', '/investment/opportunities',
           'investment:edit', 2
    UNION ALL SELECT 621, '投资论证审批', '/investment/feasibility',
           'investment:approve', 1
    UNION ALL SELECT 631, '投资决策处理', '/investment/decisions',
           'investment:decision', 1
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
       0, 0, 'Sprint 2-2 超级管理员Investment权限', 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'investment:view', 'investment:create', 'investment:edit',
    'investment:approve', 'investment:decision'
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
       0, 0, 'Sprint 2-2 超级管理员Investment菜单', 0
FROM sys_role r
JOIN sys_menu m ON m.id IN (600, 610, 611, 612, 620, 621, 630, 631)
    AND m.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );

COMMIT;
