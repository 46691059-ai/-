-- 仅用于Sprint 1-3.1专用验收数据库，请勿在生产库执行。
-- 验收完成后应删除以下测试用户、员工及团队数据。
USE enterprise_platform;
SET NAMES utf8mb4;
START TRANSACTION;

-- 数字产业部下属测试团队。
INSERT INTO sys_org (
    id, org_code, org_name, org_type, parent_id, leader_id, tree_path, tree_level,
    sort_no, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.org_code, s.org_name, 'PROJECT_TEAM', parent.id, NULL,
       CONCAT(parent.tree_path, parent.id, '/'), parent.tree_level + 1,
       s.sort_no, 1, CURRENT_TIMESTAMP(3), 'acceptance-test', CURRENT_TIMESTAMP(3), 'acceptance-test',
       0, 0, 'Sprint 1-3.1验收数据', 0
FROM (
    SELECT 10301 AS id, 'TEAM-DATA-LABEL' AS org_code, '数据标注团队' AS org_name, 1 AS sort_no
    UNION ALL SELECT 10302, 'TEAM-DATA-COLLECT', '数据采集团队', 2
    UNION ALL SELECT 10303, 'TEAM-PLATFORM-RD', '平台研发团队', 3
) s
JOIN sys_org parent ON parent.org_code = 'DEPT003' AND parent.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_org o WHERE o.org_code = s.org_code AND o.deleted = 0);

INSERT INTO hr_employee (
    id, employee_no, name, org_id, employee_type, status,
    create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
)
SELECT s.id, s.employee_no, s.name, org.id, 'FORMAL', 'ACTIVE',
       CURRENT_TIMESTAMP(3), 'acceptance-test', CURRENT_TIMESTAMP(3), 'acceptance-test',
       0, 0, 'Sprint 1-3.1验收员工', 0
FROM (
    SELECT 91001 AS id, 'TEST-PM-001' AS employee_no, '项目经理测试' AS name, 'TEAM-PLATFORM-RD' AS org_code
    UNION ALL SELECT 91002, 'TEST-EMP-001', '普通员工测试', 'TEAM-DATA-LABEL'
    UNION ALL SELECT 91003, 'TEST-DM-001', '数字产业部负责人测试', 'DEPT003'
) s
JOIN sys_org org ON org.org_code = s.org_code AND org.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM hr_employee e WHERE e.employee_no = s.employee_no AND e.deleted = 0);

-- 密码摘要为独立随机验收口令的BCrypt cost=12结果，仓库不保存明文口令。
INSERT INTO sys_user (
    id, username, password, real_name, phone, org_id, employee_id, status,
    token_version, login_fail_count, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.username, s.password_hash, employee.name, NULL, employee.org_id, employee.id, 1,
       0, 0, CURRENT_TIMESTAMP(3), 'acceptance-test', CURRENT_TIMESTAMP(3), 'acceptance-test',
       0, 0, 'Sprint 1-3.1验收账号', 0
FROM (
    SELECT 90001 AS id, 'project_manager' AS username,
           '$2b$12$tK12vet9KZTzd0zwAOu3v.cXTCgLs2nfQwvMrDH3bKZA7UMzKVKIe' AS password_hash,
           'TEST-PM-001' AS employee_no
    UNION ALL SELECT 90002, 'employee_test',
           '$2b$12$ikt7mIeyabPbnUZw0bW7WOs2oz0EKn6GuwRp3.h6xhLYXQWo2caxm', 'TEST-EMP-001'
    UNION ALL SELECT 90003, 'digital_manager',
           '$2b$12$LRch0sj9L1oAoXXOZmVq1.hKcUE5a5oCaS6OiB9q6H2tXbWb6Mgxy', 'TEST-DM-001'
) s
JOIN hr_employee employee ON employee.employee_no = s.employee_no AND employee.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.username = s.username AND u.deleted = 0);

INSERT INTO sys_user_role (
    id, user_id, role_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, user_row.id, role_row.id,
       CURRENT_TIMESTAMP(3), 'acceptance-test', CURRENT_TIMESTAMP(3), 'acceptance-test',
       0, 0, 'Sprint 1-3.1验收授权', 0
FROM (
    SELECT 93001 AS id, 'project_manager' AS username, 'PROJECT_MANAGER' AS role_code
    UNION ALL SELECT 93002, 'employee_test', 'COMMON_USER'
    UNION ALL SELECT 93003, 'digital_manager', 'DEPT_MANAGER'
) s
JOIN sys_user user_row ON user_row.username = s.username AND user_row.deleted = 0
JOIN sys_role role_row ON role_row.role_code = s.role_code AND role_row.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user_role ur
    WHERE ur.user_id = user_row.id AND ur.role_id = role_row.id AND ur.deleted = 0
);

COMMIT;

-- 验收核对（只读）：角色、权限及组织范围应与三个场景一致。
SELECT u.username, r.role_code, r.data_scope_type,
       GROUP_CONCAT(DISTINCT p.permission_code ORDER BY p.permission_code) AS permissions
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.deleted = 0
LEFT JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0
WHERE u.username IN ('project_manager', 'employee_test', 'digital_manager') AND u.deleted = 0
GROUP BY u.username, r.role_code, r.data_scope_type
ORDER BY u.username;

SELECT child.id, child.org_code, child.org_name, child.org_type
FROM sys_org manager_org
JOIN sys_user manager_user ON manager_user.org_id = manager_org.id
JOIN sys_org child ON (
    child.id = manager_org.id
    OR child.tree_path LIKE CONCAT(manager_org.tree_path, manager_org.id, '/%')
)
WHERE manager_user.username = 'digital_manager'
  AND manager_user.deleted = 0 AND manager_org.deleted = 0 AND child.deleted = 0
ORDER BY child.tree_level, child.sort_no, child.id;
