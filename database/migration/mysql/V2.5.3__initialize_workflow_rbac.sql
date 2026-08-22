-- Sprint 2-3.7-WF2.5: initialize Workflow Lite RBAC permissions and menus.
-- MySQL 8.x. Requires the immutable V2.5.2 Workflow task-action baseline.
-- This migration changes RBAC data only. It creates no Workflow business table.
USE enterprise_platform;
SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO sys_permission (
    id, permission_name, permission_code, permission_type, resource_path, http_method,
    module_code, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.permission_name, s.permission_code, s.permission_type,
       s.resource_path, s.http_method, 'workflow', 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-3.7-WF2.5 Workflow RBAC', 0
FROM (
    SELECT 25301 id, '流程治理中心访问' permission_name,
           'workflow:manage' permission_code, 'BUTTON' permission_type,
           NULL resource_path, NULL http_method
    UNION ALL SELECT 25302, '流程定义查看', 'workflow:definition:view',
           'API', '/workflow/definitions/**', 'GET'
    UNION ALL SELECT 25303, '流程定义新建', 'workflow:definition:create',
           'API', '/workflow/definitions', 'POST'
    UNION ALL SELECT 25304, '流程定义编辑', 'workflow:definition:edit',
           'API', '/workflow/definitions/**', NULL
    UNION ALL SELECT 25305, '流程定义发布', 'workflow:definition:publish',
           'API', '/workflow/definitions/**', 'POST'
    UNION ALL SELECT 25306, '流程实例与任务查看', 'workflow:view',
           'API', '/workflow/**', 'GET'
    UNION ALL SELECT 25307, '流程启动', 'workflow:start',
           'API', '/workflow/instances', 'POST'
    UNION ALL SELECT 25308, '流程任务审批', 'workflow:approve',
           'API', '/workflow/tasks/**', 'POST'
    UNION ALL SELECT 25309, '流程任务撤回', 'workflow:withdraw',
           'API', '/workflow/tasks/**', 'POST'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p
    WHERE p.permission_code = s.permission_code AND p.deleted = 0
);

-- M/C menu stable key: active path. sys_menu has no menu_code column.
INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 25300, '流程治理中心', NULL, 'M', '/workflow', NULL,
       'workflow:manage', 'Connection', 10, 1, 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-3.7-WF2.5 Workflow目录', 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m
    WHERE m.parent_id IS NULL AND m.path = '/workflow' AND m.deleted = 0
);

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.menu_name, parent.id, 'C', s.path, s.component,
       s.permission, s.icon, s.sort_no, 1, 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-3.7-WF2.5 Workflow功能菜单', 0
FROM (
    SELECT 25310 id, '流程定义' menu_name, '/workflow/definitions' path,
           'workflow/definition/index' component,
           'workflow:definition:view' permission, 'Document' icon, 1 sort_no
    UNION ALL SELECT 25320, '流程实例', '/workflow/instances',
           'workflow/instance/index', 'workflow:view', 'List', 2
    UNION ALL SELECT 25330, '待办任务', '/workflow/tasks',
           'workflow/task/index', 'workflow:view', 'Checked', 3
) s
JOIN sys_menu parent
  ON parent.parent_id IS NULL AND parent.path = '/workflow'
 AND parent.menu_type = 'M' AND parent.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.path = s.path AND m.deleted = 0
);

-- B menu stable key: active parent path plus permission code.
INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.menu_name, parent.id, 'B', NULL, NULL,
       s.permission, NULL, s.sort_no, 0, 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-3.7-WF2.5 Workflow按钮', 0
FROM (
    SELECT 25311 id, '/workflow/definitions' parent_path,
           '流程定义新建' menu_name, 'workflow:definition:create' permission, 1 sort_no
    UNION ALL SELECT 25312, '/workflow/definitions',
           '流程定义编辑', 'workflow:definition:edit', 2
    UNION ALL SELECT 25313, '/workflow/definitions',
           '流程定义发布', 'workflow:definition:publish', 3
    UNION ALL SELECT 25321, '/workflow/instances',
           '流程启动', 'workflow:start', 1
    UNION ALL SELECT 25331, '/workflow/tasks',
           '任务审批', 'workflow:approve', 1
    UNION ALL SELECT 25332, '/workflow/tasks',
           '任务撤回', 'workflow:withdraw', 2
) s
JOIN sys_menu parent
  ON parent.path = s.parent_path AND parent.menu_type = 'C' AND parent.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m
    WHERE m.parent_id = parent.id AND m.menu_type = 'B'
      AND m.permission = s.permission AND m.deleted = 0
);

-- Only SUPER_ADMIN receives the complete Workflow permission matrix by default.
INSERT INTO sys_role_permission (
    id, role_id, permission_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 253100000000000000 + r.id * 100000 + p.id,
       r.id, p.id, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, '超级管理员Workflow权限', 0
FROM sys_role r
JOIN sys_permission p
  ON p.permission_code IN (
      'workflow:manage',
      'workflow:definition:view',
      'workflow:definition:create',
      'workflow:definition:edit',
      'workflow:definition:publish',
      'workflow:view',
      'workflow:start',
      'workflow:approve',
      'workflow:withdraw'
  ) AND p.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted = 0
  );

-- Grant the root, feature menus, and buttons under the stable /workflow root.
INSERT INTO sys_role_menu (
    id, role_id, menu_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 253200000000000000 + r.id * 100000 + m.id,
       r.id, m.id, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, '超级管理员Workflow菜单', 0
FROM sys_role r
JOIN sys_menu root
  ON root.parent_id IS NULL AND root.path = '/workflow'
 AND root.menu_type = 'M' AND root.deleted = 0
JOIN sys_menu m
  ON m.deleted = 0 AND (
      m.id = root.id
      OR m.parent_id = root.id
      OR EXISTS (
          SELECT 1 FROM sys_menu feature
          WHERE feature.id = m.parent_id
            AND feature.parent_id = root.id
            AND feature.deleted = 0
      )
  )
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );

COMMIT;
