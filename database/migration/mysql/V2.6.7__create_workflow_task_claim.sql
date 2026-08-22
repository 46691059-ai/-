-- Sprint 2-3.7-WF4.2: Candidate Pool Claim runtime persistence.
-- Claim only; Release/Transfer/Delegate/Timeout and resolver execution are out of scope.
USE enterprise_platform;

ALTER TABLE workflow_task
    DROP CHECK chk_workflow_task_assignment_mode,
    ADD CONSTRAINT chk_workflow_task_assignment_mode CHECK (
        assignment_mode IN ('DIRECT', 'CANDIDATE_POOL')
        AND (
            (assignment_mode = 'DIRECT'
                AND (node_execution_id IS NULL OR assignee_user_id IS NOT NULL))
            OR
            (assignment_mode = 'CANDIDATE_POOL'
                AND node_execution_id IS NOT NULL
                AND (
                    (status = 'PENDING' AND assignee_user_id IS NULL AND claimed_time IS NULL)
                    OR
                    (status IN ('CLAIMED', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')
                        AND assignee_user_id IS NOT NULL AND claimed_time IS NOT NULL)
                ))
        )
    );

ALTER TABLE workflow_task_candidate_member
    ADD UNIQUE KEY uk_workflow_candidate_member_claim_owner (
        id, pool_id, task_id, instance_id, candidate_user_id
    );

CREATE TABLE workflow_task_claim (
    id BIGINT NOT NULL COMMENT 'Claim ID',
    claim_no VARCHAR(100) NOT NULL COMMENT 'Stable Claim number',
    task_id BIGINT NOT NULL COMMENT 'Claimed task',
    candidate_pool_id BIGINT NOT NULL COMMENT 'Frozen Candidate Pool',
    candidate_member_id BIGINT NOT NULL COMMENT 'Frozen Candidate Member',
    instance_id BIGINT NOT NULL COMMENT 'Workflow instance',
    node_execution_id BIGINT NOT NULL COMMENT 'Active node execution',
    candidate_user_id BIGINT NOT NULL COMMENT 'Claimant user',
    operator_user_id BIGINT NOT NULL COMMENT 'Operator user',
    status VARCHAR(30) NOT NULL COMMENT 'CLAIMED; later RELEASED/CANCELLED reserved',
    claim_time DATETIME(3) NOT NULL COMMENT 'Claim time',
    eligibility_snapshot_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    realtime_eligibility_result TEXT NOT NULL COMMENT 'Non-sensitive current eligibility evidence',
    data_scope_result VARCHAR(1000) NOT NULL COMMENT 'Data-scope decision',
    sod_result VARCHAR(1000) NOT NULL COMMENT 'Segregation-of-duties decision',
    rbac_result VARCHAR(1000) NOT NULL COMMENT 'RBAC decision',
    task_status_before VARCHAR(30) NOT NULL,
    task_status_after VARCHAR(30) NOT NULL,
    pool_status_before VARCHAR(30) NOT NULL,
    pool_status_after VARCHAR(30) NOT NULL,
    task_version_before INT NOT NULL,
    task_version_after INT NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    active_token BIGINT NOT NULL DEFAULT 0 COMMENT '0 for active; Claim ID when terminal',
    created_by VARCHAR(64) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_task_claim_no (claim_no, delete_token),
    UNIQUE KEY uk_workflow_task_claim_active (task_id, active_token),
    UNIQUE KEY uk_workflow_task_claim_idempotency (task_id, idempotency_key, delete_token),
    KEY idx_workflow_task_claim_user (candidate_user_id, status, claim_time, deleted),
    KEY idx_workflow_task_claim_pool (candidate_pool_id, status, deleted),
    KEY idx_workflow_task_claim_trace (trace_id, claim_time),
    CONSTRAINT fk_workflow_task_claim_pool
        FOREIGN KEY (candidate_pool_id, task_id, instance_id)
        REFERENCES workflow_task_candidate_pool(id, task_id, instance_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_task_claim_member
        FOREIGN KEY (candidate_member_id, candidate_pool_id, task_id, instance_id, candidate_user_id)
        REFERENCES workflow_task_candidate_member(id, pool_id, task_id, instance_id, candidate_user_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_task_claim_status CHECK (
        status IN ('CLAIMED', 'RELEASED', 'CANCELLED')
    ),
    CONSTRAINT chk_workflow_task_claim_active CHECK (
        (status = 'CLAIMED' AND active_token = 0)
        OR (status IN ('RELEASED', 'CANCELLED') AND active_token = id)
    ),
    CONSTRAINT chk_workflow_task_claim_transition CHECK (
        task_status_before = 'PENDING'
        AND task_status_after = 'CLAIMED'
        AND pool_status_before = 'AVAILABLE'
        AND pool_status_after = 'CLAIMED'
        AND task_version_before >= 0
        AND task_version_after = task_version_before + 1
    ),
    CONSTRAINT chk_workflow_task_claim_hash CHECK (
        eligibility_snapshot_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_workflow_task_claim_evidence CHECK (
        CHAR_LENGTH(TRIM(realtime_eligibility_result)) > 0
        AND CHAR_LENGTH(TRIM(data_scope_result)) > 0
        AND CHAR_LENGTH(TRIM(sod_result)) > 0
        AND CHAR_LENGTH(TRIM(rbac_result)) > 0
    ),
    CONSTRAINT chk_workflow_task_claim_identity CHECK (
        candidate_user_id > 0 AND operator_user_id > 0
        AND candidate_user_id = operator_user_id
    ),
    CONSTRAINT chk_workflow_task_claim_keys CHECK (
        CHAR_LENGTH(TRIM(idempotency_key)) > 0
        AND CHAR_LENGTH(TRIM(trace_id)) > 0
    ),
    CONSTRAINT chk_workflow_task_claim_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_task_claim_delete_token CHECK (
        (deleted = 0 AND delete_token = 0)
        OR (deleted = 1 AND delete_token = id)
    ),
    CONSTRAINT chk_workflow_task_claim_optimistic CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Workflow Candidate Pool Claim fact';

CREATE TABLE workflow_task_claim_audit (
    id BIGINT NOT NULL COMMENT 'Claim audit event ID',
    event_no VARCHAR(100) NOT NULL,
    task_id BIGINT NULL,
    candidate_pool_id BIGINT NULL,
    claim_id BIGINT NULL,
    candidate_member_id BIGINT NULL,
    instance_id BIGINT NULL,
    node_execution_id BIGINT NULL,
    operator_user_id BIGINT NULL,
    claimant_user_id BIGINT NULL,
    event_type VARCHAR(30) NOT NULL COMMENT 'CLAIM/REJECT; later actions reserved',
    result VARCHAR(30) NOT NULL COMMENT 'SUCCESS/DENIED/CONFLICT/FAILED',
    reason_code VARCHAR(100) NOT NULL,
    frozen_eligibility_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    realtime_eligibility_result TEXT NULL,
    rbac_result VARCHAR(1000) NULL,
    data_scope_result VARCHAR(1000) NULL,
    sod_result VARCHAR(1000) NULL,
    task_status_before VARCHAR(30) NULL,
    task_status_after VARCHAR(30) NULL,
    pool_status_before VARCHAR(30) NULL,
    pool_status_after VARCHAR(30) NULL,
    event_time DATETIME(3) NOT NULL,
    trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    previous_event_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    event_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by VARCHAR(64) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_task_claim_audit_no (event_no, delete_token),
    UNIQUE KEY uk_workflow_task_claim_audit_idem (task_id, event_type, idempotency_key, delete_token),
    KEY idx_workflow_task_claim_audit_task (task_id, event_time),
    KEY idx_workflow_task_claim_audit_trace (trace_id, event_time),
    KEY idx_workflow_task_claim_audit_operator (operator_user_id, event_time),
    CONSTRAINT fk_workflow_task_claim_audit_claim FOREIGN KEY (claim_id)
        REFERENCES workflow_task_claim(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_task_claim_audit_event CHECK (
        event_type IN ('CLAIM', 'REJECT', 'RELEASE', 'CANCEL', 'TRANSFER', 'DELEGATE')
        AND result IN ('SUCCESS', 'DENIED', 'CONFLICT', 'FAILED')
    ),
    CONSTRAINT chk_workflow_task_claim_audit_hash CHECK (
        (frozen_eligibility_hash IS NULL
            OR frozen_eligibility_hash REGEXP '^[0-9a-f]{64}$')
        AND (previous_event_hash IS NULL
            OR previous_event_hash REGEXP '^[0-9a-f]{64}$')
        AND event_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_workflow_task_claim_audit_reason CHECK (
        CHAR_LENGTH(TRIM(reason_code)) > 0
        AND CHAR_LENGTH(TRIM(trace_id)) > 0
        AND CHAR_LENGTH(TRIM(idempotency_key)) > 0
    ),
    CONSTRAINT chk_workflow_task_claim_audit_immutable CHECK (
        deleted = 0 AND delete_token = 0 AND version = 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Append-only Workflow Claim audit events';
