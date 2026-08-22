-- Sprint 2-3.7-WF3.4: persist immutable task-assignment evidence.
-- Candidate only. Requires immutable V2.6.1. USER is the only executable strategy.
USE enterprise_platform;

ALTER TABLE workflow_task
    ADD UNIQUE KEY uk_workflow_task_assignment_owner (
        id, instance_id, version_id, node_id, node_execution_id
    );

CREATE TABLE workflow_task_assignment_snapshot (
    id BIGINT NOT NULL COMMENT 'Assignment snapshot ID',
    task_id BIGINT NOT NULL COMMENT 'Workflow task ID',
    instance_id BIGINT NOT NULL COMMENT 'Workflow instance ID snapshot',
    version_id BIGINT NOT NULL COMMENT 'Frozen workflow version ID',
    node_id BIGINT NOT NULL COMMENT 'Workflow node ID',
    node_execution_id BIGINT NOT NULL COMMENT 'Owning node execution ID',
    strategy_type VARCHAR(30) NOT NULL COMMENT 'USER/ROLE/POSITION/ORG',
    target_type VARCHAR(30) NOT NULL COMMENT 'USER/ROLE/POSITION/ORG',
    target_snapshot TEXT NOT NULL COMMENT 'Canonical non-sensitive target snapshot',
    resolved_users TEXT NOT NULL COMMENT 'Canonical sorted user ID array',
    resolved_user_count INT NOT NULL COMMENT 'Resolved user count',
    resolve_time DATETIME(3) NOT NULL COMMENT 'Resolution time',
    audit_info TEXT NOT NULL COMMENT 'Non-sensitive assignment reason and evidence',
    trace_id VARCHAR(64) NULL COMMENT 'Cross-domain trace ID',
    created_by VARCHAR(64) NULL COMMENT 'Creator stable identifier',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Creation time',
    updated_by VARCHAR(64) NULL COMMENT 'Updater stable identifier',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Update time',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT 'Logical deletion flag',
    delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '0 when active; row ID after logical deletion',
    remark VARCHAR(500) NULL COMMENT 'Non-sensitive remark',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_task_assignment_task (task_id, delete_token),
    UNIQUE KEY uk_workflow_task_assignment_owner (
        task_id, instance_id, version_id, node_id, node_execution_id, delete_token
    ),
    KEY idx_workflow_task_assignment_execution (node_execution_id, deleted),
    KEY idx_workflow_task_assignment_strategy (strategy_type, resolve_time, deleted),
    CONSTRAINT fk_workflow_task_assignment_task
        FOREIGN KEY (task_id, instance_id, version_id, node_id, node_execution_id)
        REFERENCES workflow_task(id, instance_id, version_id, node_id, node_execution_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_task_assignment_strategy CHECK (
        strategy_type IN ('USER', 'ROLE', 'POSITION', 'ORG')
    ),
    CONSTRAINT chk_workflow_task_assignment_target CHECK (
        target_type IN ('USER', 'ROLE', 'POSITION', 'ORG')
        AND target_type = strategy_type
    ),
    CONSTRAINT chk_workflow_task_assignment_target_snapshot CHECK (
        CHAR_LENGTH(TRIM(target_snapshot)) > 0
    ),
    CONSTRAINT chk_workflow_task_assignment_users CHECK (
        resolved_user_count > 0 AND CHAR_LENGTH(TRIM(resolved_users)) > 2
    ),
    CONSTRAINT chk_workflow_task_assignment_audit CHECK (
        CHAR_LENGTH(TRIM(audit_info)) > 0
    ),
    CONSTRAINT chk_workflow_task_assignment_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_task_assignment_optimistic CHECK (version >= 0),
    CONSTRAINT chk_workflow_task_assignment_delete_token CHECK (
        (deleted = 0 AND delete_token = 0)
        OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Immutable Workflow task assignment snapshot';
