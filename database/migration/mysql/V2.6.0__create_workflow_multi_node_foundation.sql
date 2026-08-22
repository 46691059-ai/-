-- Sprint 2-3.7-WF3.1: Workflow multi-node runtime foundation.
-- Candidate only. Requires V2.5.5. Does not enable automatic node advancement.
USE enterprise_platform;

CREATE TABLE workflow_transition (
    id BIGINT NOT NULL COMMENT 'Workflow transition ID',
    version_id BIGINT NOT NULL COMMENT 'Frozen workflow version ID',
    transition_code VARCHAR(100) NOT NULL COMMENT 'Stable code within the workflow version',
    transition_name VARCHAR(200) NOT NULL COMMENT 'Transition display name',
    from_node_id BIGINT NOT NULL COMMENT 'Source node ID',
    to_node_id BIGINT NOT NULL COMMENT 'Target node ID',
    trigger_type VARCHAR(30) NOT NULL COMMENT 'APPROVE/REJECT',
    route_type VARCHAR(40) NOT NULL DEFAULT 'DIRECT'
        COMMENT 'DIRECT/CONDITIONAL_RESERVED',
    priority INT NOT NULL DEFAULT 1 COMMENT 'Stable route priority; V2.6 foundation requires positive values',
    condition_config TEXT NULL COMMENT 'Reserved canonical condition configuration; unused for DIRECT routes',
    enabled SMALLINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    created_by VARCHAR(64) NULL COMMENT 'Creator stable identifier',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Creation time',
    updated_by VARCHAR(64) NULL COMMENT 'Updater stable identifier',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Update time',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT 'Logical deletion flag',
    delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '0 when active; row ID after logical deletion',
    remark VARCHAR(500) NULL COMMENT 'Non-sensitive remark',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_transition_code (version_id, transition_code, delete_token),
    UNIQUE KEY uk_workflow_transition_route (
        version_id, from_node_id, trigger_type, priority, delete_token
    ),
    UNIQUE KEY uk_workflow_transition_owner (version_id, id),
    KEY idx_workflow_transition_target (version_id, to_node_id, enabled, deleted),
    KEY idx_workflow_transition_source (version_id, from_node_id, trigger_type, enabled, deleted),
    CONSTRAINT fk_workflow_transition_version FOREIGN KEY (version_id)
        REFERENCES workflow_version(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_transition_from_node FOREIGN KEY (version_id, from_node_id)
        REFERENCES workflow_node(version_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_transition_to_node FOREIGN KEY (version_id, to_node_id)
        REFERENCES workflow_node(version_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_transition_nodes CHECK (from_node_id <> to_node_id),
    CONSTRAINT chk_workflow_transition_trigger CHECK (trigger_type IN ('APPROVE', 'REJECT')),
    CONSTRAINT chk_workflow_transition_route_type CHECK (
        route_type IN ('DIRECT', 'CONDITIONAL_RESERVED')
    ),
    CONSTRAINT chk_workflow_transition_route_config CHECK (
        (route_type = 'DIRECT' AND condition_config IS NULL)
        OR (route_type = 'CONDITIONAL_RESERVED' AND condition_config IS NOT NULL
            AND CHAR_LENGTH(TRIM(condition_config)) > 0)
    ),
    CONSTRAINT chk_workflow_transition_priority CHECK (priority > 0),
    CONSTRAINT chk_workflow_transition_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_workflow_transition_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_transition_optimistic CHECK (version >= 0),
    CONSTRAINT chk_workflow_transition_delete_token CHECK (
        (deleted = 0 AND delete_token = 0)
        OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Frozen workflow-version node transitions';

CREATE TABLE workflow_node_execution (
    id BIGINT NOT NULL COMMENT 'Workflow node execution ID',
    execution_no VARCHAR(100) NOT NULL COMMENT 'Stable external execution number',
    instance_id BIGINT NOT NULL COMMENT 'Workflow instance ID',
    version_id BIGINT NOT NULL COMMENT 'Frozen workflow version ID',
    node_id BIGINT NOT NULL COMMENT 'Executed workflow node ID',
    node_code_snapshot VARCHAR(100) NOT NULL COMMENT 'Frozen node code',
    node_name_snapshot VARCHAR(200) NOT NULL COMMENT 'Frozen node name',
    visit_no INT NOT NULL DEFAULT 1 COMMENT 'Node visit number within this instance',
    previous_execution_id BIGINT NULL COMMENT 'Previous execution in the actual path',
    source_transition_id BIGINT NULL COMMENT 'Transition used to enter this execution',
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED'
        COMMENT 'CREATED/ACTIVE/COMPLETED/REJECTED/CANCELLED/FAILED',
    result VARCHAR(40) NULL COMMENT 'Terminal node result',
    entered_time DATETIME(3) NOT NULL COMMENT 'Node entry time',
    activated_time DATETIME(3) NULL COMMENT 'Task activation time',
    completed_by BIGINT NULL COMMENT 'Completing operator user ID',
    completed_time DATETIME(3) NULL COMMENT 'Terminal time',
    failure_code VARCHAR(64) NULL COMMENT 'Stable non-sensitive failure code for FAILED status',
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
    UNIQUE KEY uk_workflow_node_execution_no (execution_no, delete_token),
    UNIQUE KEY uk_workflow_node_execution_visit (
        instance_id, node_id, visit_no, delete_token
    ),
    UNIQUE KEY uk_workflow_node_execution_owner (instance_id, id),
    KEY idx_workflow_node_execution_status (instance_id, status, entered_time, deleted),
    KEY idx_workflow_node_execution_node (version_id, node_id, status, deleted),
    KEY idx_workflow_node_execution_transition (version_id, source_transition_id, deleted),
    CONSTRAINT fk_workflow_node_execution_instance FOREIGN KEY (version_id, instance_id)
        REFERENCES workflow_instance(version_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_node_execution_node FOREIGN KEY (version_id, node_id)
        REFERENCES workflow_node(version_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_node_execution_previous FOREIGN KEY (instance_id, previous_execution_id)
        REFERENCES workflow_node_execution(instance_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_node_execution_transition FOREIGN KEY (version_id, source_transition_id)
        REFERENCES workflow_transition(version_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_node_execution_visit CHECK (visit_no > 0),
    CONSTRAINT chk_workflow_node_execution_status CHECK (
        status IN ('CREATED', 'ACTIVE', 'COMPLETED', 'REJECTED', 'CANCELLED', 'FAILED')
    ),
    CONSTRAINT chk_workflow_node_execution_times CHECK (
        (status = 'CREATED' AND activated_time IS NULL AND completed_time IS NULL)
        OR (status = 'ACTIVE' AND activated_time IS NOT NULL AND completed_time IS NULL)
        OR (status IN ('COMPLETED', 'REJECTED', 'CANCELLED', 'FAILED')
            AND completed_time IS NOT NULL)
    ),
    CONSTRAINT chk_workflow_node_execution_result CHECK (
        (status IN ('CREATED', 'ACTIVE') AND result IS NULL)
        OR (status IN ('COMPLETED', 'REJECTED', 'CANCELLED', 'FAILED')
            AND result IS NOT NULL AND CHAR_LENGTH(TRIM(result)) > 0)
    ),
    CONSTRAINT chk_workflow_node_execution_failure CHECK (
        failure_code IS NULL OR status = 'FAILED'
    ),
    CONSTRAINT chk_workflow_node_execution_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_node_execution_optimistic CHECK (version >= 0),
    CONSTRAINT chk_workflow_node_execution_delete_token CHECK (
        (deleted = 0 AND delete_token = 0)
        OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Append-only workflow node execution trajectory';
