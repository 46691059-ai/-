-- Sprint 2-3.6: Investment-side Workflow delivery reliability and callback governance.
-- Workflow remains external; no Workflow-owned schema or engine objects are created.
USE enterprise_platform;

ALTER TABLE investment_workflow_outbox
    ADD COLUMN worker_id VARCHAR(100) NULL COMMENT '当前投递Worker实例' AFTER trace_id,
    ADD COLUMN locked_at DATETIME(3) NULL COMMENT '抢占时间' AFTER worker_id,
    ADD COLUMN lock_until DATETIME(3) NULL COMMENT '抢占租约截止时间' AFTER locked_at,
    ADD COLUMN dead_time DATETIME(3) NULL COMMENT '进入死信时间' AFTER lock_until,
    ADD COLUMN dead_reason VARCHAR(200) NULL COMMENT '死信原因码' AFTER dead_time,
    ADD KEY idx_inv_workflow_outbox_lease (status, lock_until, deleted);

ALTER TABLE investment_workflow_inbox
    ADD COLUMN payload_json TEXT NULL COMMENT '已验签原始事件，用于受控重放' AFTER payload_hash,
    ADD COLUMN replay_count INT NOT NULL DEFAULT 0 COMMENT '人工重放次数' AFTER processed_time,
    ADD COLUMN last_replay_time DATETIME(3) NULL COMMENT '最近人工重放时间' AFTER replay_count,
    ADD COLUMN last_replay_by BIGINT NULL COMMENT '最近人工重放人' AFTER last_replay_time,
    ADD COLUMN manual_review_required SMALLINT NOT NULL DEFAULT 0 COMMENT '是否需要人工核查' AFTER last_replay_by,
    ADD KEY idx_inv_workflow_inbox_gap (binding_id, process_status, event_sequence, deleted),
    ADD CONSTRAINT chk_inv_workflow_inbox_replay CHECK (replay_count >= 0),
    ADD CONSTRAINT chk_inv_workflow_inbox_review CHECK (manual_review_required IN (0, 1));

CREATE TABLE investment_workflow_reliability_audit (
    id BIGINT NOT NULL,
    decision_id BIGINT NULL,
    binding_id BIGINT NULL,
    outbox_id BIGINT NULL,
    inbox_id BIGINT NULL,
    audit_type VARCHAR(40) NOT NULL,
    action_source VARCHAR(20) NOT NULL,
    result_status VARCHAR(20) NOT NULL,
    reason_code VARCHAR(100) NULL,
    detail_summary VARCHAR(500) NULL,
    operator_id BIGINT NULL,
    reviewer_id BIGINT NULL,
    ticket_no VARCHAR(100) NULL,
    trace_id VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_inv_workflow_audit_decision (decision_id, create_time, deleted),
    KEY idx_inv_workflow_audit_outbox (outbox_id, create_time, deleted),
    KEY idx_inv_workflow_audit_inbox (inbox_id, create_time, deleted),
    KEY idx_inv_workflow_audit_type (audit_type, result_status, create_time, deleted),
    CONSTRAINT fk_inv_workflow_audit_decision FOREIGN KEY (decision_id) REFERENCES investment_decision(id),
    CONSTRAINT fk_inv_workflow_audit_binding FOREIGN KEY (binding_id) REFERENCES investment_workflow_binding(id),
    CONSTRAINT fk_inv_workflow_audit_outbox FOREIGN KEY (outbox_id) REFERENCES investment_workflow_outbox(id),
    CONSTRAINT fk_inv_workflow_audit_inbox FOREIGN KEY (inbox_id) REFERENCES investment_workflow_inbox(id),
    CONSTRAINT chk_inv_workflow_audit_type CHECK (
        audit_type IN ('AUTO_RETRY', 'DEAD_LETTER', 'INBOX_FAILURE', 'GAP_DETECTED',
                       'GAP_RECOVERED', 'MANUAL_REPLAY', 'SECURITY_REJECTED', 'ADMIN_OPERATION')
    ),
    CONSTRAINT chk_inv_workflow_audit_source CHECK (action_source IN ('SYSTEM', 'ADMIN')),
    CONSTRAINT chk_inv_workflow_audit_result CHECK (result_status IN ('SUCCESS', 'FAILED', 'REJECTED')),
    CONSTRAINT chk_inv_workflow_audit_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Investment Workflow可靠性审计记录';

START TRANSACTION;

INSERT INTO sys_permission (
    id, permission_name, permission_code, permission_type, module_code, status,
    create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
)
SELECT 2748, 'Workflow事件人工重放', 'investment:workflow:replay', 'BUTTON', 'investment', 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-3.6 Workflow可靠性治理权限', 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission
    WHERE permission_code = 'investment:workflow:replay' AND deleted = 0
);

INSERT INTO sys_menu (
    id, menu_name, parent_id, menu_type, path, component, permission, sort_no,
    visible, status, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 639, 'Workflow事件重放', parent.id, 'B', NULL, NULL,
       'investment:workflow:replay', 8, 0, 1,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, 'Sprint 2-3.6 Workflow可靠性治理按钮', 0
FROM sys_menu parent
WHERE parent.path = '/investment/decisions' AND parent.menu_type = 'C' AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu m
      WHERE m.parent_id = parent.id
        AND m.permission = 'investment:workflow:replay' AND m.deleted = 0
  );

INSERT INTO sys_role_permission (
    id, role_id, permission_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 980000000 + r.id * 10000 + p.id, r.id, p.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, '超级管理员Workflow事件重放权限', 0
FROM sys_role r
JOIN sys_permission p
  ON p.permission_code = 'investment:workflow:replay' AND p.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted = 0
  );

INSERT INTO sys_role_menu (
    id, role_id, menu_id, create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 990000000 + r.id * 10000 + m.id, r.id, m.id,
       CURRENT_TIMESTAMP(3), 'system', CURRENT_TIMESTAMP(3), 'system',
       0, 0, '超级管理员Workflow事件重放菜单', 0
FROM sys_role r
JOIN sys_menu m ON m.id = 639 AND m.deleted = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );

COMMIT;
