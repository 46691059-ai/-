-- Sprint 2-3.4: canonical decision states and approval-closure RBAC metadata.
-- No Workflow-owned schema objects are created.
USE enterprise_platform;

ALTER TABLE investment_decision
    DROP CHECK chk_inv_decision_approval_status,
    ADD CONSTRAINT chk_inv_decision_approval_status CHECK (
        approval_status IN (
            'DRAFT', 'SUBMITTED', 'IN_APPROVAL', 'APPROVED', 'REJECTED',
            'WITHDRAWN', 'ARCHIVED',
            'NOT_SUBMITTED', 'MATERIAL_REVIEW', 'ROUTE_CONFIRMED',
            'SUBMITTING', 'IN_WORKFLOW', 'IN_DECISION', 'CONDITION_PENDING',
            'CONDITIONAL_PENDING', 'RETURNED', 'DEFERRED', 'SUPERSEDED', 'COMPLETED'
        )
    );

START TRANSACTION;

INSERT INTO sys_permission (
    id, permission_name, permission_code, permission_type, module_code, status,
    create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
)
SELECT s.id, s.name, s.code, 'BUTTON', 'investment', 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-3.4 投资决策审批闭环权限', 0
FROM (
    SELECT 2745 id, '投资决策审批' name, 'investment:decision:approve' code
    UNION ALL SELECT 2746, '投资决策附条件管理', 'investment:decision:condition'
    UNION ALL SELECT 2747, '投资决策归档', 'investment:decision:archive'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.permission_code = s.code AND p.deleted = 0
);

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT s.id, s.name, parent.id, 'B', NULL, NULL, s.permission, s.sort_no,
       0, 1, CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-3.4 投资决策审批闭环按钮', 0
FROM (
    SELECT 636 id, '决策审批' name, 'investment:decision:approve' permission, 5 sort_no
    UNION ALL SELECT 637, '附条件管理', 'investment:decision:condition', 6
    UNION ALL SELECT 638, '决策归档', 'investment:decision:archive', 7
) s
JOIN sys_menu parent
  ON parent.path = '/investment/decisions' AND parent.menu_type = 'C' AND parent.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m
    WHERE m.parent_id = parent.id AND m.permission = s.permission AND m.deleted = 0
);

INSERT INTO sys_role_permission (
    id, role_id, permission_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 960000000 + r.id * 10000 + p.id, r.id, p.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, '超级管理员投资决策审批闭环权限', 0
FROM sys_role r
JOIN sys_permission p
  ON p.permission_code IN (
      'investment:decision:approve',
      'investment:decision:condition',
      'investment:decision:archive'
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
SELECT 970000000 + r.id * 10000 + m.id, r.id, m.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, '超级管理员投资决策审批闭环菜单', 0
FROM sys_role r
JOIN sys_menu m ON m.id IN (636, 637, 638) AND m.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );

COMMIT;
