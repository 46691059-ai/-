-- Sprint 2-3.7-WF4.1: Candidate Pool persistence foundation only.
-- Claim, ROLE/POSITION/ORG resolver execution and Investment integration are intentionally absent.
USE enterprise_platform;

ALTER TABLE workflow_task
    ADD COLUMN assignment_mode VARCHAR(30) NOT NULL DEFAULT 'DIRECT'
        COMMENT 'DIRECT/CANDIDATE_POOL; Candidate Pool tasks remain unclaimed in V2.6.6'
        AFTER candidate_snapshot,
    ADD KEY idx_workflow_task_assignment_mode (assignment_mode, status, deleted),
    ADD CONSTRAINT chk_workflow_task_assignment_mode CHECK (
        assignment_mode IN ('DIRECT', 'CANDIDATE_POOL')
        AND (
            (assignment_mode = 'DIRECT'
                AND (node_execution_id IS NULL OR assignee_user_id IS NOT NULL))
            OR
            (assignment_mode = 'CANDIDATE_POOL'
                AND node_execution_id IS NOT NULL
                AND assignee_user_id IS NULL
                AND claimed_time IS NULL
                AND status = 'PENDING')
        )
    );

-- V2.6.5 remains immutable. Its USER+DIRECT invariant is preserved while the
-- future Candidate Pool combinations are allow-listed and kept unreachable by
-- the current Static Resolver Registry.
ALTER TABLE workflow_instance_resolver_binding
    DROP CHECK chk_workflow_resolver_binding_mode,
    ADD UNIQUE KEY uk_workflow_resolver_binding_candidate_owner (
        id, binding_set_id, instance_id, strategy_type, resolver_mode
    ),
    ADD CONSTRAINT chk_workflow_resolver_binding_mode CHECK (
        resolver_mode IN ('DIRECT', 'CANDIDATE_POOL')
        AND (
            (strategy_type = 'USER' AND resolver_mode = 'DIRECT')
            OR
            (strategy_type IN ('ROLE', 'POSITION', 'ORG')
                AND resolver_mode = 'CANDIDATE_POOL')
        )
    );

ALTER TABLE workflow_node_resolver_binding_snapshot
    DROP CHECK chk_workflow_node_resolver_binding_strategy,
    DROP CHECK chk_workflow_node_resolver_binding_mode,
    ADD UNIQUE KEY uk_workflow_node_resolver_candidate_owner (
        id, instance_id, node_id, resolver_binding_id, strategy_type, resolver_mode
    ),
    ADD CONSTRAINT chk_workflow_node_resolver_binding_strategy CHECK (
        strategy_type = target_type
        AND (
            (strategy_type = 'USER' AND resolver_mode = 'DIRECT')
            OR
            (strategy_type IN ('ROLE', 'POSITION', 'ORG')
                AND resolver_mode = 'CANDIDATE_POOL')
        )
    ),
    ADD CONSTRAINT chk_workflow_node_resolver_binding_mode CHECK (
        resolver_mode IN ('DIRECT', 'CANDIDATE_POOL')
    );

ALTER TABLE workflow_task_assignment_snapshot
    ADD UNIQUE KEY uk_workflow_task_assignment_candidate_owner (
        id, task_id, instance_id, version_id, node_id, node_execution_id
    );

CREATE TABLE workflow_task_candidate_pool (
    id BIGINT NOT NULL COMMENT 'Candidate Pool ID',
    pool_no VARCHAR(100) NOT NULL COMMENT 'Stable external Candidate Pool number',
    task_id BIGINT NOT NULL COMMENT 'Owning Workflow task ID',
    instance_id BIGINT NOT NULL COMMENT 'Owning Workflow instance ID',
    version_id BIGINT NOT NULL COMMENT 'Frozen Workflow definition version ID',
    node_id BIGINT NOT NULL COMMENT 'Owning Workflow node ID',
    node_execution_id BIGINT NOT NULL COMMENT 'Owning node execution ID',
    binding_set_id BIGINT NOT NULL COMMENT 'Frozen resolver binding set ID',
    resolver_binding_id BIGINT NOT NULL COMMENT 'Frozen exact resolver binding ID',
    node_resolver_binding_id BIGINT NOT NULL COMMENT 'Frozen node resolver binding ID',
    assignment_snapshot_id BIGINT NOT NULL COMMENT 'Immutable assignment snapshot ID',
    assignment_mode VARCHAR(30) NOT NULL COMMENT 'Always CANDIDATE_POOL',
    strategy_type VARCHAR(30) NOT NULL COMMENT 'ROLE/POSITION/ORG',
    resolver_code VARCHAR(64) NOT NULL COMMENT 'Frozen resolver code',
    resolver_version VARCHAR(64) NOT NULL COMMENT 'Frozen resolver version',
    contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Frozen resolver contract SHA-256',
    rule_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Frozen node assignment rule SHA-256',
    candidate_count INT NOT NULL COMMENT 'Frozen Candidate Member count',
    generated_time DATETIME(3) NOT NULL COMMENT 'Candidate resolution time',
    effective_time DATETIME(3) NOT NULL COMMENT 'Pool availability start time',
    expires_time DATETIME(3) NULL COMMENT 'Optional Pool expiry time',
    pool_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Canonical Candidate Pool SHA-256',
    status VARCHAR(30) NOT NULL COMMENT 'CREATED/AVAILABLE; later states are reserved',
    audit_info TEXT NOT NULL COMMENT 'Non-sensitive frozen assignment evidence',
    created_by VARCHAR(64) NULL COMMENT 'Creator stable identifier',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Creation time',
    updated_by VARCHAR(64) NULL COMMENT 'Updater stable identifier',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Update time',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT 'Logical deletion flag',
    delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '0 when active; row ID after logical deletion',
    remark VARCHAR(500) NULL COMMENT 'Non-sensitive remark',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_candidate_pool_no (pool_no, delete_token),
    UNIQUE KEY uk_workflow_candidate_pool_task (task_id, delete_token),
    UNIQUE KEY uk_workflow_candidate_pool_owner (id, task_id, instance_id),
    KEY idx_workflow_candidate_pool_instance (instance_id, status, deleted),
    KEY idx_workflow_candidate_pool_expiry (expires_time, status, deleted),
    KEY idx_workflow_candidate_pool_resolver (
        resolver_code, resolver_version, strategy_type, status, deleted
    ),
    CONSTRAINT fk_workflow_candidate_pool_task
        FOREIGN KEY (task_id, instance_id, version_id, node_id, node_execution_id)
        REFERENCES workflow_task(id, instance_id, version_id, node_id, node_execution_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_candidate_pool_resolver
        FOREIGN KEY (
            resolver_binding_id, binding_set_id, instance_id, strategy_type, assignment_mode
        )
        REFERENCES workflow_instance_resolver_binding(
            id, binding_set_id, instance_id, strategy_type, resolver_mode
        ) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_candidate_pool_node_resolver
        FOREIGN KEY (
            node_resolver_binding_id, instance_id, node_id,
            resolver_binding_id, strategy_type, assignment_mode
        )
        REFERENCES workflow_node_resolver_binding_snapshot(
            id, instance_id, node_id, resolver_binding_id, strategy_type, resolver_mode
        ) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_candidate_pool_assignment
        FOREIGN KEY (
            assignment_snapshot_id, task_id, instance_id,
            version_id, node_id, node_execution_id
        )
        REFERENCES workflow_task_assignment_snapshot(
            id, task_id, instance_id, version_id, node_id, node_execution_id
        ) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_candidate_pool_mode CHECK (assignment_mode = 'CANDIDATE_POOL'),
    CONSTRAINT chk_workflow_candidate_pool_strategy CHECK (
        strategy_type IN ('ROLE', 'POSITION', 'ORG')
    ),
    CONSTRAINT chk_workflow_candidate_pool_hashes CHECK (
        contract_hash REGEXP '^[0-9a-f]{64}$'
        AND rule_hash REGEXP '^[0-9a-f]{64}$'
        AND pool_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_workflow_candidate_pool_count CHECK (candidate_count > 0),
    CONSTRAINT chk_workflow_candidate_pool_time CHECK (
        effective_time >= generated_time
        AND (expires_time IS NULL OR expires_time > effective_time)
    ),
    CONSTRAINT chk_workflow_candidate_pool_status CHECK (
        status IN ('CREATED', 'AVAILABLE', 'CLAIMED', 'EXPIRED', 'CANCELLED', 'CLOSED')
    ),
    CONSTRAINT chk_workflow_candidate_pool_audit CHECK (CHAR_LENGTH(TRIM(audit_info)) > 0),
    CONSTRAINT chk_workflow_candidate_pool_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_candidate_pool_optimistic CHECK (version >= 0),
    CONSTRAINT chk_workflow_candidate_pool_delete_token CHECK (
        (deleted = 0 AND delete_token = 0)
        OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Immutable Workflow Candidate Pool frozen at task creation';

CREATE TABLE workflow_task_candidate_member (
    id BIGINT NOT NULL COMMENT 'Candidate Member ID',
    pool_id BIGINT NOT NULL COMMENT 'Owning Candidate Pool ID',
    task_id BIGINT NOT NULL COMMENT 'Owning Workflow task ID',
    instance_id BIGINT NOT NULL COMMENT 'Owning Workflow instance ID',
    candidate_user_id BIGINT NOT NULL COMMENT 'Candidate user logical reference',
    source_type VARCHAR(30) NOT NULL COMMENT 'ROLE/POSITION/ORG',
    source_ref_snapshot VARCHAR(200) NOT NULL COMMENT 'Frozen source stable business reference',
    org_id_snapshot BIGINT NULL COMMENT 'Frozen organization logical reference',
    position_id_snapshot BIGINT NULL COMMENT 'Frozen position logical reference',
    role_id_snapshot BIGINT NULL COMMENT 'Frozen role logical reference',
    eligibility_snapshot TEXT NOT NULL COMMENT 'Canonical non-sensitive eligibility facts',
    eligibility_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Eligibility snapshot SHA-256',
    sort_order INT NOT NULL COMMENT 'Deterministic Candidate ordering',
    generated_time DATETIME(3) NOT NULL COMMENT 'Candidate resolution time',
    status VARCHAR(30) NOT NULL COMMENT 'INCLUDED/CANCELLED/SECURITY_BLOCKED',
    audit_info TEXT NOT NULL COMMENT 'Non-sensitive Candidate evidence',
    created_by VARCHAR(64) NULL COMMENT 'Creator stable identifier',
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Creation time',
    updated_by VARCHAR(64) NULL COMMENT 'Updater stable identifier',
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Update time',
    deleted SMALLINT NOT NULL DEFAULT 0 COMMENT 'Logical deletion flag',
    delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '0 when active; row ID after logical deletion',
    remark VARCHAR(500) NULL COMMENT 'Non-sensitive remark',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_candidate_member_user (
        pool_id, candidate_user_id, delete_token
    ),
    UNIQUE KEY uk_workflow_candidate_member_order (
        pool_id, sort_order, delete_token
    ),
    KEY idx_workflow_candidate_member_task (task_id, status, sort_order, deleted),
    KEY idx_workflow_candidate_member_user (candidate_user_id, status, deleted),
    CONSTRAINT fk_workflow_candidate_member_pool
        FOREIGN KEY (pool_id, task_id, instance_id)
        REFERENCES workflow_task_candidate_pool(id, task_id, instance_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_candidate_member_user CHECK (candidate_user_id > 0),
    CONSTRAINT chk_workflow_candidate_member_source CHECK (
        source_type IN ('ROLE', 'POSITION', 'ORG')
    ),
    CONSTRAINT chk_workflow_candidate_member_source_ref CHECK (
        CHAR_LENGTH(TRIM(source_ref_snapshot)) > 0
    ),
    CONSTRAINT chk_workflow_candidate_member_eligibility CHECK (
        CHAR_LENGTH(TRIM(eligibility_snapshot)) > 0
        AND eligibility_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_workflow_candidate_member_order CHECK (sort_order > 0),
    CONSTRAINT chk_workflow_candidate_member_status CHECK (
        status IN ('INCLUDED', 'CANCELLED', 'SECURITY_BLOCKED')
    ),
    CONSTRAINT chk_workflow_candidate_member_audit CHECK (CHAR_LENGTH(TRIM(audit_info)) > 0),
    CONSTRAINT chk_workflow_candidate_member_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_candidate_member_optimistic CHECK (version >= 0),
    CONSTRAINT chk_workflow_candidate_member_delete_token CHECK (
        (deleted = 0 AND delete_token = 0)
        OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Immutable Candidate members frozen with a Workflow Candidate Pool';
