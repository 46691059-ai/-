-- 仅用于Sprint 1-4独立验收库，禁止在生产数据库执行。
USE enterprise_platform;
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_user (
    id, username, password, real_name, org_id, status, token_version, login_fail_count,
    create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
)
SELECT 90000, 'admin', '$2b$12$ZBf/FzENOlKIdThxLibJDOQLMIpMkeNmfEXIXUP4rvuPC24IYpUWy',
       '菜单验收管理员', org.id, 1, 0, 0,
       CURRENT_TIMESTAMP(3), 'acceptance-test', CURRENT_TIMESTAMP(3), 'acceptance-test',
       0, 0, 'Sprint 1-4菜单中心验收账号', 0
FROM sys_org org
WHERE org.org_code = 'ROOT001' AND org.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.username = 'admin' AND u.deleted = 0);

INSERT INTO sys_user_role (
    id, user_id, role_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 93000, u.id, r.id, CURRENT_TIMESTAMP(3), 'acceptance-test',
       CURRENT_TIMESTAMP(3), 'acceptance-test', 0, 0, 'Sprint 1-4菜单中心验收授权', 0
FROM sys_user u
JOIN sys_role r ON r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
WHERE u.username = 'admin' AND u.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_role ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id AND ur.deleted = 0
  );

COMMIT;
