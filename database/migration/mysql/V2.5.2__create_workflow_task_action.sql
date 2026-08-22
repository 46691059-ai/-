-- Sprint 2-3.7-WF2.4: Workflow Lite task-action audit schema.
-- MySQL 8.x. Requires the immutable V2.5.1 Workflow runtime baseline.
-- This migration creates a Workflow-owned immutable action record only.
USE enterprise_platform;

CREATE TABLE workflow_task_action (
    id BIGINT NOT NULL COMMENT 'Workflow task action ID',
    action_no VARCHAR(100) NOT NULL COMMENT 'Stable external action number',
    task_id BIGINT NOT NULL COMMENT 'Workflow task ID',
    instance_id BIGINT NOT NULL COMMENT 'Workflow instance ID snapshot',
    action_type VARCHAR(30) NOT NULL COMMENT 'APPROVE/REJECT/WITHDRAW',
    operator_user_id BIGINT NOT NULL COMMENT 'Action operator user ID',
    operator_org_id BIGINT NOT NULL COMMENT 'Action operator organization snapshot',
    action_comment VARCHAR(1000) NULL COMMENT 'Non-sensitive approval comment',
    action_time DATETIME(3) NOT NULL COMMENT 'Business action time',
    idempotency_key VARCHAR(200) NOT NULL COMMENT 'Client action idempotency key',
    request_hash VARCHAR(128) NOT NULL COMMENT 'Canonical action request SHA-256',
    trace_id VARCHAR(64) NULL COMMENT 'Cross-domain trace ID',
    created_by VARCHAR(64) NULL COMMENT 'Creator stable identifier',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Creation time',
    updated_by VARCHAR(64) NULL COMMENT 'Updater stable identifier',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Update time',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT 'Logical deletion flag',
    delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '0 when active; row ID after logical deletion',
    remark VARCHAR(500) NULL COMMENT 'Audit remark',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_task_action_no (action_no, delete_token),
    UNIQUE KEY uk_workflow_task_action_idempotency (task_id, idempotency_key, delete_token),
    KEY idx_workflow_task_action_instance (instance_id, action_time, deleted),
    KEY idx_workflow_task_action_operator (operator_user_id, action_time, deleted),
    KEY idx_workflow_task_action_task_type (task_id, action_type, action_time, deleted),
    CONSTRAINT fk_workflow_task_action_task FOREIGN KEY (task_id)
        REFERENCES workflow_task(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_task_action_instance FOREIGN KEY (instance_id)
        REFERENCES workflow_instance(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_task_action_type CHECK (
        action_type IN ('APPROVE', 'REJECT', 'WITHDRAW')
    ),
    CONSTRAINT chk_workflow_task_action_operator CHECK (
        operator_user_id > 0 AND operator_org_id > 0
    ),
    CONSTRAINT chk_workflow_task_action_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_task_action_optimistic CHECK (version >= 0),
    CONSTRAINT chk_workflow_task_action_delete_token CHECK (
        (deleted = 0 AND delete_token = 0)
        OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Immutable Workflow task approval action';
