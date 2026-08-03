-- Sprint 1-3.1 RBAC补全：项目按钮权限、个人中心、标准角色基线授权
-- 不包含测试账号，不修改表结构，可重复执行。
USE enterprise_platform;
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_permission (
    id, permission_name, permission_code, permission_type, resource_path, http_method,
    module_code, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.permission_name, s.permission_code, 'API', s.resource_path, s.http_method,
       s.module_code, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM (
    SELECT 2401 AS id, '项目新增' AS permission_name, 'project:add' AS permission_code,
           '/projects' AS resource_path, 'POST' AS http_method, 'project' AS module_code
    UNION ALL SELECT 2402, '项目修改', 'project:edit', '/projects/**', 'PUT', 'project'
    UNION ALL SELECT 2403, '个人信息查看', 'profile:view', '/profile', 'GET', 'profile'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.permission_code = s.permission_code AND p.deleted = 0
);

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.menu_name, parent.id, 'B', NULL, NULL, s.permission, NULL, s.sort_no,
       0, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM (
    SELECT 411 AS id, '项目新增' AS menu_name, 'project:add' AS permission, 1 AS sort_no
    UNION ALL SELECT 412, '项目修改', 'project:edit', 2
) s
JOIN sys_menu parent ON parent.permission = 'project:lifecycle:list' AND parent.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.permission = s.permission AND m.deleted = 0
);

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, icon, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 950, '个人中心', NULL, 'C', '/profile', 'profile/index', 'profile:view',
       'UserFilled', 10, 1, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.permission = 'profile:view' AND m.deleted = 0
);

-- 超级管理员补齐新增资源。
INSERT INTO sys_role_permission (
    id, role_id, permission_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 10000 + p.id, r.id, p.id, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('project:add','project:edit','profile:view') AND p.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted = 0
  );

INSERT INTO sys_role_menu (
    id, role_id, menu_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 20000 + m.id, r.id, m.id, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, NULL, 0
FROM sys_role r
JOIN sys_menu m ON m.permission IN ('project:add','project:edit','profile:view') AND m.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );

-- 标准角色数据范围基线。
UPDATE sys_role SET data_scope_type = 'ORG_AND_CHILDREN', update_time = CURRENT_TIMESTAMP(3),
    update_by = 'system', version = version + 1
WHERE role_code IN ('PROJECT_MANAGER','DEPT_MANAGER') AND deleted = 0
  AND data_scope_type <> 'ORG_AND_CHILDREN';

UPDATE sys_role SET data_scope_type = 'SELF', update_time = CURRENT_TIMESTAMP(3),
    update_by = 'system', version = version + 1
WHERE role_code = 'COMMON_USER' AND deleted = 0 AND data_scope_type <> 'SELF';

-- 验收角色最小权限约束：项目经理不得继承投资、数据资产、系统管理资源；普通员工仅保留个人中心。
UPDATE sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id AND r.role_code = 'PROJECT_MANAGER' AND r.deleted = 0
JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0
SET rp.deleted = 1, rp.delete_token = rp.id, rp.update_time = CURRENT_TIMESTAMP(3),
    rp.update_by = 'system', rp.version = rp.version + 1
WHERE rp.deleted = 0
  AND (p.permission_code LIKE 'investment:%'
       OR p.permission_code LIKE 'data_asset:%'
       OR p.permission_code LIKE 'system:%');

UPDATE sys_role_menu rm
JOIN sys_role r ON r.id = rm.role_id AND r.role_code = 'PROJECT_MANAGER' AND r.deleted = 0
JOIN sys_menu m ON m.id = rm.menu_id AND m.deleted = 0
SET rm.deleted = 1, rm.delete_token = rm.id, rm.update_time = CURRENT_TIMESTAMP(3),
    rm.update_by = 'system', rm.version = rm.version + 1
WHERE rm.deleted = 0
  AND (m.permission LIKE 'investment:%'
       OR m.permission LIKE 'data_asset:%'
       OR m.permission LIKE 'system:%');

UPDATE sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id AND r.role_code = 'COMMON_USER' AND r.deleted = 0
JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0
SET rp.deleted = 1, rp.delete_token = rp.id, rp.update_time = CURRENT_TIMESTAMP(3),
    rp.update_by = 'system', rp.version = rp.version + 1
WHERE rp.deleted = 0 AND p.permission_code <> 'profile:view';

UPDATE sys_role_menu rm
JOIN sys_role r ON r.id = rm.role_id AND r.role_code = 'COMMON_USER' AND r.deleted = 0
JOIN sys_menu m ON m.id = rm.menu_id AND m.deleted = 0
SET rm.deleted = 1, rm.delete_token = rm.id, rm.update_time = CURRENT_TIMESTAMP(3),
    rm.update_by = 'system', rm.version = rm.version + 1
WHERE rm.deleted = 0 AND m.permission <> 'profile:view';

-- 项目经理：项目查看、新增、修改。
INSERT INTO sys_role_permission (
    id, role_id, permission_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 400000 + r.id * 10000 + p.id, r.id, p.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'project:view','project:lifecycle:list','project:add','project:edit'
) AND p.deleted = 0
WHERE r.role_code = 'PROJECT_MANAGER' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted = 0
  );

INSERT INTO sys_role_menu (
    id, role_id, menu_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 300000 + r.id * 10000 + m.id, r.id, m.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM sys_role r
JOIN sys_menu m ON m.permission IN (
    'project:view','project:lifecycle:list','project:add','project:edit'
) AND m.deleted = 0
WHERE r.role_code = 'PROJECT_MANAGER' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );

-- 普通员工：仅个人中心基础权限。
INSERT INTO sys_role_permission (
    id, role_id, permission_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 400000 + r.id * 10000 + p.id, r.id, p.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'profile:view' AND p.deleted = 0
WHERE r.role_code = 'COMMON_USER' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted = 0
  );

INSERT INTO sys_role_menu (
    id, role_id, menu_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 300000 + r.id * 10000 + m.id, r.id, m.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM sys_role r
JOIN sys_menu m ON m.permission = 'profile:view' AND m.deleted = 0
WHERE r.role_code = 'COMMON_USER' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );

-- 部门负责人：组织管理查看，数据范围由ORG_AND_CHILDREN约束。
INSERT INTO sys_role_permission (
    id, role_id, permission_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 400000 + r.id * 10000 + p.id, r.id, p.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('system:view','system:org:view') AND p.deleted = 0
WHERE r.role_code = 'DEPT_MANAGER' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted = 0
  );

INSERT INTO sys_role_menu (
    id, role_id, menu_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 300000 + r.id * 10000 + m.id, r.id, m.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system', 0, 0, NULL, 0
FROM sys_role r
JOIN sys_menu m ON m.permission IN ('system:view','system:org:view') AND m.deleted = 0
WHERE r.role_code = 'DEPT_MANAGER' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );

COMMIT;
