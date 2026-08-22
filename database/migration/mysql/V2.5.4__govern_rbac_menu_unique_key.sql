-- Sprint 2-3.7-WF2.5.3: govern the stable RBAC menu business key.
-- MySQL 8.x. Requires the unchanged V2.5.3 Workflow RBAC candidate.
-- All data-quality checks intentionally run before the first permanent DDL.
USE enterprise_platform;
SET NAMES utf8mb4;

-- Phase A-C: build the reviewed, connection-local mapping registry.
CREATE TEMPORARY TABLE tmp_v254_menu_mapping (
    id BIGINT NOT NULL,
    expected_parent_id BIGINT NULL,
    expected_menu_type VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    expected_menu_name VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    expected_path VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    expected_permission VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    expected_deleted SMALLINT NOT NULL,
    expected_delete_token BIGINT NOT NULL,
    menu_code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tmp_v254_menu_code (menu_code, expected_delete_token),
    CONSTRAINT chk_tmp_v254_menu_code_format CHECK (
        menu_code REGEXP '^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){0,7}$'
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

INSERT INTO tmp_v254_menu_mapping (
    id, expected_parent_id, expected_menu_type, expected_menu_name,
    expected_path, expected_permission, expected_deleted, expected_delete_token, menu_code
) VALUES
    (100, NULL, 'M', '首页驾驶舱', '/home', 'dashboard:view', 0, 0, 'home'),
    (200, NULL, 'M', '党建治理', '/party', 'party:view', 0, 0, 'party'),
    (300, NULL, 'M', '组织人事', '/hr', 'hr:view', 0, 0, 'hr'),
    (400, NULL, 'M', '项目管理', '/projects', 'project:view', 0, 0, 'project'),
    (410, 400, 'C', '项目库', '/projects', 'project:lifecycle:list', 0, 0, 'project.lifecycle'),
    (411, 410, 'B', '项目新增', NULL, 'project:add', 0, 0, 'project.lifecycle.create'),
    (412, 410, 'B', '项目修改', NULL, 'project:edit', 0, 0, 'project.lifecycle.edit'),
    (500, NULL, 'M', '经营管理', '/operation', 'operation:view', 0, 0, 'operation'),
    (600, NULL, 'M', '投资管理', '/investment', 'investment:view', 0, 0, 'investment'),
    (610, 600, 'C', '投资机会', '/investment/opportunities', 'investment:view', 0, 0, 'investment.opportunity'),
    (611, 610, 'B', '投资事项创建', NULL, 'investment:create', 0, 0, 'investment.opportunity.investment_create'),
    (612, 610, 'B', '投资事项编辑', NULL, 'investment:edit', 0, 0, 'investment.opportunity.investment_edit'),
    (613, 610, 'B', '投资机会创建', NULL, 'investment:opportunity:create', 0, 0, 'investment.opportunity.create'),
    (614, 610, 'B', '投资机会审核', NULL, 'investment:opportunity:review', 0, 0, 'investment.opportunity.review'),
    (615, 610, 'B', '投资机会转项目', NULL, 'investment:opportunity:convert', 0, 0, 'investment.opportunity.convert'),
    (620, 600, 'C', '投资论证', '/investment/feasibility', 'investment:view', 0, 0, 'investment.feasibility'),
    (621, 620, 'B', '投资论证审批', NULL, 'investment:approve', 0, 0, 'investment.feasibility.approve'),
    (622, 620, 'B', '可研查看', NULL, 'investment:feasibility:view', 0, 0, 'investment.feasibility.view'),
    (623, 620, 'B', '可研编辑', NULL, 'investment:feasibility:edit', 0, 0, 'investment.feasibility.edit'),
    (630, 600, 'C', '投资决策', '/investment/decisions', 'investment:view', 0, 0, 'investment.decision'),
    (631, 630, 'B', '投资决策处理', NULL, 'investment:decision', 0, 0, 'investment.decision.handle'),
    (632, 630, 'B', '决策查看', NULL, 'investment:decision:view', 0, 0, 'investment.decision.view'),
    (633, 630, 'B', '决策创建', NULL, 'investment:decision:create', 0, 0, 'investment.decision.create'),
    (634, 630, 'B', '决策提交', NULL, 'investment:decision:submit', 0, 0, 'investment.decision.submit'),
    (635, 630, 'B', '决策撤回', NULL, 'investment:decision:withdraw', 0, 0, 'investment.decision.withdraw'),
    (636, 630, 'B', '决策审批', NULL, 'investment:decision:approve', 0, 0, 'investment.decision.approve'),
    (637, 630, 'B', '附条件管理', NULL, 'investment:decision:condition', 0, 0, 'investment.decision.condition'),
    (638, 630, 'B', '决策归档', NULL, 'investment:decision:archive', 0, 0, 'investment.decision.archive'),
    (639, 630, 'B', 'Workflow事件重放', NULL, 'investment:workflow:replay', 0, 0, 'investment.decision.workflow_replay'),
    (640, 600, 'C', '尽职调查', '/investment/due-diligence', 'investment:due_diligence:view', 0, 0, 'investment.due_diligence'),
    (641, 640, 'B', '尽调查看', NULL, 'investment:due_diligence:view', 0, 0, 'investment.due_diligence.view'),
    (642, 640, 'B', '尽调编辑', NULL, 'investment:due_diligence:edit', 0, 0, 'investment.due_diligence.edit'),
    (650, 600, 'C', '投资方案', '/investment/schemes', 'investment:scheme:view', 0, 0, 'investment.scheme'),
    (651, 650, 'B', '投资方案查看', NULL, 'investment:scheme:view', 0, 0, 'investment.scheme.view'),
    (652, 650, 'B', '投资方案编辑', NULL, 'investment:scheme:edit', 0, 0, 'investment.scheme.edit'),
    (700, NULL, 'M', '数据资产', '/data-assets', 'data_asset:view', 0, 0, 'data_asset'),
    (800, NULL, 'M', '风险合规', '/risk', 'risk:view', 0, 0, 'risk'),
    (900, NULL, 'M', '系统管理', '/system', 'system:view', 0, 0, 'system'),
    (910, 900, 'C', '用户管理', '/system/user', 'system:user:view', 0, 0, 'system.user'),
    (911, 910, 'B', '用户新增', NULL, 'user:add', 0, 0, 'system.user.create'),
    (912, 910, 'B', '用户编辑及授权', NULL, 'user:edit', 0, 0, 'system.user.edit'),
    (913, 910, 'B', '用户删除', NULL, 'user:delete', 0, 0, 'system.user.delete'),
    (914, 910, 'B', '用户密码重置', NULL, 'user:resetPassword', 0, 0, 'system.user.reset_password'),
    (920, 900, 'C', '组织管理', '/system/org', 'system:org:view', 0, 0, 'system.org'),
    (921, 920, 'B', '组织新增', NULL, 'org:add', 0, 0, 'system.org.create'),
    (922, 920, 'B', '组织编辑', NULL, 'org:edit', 0, 0, 'system.org.edit'),
    (923, 920, 'B', '组织删除', NULL, 'org:delete', 0, 0, 'system.org.delete'),
    (930, 900, 'C', '角色权限', '/system/role', 'system:role:view', 0, 0, 'system.role'),
    (931, 930, 'B', '角色新增', NULL, 'role:add', 0, 0, 'system.role.create'),
    (932, 930, 'B', '角色编辑', NULL, 'role:edit', 0, 0, 'system.role.edit'),
    (933, 930, 'B', '角色删除', NULL, 'role:delete', 0, 0, 'system.role.delete'),
    (934, 930, 'B', '权限配置', NULL, 'role:permission', 0, 0, 'system.role.permission'),
    (940, 900, 'C', '菜单管理', '/system/menu', 'system:menu:view', 0, 0, 'system.menu'),
    (941, 940, 'B', '菜单新增', NULL, 'menu:add', 0, 0, 'system.menu.create'),
    (942, 940, 'B', '菜单修改', NULL, 'menu:edit', 0, 0, 'system.menu.edit'),
    (943, 940, 'B', '菜单删除', NULL, 'menu:delete', 0, 0, 'system.menu.delete'),
    (950, NULL, 'C', '个人中心', '/profile', 'profile:view', 0, 0, 'profile'),
    (960, 900, 'C', '操作日志', '/system/log', 'system:log:view', 0, 0, 'system.log'),
    (961, 960, 'B', '日志查询', NULL, 'system:log:query', 0, 0, 'system.log.query'),
    (962, 960, 'B', '日志详情', NULL, 'system:log:detail', 0, 0, 'system.log.detail'),
    (963, 960, 'B', '日志删除', NULL, 'system:log:delete', 0, 0, 'system.log.delete'),
    (25300, NULL, 'M', '流程治理中心', '/workflow', 'workflow:manage', 0, 0, 'workflow'),
    (25310, 25300, 'C', '流程定义', '/workflow/definitions', 'workflow:definition:view', 0, 0, 'workflow.definition'),
    (25311, 25310, 'B', '流程定义新建', NULL, 'workflow:definition:create', 0, 0, 'workflow.definition.create'),
    (25312, 25310, 'B', '流程定义编辑', NULL, 'workflow:definition:edit', 0, 0, 'workflow.definition.edit'),
    (25313, 25310, 'B', '流程定义发布', NULL, 'workflow:definition:publish', 0, 0, 'workflow.definition.publish'),
    (25320, 25300, 'C', '流程实例', '/workflow/instances', 'workflow:view', 0, 0, 'workflow.instance'),
    (25321, 25320, 'B', '流程启动', NULL, 'workflow:start', 0, 0, 'workflow.instance.start'),
    (25330, 25300, 'C', '待办任务', '/workflow/tasks', 'workflow:view', 0, 0, 'workflow.task'),
    (25331, 25330, 'B', '任务审批', NULL, 'workflow:approve', 0, 0, 'workflow.task.approve'),
    (25332, 25330, 'B', '任务撤回', NULL, 'workflow:withdraw', 0, 0, 'workflow.task.withdraw');

-- Diagnostic result sets are emitted before the assertion table rejects a violation.
SELECT s.id, s.menu_name, s.path, s.parent_id, s.menu_type, s.delete_token, NULL AS target_menu_code,
       'UNMAPPED_SYS_MENU' AS violation
FROM sys_menu s
LEFT JOIN tmp_v254_menu_mapping m ON m.id = s.id
WHERE m.id IS NULL;

SELECT m.id, COALESCE(s.menu_name, m.expected_menu_name) AS menu_name,
       s.path, s.parent_id, s.menu_type, s.delete_token, m.menu_code AS target_menu_code,
       'MAPPING_FINGERPRINT_MISMATCH' AS violation
FROM tmp_v254_menu_mapping m
LEFT JOIN sys_menu s
  ON s.id = m.id
 AND (s.parent_id <=> m.expected_parent_id)
 AND s.menu_type = m.expected_menu_type
 AND s.menu_name = m.expected_menu_name
 AND (s.path <=> m.expected_path)
 AND (s.permission <=> m.expected_permission)
 AND s.deleted = m.expected_deleted
 AND s.delete_token = m.expected_delete_token
WHERE s.id IS NULL;

SELECT s.id AS menu_id, s.menu_name, s.path, s.parent_id,
       s.menu_type, s.delete_token, m.menu_code AS target_menu_code,
       'DUPLICATE_ACTIVE_ROUTE_IDENTITY' AS violation
FROM sys_menu s
JOIN (
    SELECT menu_type, path
    FROM sys_menu
    WHERE deleted = 0 AND menu_type IN ('M', 'C')
    GROUP BY menu_type, path
    HAVING path IS NULL OR path = '' OR COUNT(*) > 1
) duplicate_route
  ON duplicate_route.menu_type = s.menu_type
 AND (duplicate_route.path <=> s.path)
LEFT JOIN tmp_v254_menu_mapping m ON m.id = s.id
WHERE s.deleted = 0
ORDER BY s.menu_type, s.path, s.id;

SELECT s.id AS menu_id, s.menu_name, s.path, s.parent_id,
       s.menu_type, s.delete_token, m.menu_code AS target_menu_code,
       'DUPLICATE_ACTIVE_BUTTON_IDENTITY' AS violation
FROM sys_menu s
JOIN (
    SELECT parent_id, permission
    FROM sys_menu
    WHERE deleted = 0 AND menu_type = 'B'
    GROUP BY parent_id, permission
    HAVING parent_id IS NULL OR permission IS NULL OR permission = '' OR COUNT(*) > 1
) duplicate_button
  ON (duplicate_button.parent_id <=> s.parent_id)
 AND (duplicate_button.permission <=> s.permission)
LEFT JOIN tmp_v254_menu_mapping m ON m.id = s.id
WHERE s.deleted = 0 AND s.menu_type = 'B'
ORDER BY s.parent_id, s.permission, s.id;

CREATE TEMPORARY TABLE tmp_v254_menu_assertion (
    assertion_name VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    violation_count BIGINT NOT NULL,
    PRIMARY KEY (assertion_name),
    CONSTRAINT chk_tmp_v254_menu_assertion CHECK (violation_count = 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Phase B-D: every violation below must fail before ALTER TABLE is reachable.
INSERT INTO tmp_v254_menu_assertion
SELECT 'sys_menu_already_has_menu_code', COUNT(*)
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'sys_menu' AND column_name = 'menu_code';

INSERT INTO tmp_v254_menu_assertion
SELECT 'unmapped_sys_menu', COUNT(*)
FROM sys_menu s LEFT JOIN tmp_v254_menu_mapping m ON m.id = s.id
WHERE m.id IS NULL;

INSERT INTO tmp_v254_menu_assertion
SELECT 'mapping_without_sys_menu', COUNT(*)
FROM tmp_v254_menu_mapping m LEFT JOIN sys_menu s ON s.id = m.id
WHERE s.id IS NULL;

INSERT INTO tmp_v254_menu_assertion
SELECT 'mapping_fingerprint_mismatch', COUNT(*)
FROM tmp_v254_menu_mapping m
JOIN sys_menu s ON s.id = m.id
WHERE NOT (
    (s.parent_id <=> m.expected_parent_id)
    AND s.menu_type = m.expected_menu_type
    AND s.menu_name = m.expected_menu_name
    AND (s.path <=> m.expected_path)
    AND (s.permission <=> m.expected_permission)
    AND s.deleted = m.expected_deleted
    AND s.delete_token = m.expected_delete_token
);

INSERT INTO tmp_v254_menu_assertion
SELECT 'invalid_menu_type_or_parent', COUNT(*)
FROM sys_menu c LEFT JOIN sys_menu p ON p.id = c.parent_id
WHERE c.menu_type NOT IN ('M', 'C', 'B')
   OR (c.parent_id IS NOT NULL AND p.id IS NULL)
   OR (c.parent_id IS NOT NULL AND p.menu_type = 'B')
   OR (c.menu_type = 'B' AND c.parent_id IS NULL);

INSERT INTO tmp_v254_menu_assertion
SELECT 'invalid_delete_token_semantics', COUNT(*)
FROM sys_menu
WHERE deleted NOT IN (0, 1)
   OR (deleted = 0 AND delete_token <> 0)
   OR (deleted = 1 AND delete_token = 0);

INSERT INTO tmp_v254_menu_assertion
SELECT 'duplicate_active_route_identity', COUNT(*)
FROM (
    SELECT menu_type, path
    FROM sys_menu
    WHERE deleted = 0 AND menu_type IN ('M', 'C')
    GROUP BY menu_type, path
    HAVING path IS NULL OR path = '' OR COUNT(*) > 1
) duplicate_route;

INSERT INTO tmp_v254_menu_assertion
SELECT 'duplicate_active_button_identity', COUNT(*)
FROM (
    SELECT parent_id, permission
    FROM sys_menu
    WHERE deleted = 0 AND menu_type = 'B'
    GROUP BY parent_id, permission
    HAVING parent_id IS NULL OR permission IS NULL OR permission = '' OR COUNT(*) > 1
) duplicate_button;

-- Phase E-F: first permanent DDL, reachable only after all preflight assertions pass.
ALTER TABLE sys_menu
    ADD COLUMN menu_code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '菜单稳定业务编码，创建后原则上不可变' AFTER id;

-- Phase G: controlled backfill; identity inference is deliberately forbidden.
UPDATE sys_menu s
JOIN tmp_v254_menu_mapping m
  ON m.id = s.id
 AND (s.parent_id <=> m.expected_parent_id)
 AND s.menu_type = m.expected_menu_type
 AND s.menu_name = m.expected_menu_name
 AND (s.path <=> m.expected_path)
 AND (s.permission <=> m.expected_permission)
 AND s.deleted = m.expected_deleted
 AND s.delete_token = m.expected_delete_token
SET s.menu_code = m.menu_code;

-- Phase H: post-backfill assertions run before the column is tightened.
INSERT INTO tmp_v254_menu_assertion
SELECT 'menu_code_null_or_blank_after_backfill', COUNT(*)
FROM sys_menu
WHERE menu_code IS NULL OR TRIM(menu_code) = '';

INSERT INTO tmp_v254_menu_assertion
SELECT 'menu_code_duplicate_after_backfill', COUNT(*)
FROM (
    SELECT menu_code, delete_token
    FROM sys_menu
    GROUP BY menu_code, delete_token
    HAVING COUNT(*) > 1
) duplicate_code;

INSERT INTO tmp_v254_menu_assertion
SELECT 'menu_code_invalid_format_after_backfill', COUNT(*)
FROM sys_menu
WHERE NOT (menu_code REGEXP '^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){0,7}$');

-- Phase I-J: enforce the stable key. No strict deleted/delete_token CHECK is added.
ALTER TABLE sys_menu
    MODIFY COLUMN menu_code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '菜单稳定业务编码，创建后原则上不可变',
    ADD CONSTRAINT chk_sys_menu_code_format CHECK (
        menu_code REGEXP '^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){0,7}$'
    ),
    ADD UNIQUE KEY uk_sys_menu_code (menu_code, delete_token);

DROP TEMPORARY TABLE tmp_v254_menu_assertion;
DROP TEMPORARY TABLE tmp_v254_menu_mapping;
