-- ============================================================
-- Sprint 1-6.1 数据权限运营验收数据
-- 仅允许在开发/测试/验收环境执行，禁止直接用于生产数据库。
-- 目标：PROJECT_MANAGER 使用 CUSTOM，并绑定平台研发团队。
-- ============================================================

USE enterprise_platform;
START TRANSACTION;

-- 安全逻辑删除项目经理角色原有的自定义组织关系，确保脚本可重复执行。
UPDATE sys_role_org ro
JOIN sys_role r ON r.id = ro.role_id AND r.deleted = 0
SET ro.deleted = 1,
    ro.delete_token = ro.id,
    ro.update_time = CURRENT_TIMESTAMP(3),
    ro.update_by = 'sprint-1-6.1-fixture',
    ro.version = ro.version + 1
WHERE r.role_code = 'PROJECT_MANAGER'
  AND ro.deleted = 0;

-- 固定验收关系ID，重复执行时恢复同一条验收记录。
INSERT INTO sys_role_org (
    id, role_id, org_id,
    create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 96001, r.id, o.id,
       CURRENT_TIMESTAMP(3), 'sprint-1-6.1-fixture',
       CURRENT_TIMESTAMP(3), 'sprint-1-6.1-fixture',
       0, 0, '项目经理CUSTOM验收组织', 0
FROM sys_role r
JOIN sys_org o ON o.org_code = 'TEAM-PLATFORM-RD' AND o.deleted = 0 AND o.status = 1
WHERE r.role_code = 'PROJECT_MANAGER' AND r.deleted = 0
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    org_id = VALUES(org_id),
    update_time = CURRENT_TIMESTAMP(3),
    update_by = 'sprint-1-6.1-fixture',
    deleted = 0,
    delete_token = 0,
    remark = '项目经理CUSTOM验收组织',
    version = sys_role_org.version + 1;

UPDATE sys_role
SET data_scope_type = 'CUSTOM',
    update_time = CURRENT_TIMESTAMP(3),
    update_by = 'sprint-1-6.1-fixture',
    version = version + 1
WHERE role_code = 'PROJECT_MANAGER'
  AND deleted = 0
  AND data_scope_type <> 'CUSTOM';

COMMIT;

-- 验收核查：应返回 PROJECT_MANAGER / CUSTOM / 平台研发团队。
SELECT r.role_code, r.data_scope_type, o.id AS org_id, o.org_code, o.org_name
FROM sys_role r
LEFT JOIN sys_role_org ro ON ro.role_id = r.id AND ro.deleted = 0
LEFT JOIN sys_org o ON o.id = ro.org_id AND o.deleted = 0
WHERE r.role_code = 'PROJECT_MANAGER' AND r.deleted = 0;
