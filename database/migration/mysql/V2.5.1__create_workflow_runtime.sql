-- Sprint 2-3.7-WF2.3: Workflow Lite runtime-domain schema.
-- MySQL 8.x. Requires the immutable V2.5.0 Workflow definition baseline.
-- This migration creates Workflow-owned runtime tables only.
USE enterprise_platform;

CREATE TABLE workflow_instance (
    id BIGINT NOT NULL COMMENT 'Workflow instance ID',
    instance_no VARCHAR(100) NOT NULL COMMENT 'Stable external instance number',
    definition_id BIGINT NOT NULL COMMENT 'Workflow definition ID',
    version_id BIGINT NOT NULL COMMENT 'Frozen published workflow version ID',
    definition_code_snapshot VARCHAR(100) NOT NULL COMMENT 'Definition code snapshot',
    definition_version_no INT NOT NULL COMMENT 'Definition version number snapshot',
    business_type VARCHAR(64) NOT NULL COMMENT 'Owning business type',
    business_id VARCHAR(100) NOT NULL COMMENT 'Opaque cross-domain business ID',
    business_key VARCHAR(200) NOT NULL COMMENT 'Stable business key',
    enterprise_id BIGINT NOT NULL COMMENT 'Enterprise isolation ID',
    snapshot_ref VARCHAR(100) NULL COMMENT 'Immutable business snapshot reference',
    snapshot_hash VARCHAR(128) NULL COMMENT 'Business snapshot SHA-256',
    attempt_no INT NOT NULL DEFAULT 1 COMMENT 'Submission attempt number',
    initiator_user_id BIGINT NOT NULL COMMENT 'Initiating user logical reference',
    initiator_org_id BIGINT NOT NULL COMMENT 'Initiating organization snapshot',
    current_node_id BIGINT NULL COMMENT 'Current primary display node',
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED'
        COMMENT 'CREATED/RUNNING/APPROVED/REJECTED/WITHDRAWN/COMPLETED/EXCEPTION',
    result VARCHAR(40) NULL COMMENT 'Terminal workflow result',
    variables_snapshot LONGTEXT NULL COMMENT 'Canonical allow-listed variable JSON snapshot',
    idempotency_key VARCHAR(200) NOT NULL COMMENT 'Start-command idempotency key',
    request_hash VARCHAR(128) NOT NULL COMMENT 'Canonical start request SHA-256',
    event_sequence BIGINT NOT NULL DEFAULT 0 COMMENT 'Last emitted event sequence',
    trace_id VARCHAR(64) NULL COMMENT 'Cross-domain trace ID',
    started_time DATETIME(3) NULL COMMENT 'Runtime start time',
    completed_time DATETIME(3) NULL COMMENT 'Runtime completion time',
    withdrawn_time DATETIME(3) NULL COMMENT 'Runtime withdrawal time',
    created_by VARCHAR(64) NULL COMMENT 'Creator stable identifier',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Creation time',
    updated_by VARCHAR(64) NULL COMMENT 'Updater stable identifier',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Update time',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT 'Logical deletion flag',
    delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '0 when active; row ID after logical deletion',
    remark VARCHAR(500) NULL COMMENT 'Non-sensitive remark',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_instance_no (instance_no, delete_token),
    UNIQUE KEY uk_workflow_instance_idempotency (enterprise_id, idempotency_key, delete_token),
    UNIQUE KEY uk_workflow_instance_business (
        enterprise_id, business_type, business_id, attempt_no, delete_token
    ),
    UNIQUE KEY uk_workflow_instance_owner (version_id, id),
    KEY idx_workflow_instance_status (enterprise_id, status, updated_time, deleted),
    KEY idx_workflow_instance_key (business_type, business_key, attempt_no, deleted),
    KEY idx_workflow_instance_initiator (initiator_user_id, status, updated_time, deleted),
    KEY idx_workflow_instance_definition (definition_id, version_id, deleted),
    CONSTRAINT fk_workflow_instance_version FOREIGN KEY (definition_id, version_id)
        REFERENCES workflow_version(definition_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_instance_current_node FOREIGN KEY (version_id, current_node_id)
        REFERENCES workflow_node(version_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_instance_version_no CHECK (definition_version_no > 0),
    CONSTRAINT chk_workflow_instance_attempt CHECK (attempt_no > 0),
    CONSTRAINT chk_workflow_instance_event_sequence CHECK (event_sequence >= 0),
    CONSTRAINT chk_workflow_instance_status CHECK (
        status IN ('CREATED', 'RUNNING', 'APPROVED', 'REJECTED',
                   'WITHDRAWN', 'COMPLETED', 'EXCEPTION')
    ),
    CONSTRAINT chk_workflow_instance_started CHECK (
        status = 'CREATED' OR started_time IS NOT NULL
    ),
    CONSTRAINT chk_workflow_instance_completed CHECK (
        completed_time IS NULL OR status IN ('APPROVED', 'REJECTED', 'COMPLETED')
    ),
    CONSTRAINT chk_workflow_instance_withdrawn CHECK (
        withdrawn_time IS NULL OR status = 'WITHDRAWN'
    ),
    CONSTRAINT chk_workflow_instance_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_instance_optimistic CHECK (version >= 0),
    CONSTRAINT chk_workflow_instance_delete_token CHECK (
        (deleted = 0 AND delete_token = 0)
        OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Workflow runtime instance';

CREATE TABLE workflow_task (
    id BIGINT NOT NULL COMMENT 'Workflow task ID',
    task_no VARCHAR(100) NOT NULL COMMENT 'Stable external task number',
    instance_id BIGINT NOT NULL COMMENT 'Workflow instance ID',
    version_id BIGINT NOT NULL COMMENT 'Frozen workflow version ID',
    node_id BIGINT NOT NULL COMMENT 'Workflow node ID',
    node_code_snapshot VARCHAR(100) NOT NULL COMMENT 'Node code snapshot',
    node_name_snapshot VARCHAR(200) NOT NULL COMMENT 'Node name snapshot',
    task_round INT NOT NULL DEFAULT 1 COMMENT 'Task round',
    participant_key VARCHAR(128) NOT NULL COMMENT 'Stable participant key',
    assignee_user_id BIGINT NULL COMMENT 'Assigned or claimed user logical reference',
    candidate_snapshot TEXT NOT NULL COMMENT 'Assignment rule and candidate snapshot',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/CLAIMED/APPROVED/REJECTED/CANCELLED/EXPIRED',
    allowed_actions VARCHAR(200) NOT NULL COMMENT 'Allow-listed action snapshot',
    claimed_time DATETIME(3) NULL COMMENT 'Claim time',
    due_time DATETIME(3) NULL COMMENT 'Due time',
    completed_by BIGINT NULL COMMENT 'Completing user logical reference',
    completed_time DATETIME(3) NULL COMMENT 'Completion time',
    decision_result VARCHAR(40) NULL COMMENT 'Approval decision result',
    created_by VARCHAR(64) NULL COMMENT 'Creator stable identifier',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Creation time',
    updated_by VARCHAR(64) NULL COMMENT 'Updater stable identifier',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Update time',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT 'Logical deletion flag',
    delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '0 when active; row ID after logical deletion',
    remark VARCHAR(500) NULL COMMENT 'Non-sensitive remark',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_task_no (task_no, delete_token),
    UNIQUE KEY uk_workflow_task_participant (
        instance_id, node_id, task_round, participant_key, delete_token
    ),
    KEY idx_workflow_task_assignee (assignee_user_id, status, due_time, deleted),
    KEY idx_workflow_task_instance (instance_id, status, created_time, deleted),
    KEY idx_workflow_task_node (node_id, status, deleted),
    CONSTRAINT fk_workflow_task_instance FOREIGN KEY (version_id, instance_id)
        REFERENCES workflow_instance(version_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_task_node FOREIGN KEY (version_id, node_id)
        REFERENCES workflow_node(version_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_task_round CHECK (task_round > 0),
    CONSTRAINT chk_workflow_task_status CHECK (
        status IN ('PENDING', 'CLAIMED', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT chk_workflow_task_completion CHECK (
        (status IN ('PENDING', 'CLAIMED') AND completed_time IS NULL AND completed_by IS NULL)
        OR (status IN ('APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED'))
    ),
    CONSTRAINT chk_workflow_task_claim CHECK (
        status <> 'CLAIMED' OR (assignee_user_id IS NOT NULL AND claimed_time IS NOT NULL)
    ),
    CONSTRAINT chk_workflow_task_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_task_optimistic CHECK (version >= 0),
    CONSTRAINT chk_workflow_task_delete_token CHECK (
        (deleted = 0 AND delete_token = 0)
        OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Workflow runtime approval task';
